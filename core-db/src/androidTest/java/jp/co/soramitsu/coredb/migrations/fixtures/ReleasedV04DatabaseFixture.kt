package jp.co.soramitsu.coredb.migrations.fixtures

import android.content.Context
import android.database.sqlite.SQLiteDatabase

/**
 * Raw database fixture matching the Room entities shipped by tag v0.4.0
 * (commit 66babd85364f00e8dc3431602b20b05dfc0d29b3).
 *
 * This deliberately does not use a newer exported Room schema: version 9
 * predates schema export in this repository, and an actual v0.4 installation
 * contained only these four tables plus Room's internal metadata.
 */
internal object ReleasedV04DatabaseFixture {

    const val VERSION = 9

    fun create(
        context: Context,
        databaseName: String,
        accounts: List<ReleasedV04Account>
    ) {
        require(accounts.isNotEmpty())
        require(accounts.map(ReleasedV04Account::address).distinct().size == accounts.size)
        require(accounts.map(ReleasedV04Account::position).distinct().size == accounts.size)

        context.deleteDatabase(databaseName)
        val databaseFile = context.getDatabasePath(databaseName)
        val databaseDirectory = checkNotNull(databaseFile.parentFile) {
            "Released v0.4 fixture database has no parent directory"
        }
        check(
            databaseDirectory.isDirectory ||
                databaseDirectory.mkdirs()
        ) {
            "Could not create the Released v0.4 fixture database directory"
        }

        SQLiteDatabase.openOrCreateDatabase(
            databaseFile,
            null
        ).use { database ->
            database.execSQL("PRAGMA foreign_keys = ON")
            SCHEMA.forEach(database::execSQL)

            accounts.forEach { account ->
                database.execSQL(
                    """
                    INSERT INTO users(
                        address,
                        username,
                        publicKey,
                        cryptoType,
                        position,
                        networkType
                    ) VALUES(?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                    arrayOf<Any>(
                        account.address,
                        account.username,
                        account.publicKeyHex,
                        account.cryptoTypeOrdinal,
                        account.position,
                        account.networkTypeOrdinal
                    )
                )
            }

            database.execSQL(
                """
                INSERT INTO nodes(name, link, networkType, isDefault)
                VALUES(?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any>(
                    "Released 0.4 node ' \" ; -- 雪",
                    "wss://released-v04.invalid/path?query='\"",
                    0,
                    1
                )
            )

            val firstAccount = accounts.minBy(ReleasedV04Account::position)
            database.execSQL(
                """
                INSERT INTO transactions(
                    accountAddress,
                    hash,
                    token,
                    senderAddress,
                    recipientAddress,
                    amount,
                    date,
                    feeInPlanks,
                    status,
                    source,
                    networkType
                ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    firstAccount.address,
                    "0xlegacy-'\";--",
                    0,
                    "legacy-sender",
                    "legacy-recipient",
                    "999999999999999999999999.123456789",
                    Long.MAX_VALUE,
                    null,
                    0,
                    2,
                    0
                )
            )
            database.execSQL(
                """
                INSERT INTO assets(
                    token,
                    accountAddress,
                    freeInPlanks,
                    reservedInPlanks,
                    miscFrozenInPlanks,
                    feeFrozenInPlanks,
                    bondedInPlanks,
                    redeemableInPlanks,
                    unbondingInPlanks,
                    dollarRate,
                    recentRateChange
                ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    0,
                    firstAccount.address,
                    "1",
                    "2",
                    "3",
                    "4",
                    "5",
                    "6",
                    "7",
                    "123.45",
                    "-0.75"
                )
            )

            database.version = VERSION
        }
    }

    private val SCHEMA = listOf(
        """
        CREATE TABLE IF NOT EXISTS `users` (
            `address` TEXT NOT NULL,
            `username` TEXT NOT NULL,
            `publicKey` TEXT NOT NULL,
            `cryptoType` INTEGER NOT NULL,
            `position` INTEGER NOT NULL,
            `networkType` INTEGER NOT NULL,
            PRIMARY KEY(`address`)
        )
        """.trimIndent(),
        """
        CREATE TABLE IF NOT EXISTS `nodes` (
            `name` TEXT NOT NULL,
            `link` TEXT NOT NULL,
            `networkType` INTEGER NOT NULL,
            `isDefault` INTEGER NOT NULL,
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL
        )
        """.trimIndent(),
        """
        CREATE TABLE IF NOT EXISTS `transactions` (
            `accountAddress` TEXT NOT NULL,
            `hash` TEXT NOT NULL,
            `token` INTEGER NOT NULL,
            `senderAddress` TEXT NOT NULL,
            `recipientAddress` TEXT NOT NULL,
            `amount` TEXT NOT NULL,
            `date` INTEGER NOT NULL,
            `feeInPlanks` TEXT,
            `status` INTEGER NOT NULL,
            `source` INTEGER NOT NULL,
            `networkType` INTEGER NOT NULL,
            PRIMARY KEY(`hash`, `accountAddress`),
            FOREIGN KEY(`accountAddress`) REFERENCES `users`(`address`)
                ON UPDATE NO ACTION ON DELETE CASCADE
        )
        """.trimIndent(),
        """
        CREATE INDEX IF NOT EXISTS `index_transactions_accountAddress`
        ON `transactions` (`accountAddress`)
        """.trimIndent(),
        """
        CREATE TABLE IF NOT EXISTS `assets` (
            `token` INTEGER NOT NULL,
            `accountAddress` TEXT NOT NULL,
            `freeInPlanks` TEXT NOT NULL,
            `reservedInPlanks` TEXT NOT NULL,
            `miscFrozenInPlanks` TEXT NOT NULL,
            `feeFrozenInPlanks` TEXT NOT NULL,
            `bondedInPlanks` TEXT NOT NULL,
            `redeemableInPlanks` TEXT NOT NULL,
            `unbondingInPlanks` TEXT NOT NULL,
            `dollarRate` TEXT,
            `recentRateChange` TEXT,
            PRIMARY KEY(`token`, `accountAddress`),
            FOREIGN KEY(`accountAddress`) REFERENCES `users`(`address`)
                ON UPDATE NO ACTION ON DELETE CASCADE
        )
        """.trimIndent(),
        """
        CREATE INDEX IF NOT EXISTS `index_assets_accountAddress`
        ON `assets` (`accountAddress`)
        """.trimIndent(),
        // Version 9 predates exported schemas in this repository. This exact
        // Room identity was independently reconstructed with Room 2.2.0's
        // SchemaIdentityKey algorithm from the four shipped v0.4 entities.
        """
        CREATE TABLE IF NOT EXISTS room_master_table (
            id INTEGER PRIMARY KEY,
            identity_hash TEXT
        )
        """.trimIndent(),
        """
        INSERT OR REPLACE INTO room_master_table(id, identity_hash)
        VALUES(42, '079448a9c6e9eb3acbb1d8ea60e942f6')
        """.trimIndent()
    )
}

internal data class ReleasedV04Account(
    val address: String,
    val username: String,
    val publicKeyHex: String,
    val cryptoTypeOrdinal: Int,
    val position: Int,
    val networkTypeOrdinal: Int = 0
)
