package jp.co.soramitsu.coredb.dao

import jp.co.soramitsu.coredb.model.chain.ChainAssetLocal
import jp.co.soramitsu.coredb.model.chain.ChainExplorerLocal
import jp.co.soramitsu.coredb.model.chain.ChainLocal
import jp.co.soramitsu.coredb.model.chain.JoinedChainInfo
import jp.co.soramitsu.coredb.model.chain.RuntimeAssetCatalogRow
import jp.co.soramitsu.coredb.model.chain.RuntimeChainCatalogRow
import jp.co.soramitsu.coredb.model.chain.RuntimeExplorerCatalogRow
import jp.co.soramitsu.coredb.recovery.selection.EndpointCatalogSnapshot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.withSettings

/** Actual writer decisions with controlled Room query replies; physical SQL rollback has separate native coverage. */
class ChainDaoCatalogPreservationTest {
    @Test fun preservedCatalogPerformsNoParentOrChildMutation() = runBlocking<Unit> {
        val fixture = Fixture(listOf(catalog("cached")))
        val added = fixture.replace { fresh ->
            assertTrue(fresh.all { it.nodes.isEmpty() })
            EndpointCatalogSnapshot(fresh, emptySet(), preservedCatalogChains = setOf("cached"))
        }
        assertTrue(added.isEmpty())
        assertTrue(fixture.writes.isEmpty())
    }

    @Test fun preservedCatalogDoesNotPreventUnrelatedCompleteReplacement() = runBlocking<Unit> {
        val fixture = Fixture(listOf(catalog("cached"), catalog("online")))
        fixture.replace { fresh ->
            EndpointCatalogSnapshot(fresh, emptySet(), preservedCatalogChains = setOf("cached"))
        }
        assertEquals(listOf("online"), fixture.writes.getValue("updateChainsProjection").map { (it as ChainLocal).id })
        assertEquals(listOf("online"), fixture.writes.getValue("updateChainAssets").map { (it as ChainAssetLocal).chainId })
        assertEquals(listOf("online"), fixture.writes.getValue("updateChainExplorers").map { (it as ChainExplorerLocal).chainId })
        assertEquals(listOf("online"), fixture.nodeReplacementIds)
        // Incoming null/[] must still be written although the local operational projection matches it.
        assertEquals(null, (fixture.writes.getValue("updateChainsProjection").single() as ChainLocal).xcm)
        assertEquals(null, (fixture.writes.getValue("updateChainAssets").single() as ChainAssetLocal).purchaseProviders)
        assertEquals("[]", (fixture.writes.getValue("updateChainExplorers").single() as ChainExplorerLocal).types)
    }

    @Test fun absentPreservedParentIsRejectedBeforeAnyMutation() = runBlocking<Unit> {
        val fixture = Fixture(listOf(catalog("cached")))
        val failure = runCatching {
            fixture.replace { EndpointCatalogSnapshot(listOf(catalog("absent")), emptySet(), preservedCatalogChains = setOf("absent")) }
        }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertTrue(fixture.writes.isEmpty())
        assertTrue(fixture.nodeReplacementIds.isEmpty())
    }

    @Test fun preservedAssetPolicyStillOnlyProtectsMissingDefinitions() = runBlocking<Unit> {
        val fixture = Fixture(listOf(catalog("online")))
        fixture.replace { fresh -> EndpointCatalogSnapshot(fresh.map { it.copy(assets = emptyList()) }, setOf("online")) }
        assertTrue("deleteChainAssets" !in fixture.writes)
        assertEquals(listOf("online"), fixture.writes.getValue("updateChainsProjection").map { (it as ChainLocal).id })
        assertEquals(listOf("online"), fixture.nodeReplacementIds)
    }

    @Test
    fun unsupportedOverrideWritesOnlyExactEcosystemCellForPreservedParent() = runBlocking<Unit> {
        val raw = "u".repeat(3 * 1024 * 1024)
        val fixture = Fixture(listOf(catalog("cached")))
        val added = fixture.replace { fresh ->
            EndpointCatalogSnapshot(fresh, emptySet(), preservedCatalogChains = setOf("cached"),
                unsupportedEcosystemOverrides = mapOf("cached" to raw))
        }
        assertTrue(added.isEmpty())
        assertEquals(mapOf("updateUnsupportedCatalogEcosystem" to listOf("cached" to raw)), fixture.writes)
        assertTrue(fixture.nodeReplacementIds.isEmpty())
    }

    @Test
    fun invalidOverrideTargetsAndSupportedValuesRejectBeforeAnyWrite() = runBlocking<Unit> {
        for (supported in jp.co.soramitsu.core.models.Ecosystem.entries.flatMap { listOf(it.name, it.name.lowercase()) }) {
            val fixture = Fixture(listOf(catalog("cached")))
            val failure = runCatching {
                fixture.replace { fresh ->
                    EndpointCatalogSnapshot(fresh, emptySet(), preservedCatalogChains = setOf("cached"),
                        unsupportedEcosystemOverrides = mapOf("cached" to supported))
                }
            }.exceptionOrNull()
            assertTrue(failure is IllegalArgumentException)
            assertTrue(fixture.writes.isEmpty())
        }
        val fixture = Fixture(listOf(catalog("cached")))
        assertTrue(runCatching {
            fixture.replace { fresh -> EndpointCatalogSnapshot(fresh, emptySet(),
                unsupportedEcosystemOverrides = mapOf("cached" to "future")) }
        }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(fixture.writes.isEmpty())
    }

    @Test
    fun overrideInputIsDefensivelyCopiedAndAbsentParentCannotBeQuarantined() = runBlocking<Unit> {
        val values = mutableMapOf("cached" to "future")
        val snapshot = EndpointCatalogSnapshot(listOf(catalog("cached")), emptySet(),
            preservedCatalogChains = setOf("cached"), unsupportedEcosystemOverrides = values)
        values["cached"] = "Substrate"
        assertEquals(mapOf("cached" to "future"), snapshot.unsupportedEcosystemOverrides)
        val fixture = Fixture(listOf(catalog("cached")))
        assertTrue(runCatching {
            fixture.replace { EndpointCatalogSnapshot(listOf(catalog("absent")), emptySet(),
                preservedCatalogChains = setOf("absent"), unsupportedEcosystemOverrides = mapOf("absent" to "future")) }
        }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(fixture.writes.isEmpty())
    }

    private class Fixture(val catalog: List<JoinedChainInfo>) {
        val writes = linkedMapOf<String, MutableList<Any>>()
        val nodeReplacementIds = mutableListOf<String>()
        val dao: ChainDao = mock(ChainDao::class.java, withSettings().useConstructor().defaultAnswer { call ->
            when (call.method.name) {
                "getEndpointMutationLane", "replaceCatalog", "replaceCatalogTransaction" -> call.callRealMethod()
                "runtimeChainPage" -> if (call.getArgument<Long?>(1) == null) catalog.mapIndexed { index, item ->
                    RuntimeChainCatalogRow(item.chain, index.toLong() + 1, null, null)
                } else emptyList<RuntimeChainCatalogRow>()
                "runtimeAssetPage" -> if (call.getArgument<Long?>(1) == null) catalog
                    .filter { call.getArgument<String?>(0) == null || it.chain.id == call.getArgument<String>(0) }.flatMap { it.assets }.mapIndexed { index, item ->
                        RuntimeAssetCatalogRow(item, index.toLong() + 1, null, null, null, null)
                    } else emptyList<RuntimeAssetCatalogRow>()
                "runtimeExplorerPage" -> if (call.getArgument<Long?>(1) == null) catalog
                    .filter { call.getArgument<String?>(0) == null || it.chain.id == call.getArgument<String>(0) }.flatMap { it.explorers }.mapIndexed { index, item ->
                        RuntimeExplorerCatalogRow(item, index.toLong() + 1, 2L, "[]".toByteArray())
                    } else emptyList<RuntimeExplorerCatalogRow>()
                "insertChains", "updateChainsProjection", "insertChainAssets", "updateChainAssets", "deleteChainAssets",
                "insertChainExplorers", "updateChainExplorers", "deleteChainExplorers" -> {
                    val rows = call.getArgument<List<Any>>(0)
                    if (rows.isNotEmpty()) writes.getOrPut(call.method.name) { mutableListOf() }.addAll(rows)
                    Unit
                }
                "updateUnsupportedCatalogEcosystem" -> {
                    writes.getOrPut(call.method.name) { mutableListOf() }.add(call.getArgument<String>(0) to call.getArgument<String>(1))
                    1
                }
                "selectedNodeRowId" -> null
                "deleteObsoleteCatalogNodes" -> { nodeReplacementIds += call.getArgument<String>(0); Unit }
                else -> error("Unexpected DAO call: ${call.method.name}")
            }
        })
        suspend fun replace(plan: (List<JoinedChainInfo>) -> EndpointCatalogSnapshot) =
            dao.replaceCatalog(dao.endpointMutationLane.admitCatalogReplacement(), plan)
    }

    companion object {
        private fun catalog(id: String) = JoinedChainInfo(
            ChainLocal(id, null, null, null, id, null, "", null, 42, false, false, false,
                false, false, false, false, false, null, "Substrate", null, null, null, null), emptyList(),
            listOf(ChainAssetLocal("0", "Asset", "A", id, "", null, "UNSUPPORTED", 12,
                null, true, null, null, null, null, true, null, null)),
            listOf(ChainExplorerLocal(id, "subscan", "[]", "https://public.invalid/"))
        )
    }
}
