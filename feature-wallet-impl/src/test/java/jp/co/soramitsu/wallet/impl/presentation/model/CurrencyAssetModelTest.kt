package jp.co.soramitsu.wallet.impl.presentation.model

import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.core.models.Asset as CoreAsset
import jp.co.soramitsu.wallet.impl.data.mappers.mapAssetToAssetModel
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.AssetAmountState
import jp.co.soramitsu.wallet.impl.domain.model.FiatValuationContext
import jp.co.soramitsu.wallet.impl.domain.model.Token
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger

class CurrencyAssetModelTest {
    private fun config(precision: Int = 2, id: String = "asset", priceId: String = "price"): CoreAsset = mockk(relaxed = true) {
        every { this@mockk.id } returns id
        every { chainId } returns "chain"
        every { this@mockk.precision } returns precision
        every { this@mockk.priceId } returns priceId
        every { priceProvider } returns null
    }
    private fun token(currency: String = "cad", quoteCurrency: String? = currency,
                      rate: AssetAmountState<BigDecimal> = AssetAmountState.Known(BigDecimal("2.50"))) = Token(
        configuration = config(), fiatRateInput = rate,
        recentRateChangeInput = AssetAmountState.Known(BigDecimal("-0.125")),
        fiatContext = FiatValuationContext(currency), quoteCurrencyId = quoteCurrency, quotePriceId = "price"
    )
    private fun amount(value: String) = AssetAmountState.Known(BigInteger(value))
    private fun asset(t: Token = token(), free: AssetAmountState<BigInteger> = amount("1200")) = Asset(
        metaId = 7, token = t, accountId = byteArrayOf(7, 8), freeInPlanksState = free,
        reservedInPlanksState = amount("30"), miscFrozenInPlanksState = amount("10"),
        feeFrozenInPlanksState = amount("20"), bondedInPlanksState = amount("40"),
        redeemableInPlanksState = amount("50"), unbondingInPlanksState = amount("60"),
        sortIndex = 17, enabled = false, minSupportedVersion = null,
        chainAccountName = "public account", markedNotNeed = true, status = null
    )

    @Test fun actualMapperRetainsCanonicalAssetTokenAndCapturedCurrency() {
        val domain = asset(); val model = mapAssetToAssetModel(domain)
        assertSame(domain, model.asset); assertSame(domain.token, model.token.token)
        assertSame(domain.token.fiatContext, model.fiatContext)
        assertEquals("cad", model.token.fiatSymbol); assertEquals("cad", model.token.quoteCurrencyId)
        assertEquals(BigDecimal("12.30"), model.total); assertEquals(BigDecimal("30.7500"), model.totalFiat)
        assertEquals(domain.fiatAmountState, model.fiatAmountState)
    }

    @Test fun missingAndDamagedQuantitiesRemainDistinctThroughDerivedFields() {
        val missing = AssetModel(asset(free = AssetAmountState.Missing))
        val damaged = AssetModel(asset(free = AssetAmountState.Unavailable("bad-free")))
        assertSame(AssetAmountState.Missing, missing.totalState)
        assertTrue(damaged.totalState is AssetAmountState.Unavailable)
        assertNull(missing.total); assertNull(damaged.total)
        assertNull(missing.totalFiat); assertNull(damaged.totalFiat)
        assertNull(missing.getAsFiatWithCurrency(null)); assertNull(damaged.getAsFiatWithCurrency(null))
    }

    @Test fun absentAndForeignPricesKeepHealthyCryptoAndSeparateQuoteState() {
        for (t in listOf(token(rate = AssetAmountState.Missing), token(quoteCurrency = "usd"))) {
            val model = AssetModel(asset(t))
            assertEquals(BigDecimal("12.30"), model.total)
            assertNull(model.fiatAmount); assertNull(model.totalFiat); assertNull(model.availableFiat)
            assertNull(model.getAsFiatWithCurrency(BigDecimal.ONE))
            assertFalse(model.token.fiatRateState is AssetAmountState.Known)
            assertEquals("cad", model.fiatContext?.currencyId)
        }
    }

    @Test fun nullQuantityCannotFormatAsZeroEvenWhenPriceIsKnown() {
        val model = AssetModel(asset())
        assertNull(model.getAsFiatWithCurrency(null))
        assertEquals(BigDecimal.ZERO.multiply(BigDecimal("2.50")).formatFiat("cad"), model.getAsFiatWithCurrency(BigDecimal.ZERO))
        assertEquals(BigDecimal("2.50").formatFiat("cad"), model.getAsFiatWithCurrency(BigDecimal.ONE))
    }

    @Test fun signedAndOverboundCallerAmountsUseCanonicalTokenBounds() {
        val model = AssetModel(asset())
        assertEquals(BigDecimal("-2.50").formatFiat("cad"), model.getAsFiatWithCurrency(BigDecimal.ONE.negate()))
        assertNull(model.getAsFiatWithCurrency(BigDecimal.ONE.scaleByPowerOfTen(4097)))
        assertNull(model.getAsFiatWithCurrency(BigDecimal("1".repeat(4098))))
    }

    @Test fun copyRecomputesCanonicalQuantityAndQuoteAdmissionWithoutRelabelling() {
        val old = AssetModel(asset())
        val changedAsset = old.asset.copy(token = old.asset.token.copy(fiatContext = FiatValuationContext("eur")), freeInPlanksState = amount("2300"))
        val changed = old.copy(asset = changedAsset)
        assertEquals(BigDecimal("23.30"), changed.total); assertNull(changed.totalFiat)
        assertNull(changed.getAsFiatWithCurrency(BigDecimal.ONE)); assertEquals("eur", changed.fiatContext?.currencyId)
        assertEquals(BigDecimal("12.30"), old.total); assertEquals(BigDecimal("30.7500"), old.totalFiat)
    }

    @Test fun configurationCopyCannotBorrowPriorPriceOrInvalidPrecision() {
        val old = AssetModel(asset())
        val repriced = old.copy(asset = old.asset.copy(token = old.asset.token.copy(configuration = config(priceId = "different"))))
        assertNull(repriced.totalFiat); assertNull(repriced.getAsFiatWithCurrency(BigDecimal.ONE))
        val malformedPrecision = old.copy(asset = old.asset.copy(token = old.asset.token.copy(configuration = config(precision = 256))))
        assertTrue(malformedPrecision.totalState is AssetAmountState.Unavailable)
        assertNull(malformedPrecision.total); assertNull(malformedPrecision.totalFiat)
    }

    @Test fun primaryKeyRetainsActualAccountRatherThanEmptyFallback() {
        val old = AssetModel(asset()); val changed = old.copy(asset = old.asset.copy(accountId = byteArrayOf(9, 10)))
        assertEquals(old.asset.uniqueKey, old.primaryKey)
        assertEquals(changed.asset.uniqueKey, changed.primaryKey)
        assertNotEquals(old.primaryKey, changed.primaryKey)
    }

    @Test fun metadataAndPerComponentStatesRemainCanonical() {
        val domain = asset(); val model = AssetModel(domain)
        assertEquals(7L, model.metaId); assertEquals(17, model.sortIndex)
        assertEquals("public account", model.chainAccountName); assertEquals(true, model.isHidden)
        assertNull(model.minSupportedVersion); assertTrue(model.isSupported)
        assertEquals(domain.lockedState, model.lockedState); assertEquals(domain.bondedState, model.bondedState)
        assertEquals(domain.frozenState, model.frozenState); assertEquals(domain.reservedState, model.reservedState)
        assertEquals(domain.redeemableState, model.redeemableState); assertEquals(domain.unbondingState, model.unbondingState)
        assertEquals(domain.transferableState, model.availableState)
        assertEquals(domain.token.fiatAmountState(domain.transferableState), model.availableFiatState)
        assertEquals(domain.token.fiatAmountState(domain.frozenState), model.frozenFiatState)
    }

    @Test fun noContextCompatibilityTokenCannotInventDollarValue() {
        val legacy = Token(config(), BigDecimal("2.50"), "$", BigDecimal.ZERO)
        val model = AssetModel(asset(legacy))
        assertEquals(BigDecimal("12.30"), model.total)
        assertNull(model.fiatContext); assertNull(model.token.fiatSymbol); assertNull(model.totalFiat)
        assertNull(model.getAsFiatWithCurrency(BigDecimal.ONE))
    }
}
