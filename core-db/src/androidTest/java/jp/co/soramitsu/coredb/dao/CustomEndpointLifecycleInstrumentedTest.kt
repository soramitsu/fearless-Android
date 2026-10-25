package jp.co.soramitsu.coredb.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.co.soramitsu.coredb.AppDatabase
import jp.co.soramitsu.coredb.model.chain.ChainNodeLocal
import jp.co.soramitsu.coredb.model.chain.EndpointProjectionLimits
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real Room transactions prove ownership retention and failure atomicity, without network access. */
@RunWith(AndroidJUnit4::class)
class CustomEndpointLifecycleInstrumentedTest : DaoTest<ChainDao>(AppDatabase::chainDao) {
    @Test fun explicitEditDeleteAndReaddKeepOneStableIdentityAndExactCurrentPayload() = runBlocking {
        val custom = seed("explicit")
        val initial = owner(custom)
        assertEquals(listOf(1L, 1L, 0L), initial.takeLast(3))
        val edited = custom.copy(url = custom.url + "?escaped=%2f#kept", name = "  Current '雪' e\u0301 \"label\"  ")
        assertEquals(1, dao.updateNode(custom.chainId, custom.url, edited.name, edited.url))
        val afterEdit = owner(edited)
        assertEquals(initial[0], afterEdit[0]); assertEquals(2L, afterEdit[4])
        assertEquals(listOf(edited.chainId, edited.url, edited.name), afterEdit.subList(1, 4))
        dao.deleteNode(edited.chainId, edited.url)
        val deleted = owner(edited)
        assertEquals(listOf(3L, 0L, 1L), deleted.takeLast(3))
        dao.deleteNode(edited.chainId, edited.url)
        assertEquals(deleted, owner(edited))
        dao.updateNodes(listOf(edited.copy(isDefault = true, isActive = true)), emptyList(), emptyList())
        assertFalse(hasProjection(edited))
        assertEquals(deleted, owner(edited))
        dao.insertChainNode(edited)
        val revived = owner(edited)
        assertEquals(initial[0], revived[0]); assertEquals(listOf(4L, 1L, 0L), revived.takeLast(3))
        assertEquals(edited.name, dao.getNode(edited.chainId, edited.url).name)
        assertEquals(1L, number("SELECT COUNT(*) FROM custom_endpoint_records"))
    }

    @Test fun parentLossAndCatalogReturnKeepPendingPayloadWithoutReactivation() = runBlocking {
        val custom = seed("pending")
        val parent = dao.getJoinChainInfo(custom.chainId)!!.chain
        val initial = owner(custom)
        dao.deleteChains(listOf(parent))
        val pending = owner(custom)
        assertEquals(initial.take(4), pending.take(4)); assertEquals(listOf(2L, 0L, 0L), pending.takeLast(3))
        dao.updateChains(listOf(parent), emptyList())
        dao.updateNodes(listOf(custom.copy(isDefault = true, isActive = true)), emptyList(), emptyList())
        assertFalse(hasProjection(custom)); assertEquals(pending, owner(custom))
        assertTrue(runCatching { dao.selectNode(custom.chainId, custom.url) }.isFailure)
        assertTrue(runCatching { dao.insertChainNode(custom) }.isFailure)
        assertEquals(pending, owner(custom))
    }

    @Test fun fullCatalogReturnCannotCopyPendingOrDeletedKeysAndFreshCustomInputsAcquireOwnership() = runBlocking {
        val custom = seed("bulk-return")
        val info = dao.getJoinChainInfo(custom.chainId)!!
        dao.deleteChains(listOf(info.chain))
        val pending = owner(custom)
        dao.update(emptyList(), listOf(info.copy(nodes = listOf(custom.copy(isDefault = true, isActive = true)))))
        assertFalse(hasProjection(custom)); assertEquals(pending, owner(custom))
        dao.deleteNode(custom.chainId, custom.url)
        val deleted = owner(custom)
        dao.update(emptyList(), listOf(info.copy(nodes = listOf(custom.copy(isActive = true)))))
        assertFalse(hasProjection(custom)); assertEquals(deleted, owner(custom))
        val fresh = custom.copy(url = custom.url + "/new", name = "Fresh custom input")
        dao.update(emptyList(), listOf(info.copy(nodes = listOf(fresh))))
        assertTrue(hasProjection(fresh)); assertEquals(listOf(1L, 1L, 0L), owner(fresh).takeLast(3))
    }

    @Test fun staleParentAddAndBulkReplacementPreserveSelectedCustomIdentityAndRevision() = runBlocking {
        val custom = seed("upsert")
        dao.selectNode(custom.chainId, custom.url)
        val info = dao.getJoinChainInfo(custom.chainId)!!
        val before = owner(custom)
        dao.updateChains(listOf(info.chain.copy(name = "Replacement")), emptyList())
        dao.update(emptyList(), listOf(info.copy(nodes = info.nodes.filter { it.isDefault })))
        assertEquals(before, owner(custom))
        assertEquals(listOf(custom.url), dao.getJoinChainInfo(custom.chainId)!!.nodes.filter { it.isActive }.map { it.url })
    }

    @Test fun editConflictsWithAnotherLiveKeyRollBackBothRecordsAndProjection() = runBlocking {
        val custom = seed("edit-live-conflict")
        val other = custom.copy(url = custom.url + "/other", name = "Other")
        dao.insertChainNode(other)
        val before = dump()
        assertTrue(runCatching { dao.updateNode(custom.chainId, custom.url, "Rejected", other.url) }.isFailure)
        assertEquals(before, dump())
    }

    @Test fun editConflictsWithTombstoneAfterProjectionWriteRollBackBothStores() = runBlocking {
        val custom = seed("edit-deleted-conflict")
        val other = custom.copy(url = custom.url + "/deleted", name = "Deleted owner")
        dao.insertChainNode(other); dao.deleteNode(other.chainId, other.url)
        val before = dump()
        assertTrue(runCatching { dao.updateNode(custom.chainId, custom.url, "Rejected", other.url) }.isFailure)
        assertEquals(before, dump())
    }

    @Test fun projectionForeignKeyFailureRollsBackNewOwnershipAndIdSequence() = runBlocking {
        val missing = ChainNodeLocal("missing-parent", "https://missing.invalid", "Missing parent", false, false)
        val before = dump()
        assertTrue(runCatching { dao.insertChainNode(missing) }.isFailure)
        assertEquals(before, dump())
        assertEquals(0L, number("SELECT COUNT(*) FROM custom_endpoint_records"))
    }

    @Test fun failedProjectionInsertRollsBackTombstoneRevival() = runBlocking {
        val custom = seed("revive-conflict")
        dao.deleteNode(custom.chainId, custom.url)
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO chain_nodes(chainId,url,name,isActive,isDefault) VALUES(?,?,?,0,1)",
            arrayOf(custom.chainId, custom.url, "Conflicting catalog projection")
        )
        val before = dump()
        assertTrue(runCatching { dao.insertChainNode(custom.copy(name = "Rejected revival")) }.isFailure)
        assertEquals(before, dump())
    }

    @Test fun failedParentDeletionRollsBackAlreadyAppliedOwnershipDetach() = runBlocking {
        val custom = seed("parent-delete-abort")
        val parent = dao.getJoinChainInfo(custom.chainId)!!.chain
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_parent_delete BEFORE DELETE ON chains BEGIN SELECT RAISE(ABORT, 'synthetic parent rejection'); END")
        val before = dump()
        assertTrue(runCatching { dao.deleteChains(listOf(parent)) }.isFailure)
        assertEquals(before, dump())
        assertNotNull(dao.getJoinChainInfo(custom.chainId))
    }

    @Test fun failedProjectionDeletionRollsBackAlreadyAppliedTombstone() = runBlocking {
        val custom = seed("node-delete-abort")
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_node_delete BEFORE DELETE ON chain_nodes BEGIN SELECT RAISE(ABORT, 'synthetic projection rejection'); END")
        val before = dump()
        assertTrue(runCatching { dao.deleteNode(custom.chainId, custom.url) }.isFailure)
        assertEquals(before, dump())
    }

    @Test fun revisionExhaustionRejectsEditDeleteParentLossAndRevivalWithoutMutation() = runBlocking {
        val custom = seed("overflow")
        db.openHelper.writableDatabase.execSQL("UPDATE custom_endpoint_records SET revision = 9223372036854775807")
        val parent = dao.getJoinChainInfo(custom.chainId)!!.chain
        val before = dump()
        assertTrue(runCatching { dao.updateNode(custom.chainId, custom.url, "Rejected", custom.url) }.isFailure)
        assertTrue(runCatching { dao.deleteNode(custom.chainId, custom.url) }.isFailure)
        assertTrue(runCatching { dao.deleteChains(listOf(parent)) }.isFailure)
        assertEquals(before, dump()); assertNotNull(dao.getJoinChainInfo(custom.chainId))
        db.openHelper.writableDatabase.execSQL("DELETE FROM chain_nodes WHERE isDefault = 0")
        db.openHelper.writableDatabase.execSQL("UPDATE custom_endpoint_records SET attached = 0, deleted = 1")
        val tombstone = dump()
        assertTrue(runCatching { dao.insertChainNode(custom) }.isFailure)
        assertEquals(tombstone, dump())
    }

    @Test fun invalidRevisionMetadataRejectsMutationWithoutNumericCoercion() = runBlocking {
        val custom = seed("invalid-revision")
        for (expression in listOf("0", "-1", "CAST('invalid' AS TEXT)", "1.5")) {
            db.openHelper.writableDatabase.execSQL("UPDATE custom_endpoint_records SET revision = $expression")
            val before = dump()
            assertTrue(runCatching { dao.updateNode(custom.chainId, custom.url, "Rejected", custom.url) }.isFailure)
            assertTrue(runCatching { dao.deleteNode(custom.chainId, custom.url) }.isFailure)
            assertTrue(runCatching { dao.deleteChains(listOf(dao.getJoinChainInfo(custom.chainId)!!.chain)) }.isFailure)
            assertEquals(before, dump())
        }
    }

    @Test fun oversizedAndMalformedTextSurvivesScalarRefreshAndParentLossWithoutCursorMaterialization() = runBlocking {
        val custom = seed("raw-text")
        dao.selectNode(custom.chainId, custom.url)
        val parent = dao.getJoinChainInfo(custom.chainId)!!.chain
        val catalog = dao.getJoinChainInfo(custom.chainId)!!.nodes.single { it.isDefault }
        val sql = db.openHelper.writableDatabase
        sql.execSQL("UPDATE custom_endpoint_records SET url = CAST(zeroblob(3145728) AS TEXT), name = CAST(X'80FE' AS TEXT)")
        sql.execSQL("UPDATE chain_nodes SET url = CAST(zeroblob(3145728) AS TEXT), name = CAST(X'80FE' AS TEXT) WHERE isDefault = 0")
        dao.updateNodes(emptyList(), listOf(catalog.copy(name = "Refreshed", isActive = true)), emptyList())
        assertEquals(1L, number("SELECT COUNT(*) FROM chain_nodes WHERE isDefault = 0 AND isActive = 1 AND CAST(url AS BLOB) = zeroblob(3145728) AND hex(CAST(name AS BLOB)) = '80FE'"))
        dao.deleteChains(listOf(parent))
        assertEquals(1L, number("SELECT COUNT(*) FROM custom_endpoint_records WHERE attached = 0 AND deleted = 0 AND revision = 2 AND typeof(url) = 'text' AND CAST(url AS BLOB) = zeroblob(3145728) AND typeof(name) = 'text' AND hex(CAST(name AS BLOB)) = '80FE'"))
        sql.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
    }

    @Test fun oversizedAndUnpairedExplicitInputRejectsWithoutChangingEitherStore() = runBlocking {
        val custom = seed("input-limit")
        val before = dump()
        val tooLarge = "x".repeat(EndpointProjectionLimits.MAX_TEXT_BYTES.toInt())
        for (invalid in listOf(tooLarge, "\uD800")) {
            assertTrue(runCatching { dao.insertChainNode(custom.copy(url = custom.url + "/new", name = invalid)) }.exceptionOrNull() is IllegalArgumentException)
            assertTrue(runCatching { dao.updateNode(custom.chainId, custom.url, invalid, custom.url) }.exceptionOrNull() is IllegalArgumentException)
            assertEquals(before, dump())
        }
    }

    @Test fun unsafeCatalogInputRejectsTheWholeRefreshAndFullUpdateBeforeParentMutation() = runBlocking {
        val custom = seed("catalog-limit")
        val info = dao.getJoinChainInfo(custom.chainId)!!
        val default = info.nodes.single { it.isDefault }
        val unsafe = default.copy(name = "x".repeat(EndpointProjectionLimits.MAX_TEXT_BYTES.toInt()))
        val before = dump()
        assertTrue(runCatching {
            dao.updateNodes(listOf(default.copy(url = default.url + "/new")), listOf(unsafe), emptyList())
        }.exceptionOrNull() is IllegalArgumentException)
        assertEquals(before, dump())
        assertTrue(runCatching {
            dao.update(listOf(info.chain), listOf(info.copy(chain = info.chain.copy(name = "Rejected parent"), nodes = listOf(unsafe))))
        }.exceptionOrNull() is IllegalArgumentException)
        assertEquals(before, dump())
        assertEquals(info.chain.name, dao.getJoinChainInfo(custom.chainId)!!.chain.name)
    }

    private suspend fun seed(id: String): ChainNodeLocal {
        val info = createTestChain(id, nodesCount = 1)
        val custom = ChainNodeLocal(id, "https://custom.invalid/$id", "  Owner '雪'  ", false, false)
        dao.update(emptyList(), listOf(info.copy(nodes = info.nodes.map { it.copy(isActive = true) } + custom)))
        assertEquals(listOf(1L, 1L, 0L), owner(custom).takeLast(3))
        return custom
    }

    private fun owner(node: ChainNodeLocal): List<Any> = db.openHelper.writableDatabase.query(
        "SELECT id,chainId,url,name,revision,attached,deleted FROM custom_endpoint_records WHERE chainId = ? AND url = ?",
        arrayOf(node.chainId, node.url)
    ).use { cursor ->
        check(cursor.moveToFirst())
        val value = listOf(cursor.getLong(0), cursor.getString(1), cursor.getString(2), cursor.getString(3), cursor.getLong(4), cursor.getLong(5), cursor.getLong(6))
        check(!cursor.moveToNext()); value
    }

    private fun hasProjection(node: ChainNodeLocal): Boolean = db.openHelper.writableDatabase.query(
        "SELECT 1 FROM chain_nodes WHERE chainId = ? AND url = ?", arrayOf(node.chainId, node.url)
    ).use { it.moveToFirst() }

    private fun number(query: String): Long = db.openHelper.writableDatabase.query(query).use { cursor ->
        check(cursor.moveToFirst()); cursor.getLong(0)
    }

    // Include SQLite storage types and byte hex so malformed values cannot be coerced by the oracle.
    private fun dump(): List<List<String>> {
        val result = mutableListOf<List<String>>()
        for ((table, columns) in listOf(
            "custom_endpoint_records" to listOf("id", "revision", "chainId", "url", "name", "attached", "deleted", "originalSnapshotRowId"),
            "chain_nodes" to listOf("chainId", "url", "name", "isActive", "isDefault"),
            "sqlite_sequence" to listOf("name", "seq")
        )) {
            val fields = columns.flatMap { listOf("typeof($it)", "hex(CAST($it AS BLOB))") }.joinToString(",")
            db.openHelper.writableDatabase.query("SELECT $fields FROM $table ORDER BY rowid").use { cursor ->
                while (cursor.moveToNext()) result += listOf(table) + (0 until cursor.columnCount).map { cursor.getString(it) }
            }
        }
        return result
    }
}
