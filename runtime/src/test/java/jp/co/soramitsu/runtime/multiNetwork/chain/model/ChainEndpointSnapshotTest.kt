@file:Suppress("PackageNaming") // Keep the existing production package.

package jp.co.soramitsu.runtime.multiNetwork.chain.model

import jp.co.soramitsu.common.utils.diffed
import jp.co.soramitsu.core.models.ChainNode
import jp.co.soramitsu.core.models.Ecosystem
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChainEndpointSnapshotTest {
    private val a = ChainNode("wss://a.invalid", "A", true, true)
    private val b = ChainNode("wss://b.invalid", "B", false, true)
    private val custom = ChainNode("wss://custom.invalid", "Custom", false, false)
    private fun chain(nodes: List<ChainNode>, solana: Boolean = false) = Chain(
        id = if (solana) "solana-mainnet" else "1".repeat(64),
        paraId = null,
        rank = 1,
        name = "Baseline",
        minSupportedVersion = null,
        assets = emptyList(),
        nodes = nodes,
        explorers = emptyList(),
        externalApi = if (solana) {
            Chain.ExternalApi(
                null,
                Chain.ExternalApi.Section(Chain.ExternalApi.Section.Type.SOLANA, "https://index.invalid"),
                null
            )
        } else {
            null
        },
        icon = "",
        addressPrefix = 42,
        isEthereumBased = false,
        isTestNet = true,
        hasCrowdloans = false,
        parentId = null,
        supportStakingPool = false,
        isEthereumChain = false,
        chainlinkProvider = false,
        supportNft = false,
        isUsesAppId = false,
        identityChain = null,
        ecosystem = Ecosystem.Substrate,
        remoteAssetsSource = null
    )

    private fun diff(before: Chain, after: Chain) = runBlocking {
        flowOf<Collection<Chain>>(listOf(before), listOf(after)).diffed().toList().last()
    }

    @Test
    fun activeOnlyChangeHasDistinctEquality() {
        assertNotEquals(chain(listOf(a, b)), chain(listOf(a.copy(isActive = false), b.copy(isActive = true))))
    }

    @Test
    fun customAdditionHasDistinctEquality() {
        assertNotEquals(chain(listOf(a, b)), chain(listOf(a, b, custom)))
    }

    @Test
    fun equalObjectsHaveEqualHashCodes() {
        val original = chain(listOf(a, b, custom))
        val values = listOf(
            original, original.copy(nodes = original.nodes.map { it.copy() }),
            original.copy(nodes = listOf(a, b)), original.copy(nodes = listOf(a.copy(isActive = false), b, custom))
        )
        values.forEach { left ->
            values.forEach { right -> assertMatchingHash(left, right) }
        }
        assertEquals(original, values[1])
        assertNotEquals(original, values[2])
    }

    @Test
    fun activeOnlyChangeEmitsAddedOrModified() {
        val before = chain(listOf(a, b))
        val after = chain(listOf(a.copy(isActive = false), b.copy(isActive = true)))
        assertEquals(listOf(after), diff(before, after).addedOrModified)
    }

    @Test
    fun customAdditionEmitsAddedOrModified() {
        val before = chain(listOf(a, b))
        val after = chain(listOf(a, b, custom))
        assertEquals(listOf(after), diff(before, after).addedOrModified)
    }

    @Test
    fun customAdditionBehaviorIsIndependentOfOtherChainCount() {
        val before = chain(listOf(a, b))
        val after = chain(listOf(a, b, custom))
        val unrelated = before.copy(id = "2".repeat(64), name = "Unrelated")
        val many = runBlocking {
            flowOf<Collection<Chain>>(listOf(before, unrelated), listOf(after, unrelated)).diffed().toList().last()
        }
        assertEquals(
            "Diff membership must not change when an unrelated chain is present",
            diff(before, after).addedOrModified, many.addedOrModified
        )
    }

    @Test
    fun defaultUrlChangeEmitsAddedOrModified() {
        val before = chain(listOf(a, b))
        val after = chain(listOf(a, b.copy(url = "wss://other.invalid")))
        assertNotEquals(before, after)
        assertEquals(listOf(after), diff(before, after).addedOrModified)
    }

    @Test
    fun solanaActiveChangeEmitsAddedOrModified() {
        val before = chain(listOf(a, b), true)
        val after = chain(listOf(a.copy(isActive = false), b.copy(isActive = true)), true)
        assertNotEquals(before, after)
        assertEquals(listOf(after), diff(before, after).addedOrModified)
    }

    @Test
    fun identicalReplayHasNoDiffAndStableHash() {
        val before = chain(listOf(a, b))
        val after = chain(listOf(a.copy(), b.copy()))
        assertEquals(before, after)
        assertEquals(before.hashCode(), after.hashCode())
        assertTrue(diff(before, after).addedOrModified.isEmpty())
        assertTrue(diff(before, after).removed.isEmpty())
    }

    private fun assertMatchingHash(left: Chain, right: Chain) {
        if (left == right) assertEquals(left.hashCode(), right.hashCode())
    }

    private fun assertMetadataChange(transform: (Chain) -> Chain) {
        val before = chain(listOf(a, b))
        val after = transform(before)
        assertNotEquals(before, after)
        assertEquals(listOf(after), diff(before, after).addedOrModified)
    }

    @Test
    fun ecosystemChangeEmitsCurrentSnapshot() = assertMetadataChange { it.copy(ecosystem = Ecosystem.Ethereum) }

    @Test
    fun androidVersionChangeEmitsCurrentSnapshot() = assertMetadataChange { it.copy(androidMinAppVersion = "999.0.0") }

    @Test
    fun assetSourceChangeEmitsCurrentSnapshot() = assertMetadataChange {
        it.copy(remoteAssetsSource = Chain.RemoteAssetsSource.OKX)
    }

    @Test
    fun bridgeUrlChangeEmitsCurrentSnapshot() = assertMetadataChange {
        it.copy(tonBridgeUrl = "https://bridge.invalid")
    }

    @Test
    fun allNodeFieldsAndOrderParticipateForBothFamilyPaths() {
        listOf(false, true).forEach { solana ->
            val before = chain(listOf(a, b, custom), solana)
            val variants = listOf(
                listOf(a, b), listOf(custom, b, a),
                listOf(a, b, custom.copy(name = "Renamed")),
                listOf(a, b, custom.copy(url = "wss://different.invalid")),
                listOf(a, b, custom.copy(isDefault = true)),
                listOf(a.copy(isActive = false), b, custom.copy(isActive = true))
            )
            variants.forEach { nodes ->
                val after = before.copy(nodes = nodes)
                assertNotEquals(before, after)
                assertEquals(listOf(after), diff(before, after).addedOrModified)
                val replay = after.copy(nodes = nodes.map { it.copy() })
                assertEquals(after, replay)
                assertEquals(after.hashCode(), replay.hashCode())
                assertTrue(diff(after, replay).addedOrModified.isEmpty())
            }
        }
    }
}
