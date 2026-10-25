package jp.co.soramitsu.coredb.migrations

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Optionally materializes declared DB30 network identities without contacting endpoints.
 * The caller admits the released archive metadata first. Original nodes always remain;
 * unsupported identities or copy budgets defer the whole archive without guessing a binding.
 */
internal object LegacyNodeMigration {
    /** Candidate discovery is bounded; archive rows themselves are never copied or discarded. */
    fun hasRetainedNodes(database: SupportSQLiteDatabase): Boolean {
        if (!hasAtMostRows(database, "nodes", NODE_ROWS)) return false
        if (hasRows(
                database,
                "SELECT 1 FROM nodes WHERE typeof(isDefault) != 'integer' OR isDefault NOT IN (0, 1) " +
                    "OR typeof(isActive) != 'integer' OR isActive NOT IN (0, 1) LIMIT 1"
            )) return false
        return hasRows(database, "SELECT 1 FROM nodes WHERE $RETAINED LIMIT 1")
    }

    /** Returns whether local copying is eligible, independently of archive presence. */
    fun requireSafe(
        database: SupportSQLiteDatabase,
        payloadLimits: TonUpgradePayloadLimits = TonUpgradePayloadLimits.PRODUCTION
    ): Boolean = prepare(database, payloadLimits).chainIds.isNotEmpty()

    fun migrate(
        database: SupportSQLiteDatabase,
        validateDownstream: ((SupportSQLiteDatabase) -> Unit)? = null
    ) {
        val plan = prepare(database, TonUpgradePayloadLimits.PRODUCTION)
        val ownsTransaction = !database.inTransaction()
        if (ownsTransaction) database.beginTransaction()
        var primaryFailure: Throwable? = null
        try {
            val lockedPlan = if (ownsTransaction) prepare(database, TonUpgradePayloadLimits.PRODUCTION) else plan
            database.execSQL("ALTER TABLE chain_nodes ADD COLUMN `isActive` INTEGER NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE chain_nodes ADD COLUMN `isDefault` INTEGER NOT NULL DEFAULT 1")
            val selected = if (validateDownstream == null) lockedPlan else qualifyTransfer(database, lockedPlan, validateDownstream)
            if (selected.chainIds.isNotEmpty()) transfer(database, selected)
            // The original rowids, stored types, bytes and sqlite_sequence entry remain intact.
            if (ownsTransaction) database.setTransactionSuccessful()
        } catch (failure: Throwable) {
            primaryFailure = failure
            throw failure
        } finally {
            if (ownsTransaction) {
                try {
                    database.endTransaction()
                } catch (cleanup: Throwable) {
                    val original = primaryFailure
                    if (original == null) throw cleanup
                    if (cleanup !== original) original.addSuppressed(cleanup)
                }
            }
        }
    }

    /** Select and apply use one locked source; the discarded candidate leaves no SQL state. */
    private fun qualifyTransfer(
        database: SupportSQLiteDatabase,
        plan: TransferPlan,
        validateDownstream: (SupportSQLiteDatabase) -> Unit
    ): TransferPlan {
        try {
            withRollbackOnlySqlPreview(database) {
                if (plan.chainIds.isNotEmpty()) transfer(database, plan)
                validateDownstream(database)
            }
            return plan
        } catch (candidateFailure: MigrationPayloadLimitException) {
            // A failed rollback/release or owned-stage cleanup must never become a fallback.
            if (plan.chainIds.isEmpty() || candidateFailure.suppressed.isNotEmpty()) throw candidateFailure
            try {
                withRollbackOnlySqlPreview(database) { validateDownstream(database) }
            } catch (fallbackFailure: Throwable) {
                if (fallbackFailure !== candidateFailure) fallbackFailure.addSuppressed(candidateFailure)
                throw fallbackFailure
            }
            return EMPTY_PLAN
        }
    }

    private fun prepare(database: SupportSQLiteDatabase, limits: TonUpgradePayloadLimits): TransferPlan = try {
        prepareTransfer(database, limits)
    } catch (failure: MigrationPayloadLimitException) {
        if (failure.suppressed.isNotEmpty()) throw failure
        EMPTY_PLAN
    }

    private fun prepareTransfer(database: SupportSQLiteDatabase, limits: TonUpgradePayloadLimits): TransferPlan {
        if (!hasRetainedNodes(database)) return EMPTY_PLAN
        if (!hasAtMostRows(database, "chains", CHAIN_ROWS) || !hasAtMostRows(database, "chain_nodes", NODE_ROWS)) return EMPTY_PLAN
        if (hasRows(
                database,
                "SELECT 1 FROM nodes WHERE ($RETAINED) AND (typeof(networkType) != 'integer' " +
                    "OR networkType NOT BETWEEN 0 AND 14 OR typeof(name) != 'text' OR typeof(link) != 'text') LIMIT 1"
            )) return EMPTY_PLAN
        var totalBytes = requireBoundedHistoricalPayload(
            database, "nodes", listOf("name", "link", "networkType", "isDefault", "isActive"),
            NODE_ROWS, 0L, limits, where = RETAINED,
            minimumCellBytes = mapOf("networkType" to legacyNodeNetworkChainIds.maxOf { it.length }.toLong())
        )
        requireNoRows(
            database,
            "SELECT 1 FROM sqlite_master WHERE name = '$STAGE' COLLATE NOCASE UNION ALL " +
                "SELECT 1 FROM sqlite_temp_master WHERE name = '$STAGE' COLLATE NOCASE LIMIT 1",
            "Legacy node migration staging name already exists"
        )
        val knownIds = legacyNodeNetworkChainIds.filter(String::isNotEmpty) + LEGACY_CORRECTED_ACALA_CHAIN_ID
        val parents = database.query("SELECT id FROM chains WHERE id COLLATE BINARY IN (${quotedIds(knownIds)}) LIMIT 17").use { cursor ->
            buildSet {
                while (cursor.moveToNext()) {
                    check(cursor.getType(0) == Cursor.FIELD_TYPE_STRING) { "Legacy chain id has invalid storage" }
                    add(cursor.getString(0))
                }
            }
        }
        val mapping = database.query("SELECT DISTINCT networkType FROM nodes WHERE $RETAINED LIMIT 16").use { cursor ->
            buildMap {
                while (cursor.moveToNext()) {
                    check(cursor.getType(0) == Cursor.FIELD_TYPE_INTEGER) { "Legacy network ordinal has invalid storage" }
                    val ordinal = cursor.getInt(0)
                    if (ordinal !in 0..14 || ordinal == 6 && legacyNodeNetworkChainIds[6] in parents &&
                        LEGACY_CORRECTED_ACALA_CHAIN_ID in parents) return EMPTY_PLAN
                    put(ordinal, legacyNodeTargetChainId(ordinal, parents))
                }
            }
        }
        check(mapping.isNotEmpty()) { "Legacy node observation changed during admission" }
        val missing = mapping.values.toSet() - parents
        val seeds = missing.map { id -> legacyNodeParentRows.singleOrNull { it[0] == id } ?: return EMPTY_PLAN }
        totalBytes = requireBoundedHistoricalPayload(
            database, "chains", CHAIN_COLUMNS, CHAIN_ROWS, totalBytes, limits,
            where = "id COLLATE BINARY IN (${quotedIds(mapping.values)})"
        )
        requireBoundedLegacySeedPayload(seeds, totalBytes, limits)
        val plan = TransferPlan(mapping, seeds)
        if (hasRows(
                database,
                "${plan.cte} SELECT 1 FROM legacy GROUP BY chainId COLLATE BINARY, url COLLATE BINARY HAVING " +
                    "COUNT(DISTINCT CASE WHEN isDefault = 0 THEN name COLLATE BINARY END) > 1 OR " +
                    "(MIN(isDefault) = 1 AND COUNT(DISTINCT name COLLATE BINARY) > 1) LIMIT 1"
            )) return EMPTY_PLAN
        if (readCount(database, "SELECT COUNT(*) FROM chains") + seeds.size > CHAIN_ROWS) return EMPTY_PLAN
        val addedNodes = readCount(
            database,
            "${plan.cte} SELECT COUNT(*) FROM merged AS m WHERE NOT EXISTS " +
                "(SELECT 1 FROM chain_nodes AS n WHERE n.chainId COLLATE BINARY = m.chainId AND n.url COLLATE BINARY = m.url)"
        )
        if (readCount(database, "SELECT COUNT(*) FROM chain_nodes") + addedNodes > NODE_ROWS) return EMPTY_PLAN
        return plan
    }

    private fun hasAtMostRows(database: SupportSQLiteDatabase, table: String, maximum: Int): Boolean =
        database.query("SELECT 1 FROM `$table` LIMIT ${maximum + 1}").use { cursor ->
            var count = 0
            while (cursor.moveToNext()) if (++count > maximum) return@use false
            true
        }

    private fun hasRows(database: SupportSQLiteDatabase, sql: String): Boolean =
        database.query(sql).use { it.moveToFirst() }

    private fun transfer(database: SupportSQLiteDatabase, plan: TransferPlan) {
        database.execSQL(
            "CREATE TEMP TABLE `$STAGE` (chainId TEXT COLLATE BINARY NOT NULL, url TEXT COLLATE BINARY NOT NULL, name TEXT COLLATE BINARY NOT NULL, " +
                "isDefault INTEGER NOT NULL, isActive INTEGER NOT NULL, PRIMARY KEY(chainId, url))"
        )
        var failure: Throwable? = null
        try {
            database.execSQL(
                "${plan.cte} INSERT INTO temp.`$STAGE` (chainId, url, name, isDefault, isActive) " +
                    "SELECT chainId, url, name, isDefault, isActive FROM merged"
            )
            plan.seeds.forEach { row ->
                database.execSQL(
                    "INSERT INTO chains (${CHAIN_COLUMNS.joinToString { "`$it`" }}) " +
                        "SELECT ${CHAIN_COLUMNS.joinToString { "?" }} WHERE NOT EXISTS (SELECT 1 FROM chains WHERE id COLLATE BINARY = ?)",
                    (row + row[0]).toTypedArray()
                )
            }
            // Existing registry rows have no ownership flags before this migration.
            // The retained legacy row wins by exact key; no parent or node REPLACE.
            database.execSQL(
                "UPDATE chain_nodes SET " +
                    "name = (SELECT s.name FROM temp.`$STAGE` s WHERE s.chainId COLLATE BINARY = chain_nodes.chainId AND s.url COLLATE BINARY = chain_nodes.url), " +
                    "isDefault = (SELECT s.isDefault FROM temp.`$STAGE` s WHERE s.chainId COLLATE BINARY = chain_nodes.chainId AND s.url COLLATE BINARY = chain_nodes.url), " +
                    "isActive = (SELECT s.isActive FROM temp.`$STAGE` s WHERE s.chainId COLLATE BINARY = chain_nodes.chainId AND s.url COLLATE BINARY = chain_nodes.url) " +
                    "WHERE EXISTS (SELECT 1 FROM temp.`$STAGE` s WHERE s.chainId COLLATE BINARY = chain_nodes.chainId AND s.url COLLATE BINARY = chain_nodes.url)"
            )
            database.execSQL(
                "INSERT INTO chain_nodes (chainId, url, name, isDefault, isActive) " +
                    "SELECT s.chainId, s.url, s.name, s.isDefault, s.isActive FROM temp.`$STAGE` s " +
                    "WHERE NOT EXISTS (SELECT 1 FROM chain_nodes n WHERE n.chainId COLLATE BINARY = s.chainId AND n.url COLLATE BINARY = s.url)"
            )
            requireNoRows(
                database,
                "SELECT 1 FROM temp.`$STAGE` s LEFT JOIN chain_nodes n ON n.chainId COLLATE BINARY = s.chainId AND n.url COLLATE BINARY = s.url " +
                    "LEFT JOIN chains c ON c.id COLLATE BINARY = s.chainId WHERE n.chainId IS NULL OR c.id IS NULL " +
                    "OR n.name COLLATE BINARY != s.name OR n.isDefault != s.isDefault OR n.isActive != s.isActive LIMIT 1",
                "Legacy endpoint transfer did not preserve every staged tuple and parent"
            )
        } catch (caught: Throwable) {
            failure = caught
            throw caught
        } finally {
            try {
                database.execSQL("DROP TABLE temp.`$STAGE`")
            } catch (cleanup: Throwable) {
                if (failure == null) throw cleanup
                failure.addSuppressed(cleanup)
            }
        }
    }

    private fun requireNoRows(database: SupportSQLiteDatabase, sql: String, message: String) {
        database.query(sql).use { check(!it.moveToFirst()) { message } }
    }

    private fun readCount(database: SupportSQLiteDatabase, sql: String): Long = database.query(sql).use {
        check(it.moveToFirst() && it.getType(0) == Cursor.FIELD_TYPE_INTEGER) { "Legacy row count is invalid" }
        val value = it.getLong(0)
        check(value >= 0L && !it.moveToNext()) { "Legacy row count is invalid" }
        value
    }

    // Only frozen source literals enter SQL; database payload is never interpolated.
    private fun quotedIds(ids: Collection<String>): String = ids.joinToString { "'$it'" }

    private data class TransferPlan(val chainIds: Map<Int, String>, val seeds: List<List<Any?>>) {
        val cte: String
            get() {
                val cases = chainIds.entries.joinToString(" ") { "WHEN ${it.key} THEN '${it.value}'" }
                return "WITH legacy AS (SELECT CASE networkType $cases END AS chainId, link COLLATE BINARY AS url, " +
                    "name COLLATE BINARY AS name, isDefault, isActive FROM nodes WHERE $RETAINED), " +
                    "merged AS (SELECT chainId, url, COALESCE(MAX(CASE WHEN isDefault = 0 THEN name COLLATE BINARY END), MAX(name COLLATE BINARY)) AS name, " +
                    "MIN(isDefault) AS isDefault, MAX(isActive) AS isActive FROM legacy GROUP BY chainId COLLATE BINARY, url COLLATE BINARY)"
            }
    }

    private val EMPTY_PLAN = TransferPlan(emptyMap(), emptyList())
    private const val RETAINED = "isDefault = 0 OR isActive = 1"
    private const val STAGE = "_migration_30_legacy_nodes"
    private val NODE_ROWS = TON_UPGRADE_FOREIGN_KEY_CHECK_LIMITS.maximumRowsByTable.getValue("chain_nodes")
    private val CHAIN_ROWS = TON_UPGRADE_FOREIGN_KEY_CHECK_LIMITS.maximumRowsByTable.getValue("chains")
    private val CHAIN_COLUMNS = listOf(
        "id", "parentId", "name", "icon", "prefix", "isEthereumBased", "isTestNet", "hasCrowdloans",
        "url", "overridesCommon", "staking_url", "staking_type", "history_url", "history_type",
        "crowdloans_url", "crowdloans_type"
    )
}

/** Exact released ordinal mapping; Acala's correction was shipped in release2.0.1. */
internal fun legacyNodeTargetChainId(ordinal: Int, existingParents: Set<String>): String {
    check(ordinal in 0..14) { "Legacy network ordinal is unknown or Undefined" }
    val oldId = legacyNodeNetworkChainIds[ordinal]
    if (ordinal != 6) return oldId
    check(!(oldId in existingParents && LEGACY_CORRECTED_ACALA_CHAIN_ID in existingParents)) {
        "Legacy Acala has ambiguous old and corrected parents"
    }
    return if (oldId in existingParents) oldId else LEGACY_CORRECTED_ACALA_CHAIN_ID
}

/** Constant seed rows share the same cell/row/aggregate policy as database staging. */
internal fun requireBoundedLegacySeedPayload(
    rows: List<List<Any?>>,
    startingTotalBytes: Long,
    limits: TonUpgradePayloadLimits
): Long {
    require(startingTotalBytes in 0..limits.maximumTotalBytes)
    var total = startingTotalBytes
    rows.forEach { row ->
        var rowBytes = 0L
        row.forEach { cell ->
            val bytes = cell?.toString()?.toByteArray(Charsets.UTF_8)?.size?.toLong() ?: 0L
            if (bytes > limits.maximumCellBytes) throw MigrationPayloadLimitException("Legacy parent cell exceeds payload limit")
            rowBytes = checkedTonUpgradePayloadByteSum(rowBytes, bytes, "Legacy parent row")
            if (rowBytes > limits.maximumRowBytes) throw MigrationPayloadLimitException("Legacy parent row exceeds payload limit")
        }
        total = checkedTonUpgradePayloadByteSum(total, rowBytes, "Legacy transfer staging")
        if (total > limits.maximumTotalBytes) throw MigrationPayloadLimitException("Legacy transfer staging exceeds payload limit")
    }
    return total
}

// Frozen DB30 ordinals, including Undefined at15. Later enum revisions reordered them.
internal val legacyNodeNetworkChainIds = listOf(
    "b0a8d493285c2df73290dfb7e61f870f17b41801197a149ca93654499ea3dafe", // KUSAMA
    "91b171bb158e2d3848fa23a9f1c25182fb8e20313b2c1eb49219da7a70ce90c3", // POLKADOT
    "e143f23803ac50e8f6f8e62695d1ce9e4e1d68aa36c1cd2cfd15340213f3423e", // WESTEND
    "0x1ab7fbd1d7c3532386268ec23fe4ff69f5bb6b3e3697947df3a2ec2786424de3", // ROCOCO
    "92cf9522510aee390f71fc1a635840918cc78e813c4a5ff76d40b478eca94a81", // POLKATRAIN
    "48239ef607d7928874027a43a67689209727dfb3d3dc5e5b03a39bdc2eda771a", // STATEMINE
    "6e966844cfd406ac4d2e9ff66ae485d60677d8b514fe8cfefb54e19a7c12e6c3", // ACALA
    "baf5aabe40646d11f0ee8abbdc64f4a4b7674925cba08e4a05ff9ebed6e2126b", // KARURA
    "401a1f9dca3da46f5c4091016c8a2f26dcea05865116b286f60f668207d1474b", // MOONRIVER
    "f1cf9022c7ebb34b162d5b5e34e705a5a740b2d0ecc1009fb89023e62a488108", // SHIDEN
    "9f28c6a68e0fc9646eff64935684f6eeeece527e37bbe1f213d22caa1d9d6bed", // BIFROST
    "d43540ba6d3eb4897c28a77d48cb5b729fea37603cbbfc7a86a73b72adb3be8d", // KHALA
    "411f057b9107718c9624d6aa4a3f23c1653898297f3d4d529d9bb6511a39dd21", // SPIRITNET
    "4ac80c99289841dd946ef92765bf659a307d39189b3ce374a92b5f0415ee17a1", // CALAMARI
    "cd4d732201ebe5d6b014edda071c4203e16867305332301dc8d092044b28e554", // QUARTZ
    "", // UNDEFINED
)

internal const val LEGACY_CORRECTED_ACALA_CHAIN_ID = "fc41b9bd8ef8fe53d58c7ea67c794c7ec9a73daf05e6d54b14ff6342c99ba64c"

// All16 DB28–30 columns, compared with the historical fixture in tests.
// Only new parent seed metadata uses app-owned URLs; existing/custom parents are untouched.
// Registry SHA256 aee46cd72ca4355f4dccfcc1385a92167a1a9b6e1ddc4b4960ef4a14a8186977.
internal val legacyNodeParentRows: List<List<Any?>> = listOf(
    listOf(
        "b0a8d493285c2df73290dfb7e61f870f17b41801197a149ca93654499ea3dafe",
        null,
        "Kusama",
        "https://raw.githubusercontent.com/soramitsu/fearless-Android/master/remote-config/assets/icons/chains/white/Kusama.svg",
        2,
        0,
        0,
        0,
        null,
        null,
        "https://fearless-wallet.squids.live/fearless-kusama-2/v/v4/graphql",
        "subsquid",
        "https://fearless-wallet.squids.live/fearless-kusama-2/v/v4/graphql",
        "subsquid",
        null,
        null,
    ),
    listOf(
        "91b171bb158e2d3848fa23a9f1c25182fb8e20313b2c1eb49219da7a70ce90c3",
        null,
        "Polkadot",
        "https://raw.githubusercontent.com/soramitsu/fearless-Android/master/remote-config/assets/icons/chains/white/Polkadot.svg",
        0,
        0,
        0,
        1,
        null,
        null,
        "https://squid.subsquid.io/fearless-polkadot/v/v3/graphql",
        "subsquid",
        "https://squid.subsquid.io/fearless-polkadot/v/v3/graphql",
        "subsquid",
        "https://raw.githubusercontent.com/soramitsu/fearless-Android/master/remote-config/production/crowdloan/polkadot.json",
        "github",
    ),
    listOf(
        "e143f23803ac50e8f6f8e62695d1ce9e4e1d68aa36c1cd2cfd15340213f3423e",
        null,
        "Westend",
        "https://raw.githubusercontent.com/soramitsu/fearless-Android/master/remote-config/assets/icons/chains/white/Westend.svg",
        42,
        0,
        1,
        1,
        null,
        null,
        null,
        null,
        null,
        null,
        "https://raw.githubusercontent.com/soramitsu/fearless-Android/master/remote-config/production/crowdloan/westend.json",
        "github",
    ),
    listOf(
        "48239ef607d7928874027a43a67689209727dfb3d3dc5e5b03a39bdc2eda771a",
        "b0a8d493285c2df73290dfb7e61f870f17b41801197a149ca93654499ea3dafe",
        "Kusama AssetHub",
        "https://raw.githubusercontent.com/soramitsu/fearless-Android/master/remote-config/assets/icons/chains/white/Assethub.svg",
        2,
        0,
        0,
        0,
        null,
        null,
        null,
        null,
        "https://squid-statemine-api.squid.tachi.soramitsu.co.jp/graphql",
        "giantsquid",
        null,
        null,
    ),
    listOf(
        "fc41b9bd8ef8fe53d58c7ea67c794c7ec9a73daf05e6d54b14ff6342c99ba64c",
        "91b171bb158e2d3848fa23a9f1c25182fb8e20313b2c1eb49219da7a70ce90c3",
        "Acala",
        "https://raw.githubusercontent.com/soramitsu/fearless-Android/master/remote-config/assets/icons/chains/white/acala.svg",
        10,
        0,
        0,
        0,
        null,
        null,
        null,
        null,
        "https://squid-acala-api.squid.tachi.soramitsu.co.jp/graphql",
        "giantsquid",
        null,
        null,
    ),
    listOf(
        "baf5aabe40646d11f0ee8abbdc64f4a4b7674925cba08e4a05ff9ebed6e2126b",
        "b0a8d493285c2df73290dfb7e61f870f17b41801197a149ca93654499ea3dafe",
        "Karura",
        "https://raw.githubusercontent.com/soramitsu/fearless-Android/master/remote-config/assets/icons/chains/white/Karura.svg",
        8,
        0,
        0,
        0,
        null,
        null,
        null,
        null,
        "https://squid-karura-api.squid.tachi.soramitsu.co.jp/graphql",
        "giantsquid",
        null,
        null,
    ),
    listOf(
        "401a1f9dca3da46f5c4091016c8a2f26dcea05865116b286f60f668207d1474b",
        "b0a8d493285c2df73290dfb7e61f870f17b41801197a149ca93654499ea3dafe",
        "Moonriver",
        "https://raw.githubusercontent.com/soramitsu/fearless-Android/master/remote-config/assets/icons/chains/white/Moonriver.svg",
        1285,
        1,
        0,
        0,
        null,
        null,
        "https://squid.subsquid.io/fearless-moonriver-1/v/v2/graphql",
        "subsquid",
        "https://squid-moonriver-api.squid.tachi.soramitsu.co.jp/graphql",
        "giantsquid",
        null,
        null,
    ),
    listOf(
        "f1cf9022c7ebb34b162d5b5e34e705a5a740b2d0ecc1009fb89023e62a488108",
        "b0a8d493285c2df73290dfb7e61f870f17b41801197a149ca93654499ea3dafe",
        "Shiden",
        "https://raw.githubusercontent.com/soramitsu/fearless-Android/master/remote-config/assets/icons/chains/white/Shiden.svg",
        5,
        0,
        0,
        0,
        null,
        null,
        null,
        null,
        "https://squid-shiden-api.squid.tachi.soramitsu.co.jp/graphql",
        "giantsquid",
        null,
        null,
    ),
    listOf(
        "9f28c6a68e0fc9646eff64935684f6eeeece527e37bbe1f213d22caa1d9d6bed",
        "b0a8d493285c2df73290dfb7e61f870f17b41801197a149ca93654499ea3dafe",
        "Bifrost",
        "https://raw.githubusercontent.com/soramitsu/fearless-Android/master/remote-config/assets/icons/chains/white/Bifrost.svg",
        6,
        0,
        0,
        0,
        "https://raw.githubusercontent.com/soramitsu/fearless-Android/master/remote-config/production/type_registry/bifrost.json",
        1,
        null,
        null,
        "https://squid.subsquid.io/gs-main-Bifrost/graphql",
        "giantsquid",
        null,
        null,
    ),
    listOf(
        "d43540ba6d3eb4897c28a77d48cb5b729fea37603cbbfc7a86a73b72adb3be8d",
        "b0a8d493285c2df73290dfb7e61f870f17b41801197a149ca93654499ea3dafe",
        "Khala",
        "https://raw.githubusercontent.com/soramitsu/fearless-Android/master/remote-config/assets/icons/chains/white/Khala.svg",
        30,
        0,
        0,
        0,
        null,
        null,
        null,
        null,
        "https://squid-khala-api.squid.tachi.soramitsu.co.jp/graphql",
        "giantsquid",
        null,
        null,
    ),
    listOf(
        "411f057b9107718c9624d6aa4a3f23c1653898297f3d4d529d9bb6511a39dd21",
        "91b171bb158e2d3848fa23a9f1c25182fb8e20313b2c1eb49219da7a70ce90c3",
        "KILT Spiritnet",
        "https://raw.githubusercontent.com/soramitsu/fearless-Android/master/remote-config/assets/icons/chains/white/kilt.svg",
        38,
        0,
        0,
        0,
        null,
        null,
        null,
        null,
        "https://squid-kilt-api.squid.tachi.soramitsu.co.jp/graphql",
        "giantsquid",
        null,
        null,
    ),
    listOf(
        "4ac80c99289841dd946ef92765bf659a307d39189b3ce374a92b5f0415ee17a1",
        "b0a8d493285c2df73290dfb7e61f870f17b41801197a149ca93654499ea3dafe",
        "Calamari",
        "https://raw.githubusercontent.com/soramitsu/fearless-Android/master/remote-config/assets/icons/chains/white/Calamari.svg",
        78,
        0,
        0,
        0,
        null,
        null,
        null,
        null,
        "https://squid-calamari-api.squid.tachi.soramitsu.co.jp/graphql",
        "giantsquid",
        null,
        null,
    ),
    listOf(
        "cd4d732201ebe5d6b014edda071c4203e16867305332301dc8d092044b28e554",
        "b0a8d493285c2df73290dfb7e61f870f17b41801197a149ca93654499ea3dafe",
        "Quartz",
        "https://raw.githubusercontent.com/soramitsu/fearless-Android/master/remote-config/assets/icons/chains/white/quartz.svg",
        255,
        0,
        0,
        0,
        null,
        null,
        null,
        null,
        "https://api.subquery.network/sq/soramitsu/fearless-wallet-quartz",
        "subquery",
        null,
        null,
    ),
)
