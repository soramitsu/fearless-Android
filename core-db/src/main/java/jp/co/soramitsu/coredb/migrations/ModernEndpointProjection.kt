package jp.co.soramitsu.coredb.migrations

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Keeps only unambiguous ordinary node rows in the operational projection.
 * Every original row must already be in the immutable raw archive. This does
 * not verify an endpoint's network identity or authorize a recovered endpoint.
 */
internal class ModernEndpointProjection(
    private val archiveIsSafe: (SupportSQLiteDatabase) -> Boolean = ModernEndpointArchive::requireSafe
) {
    /**
     * Removes non-projectable rows only in the same transaction as capture.
     * Duplicate logical keys are all excluded; no arbitrary winner is chosen.
     */
    fun retainOrdinaryRows(database: SupportSQLiteDatabase) {
        check(database.inTransaction()) { "Endpoint projection requires the migration transaction" }
        check(archiveIsSafe(database)) { "Endpoint projection requires a complete raw archive" }
        database.execSQL(
            """
            DELETE FROM main.chain_nodes
            WHERE rowid NOT IN (
                SELECT MIN(rowid) FROM main.chain_nodes
                GROUP BY chainId COLLATE BINARY, url COLLATE BINARY
                HAVING COUNT(*) = 1
            ) OR NOT (
                typeof(chainId) = 'text' AND typeof(url) = 'text' AND typeof(name) = 'text'
                AND typeof(isActive) = 'integer' AND isActive IN (0, 1)
                AND typeof(isDefault) = 'integer' AND isDefault IN (0, 1)
                AND EXISTS (
                    SELECT 1 FROM main.chains AS parent
                    WHERE parent.id COLLATE BINARY = chain_nodes.chainId COLLATE BINARY
                )
            )
            """.trimIndent()
        )
    }
}
