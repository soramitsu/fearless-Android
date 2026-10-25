package jp.co.soramitsu.app.root.domain.keyloss

import android.content.Context
import android.content.ContextWrapper
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real OPEN_READONLY consumer tests; no Room, preferences, keys or provider traffic. */
@RunWith(AndroidJUnit4::class)
class LegacyPublicSnapshotSchemaInstrumentedTest {
    @Test
    fun renamedUnknownPublicKeyIsDisclosedWithoutHidingKnownAddressOrChangingRawBytes() {
        withPublicDatabase(
            prepare = { database ->
                database.execSQL("CREATE TABLE users(address TEXT NOT NULL PRIMARY KEY, username TEXT NOT NULL, unknownPublicKey BLOB NOT NULL, cryptoType INTEGER NOT NULL, position INTEGER NOT NULL)")
                database.execSQL("INSERT INTO users VALUES(?, ?, ?, 0, 0)", arrayOf(ADDRESS, NAME, byteArrayOf(0, -1, 0, 42)))
            },
            rawTable = "users"
        ) { snapshot ->
            assertEquals(listOf(wallet(listOf("address" to ADDRESS))), snapshot.wallets)
            assertTrue("An omitted unknown public-key column must be disclosed", snapshot.incomplete)
        }
    }

    @Test
    fun additionalUnknownIdentityColumnIsDisclosedEvenWhenKnownPublicKeyIsReadable() {
        withPublicDatabase(
            prepare = { database ->
                database.execSQL(USERS_FIVE)
                database.execSQL("ALTER TABLE users ADD COLUMN unknownPublicKey BLOB")
                database.execSQL("INSERT INTO users VALUES(?, ?, ?, 0, 0, ?)", arrayOf(ADDRESS, NAME, PUBLIC_KEY, byteArrayOf(1, 0, -2)))
            },
            rawTable = "users"
        ) { snapshot ->
            assertEquals(listOf(wallet(IDENTITIES)), snapshot.wallets)
            assertTrue(snapshot.incomplete)
        }
    }

    @Test
    fun missingPublicKeyColumnIsDisclosedWhileKnownAddressRemainsAvailable() {
        withPublicDatabase(
            prepare = { database ->
                database.execSQL("CREATE TABLE users(address TEXT NOT NULL PRIMARY KEY, username TEXT NOT NULL, cryptoType INTEGER NOT NULL, position INTEGER NOT NULL)")
                database.execSQL("INSERT INTO users VALUES(?, ?, 0, 0)", arrayOf(ADDRESS, NAME))
            },
            rawTable = "users"
        ) { snapshot ->
            assertEquals(listOf(wallet(listOf("address" to ADDRESS))), snapshot.wallets)
            assertTrue(snapshot.incomplete)
        }
    }

    @Test
    fun exactReleasedFiveColumnUsersRemainCompleteAndReadOnly() {
        withPublicDatabase(
            asset = "shipped-3.7.4-v71.db",
            prepare = { database ->
                assertEquals(FIVE_COLUMNS, database.columnNames("users"))
                database.execSQL("INSERT INTO users(address,username,publicKey,cryptoType,position) VALUES(?, ?, ?, 0, 0)", arrayOf(ADDRESS, NAME, PUBLIC_KEY))
            },
            rawTable = "users"
        ) { snapshot ->
            assertEquals(listOf(wallet(IDENTITIES)), snapshot.wallets)
            assertFalse(snapshot.incomplete)
        }
    }

    @Test
    fun exactReleasedSixColumnUsersRemainCompleteAndReadOnly() {
        withPublicDatabase(
            asset = "shipped-2.0.0-v30.db",
            prepare = { database ->
                assertEquals(FIVE_COLUMNS + "networkType", database.columnNames("users"))
                database.execSQL("INSERT INTO users(address,username,publicKey,cryptoType,position,networkType) VALUES(?, ?, ?, 0, 0, 0)", arrayOf(ADDRESS, NAME, PUBLIC_KEY))
            },
            rawTable = "users"
        ) { snapshot ->
            assertEquals(listOf(wallet(IDENTITIES)), snapshot.wallets)
            assertFalse(snapshot.incomplete)
        }
    }

    @Test
    fun canonicalRenamedSixColumnRecoveryLedgerRemainsCompleteAndReadOnly() {
        withPublicDatabase(
            asset = "shipped-3.7.4-v71.db",
            prepare = { database ->
                database.execSQL("INSERT INTO users(address,username,publicKey,cryptoType,position) VALUES(?, ?, ?, 0, 0)", arrayOf(ADDRESS, NAME, PUBLIC_KEY))
                database.execSQL("ALTER TABLE users RENAME TO legacy_users_recovery")
                database.execSQL("ALTER TABLE legacy_users_recovery ADD COLUMN recoveryState TEXT NOT NULL DEFAULT 'PRESERVED_PENDING_REVIEW'")
                assertEquals(FIVE_COLUMNS + "recoveryState", database.columnNames("legacy_users_recovery"))
            },
            rawTable = "legacy_users_recovery"
        ) { snapshot ->
            assertEquals(listOf(wallet(IDENTITIES)), snapshot.wallets)
            assertFalse(snapshot.incomplete)
        }
    }

    @Test
    fun unknownLedgerPublicKeyFieldIsDisclosedWithoutInventingAnIdentity() {
        withPublicDatabase(
            prepare = { database ->
                database.execSQL("CREATE TABLE legacy_users_recovery(address TEXT NOT NULL PRIMARY KEY, username TEXT NOT NULL, unknownPublicKey BLOB NOT NULL, cryptoType INTEGER NOT NULL, position INTEGER NOT NULL, recoveryState TEXT NOT NULL)")
                database.execSQL("INSERT INTO legacy_users_recovery VALUES(?, ?, ?, 0, 0, 'PRESERVED_PENDING_REVIEW')", arrayOf(ADDRESS, NAME, byteArrayOf(0, 127, -128)))
            },
            rawTable = "legacy_users_recovery"
        ) { snapshot ->
            assertEquals(listOf(wallet(listOf("address" to ADDRESS))), snapshot.wallets)
            assertTrue(snapshot.incomplete)
        }
    }

    @Test
    fun emptyUnsupportedLegacyTableStillDisclosesItsLayoutBesideAHealthyCurrentWallet() {
        withPublicDatabase(
            prepare = { database ->
                createCurrentWallet(database)
                database.execSQL("CREATE TABLE users(address TEXT NOT NULL PRIMARY KEY, username TEXT NOT NULL, unknownPublicKey BLOB)")
            },
            rawTable = "users"
        ) { snapshot ->
            assertEquals(listOf(wallet(listOf("ethereumAddress" to CURRENT_ADDRESS))), snapshot.wallets)
            assertTrue(snapshot.incomplete)
        }
    }

    @Test
    fun boundedBlobInAKnownLegacyPublicKeyColumnKeepsExistingReadableBehavior() {
        withPublicDatabase(
            prepare = { database ->
                database.execSQL(USERS_FIVE)
                database.execSQL("INSERT INTO users VALUES(?, ?, ?, 0, 0)", arrayOf(ADDRESS, NAME, byteArrayOf(0, -1, 42)))
            },
            rawTable = "users"
        ) { snapshot ->
            assertEquals(listOf(wallet(listOf("address" to ADDRESS, "publicKey" to "00ff2a"))), snapshot.wallets)
            assertFalse(snapshot.incomplete)
        }
    }

    @Test
    fun currentWalletWithoutLegacyTablesKeepsExistingCompleteBehavior() {
        withPublicDatabase(prepare = ::createCurrentWallet, rawTable = "meta_accounts") { snapshot ->
            assertEquals(listOf(wallet(listOf("ethereumAddress" to CURRENT_ADDRESS))), snapshot.wallets)
            assertFalse(snapshot.incomplete)
        }
    }

    @Test
    fun nullLegacyUsernameIsDisclosedWithoutHidingKnownIdentities() {
        withPublicDatabase(
            prepare = { database ->
                database.execSQL(USERS_FIVE.replace("username TEXT NOT NULL", "username TEXT"))
                database.execSQL("INSERT INTO users VALUES(?, NULL, ?, 0, 0)", arrayOf(ADDRESS, PUBLIC_KEY))
            },
            rawTable = "users"
        ) { snapshot ->
            assertEquals(listOf(WalletPublicSnapshot.Wallet("", IDENTITIES, emptyList())), snapshot.wallets)
            assertTrue("A missing saved name must be disclosed", snapshot.incomplete)
        }
    }

    @Test
    fun nullLegacyPublicKeyIsDisclosedWithoutHidingKnownAddress() {
        withPublicDatabase(
            prepare = { database ->
                database.execSQL(USERS_FIVE.replace("publicKey TEXT NOT NULL", "publicKey TEXT"))
                database.execSQL("INSERT INTO users VALUES(?, ?, NULL, 0, 0)", arrayOf(ADDRESS, NAME))
            },
            rawTable = "users"
        ) { snapshot ->
            assertEquals(listOf(wallet(listOf("address" to ADDRESS))), snapshot.wallets)
            assertTrue("An omitted saved public key must be disclosed", snapshot.incomplete)
        }
    }

    @Test
    fun nullLegacyAddressIsDisclosedWithoutHidingKnownPublicKey() {
        withPublicDatabase(
            prepare = { database ->
                database.execSQL(USERS_FIVE.replace("address TEXT NOT NULL PRIMARY KEY", "address TEXT PRIMARY KEY"))
                database.execSQL("INSERT INTO users VALUES(NULL, ?, ?, 0, 0)", arrayOf(NAME, PUBLIC_KEY))
            },
            rawTable = "users"
        ) { snapshot ->
            assertEquals(listOf(wallet(listOf("publicKey" to PUBLIC_KEY))), snapshot.wallets)
            assertTrue("An omitted saved address must be disclosed", snapshot.incomplete)
        }
    }

    @Test
    fun presentEmptyLegacyUsernameRemainsCompleteWithoutInventingAName() {
        withPublicDatabase(
            prepare = { database ->
                database.execSQL(USERS_FIVE)
                database.execSQL("INSERT INTO users VALUES(?, '', ?, 0, 0)", arrayOf(ADDRESS, PUBLIC_KEY))
            },
            rawTable = "users"
        ) { snapshot ->
            assertEquals(listOf(WalletPublicSnapshot.Wallet("", IDENTITIES, emptyList())), snapshot.wallets)
            assertFalse(snapshot.incomplete)
        }
    }

    private fun withPublicDatabase(
        asset: String? = null,
        prepare: (SQLiteDatabase) -> Unit,
        rawTable: String,
        assertions: (WalletPublicSnapshot) -> Unit
    ) {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val file = File.createTempFile("legacy-public-snapshot-", ".db", app.cacheDir)
        val context = object : ContextWrapper(app) {
            override fun getDatabasePath(name: String): File {
                check(name == "app.db")
                return file
            }
        }
        try {
            if (asset != null) {
                InstrumentationRegistry.getInstrumentation().context.assets.open("keyloss/$asset").use { input ->
                    file.outputStream().use(input::copyTo)
                }
            }
            SQLiteDatabase.openOrCreateDatabase(file, null).use { database ->
                database.disableWriteAheadLogging()
                prepare(database)
            }
            val beforeRows = rawSnapshot(file, rawTable)
            val beforeFiles = fileDigests(file)
            try {
                assertions(WalletPublicSnapshotReader(context).read())
            } finally {
                assertEquals("All original typed rows and schema remain exact", beforeRows, rawSnapshot(file, rawTable))
                assertEquals("OPEN_READONLY must not alter the database or its sidecars", beforeFiles, fileDigests(file))
            }
        } finally {
            listOf("", "-journal", "-wal", "-shm").forEach { suffix -> File(file.path + suffix).delete() }
        }
    }

    private fun rawSnapshot(file: File, table: String): List<List<String>> =
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { database ->
            require(table in setOf("users", "legacy_users_recovery", "meta_accounts"))
            database.typedRows("PRAGMA user_version") +
                database.typedRows("SELECT type,name,tbl_name,sql FROM sqlite_master ORDER BY rowid") +
                database.typedRows("SELECT rowid,* FROM `$table` ORDER BY rowid")
        }

    private fun SQLiteDatabase.typedRows(sql: String): List<List<String>> = rawQuery(sql, null).use { cursor ->
        buildList {
            while (cursor.moveToNext()) add(List(cursor.columnCount) { column ->
                val type = cursor.getType(column)
                val value = when (type) {
                    Cursor.FIELD_TYPE_NULL -> "<null>"
                    Cursor.FIELD_TYPE_BLOB -> cursor.getBlob(column).hex()
                    else -> cursor.getString(column)
                }
                "$type:$value"
            })
        }
    }

    private fun SQLiteDatabase.columnNames(table: String): Set<String> = rawQuery("PRAGMA table_info(`$table`)", null).use { cursor ->
        buildSet { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) }
    }

    private fun fileDigests(file: File): Map<String, String> = listOf("", "-journal", "-wal", "-shm").mapNotNull { suffix ->
        val part = File(file.path + suffix)
        if (part.exists()) suffix to MessageDigest.getInstance("SHA-256").digest(part.readBytes()).hex() else null
    }.toMap()

    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }

    private fun wallet(identities: List<Pair<String, String>>) = WalletPublicSnapshot.Wallet(NAME, identities, emptyList())

    private fun createCurrentWallet(database: SQLiteDatabase) {
        database.execSQL("CREATE TABLE meta_accounts(id INTEGER, name TEXT, ethereumAddress TEXT)")
        database.execSQL("INSERT INTO meta_accounts VALUES(1, ?, ?)", arrayOf(NAME, CURRENT_ADDRESS))
    }

    private companion object {
        const val ADDRESS = "offline-public-identity"
        const val NAME = "Saved public identity 雪"
        const val PUBLIC_KEY = "0123456789abcdef"
        const val CURRENT_ADDRESS = "0x0000000000000000000000000000000000000001"
        const val USERS_FIVE = "CREATE TABLE users(address TEXT NOT NULL PRIMARY KEY, username TEXT NOT NULL, publicKey TEXT NOT NULL, cryptoType INTEGER NOT NULL, position INTEGER NOT NULL)"
        val FIVE_COLUMNS = setOf("address", "username", "publicKey", "cryptoType", "position")
        val IDENTITIES = listOf("address" to ADDRESS, "publicKey" to PUBLIC_KEY)
    }
}
