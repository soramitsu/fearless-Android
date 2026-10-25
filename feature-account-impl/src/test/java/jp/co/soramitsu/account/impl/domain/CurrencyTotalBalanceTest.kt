package jp.co.soramitsu.account.impl.domain

import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.TotalBalance
import jp.co.soramitsu.common.data.network.coingecko.FiatCurrency
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.coredb.model.cache.CachedAssetWithPriceRead
import jp.co.soramitsu.coredb.model.cache.CachedNumericCell
import jp.co.soramitsu.coredb.model.cache.RawCachedAssetRow
import jp.co.soramitsu.coredb.model.cache.RawCachedTokenPriceRow
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.impl.data.repository.CurrentAccountAssetContext
import jp.co.soramitsu.wallet.impl.data.repository.CurrentAccountAssetSnapshot
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.AssetAmountState
import jp.co.soramitsu.wallet.impl.domain.model.FiatValuationContext
import jp.co.soramitsu.wallet.impl.domain.model.Token
import org.junit.Assert.*
import org.junit.Test
import jp.co.soramitsu.core.models.Asset as CoreAsset

class CurrencyTotalBalanceTest {
    private val context = FiatValuationContext("cad")
    private fun configuration(id: String = "asset", detected: Boolean = false): CoreAsset = mockk(relaxed = true) {
        every { this@mockk.id } returns id
        every { chainId } returns "chain"
        every { precision } returns 0
        every { priceId } returns "price-$id"
        every { priceProvider } returns null
        every { type } returns if (detected) ChainAssetType.Unknown else ChainAssetType.Normal
    }
    private fun known(value: String) = AssetAmountState.Known(BigDecimal(value))
    private fun quantity(value: String) = AssetAmountState.Known(BigInteger(value))
    private fun token(
        rate: AssetAmountState<BigDecimal> = known("2"),
        change: AssetAmountState<BigDecimal> = known("10"),
        quoteCurrency: String = "cad",
        config: CoreAsset = configuration()
    ) = Token(config, rate, change, context, quoteCurrency, "price-${config.id}")

    private fun asset(
        t: Token = token(), free: AssetAmountState<BigInteger> = quantity("4"),
        reserved: AssetAmountState<BigInteger> = quantity("1"), enabled: Boolean? = true
    ) = Asset(
        metaId = 7, token = t, accountId = byteArrayOf(7, 8),
        freeInPlanksState = free, reservedInPlanksState = reserved,
        miscFrozenInPlanksState = quantity("2"), feeFrozenInPlanksState = quantity("0"),
        bondedInPlanksState = quantity("0"), redeemableInPlanksState = quantity("0"),
        unbondingInPlanksState = quantity("0"), sortIndex = 0, enabled = enabled,
        minSupportedVersion = null, chainAccountName = null, markedNotNeed = false, status = null
    )
    private fun cell(value: String?) = value?.toByteArray(Charsets.UTF_8)?.let {
        CachedNumericCell("text", it.size.toLong(), it)
    } ?: CachedNumericCell("null", 0, null)

    private fun row(id: String = "asset", free: String? = "4", enabled: Boolean? = true,
                    currency: String = "cad", rate: String? = "2") = CachedAssetWithPriceRead(
        RawCachedAssetRow(id, "chain", byteArrayOf(7, 8), 7, "untrusted-old-pointer", 0,
            enabled, false, null, null, cell(free), cell("1"), cell("2"), cell("0"),
            cell("0"), cell("0"), cell("0")).decode(),
        RawCachedTokenPriceRow("price-$id", "FOREIGN", cell(rate), cell("10"), cell(currency)).decode()
    )

    private fun snapshot(rows: List<CachedAssetWithPriceRead>, configs: List<CoreAsset>,
                         fiat: FiatValuationContext = context, ready: Boolean = true): CurrentAccountAssetSnapshot {
        val meta = MetaAccount(7, emptyMap(), emptyMap(), null, CryptoType.SR25519, byteArrayOf(7, 8),
            byteArrayOf(9, 10), null, null, true, true, null, "public-fixture", true)
        val chain: Chain = mockk(relaxed = true) {
            every { id } returns "chain"
            every { assetsById } returns configs.associateBy { it.id }
            every { minSupportedVersion } returns null
        }
        return CurrentAccountAssetSnapshot(CurrentAccountAssetContext(meta, fiat), mapOf("chain" to chain), rows, ready)
    }

    private fun assertAmount(expected: String, actual: BigDecimal?) {
        assertNotNull(actual)
        assertEquals(0, BigDecimal(expected).compareTo(actual!!))
    }

    @Test fun completedEmptyPortfolioProvesZeroOnlyInValidCapturedCurrency() {
        val result = summarizeHoldings(context, emptyList())
        assertAmount("0", result.amount); assertAmount("0", result.knownSubtotal)
        assertAmount("0", result.change); assertEquals(0, result.holdingCount)
        assertNull(summarizeHoldings(FiatValuationContext(" "), emptyList()).amount)
    }

    @Test fun missingAndDamagedQuantitiesAreCountedWithoutManufacturingZero() {
        for (free in listOf(AssetAmountState.Missing, AssetAmountState.Unavailable("bad-free"))) {
            val result = summarizeHoldings(context, listOf(asset(free = free)))
            assertNull(result.amount); assertNull(result.knownSubtotal); assertNull(result.change)
            assertEquals(1, result.unknownQuantityCount); assertEquals(1, result.holdingCount)
        }
    }

    @Test fun allUnpricedFundedHoldingsHaveNoCompleteValueOrKnownSubtotal() {
        val result = summarizeHoldings(context, listOf(asset(token(rate = AssetAmountState.Missing))))
        assertNull(result.amount); assertNull(result.knownSubtotal); assertNull(result.change)
        assertEquals(1, result.unpricedAssetCount); assertEquals(0, result.pricedCount)
    }

    @Test fun partialSubtotalIsSeparateFromCompleteValueAndUnknownCounts() {
        val result = summarizeHoldings(context, listOf(asset(), asset(token(rate = AssetAmountState.Missing)),
            asset(free = AssetAmountState.Missing)))
        assertNull(result.amount); assertAmount("10", result.knownSubtotal)
        assertNull(result.change); assertNull(result.rateChange)
        assertEquals(1, result.pricedCount); assertEquals(1, result.unpricedAssetCount)
        assertEquals(1, result.unknownQuantityCount); assertEquals(3, result.holdingCount)
    }

    @Test fun sameSymbolForeignCurrencyCannotJoinSubtotal() {
        val result = summarizeHoldings(context, listOf(asset(), asset(token(quoteCurrency = "usd"))))
        assertAmount("10", result.knownSubtotal); assertNull(result.amount)
        assertEquals(1, result.unpricedAssetCount)
    }

    @Test fun headerUsesFreePlusReservedRatherThanTransferable() {
        val holding = asset()
        assertAmount("2", holding.transferable)
        val result = summarizeHoldings(context, listOf(holding))
        assertAmount("10", result.amount); assertAmount("1", result.change)
        assertAmount("10", result.rateChange)
    }

    @Test fun missingRecentChangeDoesNotEraseKnownCurrentValueOrBecomeZero() {
        val result = summarizeHoldings(context, listOf(asset(token(change = AssetAmountState.Missing))))
        assertAmount("10", result.amount); assertAmount("10", result.knownSubtotal)
        assertNull(result.change); assertNull(result.rateChange)
    }

    @Test fun signedRecentChangeIsPreserved() {
        val result = summarizeHoldings(context, listOf(asset(token(change = known("-12.5")))))
        assertAmount("-1.25", result.change); assertAmount("-12.5", result.rateChange)
    }

    @Test fun knownZeroQuantityDoesNotRequireQuoteButUnknownQuantityDoes() {
        val zero = asset(token(rate = AssetAmountState.Missing), quantity("0"), quantity("0"))
        val result = summarizeHoldings(context, listOf(zero))
        assertAmount("0", result.amount); assertAmount("0", result.change)
        assertEquals(0, result.holdingCount); assertEquals(0, result.unpricedAssetCount)
    }

    @Test fun admittedZeroPriceIsKnownAndKeepsItsFundedHoldingCount() {
        val result = summarizeHoldings(context, listOf(asset(token(rate = known("0")))))
        assertAmount("0", result.amount); assertEquals(1, result.pricedCount)
        assertEquals(1, result.holdingCount); assertEquals(0, result.unpricedAssetCount)
    }

    @Test fun hiddenAndShownCompletenessRemainIndependentAndAllHoldingsIsIncomplete() {
        val configs = listOf(configuration(), configuration("hidden"))
        val source = snapshot(listOf(row(), row("hidden", enabled = false, rate = null)), configs)
        val result = calculateTrustedTotalBalance(source, { false }, { _, _ -> true }) as TotalBalance.Ready
        assertAmount("10", result.shown.amount); assertNull(result.hidden.amount)
        assertEquals(1, result.hidden.holdingCount); assertEquals(1, result.hidden.unpricedAssetCount)
        assertNull(result.allHoldings.amount); assertAmount("10", result.allHoldings.knownSubtotal)
        assertSame(source.context.meta, result.meta)
    }

    @Test fun testnetAndDetectedHoldingsAreExcludedFromValuesAndExclusionCounts() {
        val detected = configuration("detected", detected = true)
        val source = snapshot(listOf(row(), row("detected")), listOf(configuration(), detected))
        val filtered = calculateTrustedTotalBalance(source, { false }, { _, _ -> true }) as TotalBalance.Ready
        assertAmount("10", filtered.shown.amount); assertEquals(1, filtered.shown.holdingCount)
        val testnet = calculateTrustedTotalBalance(source, { true }, { _, _ -> true }) as TotalBalance.Ready
        assertAmount("0", testnet.allHoldings.amount); assertEquals(0, testnet.allHoldings.holdingCount)
    }

    @Test fun deniedRegistryPricePreservesQuantityAndReportsUnpricedHolding() {
        val source = snapshot(listOf(row()), listOf(configuration()))
        val result = calculateTrustedTotalBalance(source, { false }, { _, _ -> false }) as TotalBalance.Ready
        assertNull(result.shown.amount); assertNull(result.shown.knownSubtotal)
        assertEquals(1, result.shown.unpricedAssetCount); assertEquals(1, result.shown.holdingCount)
    }

    @Test fun selectedCurrencyAloneSuppliesLabelEvenWithoutMetadata() {
        val source = snapshot(listOf(row()), listOf(configuration()))
        val result = calculateTrustedTotalBalance(source, { false }, { _, _ -> true }) as TotalBalance.Ready
        assertEquals("cad", result.currencyId); assertEquals("cad", result.fiatSymbol)
        assertTrue(result.quotesReady)
        val mismatch = source.copy(context = CurrentAccountAssetContext(source.context.meta,
            FiatValuationContext("cad", FiatCurrency("usd", "$", "", ""))))
        val relabelled = calculateTrustedTotalBalance(mismatch, { false }, { _, _ -> true }) as TotalBalance.Ready
        assertEquals("cad", relabelled.fiatSymbol); assertAmount("10", relabelled.shown.amount)
    }

    @Test fun invalidCurrencyCannotProduceReadyValueEvenForEmptySnapshot() {
        val source = snapshot(emptyList(), emptyList(), FiatValuationContext(" "))
        assertSame(TotalBalance.Unavailable, calculateTrustedTotalBalance(source, { false }, { _, _ -> true }))
    }

    @Test fun completedCryptoWithPendingQuotesIsNotInitialOrCompleteFiatZero() {
        val source = snapshot(listOf(row().copy(token = null)), listOf(configuration()), ready = false)
        val result = calculateTrustedTotalBalance(source, { false }, { _, _ -> true }) as TotalBalance.Ready
        assertNull(result.shown.amount); assertNull(result.shown.knownSubtotal)
        assertEquals(1, result.shown.unpricedAssetCount)
        assertFalse(result.quotesReady)
    }
}
