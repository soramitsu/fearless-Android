package jp.co.soramitsu.wallet.impl.data.repository

import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.common.data.network.coingecko.CoingeckoApi
import jp.co.soramitsu.common.data.network.coingecko.FiatCurrency
import jp.co.soramitsu.common.data.network.ton.AccountAddress
import jp.co.soramitsu.common.data.network.ton.JettonBalance
import jp.co.soramitsu.common.data.network.ton.JettonPreview
import jp.co.soramitsu.common.data.network.ton.TokenRates
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.impl.data.network.blockchain.EthereumRemoteSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*

class QuoteCurrencyBindingTest {
    private val usd = FiatCurrency("usd", "$", "US Dollar", "")
    private val cad = FiatCurrency("cad", "$", "Canadian Dollar", "")
    private fun asset(price: String = "dot", provider: Asset.PriceProvider? = null) = mock<Asset> {
        on { priceId } doReturn price
        on { priceProvider } doReturn provider
    }
    private fun chain(assets: List<Asset>) = mock<Chain> {
        on { id } doReturn "relay"
        on { this.assets } doReturn assets
        on { chainlinkProvider } doReturn true
    }
    @Test fun coingeckoRowsKeepExactRequestCurrencyEvenWhenDisplaySymbolsMatch() = runBlocking {
        val repository = mock<ChainsRepository>(); val api = mock<CoingeckoApi>()
        val catalog = listOf(chain(listOf(asset())))
        whenever(repository.getChains()).thenReturn(catalog)
        whenever(api.getAssetPrice("dot", "usd", true)).thenReturn(mapOf("dot" to mapOf("usd" to BigDecimal("5"))))
        whenever(api.getAssetPrice("dot", "cad", true)).thenReturn(mapOf("dot" to mapOf("cad" to BigDecimal("7"))))
        val service = CoingeckoPricesService(api, repository)
        val us = service.load(usd).single(); val ca = service.load(cad).single()
        assertEquals("usd", us.fiatCurrencyId); assertEquals("cad", ca.fiatCurrencyId)
        assertEquals(us.fiatSymbol, ca.fiatSymbol); assertEquals(BigDecimal("5"), us.fiatRate); assertEquals(BigDecimal("7"), ca.fiatRate)
    }
    @Test fun coingeckoCachedRowsKeepTheirOwnCurrencyAcrossAnotherCurrencyRefresh() = runBlocking<Unit> {
        val repository = mock<ChainsRepository>(); val api = mock<CoingeckoApi>()
        val catalog = listOf(chain(listOf(asset())))
        whenever(repository.getChains()).thenReturn(catalog)
        whenever(api.getAssetPrice("dot", "usd", true)).thenReturn(mapOf("dot" to mapOf("usd" to BigDecimal("5"))))
        whenever(api.getAssetPrice("dot", "cad", true)).thenReturn(mapOf("dot" to mapOf("cad" to BigDecimal("7"))))
        val service = CoingeckoPricesService(api, repository)
        service.load(usd); service.load(cad); val repeated = service.load(usd).single()
        assertEquals("usd", repeated.fiatCurrencyId); assertEquals(BigDecimal("5"), repeated.fiatRate)
        verify(api, times(1)).getAssetPrice("dot", "usd", true)
    }
    @Test fun partialCoingeckoResponseDoesNotCreateAQuoteForTheMissingAsset() = runBlocking {
        val repository = mock<ChainsRepository>(); val api = mock<CoingeckoApi>()
        val catalog = listOf(chain(listOf(asset("a"), asset("b"))))
        whenever(repository.getChains()).thenReturn(catalog)
        whenever(api.getAssetPrice("a,b", "cad", true)).thenReturn(mapOf("a" to mapOf("cad" to BigDecimal.ONE)))
        val rows = CoingeckoPricesService(api, repository).load(cad)
        assertEquals(listOf("a"), rows.map { it.priceId }); assertEquals("cad", rows.single().fiatCurrencyId)
    }
    @Test fun chainlinkQuoteIsStampedWithCapturedUsdIdentity() = runBlocking {
        val repository = mock<ChainsRepository>(); val source = mock<EthereumRemoteSource>()
        val provider = Asset.PriceProvider("public-feed", Asset.PriceProviderType.Chainlink, 2)
        val catalog = listOf(chain(listOf(asset(provider = provider))))
        whenever(repository.getChains()).thenReturn(catalog)
        whenever(source.fetchPriceFeed("relay", "public-feed")).thenReturn(BigInteger("12345"))
        val row = ChainlinkPricesService(source, repository).load(usd, mapOf("dot" to BigDecimal("-1"))).single()
        assertEquals("usd", row.fiatCurrencyId); assertEquals("public-feed", row.priceId)
        assertEquals(BigDecimal("123.45"), row.fiatRate); assertEquals(BigDecimal("-1"), row.recentRateChange)
    }
    @Test fun sameDisplaySymbolCannotMakeNonUsdChainlinkQuoteEligible() = runBlocking {
        val repository = mock<ChainsRepository>(); val source = mock<EthereumRemoteSource>()
        assertTrue(ChainlinkPricesService(source, repository).load(cad, emptyMap()).isEmpty())
        verifyNoInteractions(repository, source)
    }
    private fun jetton(verification: String) = JettonBalance(
        balance = "1", walletAddress = AccountAddress("public-master", false, true),
        jetton = JettonPreview("public-master", "DUP", "DUP", 9, "", verification),
        price = TokenRates(prices = mapOf("USD" to BigDecimal("5"), "CAD" to BigDecimal("7")),
            diff24h = mapOf("USD" to "+1%", "CAD" to "-2%")))
    @Test fun verifiedJettonUsesMatchingResponseKeyAndRetainsCanonicalCurrencyId() {
        val us = jetton("verified").toVerifiedTonTokenPrice(usd)!!
        val ca = jetton("whitelist").toVerifiedTonTokenPrice(cad)!!
        assertEquals("usd", us.fiatCurrencyId); assertEquals("cad", ca.fiatCurrencyId)
        assertEquals(BigDecimal("5"), us.fiatRate); assertEquals(BigDecimal("7"), ca.fiatRate)
        assertEquals(BigDecimal("-2"), ca.recentRateChange)
    }
    @Test fun unverifiedJettonCannotAcquirePriceAuthorityFromCurrencyStamp() {
        assertNull(jetton("none").toVerifiedTonTokenPrice(usd))
        assertNull(jetton("none").toVerifiedTonTokenPrice(cad))
    }
}
