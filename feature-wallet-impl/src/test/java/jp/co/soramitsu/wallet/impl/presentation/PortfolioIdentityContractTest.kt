package jp.co.soramitsu.wallet.impl.presentation

import jp.co.soramitsu.common.model.WalletEcosystem
import jp.co.soramitsu.runtime.multiNetwork.chain.model.tonMainnetChainId
import jp.co.soramitsu.wallet.impl.presentation.balance.list.forcedPortfolioChainId
import jp.co.soramitsu.wallet.impl.presentation.manageassets.ManageAssetItemState
import jp.co.soramitsu.wallet.impl.presentation.manageassets.groupManageAssetsByCanonicalIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PortfolioIdentityContractTest {

    @Test
    fun `TON only wallet is scoped to TON but hybrid wallet keeps all networks`() {
        assertEquals(
            tonMainnetChainId,
            forcedPortfolioChainId(false, setOf(WalletEcosystem.Ton))
        )
        assertNull(
            forcedPortfolioChainId(
                isPendulumMode = false,
                supportedEcosystems = setOf(WalletEcosystem.Ton, WalletEcosystem.Substrate)
            )
        )
    }

    @Test
    fun `manage assets never groups same-symbol identities`() {
        val assets = listOf(
            item("evm:ethereum:0xaaa", "0xaaa", "ethereum", "USD"),
            item("evm:ethereum:0xbbb", "0xbbb", "ethereum", "USD"),
            item("solana:mainnet:mint-a", "mint-a", "solana", "USD")
        )

        val grouped = groupManageAssetsByCanonicalIdentity(assets)

        assertEquals(3, grouped.size)
        assertEquals(setOf("0xaaa", "0xbbb", "mint-a"), grouped.values.map { it.single().id }.toSet())
    }

    private fun item(
        canonicalAssetKey: String,
        id: String,
        chainId: String,
        symbol: String
    ) = ManageAssetItemState(
        canonicalAssetKey = canonicalAssetKey,
        id = id,
        imageUrl = null,
        chainName = chainId,
        assetName = symbol,
        symbol = symbol,
        amount = "1",
        fiatAmount = null,
        chainId = chainId,
        isChecked = true,
        showEdit = false,
        isZeroAmount = false
    )
}
