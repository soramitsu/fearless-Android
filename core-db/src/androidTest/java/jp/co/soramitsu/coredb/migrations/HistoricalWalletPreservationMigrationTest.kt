package jp.co.soramitsu.coredb.migrations

import android.content.Context
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoricalWalletPreservationMigrationTest {

    private val context: Context =
        InstrumentationRegistry.getInstrumentation().targetContext
    private val databaseNames = mutableListOf<String>()

    @After
    fun deleteDatabases() {
        databaseNames.forEach(context::deleteDatabase)
    }

    @Test
    fun version35To36PreservesUnexpectedChainAccountAndBackfillsEmptyName() {
        withDatabase(::createVersion35Schema) { db ->
            insertVersion35Wallet(db)

            db.migrateAtomically(RemoveLegacyData_35_36)

            assertEquals(1L, db.singleLong("SELECT COUNT(*) FROM chain_accounts"))
            assertEquals(
                "",
                db.singleString("SELECT name FROM chain_accounts")
            )
            assertArrayEquals(
                SUBSTRATE_ACCOUNT_ID,
                db.singleBlob("SELECT accountId FROM chain_accounts")
            )
            db.query("PRAGMA foreign_key_check(chain_accounts)").use { cursor ->
                assertFalse(cursor.moveToFirst())
            }
        }
    }

    @Test
    fun version35To36RejectsOrphanWithoutDroppingSourceRow() {
        withDatabase(::createVersion35Schema) { db ->
            insertVersion35Wallet(db)
            db.execSQL("PRAGMA foreign_keys = OFF")
            db.execSQL("UPDATE chain_accounts SET chainId = 'missing-chain'")

            assertThrows(IllegalStateException::class.java) {
                db.migrateAtomically(RemoveLegacyData_35_36)
            }

            assertEquals(1L, db.singleLong("SELECT COUNT(*) FROM chain_accounts"))
            assertFalse(db.columnExists("chain_accounts", "name"))
        }
    }

    @Test
    fun version45To46PreservesAllSupportedChainAccountKindsAndCustomEndpoints() {
        withDatabase(
            createSchema = {
                createVersion45Schema(
                    db = this,
                    declaredChainParent = "_chains"
                )
            }
        ) { db ->
            insertVersion45Fixtures(db)

            db.migrateAtomically(Migration_45_46)

            assertEquals(4L, db.singleLong("SELECT COUNT(*) FROM chain_accounts"))
            assertEquals(4L, db.singleLong("SELECT COUNT(*) FROM chains"))
            assertEquals(1L, db.singleLong("SELECT COUNT(*) FROM chain_nodes"))
            assertEquals(1L, db.singleLong("SELECT COUNT(*) FROM chain_explorers"))
            assertEquals(0L, db.singleLong("SELECT COUNT(*) FROM assets"))
            assertEquals(0L, db.singleLong("SELECT COUNT(*) FROM chain_assets"))
            assertEquals(
                "wss://custom.invalid",
                db.singleString("SELECT url FROM chain_nodes")
            )
            assertEquals(
                "https://explorer.invalid",
                db.singleString("SELECT url FROM chain_explorers")
            )
            assertEquals(
                20L,
                db.singleLong(
                    "SELECT length(accountId) FROM chain_accounts " +
                        "WHERE cryptoType = 'ECDSA' AND chainId = 'ethereum'"
                )
            )
            assertEquals(
                32L,
                db.singleLong(
                    "SELECT length(accountId) FROM chain_accounts " +
                        "WHERE cryptoType = 'ECDSA' AND chainId = 'substrate-ecdsa'"
                )
            )
            assertEquals(
                setOf("chains", "meta_accounts"),
                db.foreignKeyParents("chain_accounts")
            )
            db.query("PRAGMA foreign_key_check").use { cursor ->
                assertFalse(cursor.moveToFirst())
            }
        }
    }

    @Test
    fun version45To46AcceptsCanonicalChainsForeignKeys() {
        withDatabase(
            createSchema = {
                createVersion45Schema(
                    db = this,
                    declaredChainParent = "chains"
                )
            }
        ) { db ->
            insertVersion45Fixtures(db)

            db.migrateAtomically(Migration_45_46)

            assertEquals(4L, db.singleLong("SELECT COUNT(*) FROM chain_accounts"))
            assertEquals(
                setOf("chains", "meta_accounts"),
                db.foreignKeyParents("chain_accounts")
            )
        }
    }

    @Test
    fun version45To46RejectsUnknownForeignKeyVariantBeforeMutation() {
        withDatabase(
            createSchema = {
                createVersion45Schema(
                    db = this,
                    declaredChainParent = "attacker_chains"
                )
            }
        ) { db ->
            insertVersion45Fixtures(db)

            assertThrows(IllegalStateException::class.java) {
                db.migrateAtomically(Migration_45_46)
            }

            assertEquals(4L, db.singleLong("SELECT COUNT(*) FROM chain_accounts"))
            assertEquals(4L, db.singleLong("SELECT COUNT(*) FROM chains"))
            assertEquals(1L, db.singleLong("SELECT COUNT(*) FROM assets"))
        }
    }

    @Test
    fun version54To55KeepsNewestCreatedThenHighestId() {
        withDatabase(::createVersion54AddressBookSchema) { db ->
            db.execSQL(
                """
                INSERT INTO address_book(id, address, name, chainId, created)
                VALUES
                (1, 'alice', 'old', 'chain', 10),
                (2, 'alice', 'new-lower-id', 'chain', 20),
                (3, 'alice', 'new-highest-id', 'chain', 20),
                (4, 'bob', 'only', 'chain', 1)
                """.trimIndent()
            )

            db.migrateAtomically(Migration_54_55)

            assertEquals(2L, db.singleLong("SELECT COUNT(*) FROM address_book"))
            assertEquals(
                3L,
                db.singleLong(
                    "SELECT id FROM address_book " +
                        "WHERE address = 'alice' AND chainId = 'chain'"
                )
            )
            assertEquals(
                "new-highest-id",
                db.singleString("SELECT name FROM address_book WHERE id = 3")
            )
        }
    }

    @Test
    fun version72To73MovesLegacyUsersIntoRecoveryLedgerWithoutResurrection() {
        withDatabase(::createVersion72LedgerSchema) { db ->
            db.execSQL(
                """
                INSERT INTO users(address, username, publicKey, cryptoType, position)
                VALUES
                ('legacy-a', 'Alice', '0a0b', 0, 7),
                ('legacy-b', 'Bob', '0c0d', 2, 8)
                """.trimIndent()
            )

            db.migrateAtomically(Migration_72_73)

            assertFalse(db.tableExists("users"))
            assertTrue(db.tableExists("legacy_users_recovery"))
            assertEquals(
                2L,
                db.singleLong("SELECT COUNT(*) FROM legacy_users_recovery")
            )
            assertEquals(
                2L,
                db.singleLong(
                    "SELECT COUNT(*) FROM legacy_users_recovery " +
                        "WHERE recoveryState = 'PRESERVED_PENDING_REVIEW'"
                )
            )
            assertEquals(
                "0a0b",
                db.singleString(
                    "SELECT publicKey FROM legacy_users_recovery " +
                        "WHERE address = 'legacy-a'"
                )
            )
        }
    }

    @Test
    fun preservationEdgesComposeWithoutSilentWalletIdentityLoss() {
        withDatabase(::createVersion35Schema) { db ->
            insertVersion35Wallet(db)
            db.migrateAtomically(RemoveLegacyData_35_36)
            createVersion45NonWalletTables(db, declaredChainParent = "chains")
            db.execSQL(
                """
                INSERT INTO chain_nodes(chainId, url, name, isActive, isDefault)
                VALUES('substrate', 'wss://compose.invalid', 'Compose', 1, 0)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO chain_explorers(chainId, type, types, url)
                VALUES('substrate', 'SUBSCAN', '[]', 'https://compose.invalid')
                """.trimIndent()
            )
            db.migrateAtomically(Migration_45_46)
            createVersion54AddressBookSchema(db)
            db.execSQL(
                """
                INSERT INTO address_book(id, address, name, chainId, created)
                VALUES
                (1, 'recipient', 'old', 'substrate', 1),
                (2, 'recipient', 'new', 'substrate', 2)
                """.trimIndent()
            )
            db.migrateAtomically(Migration_54_55)
            db.migrateAtomically(Migration_72_73)

            assertEquals(1L, db.singleLong("SELECT COUNT(*) FROM chain_accounts"))
            assertArrayEquals(
                SUBSTRATE_ACCOUNT_ID,
                db.singleBlob("SELECT accountId FROM chain_accounts")
            )
            assertEquals(1L, db.singleLong("SELECT COUNT(*) FROM chains"))
            assertEquals(1L, db.singleLong("SELECT COUNT(*) FROM chain_nodes"))
            assertEquals(1L, db.singleLong("SELECT COUNT(*) FROM address_book"))
            assertEquals(
                "new",
                db.singleString("SELECT name FROM address_book")
            )
            assertEquals(
                1L,
                db.singleLong("SELECT COUNT(*) FROM legacy_users_recovery")
            )
        }
    }

    private fun withDatabase(
        createSchema: SupportSQLiteDatabase.() -> Unit,
        action: (SupportSQLiteDatabase) -> Unit
    ) {
        val name = "historical-preservation-${UUID.randomUUID()}.db"
        databaseNames += name
        val callback = object : SupportSQLiteOpenHelper.Callback(1) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.createSchema()
            }

            override fun onUpgrade(
                db: SupportSQLiteDatabase,
                oldVersion: Int,
                newVersion: Int
            ) = Unit
        }
        val configuration = SupportSQLiteOpenHelper.Configuration
            .builder(context)
            .name(name)
            .callback(callback)
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(configuration)
        try {
            action(helper.writableDatabase)
        } finally {
            helper.close()
        }
    }

    private fun SupportSQLiteDatabase.migrateAtomically(
        migration: Migration
    ) {
        beginTransaction()
        try {
            migration.migrate(this)
            setTransactionSuccessful()
        } finally {
            endTransaction()
        }
    }

    private fun createVersion35Schema(db: SupportSQLiteDatabase) = with(db) {
        execSQL("CREATE TABLE chains(id TEXT NOT NULL PRIMARY KEY)")
        execSQL("CREATE TABLE meta_accounts(id INTEGER NOT NULL PRIMARY KEY)")
        execSQL(
            """
            CREATE TABLE chain_accounts(
            metaId INTEGER NOT NULL,
            chainId TEXT NOT NULL,
            publicKey BLOB NOT NULL,
            accountId BLOB NOT NULL,
            cryptoType TEXT NOT NULL,
            PRIMARY KEY(metaId, chainId),
            FOREIGN KEY(chainId) REFERENCES chains(id)
                ON UPDATE NO ACTION ON DELETE NO ACTION
                DEFERRABLE INITIALLY DEFERRED,
            FOREIGN KEY(metaId) REFERENCES meta_accounts(id)
                ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        execSQL(
            """
            CREATE TABLE users(
            address TEXT NOT NULL PRIMARY KEY,
            username TEXT NOT NULL,
            publicKey TEXT NOT NULL,
            cryptoType INTEGER NOT NULL,
            networkType INTEGER NOT NULL,
            position INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    private fun insertVersion35Wallet(db: SupportSQLiteDatabase) = with(db) {
        execSQL("INSERT INTO chains(id) VALUES('substrate')")
        execSQL("INSERT INTO meta_accounts(id) VALUES(1)")
        execSQL(
            """
            INSERT INTO chain_accounts(
                metaId,
                chainId,
                publicKey,
                accountId,
                cryptoType
            ) VALUES(?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any>(
                1L,
                "substrate",
                SUBSTRATE_PUBLIC_KEY,
                SUBSTRATE_ACCOUNT_ID,
                "SR25519"
            )
        )
        execSQL(
            """
            INSERT INTO users(
                address,
                username,
                publicKey,
                cryptoType,
                networkType,
                position
            ) VALUES('legacy', 'Legacy', '0102', 0, 0, 1)
            """.trimIndent()
        )
    }

    private fun createVersion45Schema(
        db: SupportSQLiteDatabase,
        declaredChainParent: String
    ) = with(db) {
        execSQL("CREATE TABLE chains(id TEXT NOT NULL PRIMARY KEY)")
        execSQL("CREATE TABLE meta_accounts(id INTEGER NOT NULL PRIMARY KEY)")
        createVersion45NonWalletTables(this, declaredChainParent)
        execSQL(
            """
            CREATE TABLE chain_accounts(
            metaId INTEGER NOT NULL,
            chainId TEXT NOT NULL,
            publicKey BLOB NOT NULL,
            accountId BLOB NOT NULL,
            cryptoType TEXT NOT NULL,
            name TEXT NOT NULL,
            PRIMARY KEY(metaId, chainId),
            FOREIGN KEY(chainId) REFERENCES `$declaredChainParent`(id)
                ON UPDATE NO ACTION ON DELETE NO ACTION
                DEFERRABLE INITIALLY DEFERRED,
            FOREIGN KEY(metaId) REFERENCES meta_accounts(id)
                ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
    }

    private fun createVersion45NonWalletTables(
        db: SupportSQLiteDatabase,
        declaredChainParent: String
    ) = with(db) {
        execSQL(
            """
            CREATE TABLE chain_nodes(
            chainId TEXT NOT NULL,
            url TEXT NOT NULL,
            name TEXT NOT NULL,
            isActive INTEGER NOT NULL,
            isDefault INTEGER NOT NULL,
            PRIMARY KEY(chainId, url),
            FOREIGN KEY(chainId) REFERENCES `$declaredChainParent`(id)
                ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        execSQL(
            """
            CREATE TABLE chain_explorers(
            chainId TEXT NOT NULL,
            type TEXT NOT NULL,
            types TEXT NOT NULL,
            url TEXT NOT NULL,
            PRIMARY KEY(chainId, type),
            FOREIGN KEY(chainId) REFERENCES `$declaredChainParent`(id)
                ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        execSQL(
            "CREATE TABLE chain_assets(id TEXT NOT NULL, chainId TEXT NOT NULL)"
        )
        execSQL(
            "CREATE TABLE assets(id TEXT NOT NULL, chainId TEXT NOT NULL)"
        )
    }

    private fun insertVersion45Fixtures(db: SupportSQLiteDatabase) = with(db) {
        listOf(
            "sr25519",
            "ed25519",
            "substrate-ecdsa",
            "ethereum"
        ).forEachIndexed { index, chainId ->
            execSQL("INSERT INTO chains(id) VALUES(?)", arrayOf<Any>(chainId))
            execSQL(
                "INSERT INTO meta_accounts(id) VALUES(?)",
                arrayOf<Any>((index + 1).toLong())
            )
        }
        val fixtures = listOf(
            ChainAccountFixture(
                metaId = 1L,
                chainId = "sr25519",
                publicKey = SUBSTRATE_PUBLIC_KEY,
                accountId = SUBSTRATE_ACCOUNT_ID,
                cryptoType = "SR25519"
            ),
            ChainAccountFixture(
                metaId = 2L,
                chainId = "ed25519",
                publicKey = ByteArray(32) { 3 },
                accountId = ByteArray(32) { 4 },
                cryptoType = "ED25519"
            ),
            ChainAccountFixture(
                metaId = 3L,
                chainId = "substrate-ecdsa",
                publicKey = ByteArray(33) { 5 },
                accountId = ByteArray(32) { 6 },
                cryptoType = "ECDSA"
            ),
            ChainAccountFixture(
                metaId = 4L,
                chainId = "ethereum",
                publicKey = ByteArray(33) { 7 },
                accountId = ByteArray(20) { 8 },
                cryptoType = "ECDSA"
            )
        )
        fixtures.forEach { fixture ->
            execSQL(
                """
                INSERT INTO chain_accounts(
                    metaId,
                    chainId,
                    publicKey,
                    accountId,
                    cryptoType,
                    name
                ) VALUES(?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any>(
                    fixture.metaId,
                    fixture.chainId,
                    fixture.publicKey,
                    fixture.accountId,
                    fixture.cryptoType,
                    fixture.chainId
                )
            )
        }
        execSQL(
            """
            INSERT INTO chain_nodes(chainId, url, name, isActive, isDefault)
            VALUES('sr25519', 'wss://custom.invalid', 'Custom', 1, 0)
            """.trimIndent()
        )
        execSQL(
            """
            INSERT INTO chain_explorers(chainId, type, types, url)
            VALUES(
                'sr25519',
                'SUBSCAN',
                '["account","extrinsic"]',
                'https://explorer.invalid'
            )
            """.trimIndent()
        )
        execSQL("INSERT INTO assets(id, chainId) VALUES('asset', 'sr25519')")
        execSQL(
            "INSERT INTO chain_assets(id, chainId) VALUES('asset', 'sr25519')"
        )
    }

    private fun createVersion54AddressBookSchema(
        db: SupportSQLiteDatabase
    ) {
        db.execSQL(
            """
            CREATE TABLE address_book(
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            address TEXT NOT NULL,
            name TEXT,
            chainId TEXT NOT NULL,
            created INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    private fun createVersion72LedgerSchema(
        db: SupportSQLiteDatabase
    ) = with(db) {
        execSQL("CREATE TABLE chains(id TEXT NOT NULL PRIMARY KEY)")
        execSQL("CREATE TABLE meta_accounts(id INTEGER NOT NULL PRIMARY KEY)")
        execSQL(
            """
            CREATE TABLE users(
            address TEXT NOT NULL PRIMARY KEY,
            username TEXT NOT NULL,
            publicKey TEXT NOT NULL,
            cryptoType INTEGER NOT NULL,
            position INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    private fun SupportSQLiteDatabase.tableExists(name: String): Boolean {
        return singleLong(
            "SELECT COUNT(*) FROM sqlite_master " +
                "WHERE type = 'table' AND name = ?",
            name
        ) == 1L
    }

    private fun SupportSQLiteDatabase.columnExists(
        table: String,
        column: String
    ): Boolean {
        return query("PRAGMA table_info(`$table`)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            var found = false
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == column) {
                    found = true
                }
            }
            found
        }
    }

    private fun SupportSQLiteDatabase.foreignKeyParents(
        table: String
    ): Set<String> {
        return query("PRAGMA foreign_key_list(`$table`)").use { cursor ->
            val parentIndex = cursor.getColumnIndexOrThrow("table")
            buildSet {
                while (cursor.moveToNext()) {
                    add(cursor.getString(parentIndex))
                }
            }
        }
    }

    private fun SupportSQLiteDatabase.singleLong(
        sql: String,
        vararg bindArgs: Any?
    ): Long {
        return query(sql, bindArgs).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getLong(0)
        }
    }

    private fun SupportSQLiteDatabase.singleString(sql: String): String {
        return query(sql).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getString(0)
        }
    }

    private fun SupportSQLiteDatabase.singleBlob(sql: String): ByteArray {
        return query(sql).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getBlob(0)
        }
    }

    private data class ChainAccountFixture(
        val metaId: Long,
        val chainId: String,
        val publicKey: ByteArray,
        val accountId: ByteArray,
        val cryptoType: String
    )

    private companion object {
        val SUBSTRATE_PUBLIC_KEY = ByteArray(32) { 1 }
        val SUBSTRATE_ACCOUNT_ID = ByteArray(32) { 2 }
    }
}
