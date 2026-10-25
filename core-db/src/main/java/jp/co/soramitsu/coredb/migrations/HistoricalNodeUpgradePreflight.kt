package jp.co.soramitsu.coredb.migrations

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Checks the additional data retained by 39 -> 40 before 31 -> 32 can commit
 * encrypted preferences. The SQL preview is rolled back before secret work.
 * Call only after [Db31UpgradeSqlPreflight] has verified the released schema.
 */
internal object HistoricalNodeUpgradePreflight {
    /** Room's real edge and its V2 preview choose the same downstream-qualified transfer. */
    fun migrateLegacyNodes(database: SupportSQLiteDatabase) {
        check(LegacyNodeArchive.requireSafe(database)) { "Legacy node source is missing" }
        val qualify = database.inTransaction() && database.query("PRAGMA foreign_keys").use { cursor ->
            check(cursor.moveToFirst() && cursor.getType(0) == Cursor.FIELD_TYPE_INTEGER) {
                "Legacy node foreign-key mode is unreadable"
            }
            val disabled = cursor.getLong(0) == 0L
            check(!cursor.moveToNext()) { "Legacy node foreign-key mode is ambiguous" }
            disabled
        }
        val validate: ((SupportSQLiteDatabase) -> Unit)? = if (qualify) {
            { candidate ->
                Db31UpgradeSqlPreflight.requireSafeBeforePreferenceAccess(candidate)
                requireSafeBeforeDb31SecretWrite(candidate)
            }
        } else null
        LegacyNodeMigration.migrate(database, validate)
    }

    /**
     * V2 has prepared its public wallet rows but has not committed preferences.
     * Replay the real cache reset and legacy transfer, then the same DB31
     * guard used by the next secret-writing edge. Both savepoints roll back;
     * Room's user_version still denotes the original source throughout.
     */
    fun requireSafeBeforeV2SecretWrite(database: SupportSQLiteDatabase) {
        if (!LegacyNodeArchive.requireSafe(database)) return
        check(database.inTransaction()) { "Legacy node preview requires the migration transaction" }
        database.query("PRAGMA foreign_keys").use { cursor ->
            check(cursor.moveToFirst() && cursor.getLong(0) == 0L && !cursor.moveToNext()) {
                "Legacy node preview requires foreign keys disabled before the migration transaction"
            }
        }
        withRollbackOnlySqlPreview(database) {
            MigrateTablesToV2_29_30.migrate(database)
            // This actual edge validates DB31 and the complete SQL suffix even
            // when no source endpoint is eligible for automatic materialization.
            MigrateTablesToV2_30_31.migrate(database)
        }
    }

    fun requireSafeBeforeDb31SecretWrite(
        database: SupportSQLiteDatabase,
        stagingPayloadLimits: TonUpgradePayloadLimits = TonUpgradePayloadLimits.PRODUCTION,
        tonPayloadLimits: TonUpgradePayloadLimits = TonUpgradePayloadLimits.PRODUCTION
    ) {
        LegacyNodeArchive.requireSafe(database)
        // Even an empty modern source is captured at 39. Its exact archive
        // schema admission must succeed before preferences can be committed.
        check(database.inTransaction()) { "Historical node preview requires the migration transaction" }
        database.query("PRAGMA foreign_keys").use { cursor ->
            check(cursor.moveToFirst() && cursor.getLong(0) == 0L && !cursor.moveToNext()) {
                "Historical node preview requires foreign keys disabled before the migration transaction"
            }
        }

        var stagingBytes = requireBoundedDb31Payloads(database, stagingPayloadLimits)
        stagingBytes = requireBoundedHistoricalPayload(
            database, "chain_accounts", CHAIN_ACCOUNT_COLUMNS,
            WalletMigrationRowLimits.PRODUCTION.maxChainAccountRows,
            stagingBytes, stagingPayloadLimits
        )
        stagingBytes = requireBoundedHistoricalPayload(
            database, "users", USER_COLUMNS,
            V2MigrationLimits.PRODUCTION.maxAccountPlans,
            stagingBytes, stagingPayloadLimits
        )
        requireBoundedHistoricalPayload(
            database, "meta_accounts", META_ACCOUNT_COLUMNS,
            WalletMigrationRowLimits.PRODUCTION.maxWalletRows,
            stagingBytes, stagingPayloadLimits,
            minimumCellBytes = mapOf("ethereumPublicKey" to 65L, "ethereumAddress" to 20L)
        )

        withRollbackOnlySqlPreview(database) {
            // 31 -> 32 derives at most these public EVM bytes. Keep longer
            // existing cells so the preview never hides an oversized value.
            // Its asset accountId changes disappear at the first SQL edge.
            database.execSQL(
                "UPDATE meta_accounts SET ethereumPublicKey = CASE " +
                    "WHEN ethereumPublicKey IS NULL OR length(CAST(ethereumPublicKey AS BLOB)) < 65 " +
                    "THEN zeroblob(65) ELSE ethereumPublicKey END, ethereumAddress = CASE " +
                    "WHEN ethereumAddress IS NULL OR length(CAST(ethereumAddress AS BLOB)) < 20 " +
                    "THEN zeroblob(20) ELSE ethereumAddress END"
            )
            HISTORICAL_SQL_MIGRATIONS_32_TO_71.forEach { it.migrate(database) }
            // Preserve the real TON guard's eight-table aggregate, including
            // newly retained chain parents and columns added along the way.
            TonUpgradeSqlPreparation.prepare(database)
            TonUpgradeSqlPreflight.requireSafeToMutate(database, payloadLimits = tonPayloadLimits)
            CurrentEndpointUpgradePreflight.runSqlSuffix(database, fromVersion = 71)
        }
    }

    /**
     * Bounds every original node copied into the archive, including malformed
     * and unselected rows. Only ordinary retained nodes contribute parents;
     * wallet parents remain included independently. No source value is changed.
     * The exact DB31 schema guarantees unique non-null node keys before use.
     */
    fun requireBoundedDb31Payloads(
        database: SupportSQLiteDatabase,
        payloadLimits: TonUpgradePayloadLimits = TonUpgradePayloadLimits.PRODUCTION
    ): Long {
        requireBoundedMigrationTableRows(database, "chain_nodes", NODE_ROWS, "Historical node rows")
        requireBoundedMigrationTableRows(database, "chains", CHAIN_ROWS, "Historical chain rows")
        requireBoundedMigrationTableRows(
            database, "chain_accounts", WalletMigrationRowLimits.PRODUCTION.maxChainAccountRows,
            "Historical chain-account rows"
        )
        val nodeBytes = requireBoundedHistoricalPayload(
            database, "chain_nodes", NODE_COLUMNS, NODE_ROWS, 0L, payloadLimits
        )
        return requireBoundedHistoricalPayload(
            database, "chains", CHAIN_COLUMNS, CHAIN_ROWS, nodeBytes, payloadLimits,
            where = "EXISTS (SELECT 1 FROM chain_accounts WHERE chain_accounts.chainId = chains.id) OR " +
                "id IN (SELECT chainId FROM chain_nodes WHERE ($RETAINED_NODE_PREDICATE) " +
                "AND typeof(chainId) = 'text' AND typeof(url) = 'text' AND typeof(name) = 'text' " +
                "AND typeof(isActive) = 'integer' AND isActive IN (0, 1) " +
                "AND typeof(isDefault) = 'integer' AND isDefault IN (0, 1))"
        )
    }

    fun hasRetainedNodes(database: SupportSQLiteDatabase): Boolean {
        requireBoundedMigrationTableRows(
            database, "chain_nodes", NODE_ROWS, "Historical node rows"
        )
        database.query(
            "SELECT 1 FROM chain_nodes WHERE typeof(isDefault) != 'integer' " +
                "OR isDefault NOT IN (0, 1) OR typeof(isActive) != 'integer' " +
                "OR isActive NOT IN (0, 1) LIMIT 1"
        ).use { cursor ->
            check(!cursor.moveToFirst()) { "Historical nodes contain invalid ownership or selection flags" }
        }
        return database.query("SELECT 1 FROM chain_nodes WHERE $RETAINED_NODE_PREDICATE LIMIT 1").use {
            it.moveToFirst()
        }
    }

    /** Bounds only rows staged for the rebuild, before SQLite copies their payloads. */
    fun requireBoundedRetentionPayloads(
        database: SupportSQLiteDatabase,
        payloadLimits: TonUpgradePayloadLimits = TonUpgradePayloadLimits.PRODUCTION
    ): Long {
        // Bound all inputs before evaluating the parent union or anti-join.
        requireBoundedMigrationTableRows(database, "chain_nodes", NODE_ROWS, "Historical node rows")
        requireBoundedMigrationTableRows(database, "chains", CHAIN_ROWS, "Historical chain rows")
        requireBoundedMigrationTableRows(
            database, "chain_accounts", WalletMigrationRowLimits.PRODUCTION.maxChainAccountRows,
            "Historical chain-account rows"
        )
        val nodeBytes = requireBoundedHistoricalPayload(
            database, "chain_nodes", NODE_COLUMNS, NODE_ROWS, 0L, payloadLimits,
            where = RETAINED_NODE_PREDICATE
        )
        database.query(
            "SELECT 1 FROM chain_nodes AS n LEFT JOIN chains AS c ON c.id = n.chainId " +
                "WHERE (n.isDefault = 0 OR n.isActive = 1) AND c.id IS NULL LIMIT 1"
        ).use { cursor ->
            check(!cursor.moveToFirst()) { "Historical retained nodes reference missing chains" }
        }
        return requireBoundedHistoricalPayload(
            database, "chains", CHAIN_COLUMNS, CHAIN_ROWS, nodeBytes, payloadLimits,
            where = "EXISTS (SELECT 1 FROM chain_accounts WHERE chain_accounts.chainId = chains.id) OR " +
                "id IN (SELECT chainId FROM chain_nodes WHERE $RETAINED_NODE_PREDICATE)"
        )
    }

    private val NODE_ROWS = TON_UPGRADE_FOREIGN_KEY_CHECK_LIMITS.maximumRowsByTable.getValue("chain_nodes")
    private val CHAIN_ROWS = TON_UPGRADE_FOREIGN_KEY_CHECK_LIMITS.maximumRowsByTable.getValue("chains")
    private const val RETAINED_NODE_PREDICATE = "isDefault = 0 OR isActive = 1"
    private val NODE_COLUMNS = listOf("chainId", "url", "name", "isActive", "isDefault")
    private val CHAIN_COLUMNS = listOf(
        "id", "parentId", "name", "icon", "prefix", "isEthereumBased", "isTestNet", "hasCrowdloans",
        "url", "overridesCommon", "staking_url", "staking_type", "history_url", "history_type",
        "crowdloans_url", "crowdloans_type"
    )
    private val CHAIN_ACCOUNT_COLUMNS = listOf("metaId", "chainId", "publicKey", "accountId", "cryptoType")
    // networkType is discarded by 35 -> 36; all copied users fields are bounded.
    private val USER_COLUMNS = listOf("address", "username", "publicKey", "cryptoType", "position")
    private val META_ACCOUNT_COLUMNS = listOf(
        "substratePublicKey", "substrateCryptoType", "substrateAccountId", "ethereumPublicKey",
        "ethereumAddress", "name", "isSelected", "position", "id"
    )
}

/**
 * Reads only scalar byte lengths, with bounded cursor size and checked sums.
 * SQL identifiers and the optional predicate are internal schema constants.
 * The returned sum lets all preview staging tables share one resource budget.
 */
internal fun requireBoundedHistoricalPayload(
    database: SupportSQLiteDatabase,
    table: String,
    columns: List<String>,
    maximumRows: Int,
    startingTotalBytes: Long,
    limits: TonUpgradePayloadLimits,
    where: String? = null,
    minimumCellBytes: Map<String, Long> = emptyMap()
): Long {
    require(maximumRows in 1 until Int.MAX_VALUE)
    require(columns.isNotEmpty())
    require((columns + table).all { name -> name.isNotEmpty() && name.all { it == '_' || it.isLetterOrDigit() } })
    require(minimumCellBytes.keys.all(columns::contains) && minimumCellBytes.values.all { it >= 0L })
    require(startingTotalBytes in 0..limits.maximumTotalBytes)
    val projection = columns.mapIndexed { index, column -> "length(CAST(`$column` AS BLOB)) AS payload$index" }
    val predicate = where?.let { " WHERE ($it)" }.orEmpty()
    val query = "SELECT ${projection.joinToString()} FROM `$table`$predicate LIMIT ${maximumRows + 1}"
    return database.query(query).use { cursor ->
        var rows = 0
        var totalBytes = startingTotalBytes
        while (cursor.moveToNext()) {
            if (rows++ == maximumRows) {
                throw MigrationPayloadLimitException("Historical $table rows exceed the safe migration limit")
            }
            var rowBytes = 0L
            columns.forEachIndexed { index, column ->
                val bytes = if (cursor.isNull(index)) {
                    0L
                } else {
                    if (cursor.getType(index) != Cursor.FIELD_TYPE_INTEGER) {
                        throw TonUpgradeSqlIntegrityException("Historical $table.$column has an invalid payload length")
                    }
                    cursor.getLong(index).also {
                        if (it < 0L) throw TonUpgradeSqlIntegrityException("Historical $table.$column has a negative payload length")
                    }
                }
                val boundedBytes = maxOf(bytes, minimumCellBytes[column] ?: 0L)
                if (boundedBytes > limits.maximumCellBytes) {
                    throw MigrationPayloadLimitException("Historical $table.$column exceeds the safe migration payload limit")
                }
                rowBytes = checkedTonUpgradePayloadByteSum(rowBytes, boundedBytes, "Historical $table row")
                if (rowBytes > limits.maximumRowBytes) {
                    throw MigrationPayloadLimitException("Historical $table row exceeds the safe migration payload limit")
                }
            }
            totalBytes = checkedTonUpgradePayloadByteSum(totalBytes, rowBytes, "Historical preview staging")
            if (totalBytes > limits.maximumTotalBytes) {
                throw MigrationPayloadLimitException("Historical preview staging exceeds the safe cumulative migration payload limit")
            }
        }
        totalBytes
    }
}
