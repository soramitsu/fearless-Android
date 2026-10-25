package jp.co.soramitsu.wallet.impl.presentation.model

import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.common.data.network.coingecko.FiatCurrency
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.coredb.model.cache.CachedNumericCell
import jp.co.soramitsu.coredb.model.cache.RawCachedTokenPriceRow
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.impl.data.mappers.mapTokenToTokenModel
import jp.co.soramitsu.wallet.impl.data.repository.configuredFiatToken
import jp.co.soramitsu.wallet.impl.domain.model.AssetAmountState
import jp.co.soramitsu.wallet.impl.domain.model.FiatAmount
import jp.co.soramitsu.wallet.impl.domain.model.FiatValuationContext
import jp.co.soramitsu.wallet.impl.domain.model.Token
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

/** Actual canonical model and complete production mapper source; no mocked valuation or formatting. */
class TokenModelCurrencyTest {
    private fun configuration(
        priceId: String? = "catalog",
        provider: Asset.PriceProvider? = null
    ): Asset = mockk {
        every { this@mockk.priceId } returns priceId
        every { this@mockk.priceProvider } returns provider
    }

    private fun context(id: String, symbol: String? = null) =
        FiatValuationContext(id, symbol?.let { FiatCurrency(id, it, id, "") })

    private fun token(
        context: FiatValuationContext? = context("cad", "$"),
        rate: AssetAmountState<BigDecimal> = AssetAmountState.Known(BigDecimal("2.5")),
        change: AssetAmountState<BigDecimal> = AssetAmountState.Known(BigDecimal("-0.125")),
        quoteCurrency: String? = context?.currencyId,
        quotePrice: String? = "catalog",
        asset: Asset = configuration()
    ) = Token(asset, rate, change, context, quoteCurrency, quotePrice)

    private fun cell(value: String?) = value?.toByteArray(Charsets.UTF_8)?.let {
        CachedNumericCell("text", it.size.toLong(), it)
    } ?: CachedNumericCell("null", 0, null)

    @Test fun mapperRetainsCanonicalTokenAndBothQuoteIdentities() {
        val source = token()
        val model = mapTokenToTokenModel(source)
        assertSame(source, model.token)
        assertSame(source.configuration, model.configuration)
        assertSame(source.fiatContext, model.fiatContext)
        assertEquals("cad", model.quoteCurrencyId)
        assertEquals("catalog", model.quotePriceId)
        assertEquals(AssetAmountState.Known(BigDecimal("2.5")), model.fiatRateState)
        assertEquals(BigDecimal("2.5"), model.fiatRate)
        assertEquals(BigDecimal("-0.125"), model.recentRateChange)
    }

    @Test fun storedQuoteSymbolCannotChoosePresentationUnit() {
        val row = RawCachedTokenPriceRow("catalog", "FOREIGN", cell("2.5"), cell("-0.125"), cell("cad")).decode()
        val source = configuredFiatToken(configuration(), row, true, context("cad"))
        val model = mapTokenToTokenModel(source)
        assertEquals("cad", model.fiatSymbol)
        assertEquals(BigDecimal("10.0"), model.fiatAmount(BigDecimal("4")))
        assertEquals(FiatAmount("cad", AssetAmountState.Known(BigDecimal("10.0"))),
            model.valuedAmount(AssetAmountState.Known(BigDecimal("4"))))
    }

    @Test fun missingAndUnavailableQuoteStatesRemainDistinct() {
        val damaged = AssetAmountState.Unavailable("damaged-cache")
        val missing = mapTokenToTokenModel(token(rate = AssetAmountState.Missing))
        val unavailable = mapTokenToTokenModel(token(rate = damaged))
        assertSame(AssetAmountState.Missing, missing.fiatRateState)
        assertSame(damaged, unavailable.fiatRateState)
        assertNull(missing.fiatRate)
        assertNull(unavailable.fiatRate)
        assertNull(missing.fiatAmount(BigDecimal.ONE))
        assertNull(unavailable.fiatAmount(BigDecimal.ONE))
        assertEquals(BigDecimal("-0.125"), unavailable.recentRateChange)
    }

    @Test fun admittedZeroPriceStaysKnownAndIsNotMissing() {
        val model = mapTokenToTokenModel(token(rate = AssetAmountState.Known(BigDecimal.ZERO)))
        assertEquals(AssetAmountState.Known(BigDecimal.ZERO), model.fiatRateState)
        assertEquals(BigDecimal.ZERO, model.fiatAmount(BigDecimal("4")))
        assertEquals(FiatAmount("cad", AssetAmountState.Known(BigDecimal.ZERO)),
            model.valuedAmount(AssetAmountState.Known(BigDecimal("4"))))
    }

    @Test fun displayUnitUsesOnlyExactSelectedMetadataOrSelectedId() {
        for ((selected, expected) in listOf(
            context("cad", "$") to "$",
            context("cad") to "cad",
            FiatValuationContext("cad", FiatCurrency("usd", "$", "", "")) to "cad",
            FiatValuationContext("CAD", FiatCurrency("cad", "$", "", "")) to "CAD",
            context("cad", " ") to "cad"
        )) {
            val model = mapTokenToTokenModel(token(context = selected))
            assertEquals(expected, model.fiatSymbol)
            assertEquals(selected.currencyId, model.valuedAmount(AssetAmountState.Known(BigDecimal.ONE))?.currencyId)
        }
    }

    @Test fun copyingCurrencyCannotRelabelPreviouslyAdmittedQuote() {
        val original = mapTokenToTokenModel(token(context("usd", "$")))
        val replacement = original.copy(token = original.token.copy(fiatContext = context("cad", "$")))
        assertEquals("cad", replacement.fiatContext?.currencyId)
        assertEquals("usd", replacement.quoteCurrencyId)
        assertEquals(AssetAmountState.Unavailable("unverified-quote-currency"), replacement.fiatRateState)
        assertNull(replacement.fiatRate)
        assertNull(replacement.fiatAmount(BigDecimal.ONE))
        assertEquals("usd", original.valuedAmount(AssetAmountState.Known(BigDecimal.ONE))?.currencyId)
        assertEquals(BigDecimal("2.5"), original.fiatRate)
    }

    @Test fun copyingConfigurationRechecksKeyAndProviderPolicy() {
        val original = mapTokenToTokenModel(token(context("usd")))
        for (changed in listOf(
            configuration("replacement"),
            configuration(provider = Asset.PriceProvider("chainlink", Asset.PriceProviderType.Chainlink))
        )) {
            val replacement = original.copy(token = original.token.copy(configuration = changed))
            assertEquals("catalog", replacement.quotePriceId)
            assertEquals(AssetAmountState.Unavailable("unverified-quote-price"), replacement.fiatRateState)
            assertNull(replacement.fiatAmount(BigDecimal.ONE))
        }
        assertEquals(BigDecimal("2.5"), original.fiatRate)
    }

    @Test fun knownSignedChangeUsesExpectedColorAndMissingChangeIsNeutral() {
        for ((change, color) in listOf(
            BigDecimal("-0.125") to R.color.red,
            BigDecimal.ZERO to R.color.green,
            BigDecimal("0.125") to R.color.green
        )) {
            val model = mapTokenToTokenModel(token(change = AssetAmountState.Known(change)))
            assertEquals(change, model.recentRateChange)
            assertEquals(color, model.rateChangeColorRes)
        }
        val model = mapTokenToTokenModel(token(change = AssetAmountState.Missing))
        assertSame(AssetAmountState.Missing, model.recentRateChangeState)
        assertEquals(R.color.gray2, model.rateChangeColorRes)
    }

    @Test fun unknownQuantityIsNotConvertedIntoKnownZero() {
        val model = mapTokenToTokenModel(token())
        val damaged = AssetAmountState.Unavailable("damaged-quantity")
        assertSame(AssetAmountState.Missing, model.fiatAmountState(AssetAmountState.Missing))
        assertEquals(damaged, model.fiatAmountState(damaged))
        assertEquals(FiatAmount("cad", AssetAmountState.Missing), model.valuedAmount(AssetAmountState.Missing))
        assertEquals(FiatAmount("cad", damaged), model.valuedAmount(damaged))
        assertEquals(AssetAmountState.Known(BigDecimal("7.5")),
            model.fiatAmountState(AssetAmountState.Known(BigDecimal("3"))))
    }

    @Test fun legacySymbolOnlyTokenCannotAcquireCurrencyAuthority() {
        val source = Token(configuration(), BigDecimal("2.5"), "$", BigDecimal("-0.125"))
        val model = mapTokenToTokenModel(source)
        assertNull(model.fiatContext)
        assertNull(model.fiatSymbol)
        assertNull(model.fiatRate)
        assertNull(model.valuedAmount(AssetAmountState.Known(BigDecimal.ONE)))
        assertEquals(AssetAmountState.Unavailable("unverified-quote-currency"), model.fiatRateState)
        assertEquals(R.color.gray2, model.rateChangeColorRes)
    }

    @Test fun invalidAndCaseChangedCurrencyCannotAuthorizeOldQuote() {
        for (context in listOf(context(" "), context("CAD"), context("cad "))) {
            val model = mapTokenToTokenModel(token(context = context, quoteCurrency = "cad"))
            assertEquals(AssetAmountState.Unavailable("unverified-quote-currency"), model.fiatRateState)
            assertNull(model.fiatAmount(BigDecimal.ONE))
        }
        assertNull(mapTokenToTokenModel(token(context = context(" "))).fiatSymbol)
    }

    @Test fun canonicalPriceAndCallerAmountBoundsSurvivePresentation() {
        val negative = mapTokenToTokenModel(token(rate = AssetAmountState.Known(BigDecimal("-1"))))
        assertEquals(AssetAmountState.Unavailable("negative-fiat-rate"), negative.fiatRateState)
        assertNull(negative.fiatAmount(BigDecimal.ONE))
        val model = mapTokenToTokenModel(token())
        assertEquals(AssetAmountState.Unavailable("fiat-amount-out-of-range"),
            model.fiatAmountState(AssetAmountState.Known(BigDecimal("1E4097"))))
        assertNull(model.fiatAmount(BigDecimal("1E4097")))
    }

    @Test fun copyingToNewAdmittedTokenUsesItsCapturedCurrencyAndLeavesOldModelHistorical() {
        val historical = mapTokenToTokenModel(token(context("usd", "$")))
        val replacementToken = historical.token.copy(
            fiatContext = context("eur", "EUR "),
            quoteCurrencyId = "eur",
            fiatRateInput = AssetAmountState.Known(BigDecimal("3"))
        )
        val replacement = historical.copy(token = replacementToken)
        assertSame(replacementToken, replacement.token)
        assertEquals(FiatAmount("eur", AssetAmountState.Known(BigDecimal("12"))),
            replacement.valuedAmount(AssetAmountState.Known(BigDecimal("4"))))
        assertEquals("EUR ", replacement.fiatSymbol)
        assertEquals(FiatAmount("usd", AssetAmountState.Known(BigDecimal("10.0"))),
            historical.valuedAmount(AssetAmountState.Known(BigDecimal("4"))))
    }
}
