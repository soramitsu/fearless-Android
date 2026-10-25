package jp.co.soramitsu.coredb.recovery.decision

import jp.co.soramitsu.coredb.model.chain.ChainAssetLocal
import jp.co.soramitsu.coredb.model.chain.ChainExplorerLocal
import jp.co.soramitsu.coredb.model.chain.ChainLocal
import jp.co.soramitsu.coredb.model.chain.ChainNodeLocal
import jp.co.soramitsu.coredb.model.chain.JoinedChainInfo
import jp.co.soramitsu.coredb.recovery.selection.EndpointCatalogSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Catalog plans cannot silently collapse duplicate keys or retarget another parent. */
class EndpointCatalogSnapshotTest {
    @Test fun duplicateParentIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { EndpointCatalogSnapshot(listOf(info(), info()), emptySet()) }
    }

    @Test fun eachChildKindMustBelongToItsParent() {
        val original = info()
        val variants = listOf(
            original.copy(nodes = listOf(original.nodes.single().copy(chainId = "other"))),
            original.copy(assets = listOf(asset("other"))),
            original.copy(explorers = listOf(explorer("other")))
        )
        variants.forEach { item ->
            assertThrows(IllegalArgumentException::class.java) {
            EndpointCatalogSnapshot(listOf(item), emptySet())
        }
        }
    }

    @Test fun eachChildIdentityMustBeUnique() {
        val original = info()
        val variants = listOf(
            original.copy(nodes = original.nodes + original.nodes),
            original.copy(assets = listOf(asset("a"), asset("a"))),
            original.copy(explorers = listOf(explorer("a"), explorer("a")))
        )
        variants.forEach { item ->
            assertThrows(IllegalArgumentException::class.java) {
            EndpointCatalogSnapshot(listOf(item), emptySet())
        }
        }
    }

    @Test fun preservationCannotNameAnUnknownParent() {
        assertThrows(IllegalArgumentException::class.java) { EndpointCatalogSnapshot(listOf(info()), setOf("other")) }
    }

    @Test fun inputListMutationDoesNotAlterCapturedPlan() {
        val nodes = info().nodes.toMutableList()
        val chains = mutableListOf(info().copy(nodes = nodes))
        val preserve = mutableSetOf("a")
        val captured = EndpointCatalogSnapshot(chains, preserve)
        nodes.clear()
        chains.clear()
        preserve.clear()
        assertEquals(1, captured.chains.size)
        assertEquals(1, captured.chains.single().nodes.size)
        assertEquals(setOf("a"), captured.preservedAssetChains)
    }

    @Test fun equalChildKeysOnDifferentParentsRemainIndependent() {
        val first = info().copy(assets = listOf(asset("a")), explorers = listOf(explorer("a")))
        val second = first.copy(
            chain = first.chain.copy(id = "b"),
            nodes = first.nodes.map { it.copy(chainId = "b") }, assets = listOf(asset("b")), explorers = listOf(explorer("b"))
        )
        assertEquals(2, EndpointCatalogSnapshot(listOf(first, second), setOf("b")).chains.size)
    }

    @Test fun catalogPreservationCannotNameAnUnknownParent() {
        assertThrows(IllegalArgumentException::class.java) {
            EndpointCatalogSnapshot(listOf(info()), emptySet(), preservedCatalogChains = setOf("other"))
        }
    }

    @Test fun catalogPreservationIncludesNodesAndCopiesTargets() {
        val targets = mutableSetOf("a")
        val captured = EndpointCatalogSnapshot(listOf(info().copy(nodes = emptyList())), emptySet(),
            preservedCatalogChains = targets)
        targets.clear()
        assertEquals(setOf("a"), captured.preservedCatalogChains)
        assertEquals(setOf("a"), captured.preservedNodeChains)
    }

    @Test fun catalogPreservationCannotAlsoSupplyNodes() {
        assertThrows(IllegalArgumentException::class.java) {
            EndpointCatalogSnapshot(listOf(info()), emptySet(), preservedCatalogChains = setOf("a"))
        }
    }

    private fun info() = JoinedChainInfo(
        ChainLocal(
            "a", null, null, null, "A", null, "", null, 42, false, false, false, false, false, false, false, false,
            null, "Substrate", null, null, null, null
        ),
        listOf(ChainNodeLocal("a", "wss://public.invalid/", "Default", true, true)), emptyList(), emptyList()
    )
    private fun asset(chainId: String) = ChainAssetLocal(
        "0", "Asset", "A", chainId, "", null, "UNSUPPORTED", 12,
        null, true, null, null, null, null, true, null, null
    )
    private fun explorer(chainId: String) = ChainExplorerLocal(chainId, "subscan", "{}", "https://public.invalid/")
}
