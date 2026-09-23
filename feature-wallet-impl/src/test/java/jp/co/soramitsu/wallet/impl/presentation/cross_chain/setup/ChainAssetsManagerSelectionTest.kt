package jp.co.soramitsu.wallet.impl.presentation.cross_chain.setup

import org.junit.Assert.assertEquals
import org.junit.Test

class ChainAssetsManagerSelectionTest {

    @Test
    fun `provider-only non-utility route remains reachable after origin selection`() {
        assertEquals(
            "LLM",
            selectReachableCrossChainAssetId(
                requestedAssetId = null,
                supportedAssetIds = listOf("LLM", "XOR"),
                supportedUtilityAssetId = null
            )
        )
    }

    @Test
    fun `an exact supported picker selection is preserved`() {
        assertEquals(
            "XOR",
            selectReachableCrossChainAssetId(
                requestedAssetId = "XOR",
                supportedAssetIds = listOf("LLD", "LLM", "XOR"),
                supportedUtilityAssetId = "LLD"
            )
        )
    }
}
