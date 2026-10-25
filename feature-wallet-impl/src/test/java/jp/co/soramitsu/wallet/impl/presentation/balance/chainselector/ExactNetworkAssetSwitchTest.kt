package jp.co.soramitsu.wallet.impl.presentation.balance.chainselector

import jp.co.soramitsu.runtime.multiNetwork.tokengroups.*
import org.junit.Assert.*
import org.junit.Test

class ExactNetworkAssetSwitchTest {
    private val groups = ResolvedTokenGroups(listOf(ResolvedTokenGroup("eth", "ETH", "Ether", 18,
        listOf(ResolvedTokenGroupMember("ethereum", "0"), ResolvedTokenGroupMember("arbitrum", "eth")))))

    @Test fun `approved exact member maps to its different asset id`() {
        val candidates = ExactNetworkAssetSwitch.candidates("ethereum", "0", groups, setOf("ethereum", "arbitrum"))
        assertEquals("eth", ExactNetworkAssetSwitch.selected(candidates, "arbitrum"))
    }
    @Test fun `wallet without destination account never gets destination choice`() {
        val candidates = ExactNetworkAssetSwitch.candidates("ethereum", "0", groups, setOf("ethereum"))
        assertNull(ExactNetworkAssetSwitch.selected(candidates, "arbitrum"))
    }
    @Test fun `ungrouped same ticker or id stays on exact source network`() {
        val candidates = ExactNetworkAssetSwitch.candidates("fake", "0", groups, setOf("fake", "ethereum", "arbitrum"))
        assertEquals(listOf(ResolvedTokenGroupMember("fake", "0")), candidates)
        assertNull(ExactNetworkAssetSwitch.selected(candidates, "ethereum"))
    }
    @Test fun `ambiguous destination is never selected`() {
        assertNull(ExactNetworkAssetSwitch.selected(listOf(ResolvedTokenGroupMember("a", "x"), ResolvedTokenGroupMember("a", "y")), "a"))
    }
}
