package jp.co.soramitsu.wallet.impl.data.mappers

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.common.domain.SelectedFiat
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.coredb.dao.TokenPriceDao
import jp.co.soramitsu.coredb.model.cache.*
import jp.co.soramitsu.wallet.impl.domain.model.FiatValuationContext
import jp.co.soramitsu.wallet.impl.data.network.blockchain.balance.InMemoryPreferences
import jp.co.soramitsu.wallet.impl.data.repository.TokenRepositoryImpl
import jp.co.soramitsu.wallet.impl.presentation.bundledPortfolioVisibility
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger

/**
 * Every stored price reaches a screen through these mappers and the token repository, so this is
 * where an asset on the bundled `untrustedAssets` list loses the price the registry binds to it.
 */
class AssetPriceTrustTest {

    private val tether = CachedTokenPriceRead(
        priceId = "tether",
        fiatSymbol = "$",
        fiatRate = CachedNumericState.Known(BigDecimal.ONE),
        recentRateChange = CachedNumericState.Known(BigDecimal("0.1")),
        fiatCurrencyIdentity = CachedFiatCurrencyIdentity.Known("usd")
    )

    @Test
    fun `a trusted asset keeps its stored price`() {
        val asset = mapCachedAssetToAsset(row(THIRD_PARTY_USDT), THIRD_PARTY_USDT, null, true, FiatValuationContext("usd"))

        assertEquals(BigDecimal.ONE, asset.token.fiatRate)
        assertEquals("usd", asset.token.fiatSymbol)
        assertEquals(0, asset.fiatAmount?.compareTo(BigDecimal("50")))
    }

    @Test
    fun `an untrusted asset keeps its balance and never carries a rate into the domain model`() {
        val asset = mapCachedAssetToAsset(row(THIRD_PARTY_USDT), THIRD_PARTY_USDT, null, false, FiatValuationContext("usd"))

        assertEquals(0, asset.total?.compareTo(BigDecimal("50")))
        assertNull(asset.token.fiatRate)
        assertEquals("usd", asset.token.fiatSymbol)
        assertNull(asset.token.recentRateChange)
        assertNull(asset.fiatAmount)
        assertNull(asset.getAsFiatWithCurrency(asset.total))
    }

    @Test
    fun `token repository gives an asset on the bundled untrusted list no price`() = runTest {
        val tokenPriceDao = mockk<TokenPriceDao> {
            coEvery { getTokenPricesByIds(listOf("tether")) } returns listOf(tether)
            every { observeTokenPricesByIds(listOf("tether")) } returns flowOf(listOf(tether))
        }
        val repository = TokenRepositoryImpl(
            tokenPriceDao = tokenPriceDao,
            selectedFiat = SelectedFiat(InMemoryPreferences()).apply { set("usd") },
            portfolioVisibility = bundledPortfolioVisibility()
        )

        assertNull(repository.getToken(THIRD_PARTY_USDT).fiatRate)
        assertNull(repository.observeToken(THIRD_PARTY_USDT).drop(1).first().fiatRate)
        assertEquals(BigDecimal.ONE, repository.getToken(ISSUER_USDT).fiatRate)
        assertEquals(BigDecimal.ONE, repository.observeToken(ISSUER_USDT).drop(1).first().fiatRate)
    }

    @Test
    fun `token repository gives a testnet token no price, by the registry option or the shared list`() = runTest {
        val enjin = CachedTokenPriceRead("enjincoin", "$", CachedNumericState.Known(BigDecimal("0.2")), CachedNumericState.Missing, CachedFiatCurrencyIdentity.Known("usd"))
        val tokenPriceDao = mockk<TokenPriceDao> {
            coEvery { getTokenPricesByIds(listOf("enjincoin")) } returns listOf(enjin)
            every { observeTokenPricesByIds(listOf("enjincoin")) } returns flowOf(listOf(enjin))
        }
        val repository = TokenRepositoryImpl(
            tokenPriceDao = tokenPriceDao,
            selectedFiat = SelectedFiat(InMemoryPreferences()).apply { set("usd") },
            portfolioVisibility = bundledPortfolioVisibility()
        )
        val canaryEnj = usdt(id = "cenj", chainId = CANARY_MATRIXCHAIN, precision = 18)
            .copy(symbol = "cENJ", priceId = "enjincoin", isTestNet = true)
        val listedOnly = canaryEnj.copy(chainId = CAGA_ANKARA, isTestNet = false)
        val mainnet = canaryEnj.copy(chainId = POLKADOT_ASSET_HUB, isTestNet = false)

        assertNull(repository.getToken(canaryEnj).fiatRate)
        assertNull(repository.observeToken(canaryEnj).drop(1).first().fiatRate)
        assertNull("listed only by the shared testnet file", repository.getToken(listedOnly).fiatRate)
        assertEquals(BigDecimal("0.2"), repository.getToken(mainnet).fiatRate)
    }

    private fun row(asset: Asset) = CachedAssetWithPriceRead(
        CachedAssetRead(
            asset.id, asset.chainId, byteArrayOf(1), 1, asset.priceId, 0, null, false, null, null,
            CachedNumericState.Known(BigInteger.valueOf(500_000)),
            CachedNumericState.Missing, CachedNumericState.Missing, CachedNumericState.Missing,
            CachedNumericState.Missing, CachedNumericState.Missing, CachedNumericState.Missing
        ), tether
    )

    private companion object {
        const val KUSAMA_ASSET_HUB = "48239ef607d7928874027a43a67689209727dfb3d3dc5e5b03a39bdc2eda771a"
        const val POLKADOT_ASSET_HUB = "68d56f15f85d3136970ec16946040bc1752654e906147f7e43e9d539d7c3de2f"
        const val CANARY_MATRIXCHAIN = "a37725fd8943d2a524cb7ecc65da438f9fa644db78ba24dcd0003e2f95645e8f"
        const val CAGA_ANKARA = "72778"

        /** Kusama AssetHub Assets id 11: named "tether usd" and priced as Tether by the registry. */
        val THIRD_PARTY_USDT = usdt(id = "27768227-8a9a-4443-a57d-6013c24f85e9", chainId = KUSAMA_ASSET_HUB, precision = 4)
        val ISSUER_USDT = usdt(id = "issuer-usdt", chainId = POLKADOT_ASSET_HUB, precision = 6)

        fun usdt(id: String, chainId: String, precision: Int) = Asset(
            id = id,
            name = "tether usd",
            symbol = "usdt",
            iconUrl = "",
            chainId = chainId,
            chainName = chainId,
            chainIcon = null,
            isTestNet = false,
            priceId = "tether",
            precision = precision,
            staking = Asset.StakingType.UNSUPPORTED,
            purchaseProviders = null,
            supportStakingPool = false,
            isUtility = false,
            type = ChainAssetType.Assets,
            currencyId = "11",
            existentialDeposit = null,
            color = null,
            isNative = false
        )
    }
}
