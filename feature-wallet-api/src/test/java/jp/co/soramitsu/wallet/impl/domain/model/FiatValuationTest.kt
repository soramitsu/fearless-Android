package jp.co.soramitsu.wallet.impl.domain.model

import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.common.data.network.coingecko.FiatCurrency
import jp.co.soramitsu.core.models.Asset as CoreAsset
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger

class FiatValuationTest {
    private val usd = FiatValuationContext("usd", FiatCurrency("usd", "$", "USD", ""))
    private val cad = FiatValuationContext("cad", FiatCurrency("cad", "$", "CAD", ""))
    private fun known(value: String) = AssetAmountState.Known(BigDecimal(value))
    private fun token(context: FiatValuationContext = usd, currency: String? = "usd") = Token(
        configuration = mockk<CoreAsset> {
            every { precision } returns 2
            every { priceId } returns "catalog"
            every { priceProvider } returns null
        },
        fiatRateInput = known("2.50"), recentRateChangeInput = known("-0.125"),
        fiatContext = context, quoteCurrencyId = currency, quotePriceId = "catalog"
    )
    private fun holding(quantity: String, amount: String?, currency: String = "usd") =
        FiatHoldingValuation(known(quantity), amount?.let { FiatAmount(currency, known(it)) })

    @Test fun contextUsesExactSelectedMetadataOrIdWithoutNormalization() {
        assertEquals("$", usd.displayUnit)
        assertEquals("cad", FiatValuationContext("cad", usd.selectedMetadata).displayUnit)
        assertEquals("cad", FiatValuationContext("cad").displayUnit)
        assertEquals("CAD", FiatValuationContext("CAD", cad.selectedMetadata).displayUnit)
        assertEquals("cad", FiatValuationContext("cad", FiatCurrency("cad", " ", "", "")).displayUnit)
        assertTrue(FiatValuationContext("x".repeat(4096)).isValid)
        assertFalse(FiatValuationContext("x".repeat(4097)).isValid)
        assertTrue(FiatValuationContext("é".repeat(2048)).isValid)
        assertFalse(FiatValuationContext("é".repeat(2049)).isValid)
        assertFalse(FiatValuationContext(" ").isValid)
    }

    @Test fun unitTravelsWithSignedAmountAndNegativeRateChangeRemainsValid() {
        val t = token()
        assertEquals(FiatAmount("usd", known("-10.00")), t.valuedAmount(known("-4")))
        assertEquals(BigDecimal("-10.00"), t.fiatAmount(BigDecimal("-4")))
        assertEquals(BigDecimal("-0.125"), t.recentRateChange)
        assertEquals(known("1.23"), t.amountFromPlanksState(AssetAmountState.Known(BigInteger("123"))))
    }

    @Test fun legacyNumericAndStateConstructorsCannotInventCurrencyFromSymbol() {
        val config = token().configuration
        for (t in listOf(
            Token(config, BigDecimal("2.5"), "$", BigDecimal.ONE),
            Token(config, known("2.5"), "$", known("1")),
            Token(config, known("2.5"), known("1"))
        )) {
            assertEquals(AssetAmountState.Unavailable("unverified-quote-currency"), t.fiatRateState)
            assertEquals(t.fiatRateState, t.recentRateChangeState)
            assertNull(t.fiatAmount(BigDecimal.ONE))
            assertNull(t.valuedAmount(known("1")))
            assertNull(t.fiatSymbol)
        }
    }

    @Test fun copiesRecomputeIdentityAdmissionAndCannotRelabelPriorUsdQuoteAsCad() {
        val original = token()
        val relabeled = original.copy(fiatContext = cad)
        assertEquals(AssetAmountState.Unavailable("unverified-quote-currency"), relabeled.fiatRateState)
        assertNull(relabeled.fiatAmount(BigDecimal.ONE))
        assertEquals(BigDecimal("2.50"), original.fiatRate)
        assertSame(AssetAmountState.Missing, original.copy(fiatRateInput = AssetAmountState.Missing).fiatRateState)
        assertEquals(known("4.00"), original.copy(fiatRateInput = known("4.00")).fiatRateState)
        assertTrue(original.copy(quoteCurrencyId = null).fiatRateState is AssetAmountState.Unavailable)
        assertTrue(original.copy(fiatContext = FiatValuationContext(" ")).fiatRateState is AssetAmountState.Unavailable)
    }

    @Test fun existingPriceAndCallerAmountBoundsSurviveCurrencyAdmission() {
        val t = token()
        assertEquals(AssetAmountState.Unavailable("negative-fiat-rate"), t.copy(fiatRateInput = known("-1")).fiatRateState)
        for (invalid in listOf(BigDecimal("1e4097"), BigDecimal("1e-4097"), BigDecimal("9".repeat(4097)))) {
            assertEquals(AssetAmountState.Unavailable("price-out-of-range"), t.copy(fiatRateInput = AssetAmountState.Known(invalid)).fiatRateState)
        }
        assertTrue(t.copy(fiatRateInput = known("9".repeat(4096))).fiatRateState is AssetAmountState.Known)
        assertTrue(t.fiatAmountState(known("9".repeat(4097))) is AssetAmountState.Known)
        assertEquals(AssetAmountState.Unavailable("fiat-amount-out-of-range"), t.fiatAmountState(known("9".repeat(4098))))
        assertEquals(AssetAmountState.Unavailable("fiat-amount-out-of-range"), t.fiatAmountState(known("1e4097")))
        assertSame(AssetAmountState.Missing, t.fiatAmountState(AssetAmountState.Missing))
        assertEquals(AssetAmountState.Unavailable("damaged"), t.fiatAmountState(AssetAmountState.Unavailable("damaged")))
    }

    @Test fun configurationAndProviderCopiesCannotBorrowTheOriginalQuote() {
        val original = token()
        val different = mockk<CoreAsset> {
            every { priceId } returns "foreign"
            every { priceProvider } returns null
        }
        val providerChanged = mockk<CoreAsset> {
            every { priceId } returns "catalog"
            every { priceProvider } returns CoreAsset.PriceProvider("chainlink", CoreAsset.PriceProviderType.Chainlink)
        }
        for (configuration in listOf(different, providerChanged)) {
            val copy = original.copy(configuration = configuration)
            assertEquals(AssetAmountState.Unavailable("unverified-quote-price"), copy.fiatRateState)
            assertEquals(copy.fiatRateState, copy.recentRateChangeState)
            assertNull(copy.fiatAmount(BigDecimal.ONE))
        }
        assertEquals(known("2.50"), token(cad, "cad").copy(configuration = providerChanged).fiatRateState)
        assertEquals(BigDecimal("2.50"), original.fiatRate)
    }

    @Test fun missingAndForeignCanonicalQuoteKeysCannotAuthorizeEvenMatchingCurrency() {
        val original = token()
        for (key in listOf(null, "foreign", "CATALOG", "catalog ")) {
            val copy = original.copy(quotePriceId = key)
            assertEquals(AssetAmountState.Unavailable("unverified-quote-price"), copy.fiatRateState)
            assertNull(copy.fiatAmount(BigDecimal.ONE))
        }
    }

    @Test fun headerTotalAndTransferableValueKeepDifferentQuantitySemantics() {
        val amounts = AssetBalanceAmounts(
            AssetAmountState.Known(BigInteger("1000")), AssetAmountState.Known(BigInteger("200")),
            AssetAmountState.Known(BigInteger("300"))
        )
        val t = token()
        val total = t.valuedAmount(t.amountFromPlanksState(amounts.total))
        val transferable = t.valuedAmount(t.amountFromPlanksState(amounts.transferable))
        assertEquals(FiatAmount("usd", known("30.0000")), total)
        assertEquals(FiatAmount("usd", known("17.5000")), transferable)
    }

    @Test fun completeEmptyAndKnownZeroPortfoliosProveZeroInSelectedCurrency() {
        for (holdings in listOf(emptyList(), listOf(holding("0", null), holding("0.00", null, "cad")))) {
            val result = aggregateFiatHoldings(usd, holdings)
            assertTrue(result.isComplete)
            assertEquals(BigDecimal.ZERO, result.knownSubtotal)
            assertEquals(AssetAmountState.Known(BigDecimal.ZERO), result.total)
            assertEquals(0, result.unknownQuantityCount + result.unpricedFundedCount + result.pricedCount)
        }
    }

    @Test fun sharedSymbolCurrenciesNeverMixIntoOneRenamedSum() {
        val result = aggregateFiatHoldings(usd, listOf(holding("1", "2.50"), holding("1", "3.00", "cad")))
        assertFalse(result.isComplete)
        assertEquals(BigDecimal("2.50"), result.knownSubtotal)
        assertEquals(1, result.pricedCount)
        assertEquals(1, result.unpricedFundedCount)
        assertEquals("usd", result.context.currencyId)
        assertTrue(result.total is AssetAmountState.Unavailable)
    }

    @Test fun damagedAndMissingQuantitiesKeepTotalIncompleteEvenWithKnownQuoteAmounts() {
        val result = aggregateFiatHoldings(usd, listOf(
            holding("1", "2.50"),
            FiatHoldingValuation(AssetAmountState.Unavailable("damaged"), FiatAmount("usd", known("999"))),
            FiatHoldingValuation(AssetAmountState.Missing, FiatAmount("usd", known("999")))
        ))
        assertEquals(BigDecimal("2.50"), result.knownSubtotal)
        assertEquals(2, result.unknownQuantityCount)
        assertEquals(1, result.pricedCount)
        assertFalse(result.isComplete)
    }

    @Test fun allFundedUnpricedHoldingsHaveNoKnownSubtotal() {
        val result = aggregateFiatHoldings(usd, listOf(
            holding("1", null),
            FiatHoldingValuation(known("2"), FiatAmount("usd", AssetAmountState.Unavailable("damaged"))),
            FiatHoldingValuation(known("3"), FiatAmount("usd", AssetAmountState.Missing))
        ))
        assertNull(result.knownSubtotal)
        assertEquals(3, result.unpricedFundedCount)
        assertEquals(0, result.pricedCount)
        assertFalse(result.isComplete)
    }

    @Test fun invalidContextCannotProveEvenEmptyZero() {
        val result = aggregateFiatHoldings(FiatValuationContext(""), emptyList())
        assertNull(result.knownSubtotal)
        assertEquals(AssetAmountState.Unavailable("invalid-selected-fiat"), result.total)
        assertFalse(result.isComplete)
    }

    @Test fun allPricedHoldingsProduceAnExactCompleteTotalWithoutChoosingFirstMemberLabel() {
        val result = aggregateFiatHoldings(cad, listOf(holding("1", "3.10", "cad"), holding("2", "4.20", "cad")))
        assertEquals(BigDecimal("7.30"), result.knownSubtotal)
        assertEquals(known("7.30"), result.total)
        assertEquals(2, result.pricedCount)
        assertSame(cad, result.context)
        assertTrue(result.isComplete)
    }

    @Test fun aggregateRejectsExtremeScaleAndOverboundProductsBeforeAddition() {
        for (invalid in listOf(
            BigDecimal(BigInteger.ONE, Int.MIN_VALUE), BigDecimal(BigInteger.ONE, Int.MAX_VALUE),
            BigDecimal(BigInteger.ONE, -8193), BigDecimal(BigInteger.ONE, 8193),
            BigDecimal("9".repeat(8194))
        )) {
            val result = aggregateFiatHoldings(usd, listOf(
                holding("1", "2.50"),
                FiatHoldingValuation(known("1"), FiatAmount("usd", AssetAmountState.Known(invalid)))
            ))
            assertEquals(BigDecimal("2.50"), result.knownSubtotal)
            assertEquals(1, result.pricedCount)
            assertEquals(1, result.unpricedFundedCount)
            assertFalse(result.isComplete)
        }
    }

    @Test fun aggregateAcceptsExactProductEnvelopeBoundaries() {
        for (scale in listOf(-8192, 8192)) {
            val value = BigDecimal(BigInteger("9".repeat(8193)), scale)
            val result = aggregateFiatHoldings(usd, listOf(
                FiatHoldingValuation(known("1"), FiatAmount("usd", AssetAmountState.Known(value)))
            ))
            assertEquals(0, value.compareTo(result.knownSubtotal))
            assertEquals(1, result.pricedCount)
            assertTrue(result.isComplete)
        }
    }

    @Test fun validMixedScaleProductsKeepTheirExactSumBeyondTheSingleProductPrecision() {
        val large = BigDecimal(BigInteger("9".repeat(8193)), -8192)
        val small = BigDecimal(BigInteger.ONE, 8192)
        val result = aggregateFiatHoldings(usd, listOf(large, small).map {
            FiatHoldingValuation(known("1"), FiatAmount("usd", AssetAmountState.Known(it)))
        })
        assertEquals(large + small, result.knownSubtotal)
        assertTrue(result.knownSubtotal!!.precision() > 8193)
        assertEquals(2, result.pricedCount)
        assertTrue(result.isComplete)
    }
}
