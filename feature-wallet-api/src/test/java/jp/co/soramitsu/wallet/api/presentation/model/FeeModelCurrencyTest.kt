package jp.co.soramitsu.wallet.api.presentation.model

import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.common.data.network.coingecko.FiatCurrency
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.wallet.api.data.mappers.mapFeeToFeeModel
import jp.co.soramitsu.wallet.impl.domain.model.AssetAmountState
import jp.co.soramitsu.wallet.impl.domain.model.FiatAmount
import jp.co.soramitsu.wallet.impl.domain.model.FiatValuationContext
import jp.co.soramitsu.wallet.impl.domain.model.Token
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

/** Known crypto fee remains usable when its captured fiat valuation is missing or rejected. */
class FeeModelCurrencyTest {
    private fun configuration(priceId: String? = "catalog", symbol: String = "TKN"): Asset = mockk {
        every { this@mockk.priceId } returns priceId
        every { this@mockk.priceProvider } returns null
        every { this@mockk.symbol } returns symbol
    }

    private fun context(id: String, symbol: String? = null) =
        FiatValuationContext(id, symbol?.let { FiatCurrency(id, it, id, "") })

    private fun token(
        context: FiatValuationContext? = context("cad", "$"),
        rate: AssetAmountState<BigDecimal> = AssetAmountState.Known(BigDecimal("2.5")),
        quoteCurrency: String? = context?.currencyId,
        quotePrice: String? = "catalog",
        asset: Asset = configuration()
    ) = Token(asset, rate, AssetAmountState.Missing, context, quoteCurrency, quotePrice)

    @Test fun mapperRetainsCanonicalTokenKnownCryptoAndIdentityBearingFiat() {
        val source = token()
        val fee = BigDecimal("4")
        val model = mapFeeToFeeModel(fee, source)
        assertSame(source, model.token)
        assertSame(fee, model.fee)
        assertSame(source.fiatContext, model.fiatContext)
        assertEquals("4 TKN", model.displayToken)
        assertEquals("$10", model.displayFiat)
        assertEquals(AssetAmountState.Known(BigDecimal("10.0")), model.fiatAmountState)
        assertEquals(FiatAmount("cad", model.fiatAmountState), model.fiatAmount)
    }

    @Test fun metadataLessSelectionFormatsExactSelectedIdWithoutImplicitDollar() {
        val model = mapFeeToFeeModel(BigDecimal("4"), token(context("cad")))
        assertEquals("cad10", model.displayFiat)
        assertEquals("cad", model.fiatAmount?.currencyId)
        assertEquals("4 TKN", model.displayToken)
    }

    @Test fun mismatchedSelectedMetadataCannotSupplyLabel() {
        val selected = FiatValuationContext("cad", FiatCurrency("usd", "$", "", ""))
        val model = mapFeeToFeeModel(BigDecimal("4"), token(selected))
        assertEquals("cad10", model.displayFiat)
        assertEquals(FiatAmount("cad", AssetAmountState.Known(BigDecimal("10.0"))), model.fiatAmount)
    }

    @Test fun missingAndUnavailableQuoteKeepKnownCryptoWithoutFiatZero() {
        for (state in listOf(AssetAmountState.Missing, AssetAmountState.Unavailable("damaged-price"))) {
            val model = mapFeeToFeeModel(BigDecimal("4"), token(rate = state))
            assertEquals(BigDecimal("4"), model.fee)
            assertEquals("4 TKN", model.displayToken)
            assertEquals(state, model.fiatAmountState)
            assertEquals(FiatAmount("cad", state), model.fiatAmount)
            assertNull(model.displayFiat)
        }
    }

    @Test fun zeroKnownFeeDoesNotProveFiatWhenQuoteIsMissing() {
        val model = mapFeeToFeeModel(BigDecimal.ZERO, token(rate = AssetAmountState.Missing))
        assertEquals(BigDecimal.ZERO, model.fee)
        assertEquals("0 TKN", model.displayToken)
        assertSame(AssetAmountState.Missing, model.fiatAmountState)
        assertEquals(FiatAmount("cad", AssetAmountState.Missing), model.fiatAmount)
        assertNull(model.displayFiat)
    }

    @Test fun admittedZeroQuoteProducesKnownZeroFiat() {
        val model = mapFeeToFeeModel(BigDecimal("4"), token(rate = AssetAmountState.Known(BigDecimal.ZERO)))
        assertEquals(AssetAmountState.Known(BigDecimal.ZERO), model.fiatAmountState)
        assertEquals("$0", model.displayFiat)
        assertEquals("4 TKN", model.displayToken)
    }

    @Test fun copyingKnownFeeRecomputesValueAndBothDisplays() {
        val original = mapFeeToFeeModel(BigDecimal("4"), token())
        val replacement = original.copy(fee = BigDecimal("8"))
        assertSame(original.token, replacement.token)
        assertEquals(BigDecimal("8"), replacement.fee)
        assertEquals("8 TKN", replacement.displayToken)
        assertEquals("$20", replacement.displayFiat)
        assertEquals(FiatAmount("cad", AssetAmountState.Known(BigDecimal("20.0"))), replacement.fiatAmount)
        assertEquals("$10", original.displayFiat)
    }

    @Test fun defaultCopyRetainsInputsAndReferenceEqualityContract() {
        val original = mapFeeToFeeModel(BigDecimal("4"), token())
        val replacement = original.copy()
        assertNotSame(original, replacement)
        assertFalse(original == replacement)
        assertSame(original.fee, replacement.fee)
        assertSame(original.token, replacement.token)
        assertEquals(original.fiatAmount, replacement.fiatAmount)
        assertEquals(original.displayToken, replacement.displayToken)
        assertEquals(original.displayFiat, replacement.displayFiat)
    }

    @Test fun currencyCopyCannotRelabelOldQuoteEvenWithSharedDollarSymbol() {
        val historical = mapFeeToFeeModel(BigDecimal("4"), token(context("usd", "$")))
        val replacement = historical.copy(token = historical.token.copy(fiatContext = context("cad", "$")))
        assertEquals("cad", replacement.fiatContext?.currencyId)
        assertEquals("usd", replacement.token.quoteCurrencyId)
        assertEquals(AssetAmountState.Unavailable("unverified-quote-currency"), replacement.fiatAmountState)
        assertEquals("4 TKN", replacement.displayToken)
        assertNull(replacement.displayFiat)
        assertEquals("usd", historical.fiatAmount?.currencyId)
        assertEquals("$10", historical.displayFiat)
    }

    @Test fun configurationCopyCannotReuseOldQuoteForNewAsset() {
        val historical = mapFeeToFeeModel(BigDecimal("4"), token())
        val replacement = historical.copy(token = historical.token.copy(configuration = configuration("new-key", "NEXT")))
        assertEquals("4 NEXT", replacement.displayToken)
        assertEquals(AssetAmountState.Unavailable("unverified-quote-price"), replacement.fiatAmountState)
        assertEquals("cad", replacement.fiatAmount?.currencyId)
        assertNull(replacement.displayFiat)
    }

    @Test fun foreignQuoteCurrencyAndPriceCannotBorrowSelectedLabel() {
        for (source in listOf(token(quoteCurrency = "usd"), token(quotePrice = "foreign"))) {
            val model = mapFeeToFeeModel(BigDecimal("4"), source)
            assertTrue(model.fiatAmountState is AssetAmountState.Unavailable)
            assertEquals("cad", model.fiatAmount?.currencyId)
            assertNull(model.displayFiat)
            assertEquals("4 TKN", model.displayToken)
        }
    }

    @Test fun legacySymbolOnlyTokenCannotFallbackToDollarFiat() {
        val source = Token(configuration(), BigDecimal("2.5"), "$", BigDecimal.ZERO)
        val model = mapFeeToFeeModel(BigDecimal("4"), source)
        assertNull(model.fiatContext)
        assertNull(model.fiatAmount)
        assertNull(model.displayFiat)
        assertEquals("4 TKN", model.displayToken)
        assertEquals(AssetAmountState.Unavailable("unverified-quote-currency"), model.fiatAmountState)
    }

    @Test fun invalidContextAndNegativeRateKeepCryptoButRejectFiat() {
        for (source in listOf(token(context(" ")), token(rate = AssetAmountState.Known(BigDecimal("-1"))))) {
            val model = mapFeeToFeeModel(BigDecimal("4"), source)
            assertTrue(model.fiatAmountState is AssetAmountState.Unavailable)
            assertNull(model.displayFiat)
            assertEquals(BigDecimal("4"), model.fee)
            assertEquals("4 TKN", model.displayToken)
        }
    }

    @Test fun newAdmittedTokenChangesFiatWithoutRelabelingHistoricalFee() {
        val historical = mapFeeToFeeModel(BigDecimal("4"), token(context("usd", "$")))
        val newToken = historical.token.copy(
            fiatContext = context("eur", "EUR "),
            quoteCurrencyId = "eur",
            fiatRateInput = AssetAmountState.Known(BigDecimal("3"))
        )
        val replacement = historical.copy(token = newToken)
        assertSame(newToken, replacement.token)
        assertEquals("EUR 12", replacement.displayFiat)
        assertEquals(FiatAmount("eur", AssetAmountState.Known(BigDecimal("12"))), replacement.fiatAmount)
        assertEquals("$10", historical.displayFiat)
        assertEquals("usd", historical.fiatAmount?.currencyId)
        assertEquals("4 TKN", replacement.displayToken)
    }
}
