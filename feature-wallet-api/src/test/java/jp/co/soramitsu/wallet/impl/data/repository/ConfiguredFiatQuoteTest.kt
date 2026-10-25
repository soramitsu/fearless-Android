package jp.co.soramitsu.wallet.impl.data.repository

import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.common.data.network.coingecko.FiatCurrency
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.coredb.model.cache.CachedNumericCell
import jp.co.soramitsu.coredb.model.cache.RawCachedTokenPriceRow
import jp.co.soramitsu.wallet.impl.domain.model.AssetAmountState
import jp.co.soramitsu.wallet.impl.domain.model.FiatValuationContext
import jp.co.soramitsu.wallet.impl.domain.model.expectedFiatPriceId
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class ConfiguredFiatQuoteTest {
    private fun configuration(
        priceId: String? = "catalog",
        provider: Asset.PriceProvider? = Asset.PriceProvider("chainlink", Asset.PriceProviderType.Chainlink)
    ): Asset = mockk {
        every { this@mockk.priceId } returns priceId
        every { this@mockk.priceProvider } returns provider
    }

    private fun cell(value: String?) = value?.toByteArray(Charsets.UTF_8)?.let {
        CachedNumericCell("text", it.size.toLong(), it)
    } ?: CachedNumericCell("null", 0, null)

    private fun quote(id: String = "catalog", currency: String? = "cad", rate: String? = "2.50") =
        RawCachedTokenPriceRow(id, "untrusted persisted symbol", cell(rate), cell("-0.125"), cell(currency)).decode()

    private fun context(id: String) = FiatValuationContext(id, FiatCurrency(id, "$", id, ""))

    @Test fun usdSelectsOnlySupportedChainlinkWhileOtherCurrenciesSelectCatalog() {
        assertEquals("chainlink", expectedFiatPriceId(configuration(), "usd"))
        assertEquals("catalog", expectedFiatPriceId(configuration(), "cad"))
        assertEquals("catalog", expectedFiatPriceId(configuration(), "USD"))
        assertEquals("catalog", expectedFiatPriceId(configuration(), " usd"))
        assertEquals("catalog", expectedFiatPriceId(configuration(provider = null), "usd"))
        assertEquals("catalog", expectedFiatPriceId(configuration(provider = Asset.PriceProvider("unknown", Asset.PriceProviderType.Unknown)), "usd"))
    }

    @Test fun configuredChainlinkAbsenceCannotFallBackToCatalogForUsd() {
        val token = configuredFiatToken(configuration(), quote(currency = "usd"), true, context("usd"))
        assertEquals(AssetAmountState.Unavailable("cached-price-identity-mismatch"), token.fiatRateState)
        assertEquals(token.fiatRateState, token.recentRateChangeState)
        assertNull(token.fiatAmount(BigDecimal.ONE))
        assertSame(AssetAmountState.Missing, configuredFiatToken(configuration(), null, true, context("usd")).fiatRateState)
    }

    @Test fun nonUsdCannotAdmitProviderEvenWhenItsStoredCurrencyMatches() {
        val token = configuredFiatToken(configuration(), quote(id = "chainlink"), true, context("cad"))
        assertEquals(AssetAmountState.Unavailable("cached-price-identity-mismatch"), token.fiatRateState)
        assertNull(token.recentRateChange)
    }

    @Test fun selectedQuoteUsesCurrentCatalogKeyWithoutAnySavedBalancePointer() {
        val token = configuredFiatToken(configuration(), quote(), true, context("cad"))
        assertEquals(BigDecimal("2.50"), token.fiatRate)
        assertEquals(BigDecimal("-0.125"), token.recentRateChange)
        assertEquals(BigDecimal("10.00"), token.fiatAmount(BigDecimal("4")))
        assertEquals("cad", token.quoteCurrencyId)
        assertEquals("$", token.fiatSymbol)
    }

    @Test fun sharedDollarAndDelayedUsdWriterCannotAuthorizeCad() {
        val c = configuration(provider = null)
        val token = configuredFiatToken(c, quote(currency = "usd"), true, context("cad"))
        assertEquals(AssetAmountState.Unavailable("cached-price-currency-mismatch"), token.fiatRateState)
        assertEquals(token.fiatRateState, token.recentRateChangeState)
        assertNull(token.fiatAmount(BigDecimal.ONE))
        assertEquals("cad", token.valuedAmount(AssetAmountState.Known(BigDecimal.ONE))?.currencyId)
    }

    @Test fun legacyAndMalformedIdentityCannotAuthorizeNumericRate() {
        for (rawIdentity in listOf(cell(null), cell(""), CachedNumericCell("blob", 3, "cad".toByteArray()),
            CachedNumericCell("text", 1, byteArrayOf(0xff.toByte())), cell("cad "))) {
            val row = RawCachedTokenPriceRow("catalog", "$", cell("2.50"), cell("-0.125"), rawIdentity).decode()
            val token = configuredFiatToken(configuration(), row, true, context("cad"))
            assertEquals(AssetAmountState.Unavailable("cached-price-currency-mismatch"), token.fiatRateState)
            assertNull(token.fiatAmount(BigDecimal.ONE))
        }
    }

    @Test fun foreignKeyAndMissingCatalogIdCannotBorrowConsistentRow() {
        for (c in listOf(configuration(), configuration(priceId = null))) {
            val token = configuredFiatToken(c, quote(id = "foreign"), true, context("cad"))
            assertEquals(AssetAmountState.Unavailable("cached-price-identity-mismatch"), token.fiatRateState)
        }
    }

    @Test fun untrustedAssetSuppressesEvenCorrectlyBoundPrice() {
        val token = configuredFiatToken(configuration(), quote(), false, context("cad"))
        assertSame(AssetAmountState.Missing, token.fiatRateState)
        assertSame(AssetAmountState.Missing, token.recentRateChangeState)
        assertNull(token.quoteCurrencyId)
        assertNull(token.fiatAmount(BigDecimal.ONE))
    }

    @Test fun missingAndDamagedNumericCellsRemainDistinctWithoutLeakingPayload() {
        val missing = configuredFiatToken(configuration(), quote(rate = null), true, context("cad"))
        val damaged = configuredFiatToken(configuration(), quote(rate = "private-broken-payload"), true, context("cad"))
        assertSame(AssetAmountState.Missing, missing.fiatRateState)
        assertTrue(damaged.fiatRateState is AssetAmountState.Unavailable)
        assertFalse(damaged.fiatRateState.toString().contains("private-broken-payload"))
        assertEquals(BigDecimal("-0.125"), damaged.recentRateChange)
    }

    @Test fun persistedSymbolNeverSuppliesDisplayUnit() {
        val original = quote()
        for (symbol in listOf(null, "$", "x".repeat(2 * 1024 * 1024))) {
            val token = configuredFiatToken(configuration(), original.copy(fiatSymbol = symbol), true, FiatValuationContext("cad"))
            assertEquals("cad", token.fiatSymbol)
            assertEquals(BigDecimal("2.50"), token.fiatRate)
        }
    }

    @Test fun invalidSelectedIdentityCannotAuthorizeEvenMatchingConstructedQuote() {
        val token = configuredFiatToken(configuration(), quote(currency = " "), true, FiatValuationContext(" "))
        assertEquals(AssetAmountState.Unavailable("invalid-selected-fiat"), token.fiatRateState)
        assertNull(token.fiatSymbol)
        assertNull(token.fiatRate)
    }
}
