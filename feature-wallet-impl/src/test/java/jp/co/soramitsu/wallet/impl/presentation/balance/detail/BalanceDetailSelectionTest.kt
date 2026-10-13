package jp.co.soramitsu.wallet.impl.presentation.balance.detail

import jp.co.soramitsu.wallet.impl.presentation.AssetPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BalanceDetailSelectionTest {

    @Test
    fun `same-symbol asset on another network cannot replace network-scoped asset`() {
        val dotOnPolkadot = AssetPayload(
            chainId = "polkadot",
            chainAssetId = "dot-native"
        )

        // The selector only returns a network. Even if Kusama's catalog also advertises DOT,
        // that is not enough information to select another canonical asset.
        val resolved = resolveNetworkScopedAssetPayload(dotOnPolkadot, selectedChainId = "kusama")

        assertNull(resolved)
    }

    @Test
    fun `current network preserves exact asset id`() {
        val expected = AssetPayload(
            chainId = "ethereum-mainnet",
            chainAssetId = "0xA0b86991c6218b36C1d19D4a2e9Eb0cE3606eB48"
        )

        assertEquals(expected, resolveNetworkScopedAssetPayload(expected, expected.chainId))
    }

    @Test
    fun `all-networks selector result fails closed`() {
        val expected = AssetPayload("solana-mainnet", "mint-a")

        assertNull(resolveNetworkScopedAssetPayload(expected, selectedChainId = null))
    }
}
