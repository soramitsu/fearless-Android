package jp.co.soramitsu.runtime.ext

import jp.co.soramitsu.common.model.UniversalWalletRegistry
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class CanonicalAssetIdentityExtTest {

    @Test
    fun `canonical ecosystem mapping covers every supported ecosystem`() {
        val cases = listOf(
            chain(UniversalWalletRegistry.bitcoinMainnet.id, Ecosystem.Substrate) to "bitcoin",
            chain(UniversalWalletRegistry.solanaMainnet.id, Ecosystem.Substrate) to "solana",
            chain(UniversalWalletRegistry.taira.id, Ecosystem.Substrate) to "iroha",
            chain("ton-mainnet", Ecosystem.Ton) to "ton",
            chain("polkadot", Ecosystem.Substrate) to "substrate",
            chain("ethereum", Ecosystem.Ethereum) to "ethereum",
            chain("polygon", Ecosystem.EthereumBased) to "ethereumbased"
        )

        cases.forEach { (chain, expected) ->
            assertEquals(expected, chain.canonicalEcosystemId())
            assertEquals("$expected:${chain.id.lowercase()}:asset", chain.assetKey("asset").serialized)
        }
    }

    @Test
    fun `runtime currency identity wins over registry storage uuid`() {
        val chain = chain("asset-hub", Ecosystem.Substrate)
        val asset = mock(Asset::class.java).also { asset ->
            `when`(asset.id).thenReturn("registry-uuid")
            `when`(asset.currencyId).thenReturn("1984")
        }

        assertEquals("1984", asset.canonicalAssetId())
        assertEquals("substrate:asset-hub:1984", chain.assetKey(asset).serialized)
    }

    private fun chain(id: String, ecosystem: Ecosystem): Chain = mock(Chain::class.java).also { chain ->
        `when`(chain.id).thenReturn(id)
        `when`(chain.ecosystem).thenReturn(ecosystem)
        `when`(chain.externalApi).thenReturn(null)
    }
}
