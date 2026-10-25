package jp.co.soramitsu.coredb.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.co.soramitsu.coredb.AppDatabase
import jp.co.soramitsu.coredb.model.chain.ChainNodeLocal
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Actual in-memory Room transactions; no retained app database or network is touched. */
@RunWith(AndroidJUnit4::class)
class CustomNodeRegistryOwnershipInstrumentedTest : DaoTest<ChainDao>(AppDatabase::chainDao) {
    @Test
    fun catalogUpdateKeepsExactCustomFieldsAndCurrentSelection() = runBlocking {
        for (active in listOf(false, true)) {
            val custom = seed("update-$active", active)
            dao.updateNodes(emptyList(), listOf(remote(custom)), emptyList())
            assertCustomExact(custom)
        }
    }

    @Test
    fun staleCatalogAddCannotReplaceCustomRowCreatedDuringFetch() = runBlocking {
        for (active in listOf(false, true)) {
            val custom = seed("add-$active", active)
            dao.updateNodes(listOf(remote(custom)), emptyList(), emptyList())
            assertCustomExact(custom)
        }
    }

    @Test
    fun staleCatalogRemoveCannotDeleteCustomRowCreatedDuringFetch() = runBlocking {
        for (active in listOf(false, true)) {
            val custom = seed("remove-$active", active)
            dao.updateNodes(emptyList(), emptyList(), listOf(remote(custom)))
            assertCustomExact(custom)
        }
    }

    @Test
    fun ordinaryCatalogLabelStillRefreshesAndCustomSelectionStaysActive() = runBlocking {
        val custom = seed("control", true)
        val originalDefault = dao.getJoinChainInfo(custom.chainId)!!.nodes.single { it.isDefault }
        dao.updateNodes(emptyList(), listOf(originalDefault.copy(name = "Updated catalog label", isActive = true)), emptyList())
        assertEquals("Updated catalog label", dao.getNode(custom.chainId, originalDefault.url).name)
        assertCustomExact(custom)
    }

    @Test
    fun catalogParentDeletionRetainsEditedUserEndpointBytesOutsideItsProjection() = runBlocking {
        val custom = editedOwnershipFixture("durable-delete")
        val parent = requireNotNull(dao.getJoinChainInfo(custom.chainId)).chain
        assertForeignKeysEnabledAndValid()
        assertOwnedPayloadRecoverable(custom, "before catalog parent deletion")

        // This is the actual final removal invoked by ChainSyncService.
        dao.deleteChains(listOf(parent))

        assertEquals(null, dao.getJoinChainInfo(custom.chainId))
        assertForeignKeysEnabledAndValid()
        assertOwnedPayloadRecoverable(custom, "CURRENT_OWNER_LOST_AFTER_CATALOG_PARENT_DELETE")
    }

    @Test
    fun staleCatalogParentInsertionRetainsEditedUserEndpointBytesAfterReplace() = runBlocking {
        val custom = editedOwnershipFixture("durable-replace")
        val parent = requireNotNull(dao.getJoinChainInfo(custom.chainId)).chain
        assertForeignKeysEnabledAndValid()
        assertOwnedPayloadRecoverable(custom, "before catalog parent replacement")

        // A fetch classified this key as absent before the current parent was
        // inserted. The real DAO's add path uses INSERT OR REPLACE.
        val replacement = parent.copy(name = "Replacement catalog parent")
        dao.updateChains(chainsToAdd = listOf(replacement), chainsToUpdate = emptyList())

        assertEquals(replacement.name, requireNotNull(dao.getJoinChainInfo(custom.chainId)).chain.name)
        assertForeignKeysEnabledAndValid()
        assertOwnedPayloadRecoverable(custom, "CURRENT_OWNER_LOST_AFTER_CATALOG_PARENT_REPLACE")
    }

    @Test
    fun bulkCatalogRemovalRetainsEditedUserEndpointBytesAfterRemovingParent() = runBlocking {
        val custom = editedOwnershipFixture("durable-bulk-remove")
        val parent = requireNotNull(dao.getJoinChainInfo(custom.chainId)).chain
        assertForeignKeysEnabledAndValid()
        assertOwnedPayloadRecoverable(custom, "before bulk catalog removal")

        dao.update(removed = listOf(parent), newOrUpdated = emptyList())

        assertEquals(null, dao.getJoinChainInfo(custom.chainId))
        assertForeignKeysEnabledAndValid()
        assertOwnedPayloadRecoverable(custom, "CURRENT_OWNER_LOST_AFTER_BULK_CATALOG_PARENT_REMOVE")
    }

    @Test
    fun bulkCatalogReplacementControlPreservesEditedUserEndpointAndSelection() = runBlocking {
        val custom = editedOwnershipFixture("durable-bulk-control")
        val original = requireNotNull(dao.getJoinChainInfo(custom.chainId))
        assertForeignKeysEnabledAndValid()
        val replacement = original.copy(
            chain = original.chain.copy(name = "Bulk replacement catalog parent"),
            nodes = original.nodes.filter { it.isDefault },
            assets = emptyList()
        )

        dao.update(removed = emptyList(), newOrUpdated = listOf(replacement))

        assertEquals(replacement.chain.name, requireNotNull(dao.getJoinChainInfo(custom.chainId)).chain.name)
        assertForeignKeysEnabledAndValid()
        assertCustomExact(custom)
        assertOwnedPayloadRecoverable(custom, "bulk replacement positive control")
    }

    private suspend fun editedOwnershipFixture(suffix: String): ChainNodeLocal {
        val initial = seed(suffix, active = false)
        // The exact current user revision differs from the add-time values.
        // Spaces, quotes, Unicode and percent-escape casing are intentional.
        val edited = initial.copy(
            url = "https://custom.invalid/$suffix/%2fRPC?token=PUBLIC_ONLY&label=%E9%9B%AA#kept",
            name = "  Edited owner '雪' e\u0301 \"quoted\"  "
        )
        assertEquals(1, dao.updateNode(initial.chainId, initial.url, edited.name, edited.url))
        assertCustomExact(edited)
        return edited
    }

    private fun assertForeignKeysEnabledAndValid() {
        val sql = db.openHelper.writableDatabase
        sql.query("PRAGMA foreign_keys").use { cursor ->
            check(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
        sql.query("PRAGMA foreign_key_check").use { cursor ->
            assertEquals("The baseline cannot pass by leaving dangling projection rows", 0, cursor.count)
        }
    }

    private fun assertOwnedPayloadRecoverable(custom: ChainNodeLocal, operation: String) {
        val carriers = exactOwnedPayloadCarriers(custom)
        assertEquals(
            "$operation: exact full current chain reference, URL and label must survive in one persisted row",
            true,
            carriers.isNotEmpty()
        )
    }

    /**
     * TEST-ONLY physical loss oracle, not a proposed product inventory API.
     * Looks for all three exact TEXT byte values in the same persisted row,
     * irrespective of future durable-table/column names. No values are logged.
     * A retained old revision, prefix, normalized URL or separately scattered
     * fields cannot satisfy this query. This proves a necessary retention
     * condition only; authenticated visibility, ownership/revision semantics,
     * delete tombstones, reopening and process death need separate controls.
     */
    private fun exactOwnedPayloadCarriers(custom: ChainNodeLocal): List<String> {
        val sql = db.openHelper.writableDatabase
        val tables = sql.query(
            "SELECT name FROM main.sqlite_master WHERE type = 'table' " +
                "AND name NOT LIKE 'sqlite_%' ORDER BY name"
        ).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
        fun quoted(value: String) = "`" + value.replace("`", "``") + "`"
        val values = listOf(custom.chainId, custom.url, custom.name)
        return tables.filter { table ->
            val columns = sql.query("PRAGMA main.table_info(${quoted(table)})").use { cursor ->
                val index = cursor.getColumnIndexOrThrow("name")
                buildList { while (cursor.moveToNext()) add(cursor.getString(index)) }
            }
            check(columns.isNotEmpty())
            val oneValue = columns.joinToString(" OR ", prefix = "(", postfix = ")") { column ->
                val field = quoted(column)
                "(typeof($field) = 'text' AND CAST($field AS BLOB) = CAST(? AS BLOB))"
            }
            val predicate = values.joinToString(" AND ") { oneValue }
            val bindings = values.flatMap { value -> List(columns.size) { value } }.toTypedArray()
            sql.query("SELECT 1 FROM main.${quoted(table)} WHERE $predicate LIMIT 1", bindings).use { it.moveToFirst() }
        }
    }

    private suspend fun seed(chainId: String, active: Boolean): ChainNodeLocal {
        val info = createTestChain(chainId, nodesCount = 1)
        dao.update(emptyList(), listOf(info.copy(nodes = info.nodes.map { it.copy(isActive = true) }, assets = emptyList())))
        val custom = ChainNodeLocal(chainId, "https://custom.invalid/rpc?token=PUBLIC_ONLY", "Owner label '雪'", active, false)
        dao.insertChainNode(custom.copy(isActive = false))
        if (active) dao.selectNode(chainId, custom.url)
        assertCustomExact(custom)
        return custom
    }

    private fun remote(custom: ChainNodeLocal) = custom.copy(name = "Catalog label", isActive = true, isDefault = true)

    private fun assertCustomExact(custom: ChainNodeLocal) {
        db.query("SELECT chainId,url,name,isActive,isDefault FROM chain_nodes WHERE chainId = ? AND url = ?",
            arrayOf(custom.chainId, custom.url)).use { cursor ->
            val rows = buildList {
                while (cursor.moveToNext()) add(listOf(cursor.getString(0), cursor.getString(1), cursor.getString(2),
                    cursor.getInt(3), cursor.getInt(4)))
            }
            assertEquals(listOf(listOf(custom.chainId, custom.url, custom.name, if (custom.isActive) 1 else 0, 0)), rows)
        }
        db.query("SELECT url FROM chain_nodes WHERE chainId = ? AND isActive = 1", arrayOf(custom.chainId)).use { cursor ->
            assertEquals(1, cursor.count)
            if (custom.isActive) {
                check(cursor.moveToFirst())
                assertEquals(custom.url, cursor.getString(0))
            }
        }
    }
}
