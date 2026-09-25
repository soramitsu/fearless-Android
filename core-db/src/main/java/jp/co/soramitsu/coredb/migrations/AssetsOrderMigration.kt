package jp.co.soramitsu.coredb.migrations

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.math.BigDecimal
import java.math.BigInteger
import java.util.Locale

class AssetsOrderMigrationIntegrityException(
    message: String,
    cause: Throwable? = null
) : IllegalStateException(message, cause)

internal data class AssetsOrderMigrationLimits(
    val maxWalletRows: Int,
    val maxAssetRows: Int,
    val maxTokenRows: Int,
    val maxChainAssetRows: Int,
    val maxChainRows: Int,
    val maxModelsPerWallet: Int
) {
    init {
        listOf(
            maxWalletRows,
            maxAssetRows,
            maxTokenRows,
            maxChainAssetRows,
            maxChainRows,
            maxModelsPerWallet
        ).forEach { require(it in 1 until Int.MAX_VALUE) }
        require(maxModelsPerWallet <= maxAssetRows)
    }

    companion object {
        val PRODUCTION = AssetsOrderMigrationLimits(
            maxWalletRows = 8_192,
            maxAssetRows = 1_048_576,
            maxTokenRows = 262_144,
            // Released v34 mapped 25 chain assets. This remains over 650x
            // larger while bounding SQLite TEMP-table native memory.
            maxChainAssetRows = 16_384,
            maxChainRows = 65_536,
            // Bounds the only Kotlin list that must be sorted in memory.
            maxModelsPerWallet = 16_384
        )
    }
}

@Suppress("LargeClass", "TooManyFunctions")
class AssetsOrderMigration internal constructor(
    private val limits: AssetsOrderMigrationLimits
) : Migration(34, 35) {

    constructor() : this(AssetsOrderMigrationLimits.PRODUCTION)

    override fun migrate(database: SupportSQLiteDatabase) {
        requireReleasedAssetMetaIdIndex(database) { message ->
            AssetsOrderMigrationIntegrityException(message)
        }
        createTemporaryChainAssetLookup(database)
        try {
            val metaIds = preflightInputs(database)

            database.execSQL(
                "ALTER TABLE assets " +
                    "ADD COLUMN `sortIndex` INTEGER NOT NULL DEFAULT 0"
            )
            database.execSQL(
                "ALTER TABLE assets " +
                    "ADD COLUMN `enabled` INTEGER NOT NULL DEFAULT 1"
            )

            metaIds.forEach { metaId ->
                val models = readAssetModels(
                    database = database,
                    metaId = metaId
                ).sortedWith(SORTING_COMPARATOR)
                models.forEachIndexed { index, sortingModel ->
                    database.updateSortIndexForAsset(sortingModel, index)
                }
            }
        } finally {
            dropTemporaryChainAssetLookup(database)
        }
    }

    private fun preflightInputs(
        database: SupportSQLiteDatabase
    ): List<Long> {
        val metaIds = getAccountIds(database)
        preflightReferencedTable(
            database = database,
            tableName = "tokens",
            primaryKeyColumn = "symbol",
            maximumRows = limits.maxTokenRows,
            context = "Token"
        )
        preflightReferencedTable(
            database = database,
            tableName = "chains",
            primaryKeyColumn = "id",
            maximumRows = limits.maxChainRows,
            context = "Chain"
        )
        requireTableRowLimit(
            database = database,
            tableName = "chain_assets",
            maximumRows = limits.maxChainAssetRows,
            context = "Chain-asset"
        )
        requireTableRowLimit(
            database = database,
            tableName = "assets",
            maximumRows = limits.maxAssetRows,
            context = "Asset"
        )
        readConsumedChainAssets(database)
        preflightAssetModels(
            database = database,
            metaIds = metaIds.toSet()
        )
        return metaIds
    }

    private fun createTemporaryChainAssetLookup(
        database: SupportSQLiteDatabase
    ) {
        // Version 34 stored lowercase/mixed-case registry ids while balances
        // used their Locale.ROOT-uppercase symbols. Keep the normalized index
        // in connection-local SQLite storage so hundreds of thousands of
        // registry rows cannot become a retained Kotlin heap map.
        dropTemporaryChainAssetLookup(database)
        database.execSQL(
            """
            CREATE TEMP TABLE `$TEMP_CHAIN_ASSET_LOOKUP` (
                `chainId` TEXT NOT NULL,
                `normalizedSymbol` TEXT NOT NULL,
                `precision` INTEGER NOT NULL,
                PRIMARY KEY(`chainId`, `normalizedSymbol`)
            ) WITHOUT ROWID
            """.trimIndent()
        )
    }

    private fun dropTemporaryChainAssetLookup(
        database: SupportSQLiteDatabase
    ) {
        database.execSQL(
            "DROP TABLE IF EXISTS temp.`$TEMP_CHAIN_ASSET_LOOKUP`"
        )
    }

    private fun getAccountIds(
        database: SupportSQLiteDatabase
    ): List<Long> {
        val boundedId = boundedIntegerProjection(
            column = "id",
            alias = BOUNDED_META_ID
        )
        val result = mutableListOf<Long>()
        val uniqueIds = mutableSetOf<Long>()
        database.query(
            "SELECT $boundedId FROM meta_accounts ORDER BY rowid ASC " +
                "LIMIT ${limits.maxWalletRows + 1}"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                if (result.size == limits.maxWalletRows) {
                    fail("Wallet rows exceed the asset-order migration limit")
                }
                val metaId = cursor.requirePositiveInteger(
                    alias = BOUNDED_META_ID,
                    context = "wallet id"
                )
                if (!uniqueIds.add(metaId)) {
                    fail("Wallet ids are not unique")
                }
                result += metaId
            }
        }
        return result
    }

    /**
     * The released v34 tables already enforce join uniqueness with their
     * single-column primary-key b-trees. Validate that exact schema invariant
     * instead of rebuilding all key values in a GROUP BY temporary b-tree.
     *
     * Only row presence is projected for the global cap. Payloads belonging
     * to unreferenced registry rows are irrelevant to this migration and are
     * validated lazily only when an asset join actually consumes them.
     */
    private fun preflightReferencedTable(
        database: SupportSQLiteDatabase,
        tableName: String,
        primaryKeyColumn: String,
        maximumRows: Int,
        context: String
    ) {
        requireReleasedTextPrimaryKey(
            database = database,
            tableName = tableName,
            primaryKeyColumn = primaryKeyColumn,
            context = context
        )
        requireTableRowLimit(
            database = database,
            tableName = tableName,
            maximumRows = maximumRows,
            context = context
        )
    }

    @Suppress("NestedBlockDepth")
    private fun readConsumedChainAssets(
        database: SupportSQLiteDatabase
    ) {
        val chainId = boundedTextProjection(
            column = "ca.chainId",
            alias = BOUNDED_CHAIN_ASSET_CHAIN_ID,
            maxBytes = MAX_CHAIN_ID_BYTES
        )
        val symbol = boundedTextProjection(
            column = "ca.id",
            alias = BOUNDED_CHAIN_ASSET_ID,
            maxBytes = MAX_TOKEN_SYMBOL_BYTES
        )
        val precision = boundedIntegerProjection(
            column = "ca.precision",
            alias = BOUNDED_CHAIN_ASSET_PRECISION
        )
        database.compileStatement(
            "INSERT OR IGNORE INTO temp.`$TEMP_CHAIN_ASSET_LOOKUP`(" +
                "chainId, normalizedSymbol, precision) VALUES(?, ?, ?)"
        ).use { insert ->
            database.query(
                "SELECT $chainId, $symbol, $precision " +
                    "FROM chain_assets AS ca " +
                    "INNER JOIN chains AS c ON c.id = ca.chainId " +
                    "WHERE EXISTS(" +
                    "SELECT 1 FROM assets AS a " +
                    "INNER JOIN meta_accounts AS m ON m.id = a.metaId " +
                    "INNER JOIN tokens AS t ON t.symbol = a.tokenSymbol " +
                    "WHERE a.chainId = ca.chainId " +
                    "AND a.tokenSymbol = UPPER(ca.id) " +
                    ") ORDER BY ca.rowid ASC " +
                    "LIMIT ${limits.maxChainAssetRows + 1}"
            ).use { cursor ->
                var rowCount = 0
                while (cursor.moveToNext()) {
                    if (rowCount == limits.maxChainAssetRows) {
                        fail(
                            "Chain-asset rows exceed the " +
                                "asset-order migration limit"
                        )
                    }
                    rowCount += 1
                    val exactChainId = cursor.requireText(
                        BOUNDED_CHAIN_ASSET_CHAIN_ID,
                        MAX_CHAIN_ID_BYTES,
                        "chain-asset chain id"
                    )
                    val exactId = cursor.requireText(
                        BOUNDED_CHAIN_ASSET_ID,
                        MAX_TOKEN_SYMBOL_BYTES,
                        "chain-asset id"
                    )
                    val exactPrecision = cursor.requireIntegerInRange(
                        BOUNDED_CHAIN_ASSET_PRECISION,
                        0L..MAX_ASSET_PRECISION.toLong(),
                        "asset precision"
                    )
                    insert.clearBindings()
                    insert.bindString(1, exactChainId)
                    insert.bindString(
                        2,
                        normalizeHistoricalAssetSymbol(exactId)
                    )
                    insert.bindLong(3, exactPrecision)
                    if (insert.executeUpdateDelete() != 1) {
                        fail(
                            "Chain-asset ids collide after historical " +
                                "case normalization"
                        )
                    }
                }
            }
        }
    }

    private fun requireReleasedTextPrimaryKey(
        database: SupportSQLiteDatabase,
        tableName: String,
        primaryKeyColumn: String,
        context: String
    ) {
        check(
            tableName.all { it == '_' || it.isLetterOrDigit() } &&
                primaryKeyColumn.all { it == '_' || it.isLetterOrDigit() }
        )
        val columnName = boundedTextProjection(
            column = "name",
            alias = BOUNDED_PRIMARY_KEY_NAME,
            maxBytes = MAX_SCHEMA_IDENTIFIER_BYTES
        )
        val declaredType = boundedTextProjection(
            column = "type",
            alias = BOUNDED_PRIMARY_KEY_TYPE,
            maxBytes = MAX_SCHEMA_TYPE_BYTES
        )
        val notNull = boundedIntegerProjection(
            column = "`notnull`",
            alias = BOUNDED_PRIMARY_KEY_NOT_NULL
        )
        val primaryKeyPosition = boundedIntegerProjection(
            column = "pk",
            alias = BOUNDED_PRIMARY_KEY_POSITION
        )
        database.query(
            "SELECT $columnName, $declaredType, $notNull, " +
                "$primaryKeyPosition FROM pragma_table_info('$tableName') " +
                "WHERE name = ? OR pk != 0 ORDER BY pk ASC LIMIT 2",
            arrayOf<Any?>(primaryKeyColumn)
        ).use { cursor ->
            if (!cursor.moveToFirst()) {
                fail("$context table does not expose its released primary key")
            }
            val exactColumnName = cursor.requireText(
                BOUNDED_PRIMARY_KEY_NAME,
                MAX_SCHEMA_IDENTIFIER_BYTES,
                "$context primary-key column"
            )
            val exactDeclaredType = cursor.requireText(
                BOUNDED_PRIMARY_KEY_TYPE,
                MAX_SCHEMA_TYPE_BYTES,
                "$context primary-key type"
            )
            val exactNotNull = cursor.requireIntegerInRange(
                BOUNDED_PRIMARY_KEY_NOT_NULL,
                0L..1L,
                "$context primary-key nullability"
            )
            val exactPrimaryKeyPosition = cursor.requireIntegerInRange(
                BOUNDED_PRIMARY_KEY_POSITION,
                0L..MAX_PRIMARY_KEY_COLUMNS.toLong(),
                "$context primary-key position"
            )
            if (
                exactColumnName != primaryKeyColumn ||
                exactDeclaredType != SQLITE_TEXT_TYPE ||
                exactNotNull != 1L ||
                exactPrimaryKeyPosition != 1L ||
                cursor.moveToNext()
            ) {
                fail("$context table does not have its released primary key")
            }
        }
    }

    private fun requireTableRowLimit(
        database: SupportSQLiteDatabase,
        tableName: String,
        maximumRows: Int,
        context: String
    ) {
        check(tableName.all { it == '_' || it.isLetterOrDigit() })
        database.query(
            "SELECT 1 FROM `$tableName` LIMIT ${maximumRows + 1}"
        ).use { cursor ->
            var rowCount = 0
            while (cursor.moveToNext()) {
                if (rowCount == maximumRows) {
                    fail(
                        "$context rows exceed the asset-order migration limit"
                    )
                }
                rowCount += 1
            }
        }
    }

    private fun normalizeHistoricalAssetSymbol(value: String): String {
        val normalized = value.uppercase(Locale.ROOT)
        if (
            normalized.isEmpty() ||
            normalized.toByteArray(Charsets.UTF_8).size >
            MAX_TOKEN_SYMBOL_BYTES
        ) {
            fail(
                "A chain-asset id has an unsafe normalized symbol"
            )
        }
        return normalized
    }

    private fun preflightAssetModels(
        database: SupportSQLiteDatabase,
        metaIds: Set<Long>
    ) {
        val modelCounts = metaIds.associateWithTo(mutableMapOf()) { 0 }
        var totalModels = 0L
        forEachAssetModel(
            database = database,
            onlyMetaId = null
        ) { model ->
            val currentCount = checkNotNull(modelCounts[model.metaId])
            if (currentCount == limits.maxModelsPerWallet) {
                fail(
                    "Joined asset rows exceed the per-wallet migration limit"
                )
            }
            modelCounts[model.metaId] = currentCount + 1
            totalModels += 1L
            if (totalModels > limits.maxAssetRows.toLong()) {
                fail(
                    "Joined asset rows exceed the total migration limit"
                )
            }
        }
    }

    private fun readAssetModels(
        database: SupportSQLiteDatabase,
        metaId: Long
    ): List<SortingModel> {
        val models = mutableListOf<SortingModel>()
        forEachAssetModel(
            database = database,
            onlyMetaId = metaId
        ) { model ->
            if (models.size == limits.maxModelsPerWallet) {
                fail(
                    "Joined asset rows exceed the per-wallet migration limit"
                )
            }
            models += model
        }
        return models
    }

    @Suppress("ComplexMethod", "LongMethod", "NestedBlockDepth")
    private fun forEachAssetModel(
        database: SupportSQLiteDatabase,
        onlyMetaId: Long?,
        action: (SortingModel) -> Unit
    ) {
        val rowId = boundedIntegerProjection(
            column = "a.rowid",
            alias = BOUNDED_MODEL_ROW_ID
        )
        val metaId = boundedIntegerProjection(
            column = "a.metaId",
            alias = BOUNDED_MODEL_META_ID
        )
        val free = boundedTextProjection(
            column = "a.freeInPlanks",
            alias = BOUNDED_MODEL_FREE,
            maxBytes = MAX_BALANCE_BYTES
        )
        val reserved = boundedTextProjection(
            column = "a.reservedInPlanks",
            alias = BOUNDED_MODEL_RESERVED,
            maxBytes = MAX_BALANCE_BYTES
        )
        val dollarRate = boundedTextProjection(
            column = "t.dollarRate",
            alias = BOUNDED_MODEL_DOLLAR_RATE,
            maxBytes = MAX_DOLLAR_RATE_BYTES
        )
        val precision = boundedIntegerProjection(
            column = "ca.precision",
            alias = BOUNDED_MODEL_PRECISION
        )
        val isTestNet = boundedIntegerProjection(
            column = "c.isTestNet",
            alias = BOUNDED_MODEL_IS_TEST_NET
        )
        val chainName = boundedTextProjection(
            column = "c.name",
            alias = BOUNDED_MODEL_CHAIN_NAME,
            maxBytes = MAX_CHAIN_NAME_BYTES
        )
        val isPolkadotOrKusama = boundedIntegerProjection(
            column = "CASE WHEN a.chainId = '$POLKADOT_CHAIN_ID' " +
                "OR a.chainId = '$KUSAMA_CHAIN_ID' THEN 1 ELSE 0 END",
            alias = BOUNDED_MODEL_IS_POLKADOT_OR_KUSAMA
        )
        val maximumRows = if (onlyMetaId == null) {
            limits.maxAssetRows
        } else {
            limits.maxModelsPerWallet
        }
        val where = if (onlyMetaId == null) {
            ""
        } else {
            "WHERE a.metaId = ? "
        }
        val bindArguments = if (onlyMetaId == null) {
            emptyArray<Any?>()
        } else {
            arrayOf<Any?>(onlyMetaId)
        }
        val assetIndex = if (onlyMetaId == null) {
            ""
        } else {
            " INDEXED BY `$RELEASED_ASSET_META_ID_INDEX`"
        }
        database.query(
            "SELECT $rowId, $metaId, " +
                "$free, $reserved, $dollarRate, $precision, " +
                "$isTestNet, $chainName, $isPolkadotOrKusama " +
                "FROM assets AS a$assetIndex " +
                "INNER JOIN meta_accounts AS m ON m.id = a.metaId " +
                "INNER JOIN tokens AS t ON t.symbol = a.tokenSymbol " +
                "INNER JOIN temp.`$TEMP_CHAIN_ASSET_LOOKUP` AS ca " +
                "ON ca.chainId = a.chainId " +
                "AND ca.normalizedSymbol = a.tokenSymbol " +
                "INNER JOIN chains AS c ON c.id = a.chainId " +
                where +
                "ORDER BY a.rowid ASC LIMIT ${maximumRows + 1}",
            bindArguments
        ).use { cursor ->
            var rowCount = 0
            while (cursor.moveToNext()) {
                if (rowCount == maximumRows) {
                    fail(
                        "Joined asset rows exceed the migration limit"
                    )
                }
                rowCount += 1
                val exactRowId = cursor.requirePositiveInteger(
                    BOUNDED_MODEL_ROW_ID,
                    "asset row id"
                )
                val exactMetaId = cursor.requirePositiveInteger(
                    BOUNDED_MODEL_META_ID,
                    "asset wallet id"
                )
                val free = cursor.requireUnsignedBigInteger(
                    BOUNDED_MODEL_FREE,
                    MAX_BALANCE_BYTES,
                    "free balance"
                )
                val reserved = cursor.requireUnsignedBigInteger(
                    BOUNDED_MODEL_RESERVED,
                    MAX_BALANCE_BYTES,
                    "reserved balance"
                )
                val precision = cursor.requireIntegerInRange(
                    BOUNDED_MODEL_PRECISION,
                    0L..MAX_ASSET_PRECISION.toLong(),
                    "asset precision"
                ).toInt()
                val totalBalance =
                    (free + reserved).toBigDecimal(precision)
                val dollarRate = cursor.readDollarRate(
                    BOUNDED_MODEL_DOLLAR_RATE
                )
                val isTestNet = cursor.requireIntegerInRange(
                    BOUNDED_MODEL_IS_TEST_NET,
                    0L..1L,
                    "chain testnet flag"
                ) == 1L
                val exactChainName = cursor.requireText(
                    BOUNDED_MODEL_CHAIN_NAME,
                    MAX_CHAIN_NAME_BYTES,
                    "chain name"
                )
                val exactIsPolkadotOrKusama =
                    cursor.requireIntegerInRange(
                        BOUNDED_MODEL_IS_POLKADOT_OR_KUSAMA,
                        0L..1L,
                        "asset relay-chain classification"
                    ) == 1L
                action(
                    SortingModel(
                        rowId = exactRowId,
                        metaId = exactMetaId,
                        totalBalance = totalBalance,
                        totalFiat = dollarRate?.multiply(
                            totalBalance
                        ),
                        isTestNet = isTestNet,
                        isPolkadotOrKusama = exactIsPolkadotOrKusama,
                        chainName = exactChainName
                    )
                )
            }
        }
    }

    private fun Cursor.readDollarRate(alias: String): BigDecimal? {
        val bounded = readBoundedText(
            alias = alias,
            maxBytes = MAX_DOLLAR_RATE_BYTES
        )
        if (!bounded.isPresent) return null
        val encoded = bounded.value
        if (
            !bounded.hasExpectedStorageClass ||
            bounded.isOversized ||
            encoded == null
        ) {
            fail("Dollar rate is not safe bounded SQLite text")
        }
        val value = try {
            BigDecimal(encoded)
        } catch (failure: NumberFormatException) {
            throw AssetsOrderMigrationIntegrityException(
                "Dollar rate is not a valid decimal",
                failure
            )
        }
        if (
            value.precision() > MAX_DOLLAR_RATE_PRECISION ||
            value.scale() !in -MAX_DOLLAR_RATE_SCALE..MAX_DOLLAR_RATE_SCALE
        ) {
            fail("Dollar rate exceeds the safe decimal range")
        }
        return value
    }

    private fun Cursor.requireUnsignedBigInteger(
        alias: String,
        maxBytes: Int,
        context: String
    ): BigInteger {
        val encoded = requireText(alias, maxBytes, context)
        if (!UNSIGNED_DECIMAL.matches(encoded)) {
            fail("$context is not a canonical unsigned integer")
        }
        return try {
            BigInteger(encoded)
        } catch (failure: NumberFormatException) {
            throw AssetsOrderMigrationIntegrityException(
                "$context is outside the safe integer range",
                failure
            )
        }
    }

    private fun Cursor.requireText(
        alias: String,
        maxBytes: Int,
        context: String
    ): String {
        val bounded = readBoundedText(alias, maxBytes)
        val value = bounded.value
        if (
            !bounded.isPresent ||
            !bounded.hasExpectedStorageClass ||
            bounded.isOversized ||
            value.isNullOrEmpty()
        ) {
            fail("$context is not safe bounded SQLite text")
        }
        return value
    }

    private fun Cursor.requireBlob(
        alias: String,
        maxBytes: Int,
        context: String
    ): ByteArray {
        val bounded = readBoundedBlob(alias, maxBytes)
        val value = bounded.value
        if (
            !bounded.isPresent ||
            !bounded.hasExpectedStorageClass ||
            bounded.isOversized ||
            value == null ||
            value.isEmpty()
        ) {
            fail("$context is not a safe bounded SQLite blob")
        }
        return value
    }

    private fun Cursor.requirePositiveInteger(
        alias: String,
        context: String
    ): Long {
        return requireIntegerInRange(
            alias = alias,
            allowedRange = 1L..Long.MAX_VALUE,
            context = context
        )
    }

    private fun Cursor.requireIntegerInRange(
        alias: String,
        allowedRange: LongRange,
        context: String
    ): Long {
        val bounded = readBoundedInteger(alias)
        val value = bounded.value
        if (
            !bounded.hasExpectedStorageClass ||
            value == null ||
            value !in allowedRange
        ) {
            fail("$context is not a safe bounded SQLite integer")
        }
        return value
    }

    private fun SupportSQLiteDatabase.updateSortIndexForAsset(
        asset: SortingModel,
        index: Int
    ) {
        val contentValues = ContentValues().apply {
            put("sortIndex", index)
        }
        val updatedRows = update(
            "assets",
            SQLiteDatabase.CONFLICT_ABORT,
            contentValues,
            "rowid=? AND metaId=?",
            arrayOf(
                asset.rowId,
                asset.metaId
            )
        )
        if (updatedRows != 1) {
            fail(
                "Asset sort-index update matched $updatedRows rows instead of one"
            )
        }
    }

    private fun fail(message: String): Nothing {
        throw AssetsOrderMigrationIntegrityException(message)
    }

    private data class SortingModel(
        val rowId: Long,
        val metaId: Long,
        val totalBalance: BigDecimal,
        val totalFiat: BigDecimal?,
        val isTestNet: Boolean,
        val isPolkadotOrKusama: Boolean,
        val chainName: String
    )

    private companion object {
        const val TEMP_CHAIN_ASSET_LOOKUP =
            "_assets_order_normalized_chain_assets"
        const val POLKADOT_CHAIN_ID =
            "91b171bb158e2d3848fa23a9f1c25182fb8e20313b2c1eb49219da7a70ce90c3"
        const val KUSAMA_CHAIN_ID =
            "b0a8d493285c2df73290dfb7e61f870f17b41801197a149ca93654499ea3dafe"

        const val MAX_TOKEN_SYMBOL_BYTES = 128
        const val MAX_CHAIN_ID_BYTES = 128
        const val MAX_BALANCE_BYTES = 128
        const val MAX_DOLLAR_RATE_BYTES = 128
        const val MAX_CHAIN_NAME_BYTES = 512
        const val MAX_SCHEMA_IDENTIFIER_BYTES = 128
        const val MAX_SCHEMA_TYPE_BYTES = 32
        const val MAX_PRIMARY_KEY_COLUMNS = 64
        const val MAX_ASSET_PRECISION = 255
        const val MAX_DOLLAR_RATE_PRECISION = 96
        const val MAX_DOLLAR_RATE_SCALE = 96
        const val SQLITE_TEXT_TYPE = "TEXT"

        const val BOUNDED_META_ID = "boundedOrderMetaId"
        const val BOUNDED_PRIMARY_KEY_NAME =
            "boundedOrderPrimaryKeyName"
        const val BOUNDED_PRIMARY_KEY_TYPE =
            "boundedOrderPrimaryKeyType"
        const val BOUNDED_PRIMARY_KEY_NOT_NULL =
            "boundedOrderPrimaryKeyNotNull"
        const val BOUNDED_PRIMARY_KEY_POSITION =
            "boundedOrderPrimaryKeyPosition"
        const val BOUNDED_CHAIN_ASSET_CHAIN_ID =
            "boundedOrderChainAssetChainId"
        const val BOUNDED_CHAIN_ASSET_ID = "boundedOrderChainAssetId"
        const val BOUNDED_CHAIN_ASSET_PRECISION =
            "boundedOrderChainAssetPrecision"
        const val BOUNDED_MODEL_ROW_ID = "boundedOrderModelRowId"
        const val BOUNDED_MODEL_TOKEN_SYMBOL =
            "boundedOrderModelTokenSymbol"
        const val BOUNDED_MODEL_CHAIN_ID = "boundedOrderModelChainId"
        const val BOUNDED_MODEL_META_ID = "boundedOrderModelMetaId"
        const val BOUNDED_MODEL_FREE = "boundedOrderModelFree"
        const val BOUNDED_MODEL_RESERVED = "boundedOrderModelReserved"
        const val BOUNDED_MODEL_DOLLAR_RATE =
            "boundedOrderModelDollarRate"
        const val BOUNDED_MODEL_PRECISION = "boundedOrderModelPrecision"
        const val BOUNDED_MODEL_IS_TEST_NET =
            "boundedOrderModelIsTestNet"
        const val BOUNDED_MODEL_CHAIN_NAME = "boundedOrderModelChainName"
        const val BOUNDED_MODEL_IS_POLKADOT_OR_KUSAMA =
            "boundedOrderModelIsPolkadotOrKusama"

        val UNSIGNED_DECIMAL = Regex("^(0|[1-9][0-9]*)$")
        val SORTING_COMPARATOR = compareByDescending<SortingModel> {
            it.totalBalance > BigDecimal.ZERO
        }
            .thenByDescending { it.totalFiat ?: BigDecimal.ZERO }
            .thenBy { it.isTestNet }
            .thenByDescending { it.isPolkadotOrKusama }
            .thenBy { it.chainName }
    }
}
