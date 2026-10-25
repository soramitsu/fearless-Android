package jp.co.soramitsu.coredb.migrations

import android.database.Cursor
import android.database.sqlite.SQLiteException
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Direct SQLite boundaries; the separate DB71 tests exercise the real Room/secret graph. */
class LegacyUsersRecoveryArchiveTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val names = mutableListOf<String>()

    @After
    fun removeOwnedDatabases() { names.forEach(context::deleteDatabase) }

    @Test
    fun canonicalRenamePreservesRowidAndAllOriginalSqliteStorageClasses() = withDatabase { db ->
        db.createUsers()
        db.execSQL(
            "INSERT INTO users(rowid,address,username,publicKey,cryptoType,position) VALUES(17, ?, ?, ?, ?, ?)",
            arrayOf("address ' 雪", "name\n雪", byteArrayOf(0, -1, 37), "retained-non-integer", Long.MAX_VALUE)
        )
        val before = db.rows("SELECT rowid,address,username,publicKey,cryptoType,position FROM users")
        assertEquals(LegacyUsersRecoveryMigration.Source.CANONICAL, LegacyUsersRecoveryMigration.requireSafeSourceAndTarget(db))
        LegacyUsersRecoveryMigration.migrate(db)
        assertEquals(before, db.rows("SELECT rowid,address,username,publicKey,cryptoType,position FROM legacy_users_recovery"))
        assertEquals(listOf(listOf("3:PRESERVED_PENDING_REVIEW")), db.rows("SELECT recoveryState FROM legacy_users_recovery"))
        assertEquals(0L, db.scalar("SELECT COUNT(*) FROM sqlite_master WHERE name = 'users'"))
        assertEquals(1L, db.scalar("SELECT COUNT(*) FROM sqlite_master WHERE name = 'sqlite_autoindex_legacy_users_recovery_1' AND tbl_name = 'legacy_users_recovery'"))
        requireExactReleasedRoomSchema(db, "Renamed identity ledger", listOf(RELEASED_LEGACY_USERS_RECOVERY_TABLE), ::IllegalStateException)
        assertFalse(db.inTransaction())
    }

    @Test
    fun absentSourceCreatesEmptyLedgerWithoutInventingIdentityRows() = withDatabase { db ->
        assertEquals(LegacyUsersRecoveryMigration.Source.ABSENT, LegacyUsersRecoveryMigration.requireSafeSourceAndTarget(db))
        LegacyUsersRecoveryMigration.migrate(db)
        assertEquals(0L, db.scalar("SELECT COUNT(*) FROM legacy_users_recovery"))
        assertEquals(0L, db.scalar("SELECT COUNT(*) FROM sqlite_master WHERE name = 'users'"))
        requireExactReleasedRoomSchema(db, "Empty identity ledger", listOf(RELEASED_LEGACY_USERS_RECOVERY_TABLE), ::IllegalStateException)
    }

    @Test
    fun metadataOnlyRenameRetainsMoreThanTheOldRowAndCellCopyBounds() = withDatabase { db ->
        db.createUsers()
        val count = V2MigrationLimits.PRODUCTION.maxAccountPlans + 1
        val bytes = TON_UPGRADE_MAX_CELL_PAYLOAD_BYTES + 1
        db.beginTransaction()
        try {
            db.compileStatement("INSERT INTO users(address,username,publicKey,cryptoType,position) VALUES(?, 'Saved', 'public', 0, ?)").use { insert ->
                repeat(count) { index ->
                    insert.bindString(1, "saved-$index")
                    insert.bindLong(2, index.toLong())
                    insert.executeInsert()
                }
            }
            db.execSQL("UPDATE users SET publicKey=zeroblob(?) WHERE address='saved-0'", arrayOf(bytes))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        LegacyUsersRecoveryMigration.migrate(db)
        assertEquals(count.toLong(), db.scalar("SELECT COUNT(*) FROM legacy_users_recovery"))
        assertEquals(1L, db.scalar("SELECT COUNT(*) FROM legacy_users_recovery WHERE address='saved-0' AND typeof(publicKey)='blob' AND length(publicKey)=$bytes AND publicKey=zeroblob($bytes)"))
        assertEquals(count.toLong(), db.scalar("SELECT COUNT(*) FROM legacy_users_recovery WHERE recoveryState='PRESERVED_PENDING_REVIEW'"))
    }

    @Test
    fun unsupportedOrdinaryIdentityColumnsRemainUntouchedBesideEmptyLedger() = withDatabase { db ->
        db.execSQL("CREATE TABLE users(address TEXT NOT NULL PRIMARY KEY, username TEXT, unknownPublicKey BLOB, cryptoType INTEGER, position INTEGER)")
        db.execSQL("INSERT INTO users(rowid,address,username,unknownPublicKey,cryptoType,position) VALUES(21, 'saved', NULL, X'00FF', 'unknown', -4)")
        val sourceRows = db.rows("SELECT rowid,* FROM users")
        val sourceSql = db.rows("SELECT type,name,tbl_name,sql FROM sqlite_master WHERE tbl_name='users' ORDER BY type")
        assertEquals(LegacyUsersRecoveryMigration.Source.RAW_ARCHIVE, LegacyUsersRecoveryMigration.requireSafeSourceAndTarget(db))
        LegacyUsersRecoveryMigration.migrate(db)
        assertTrue(LegacyUsersRecoveryMigration.requireSafeRawArchive(db))
        assertEquals(sourceRows, db.rows("SELECT rowid,* FROM users"))
        assertEquals(sourceSql, db.rows("SELECT type,name,tbl_name,sql FROM sqlite_master WHERE tbl_name='users' ORDER BY type"))
        assertEquals(0L, db.scalar("SELECT COUNT(*) FROM legacy_users_recovery"))
    }

    @Test
    fun nonCanonicalConstraintsStayRawInsteadOfFailingTheLaterLedgerContract() {
        listOf(" COLLATE NOCASE", " CHECK(length(username) > 0)").forEach { constraint ->
            withDatabase { db ->
                db.createUsers(constraint)
                db.execSQL("INSERT INTO users VALUES('saved', 'Legacy', 'public', 0, 3)")
                val before = db.rows("SELECT rowid,* FROM users")
                assertEquals(LegacyUsersRecoveryMigration.Source.RAW_ARCHIVE, LegacyUsersRecoveryMigration.requireSafeSourceAndTarget(db))
                LegacyUsersRecoveryMigration.migrate(db)
                assertEquals(before, db.rows("SELECT rowid,* FROM users"))
                assertEquals(0L, db.scalar("SELECT COUNT(*) FROM legacy_users_recovery"))
                assertTrue(LegacyUsersRecoveryMigration.requireSafeRawArchive(db))
            }
        }
    }

    @Test
    fun failureAfterRenameRollsBackOwnedTransactionAndSameSourceCanRetry() = withDatabase { db ->
        db.createUsers()
        db.execSQL("INSERT INTO users VALUES('saved', 'Legacy', 'public', 0, 3)")
        val before = db.rows("SELECT rowid,* FROM users")
        val original = SQLiteException("Owned failure after canonical identity rename")
        val failing = object : SupportSQLiteDatabase by db {
            override fun execSQL(sql: String) {
                if (sql.contains("ADD COLUMN", ignoreCase = true)) throw original
                db.execSQL(sql)
            }
        }
        assertSame(original, assertThrows(SQLiteException::class.java) { LegacyUsersRecoveryMigration.migrate(failing) })
        assertFalse(db.inTransaction())
        assertEquals(before, db.rows("SELECT rowid,* FROM users"))
        assertEquals(0L, db.scalar("SELECT COUNT(*) FROM sqlite_master WHERE name='legacy_users_recovery'"))
        LegacyUsersRecoveryMigration.migrate(db)
        assertEquals(before, db.rows("SELECT rowid,address,username,publicKey,cryptoType,position FROM legacy_users_recovery"))
    }

    @Test
    fun migrationLeavesOuterTransactionOwnershipAndRollbackToCaller() = withDatabase { db ->
        db.createUsers()
        db.execSQL("INSERT INTO users VALUES('saved', 'Legacy', 'public', 0, 3)")
        val before = db.rows("SELECT rowid,* FROM users")
        db.beginTransaction()
        try {
            LegacyUsersRecoveryMigration.migrate(db)
            assertTrue(db.inTransaction())
            assertEquals(before, db.rows("SELECT rowid,address,username,publicKey,cryptoType,position FROM legacy_users_recovery"))
        } finally { db.endTransaction() }
        assertEquals(before, db.rows("SELECT rowid,* FROM users"))
        assertEquals(0L, db.scalar("SELECT COUNT(*) FROM sqlite_master WHERE name='legacy_users_recovery'"))
    }

    @Test
    fun caseInsensitiveMainAndTemporaryLedgerCollisionsNeverOverwriteOtherObjects() {
        listOf("", "TEMP ").forEach { scope ->
            withDatabase { db ->
                db.createUsers()
                db.execSQL("CREATE ${scope}TABLE LeGaCy_UsErS_ReCoVeRy(note TEXT)")
                db.execSQL("INSERT INTO LeGaCy_UsErS_ReCoVeRy VALUES('owner sentinel')")
                assertThrows(IllegalStateException::class.java) { LegacyUsersRecoveryMigration.migrate(db) }
                assertEquals(listOf(listOf("3:owner sentinel")), db.rows("SELECT note FROM LeGaCy_UsErS_ReCoVeRy"))
                assertEquals(1L, db.scalar("SELECT COUNT(*) FROM main.sqlite_master WHERE name='users'"))
            }
        }
    }

    @Test
    fun temporarySourceShadowIsRejectedBeforeChangingEitherSource() = withDatabase { db ->
        db.createUsers()
        db.execSQL("CREATE TEMP TABLE users(note TEXT)")
        db.execSQL("INSERT INTO temp.users VALUES('temporary owner')")
        assertThrows(IllegalStateException::class.java) { LegacyUsersRecoveryMigration.requireSafeSourceAndTarget(db) }
        assertEquals(listOf(listOf("3:temporary owner")), db.rows("SELECT note FROM temp.users"))
        assertEquals(0L, db.scalar("SELECT COUNT(*) FROM main.users"))
        assertEquals(0L, db.scalar("SELECT COUNT(*) FROM main.sqlite_master WHERE name='legacy_users_recovery'"))
    }

    @Test
    fun arbitraryRecoveryStateDefaultIsNotAdmittedAsTheCanonicalLedger() = withDatabase { db ->
        db.execSQL("CREATE TABLE legacy_users_recovery(address TEXT NOT NULL PRIMARY KEY, username TEXT NOT NULL, publicKey TEXT NOT NULL, cryptoType INTEGER NOT NULL, position INTEGER NOT NULL, recoveryState TEXT NOT NULL DEFAULT 'UNREVIEWED')")
        assertThrows(IllegalStateException::class.java) {
            requireExactReleasedRoomSchema(db, "Unrecognized state default", listOf(RELEASED_LEGACY_USERS_RECOVERY_TABLE), ::IllegalStateException)
        }
    }

    private fun withDatabase(action: (SupportSQLiteDatabase) -> Unit) {
        val name = "legacy-users-recovery-archive-${names.size}"
        names += name
        val callback = object : SupportSQLiteOpenHelper.Callback(1) {
            override fun onCreate(db: SupportSQLiteDatabase) = Unit
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = error("Unexpected test upgrade")
        }
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(callback).build()
        )
        try { action(helper.writableDatabase) } finally { helper.close() }
    }

    private fun SupportSQLiteDatabase.createUsers(usernameConstraint: String = "") {
        execSQL("CREATE TABLE users(address TEXT NOT NULL PRIMARY KEY, username TEXT NOT NULL$usernameConstraint, publicKey TEXT NOT NULL, cryptoType INTEGER NOT NULL, position INTEGER NOT NULL)")
    }

    private fun SupportSQLiteDatabase.scalar(sql: String): Long = query(sql).use {
        assertTrue(it.moveToFirst()); val value = it.getLong(0); assertFalse(it.moveToNext()); value
    }

    private fun SupportSQLiteDatabase.rows(sql: String): List<List<String>> = query(sql).use { cursor ->
        buildList {
            while (cursor.moveToNext()) add(List(cursor.columnCount) { index ->
                val type = cursor.getType(index)
                val value = when (type) {
                    Cursor.FIELD_TYPE_NULL -> "<null>"
                    Cursor.FIELD_TYPE_BLOB -> cursor.getBlob(index).toHexString(withPrefix = false)
                    else -> cursor.getString(index)
                }
                "$type:$value"
            })
        }
    }
}
