package jp.co.soramitsu.account.impl.domain

import java.math.BigDecimal
import jp.co.soramitsu.account.api.domain.model.AssetBalance
import jp.co.soramitsu.common.data.network.coingecko.FiatCurrency
import jp.co.soramitsu.wallet.impl.data.repository.CurrentAccountAssetSnapshot
import org.junit.Assert.*
import org.junit.Test

class CurrencyAssetBalanceTest {
    private val fixture = AssetBalanceFixtures
    private fun calculate(snapshot: CurrentAccountAssetSnapshot = fixture.snapshot(),
                          testnet: Boolean = false, trusted: Boolean = true): AssetBalance =
        calculateAssetBalance(snapshot, "chain", "asset", { testnet }, { _, _ -> trusted })
    private fun ready(snapshot: CurrentAccountAssetSnapshot = fixture.snapshot(),
                      testnet: Boolean = false, trusted: Boolean = true) = calculate(snapshot, testnet, trusted) as AssetBalance.Ready
    private fun amount(expected: String, actual: BigDecimal?) {
        assertNotNull(actual); assertEquals(0, BigDecimal(expected).compareTo(actual!!))
    }

    @Test fun transferableUsesFreeMinusFrozenAndDoesNotIncludeReserved() {
        val value = ready()
        amount("2", value.transferable); amount("2", value.knownTransferableSubtotal)
        amount("4", value.fiat.amount); amount("0.4", value.fiat.change); amount("10", value.fiat.rateChange)
    }
    @Test fun capturedAccountAndAssetConfigurationArePreservedByIdentity() {
        val source = fixture.snapshot(); val value = ready(source)
        assertSame(source.context.meta, value.meta)
        assertSame(source.chainsById.getValue("chain").assetsById.getValue("asset"), value.configuration)
    }
    @Test fun sameSymbolForeignCurrencyCannotSupplyFiatValue() {
        val value = ready(fixture.snapshot(rows = listOf(fixture.row(currency = "usd")),
            metadata = FiatCurrency("cad", "$", "", "")))
        amount("2", value.transferable); assertNull(value.fiat.amount); assertNull(value.fiat.knownSubtotal)
        assertEquals("cad", value.currencyId); assertEquals("$", value.fiatSymbol); assertEquals(1, value.fiat.unpricedAssetCount)
    }
    @Test fun foreignDisplayMetadataCannotRelabelCapturedCurrency() {
        val value = ready(fixture.snapshot(metadata = FiatCurrency("usd", "$", "", "")))
        amount("4", value.fiat.amount); assertEquals("cad", value.currencyId); assertEquals("cad", value.fiatSymbol)
    }
    @Test fun absentSpecificCachedAssetIsUnavailableRatherThanAssumedZero() {
        assertSame(AssetBalance.Unavailable, calculate(fixture.snapshot(rows = emptyList())))
    }
    @Test fun onlyForeignCachedRowsCannotEstablishRequestedAssetZero() {
        assertSame(AssetBalance.Unavailable, calculate(fixture.snapshot(rows = listOf(
            fixture.row(id = "other"), fixture.row(chainId = "other")
        ))))
    }
    @Test fun invalidCurrencyCannotBecomeReadyEvenWithNoBalances() {
        assertSame(AssetBalance.Unavailable, calculate(fixture.snapshot(rows = emptyList(), currency = " ")))
    }
    @Test fun removedChainCannotBecomeZero() {
        assertSame(AssetBalance.Unavailable, calculate(fixture.snapshot().copy(chainsById = emptyMap())))
    }
    @Test fun removedAssetCannotBecomeZero() {
        assertSame(AssetBalance.Unavailable, calculate(fixture.snapshot(configs = emptyList())))
    }
    @Test fun mismatchedCatalogIdentityIsRejected() {
        assertSame(AssetBalance.Unavailable, calculate(fixture.snapshot(configs = listOf(fixture.configuration(chainId = "other")))))
    }
    @Test fun unrelatedMalformedRowsCannotPoisonExactNetworkAsset() {
        val value = ready(fixture.snapshot(rows = listOf(fixture.row(), fixture.row(id = "missing", free = "bad"),
            fixture.row(chainId = "foreign", free = null))))
        amount("2", value.transferable); amount("4", value.fiat.amount); assertEquals(1, value.fiat.holdingCount)
    }
    @Test fun missingFreeAndMalformedFrozenQuantitiesRemainUnknown() {
        for (row in listOf(fixture.row(free = null), fixture.row(frozen = "bad"), fixture.row(free = "bad"))) {
            val value = ready(fixture.snapshot(rows = listOf(row)))
            assertNull(value.transferable); assertNull(value.knownTransferableSubtotal); assertNull(value.fiat.amount)
            assertNull(value.fiat.knownSubtotal); assertNull(value.fiat.change)
            assertEquals(1, value.unknownQuantityCount); assertEquals(1, value.fiat.unknownQuantityCount)
        }
    }
    @Test fun absentOptionalFrozenComponentPreservesReleasedWriterZeroComponentSemantics() {
        val value = ready(fixture.snapshot(rows = listOf(fixture.row(frozen = null))))
        amount("4", value.transferable); amount("8", value.fiat.amount)
        assertEquals(0, value.unknownQuantityCount)
    }
    @Test fun missingReservedDoesNotEraseKnownTransferableQuantity() {
        val value = ready(fixture.snapshot(rows = listOf(fixture.row(reserved = null))))
        amount("2", value.transferable); amount("4", value.fiat.amount)
    }
    @Test fun partialCryptoSubtotalCannotBePresentedAsCompleteTransferableBalance() {
        val value = ready(fixture.snapshot(rows = listOf(fixture.row(), fixture.row(free = null))))
        assertNull(value.transferable); amount("2", value.knownTransferableSubtotal)
        assertNull(value.fiat.amount); amount("4", value.fiat.knownSubtotal)
        assertEquals(1, value.unknownQuantityCount); assertEquals(1, value.fiat.unknownQuantityCount)
        assertNull(value.fiat.change); assertNull(value.fiat.rateChange)
    }
    @Test fun unpricedCryptoRemainsKnownWithoutManufacturingFiatZero() {
        val value = ready(fixture.snapshot(rows = listOf(fixture.row(rate = null))))
        amount("2", value.transferable); assertNull(value.fiat.amount); assertNull(value.fiat.knownSubtotal)
        assertEquals(1, value.fiat.unpricedAssetCount)
    }
    @Test fun pendingQuoteStateRetainsCryptoAndReportsPendingValuation() {
        val value = ready(fixture.snapshot(rows = listOf(fixture.row().copy(token = null)), ready = false))
        amount("2", value.transferable); assertFalse(value.quotesReady); assertNull(value.fiat.amount)
    }
    @Test fun zeroTransferableDoesNotRequirePrice() {
        val value = ready(fixture.snapshot(rows = listOf(fixture.row(free = "2", rate = null))))
        amount("0", value.transferable); amount("0", value.fiat.amount); amount("0", value.fiat.change)
        assertEquals(0, value.fiat.holdingCount); assertEquals(0, value.fiat.unpricedAssetCount)
    }
    @Test fun validZeroPriceIsKnownForFundedAsset() {
        val value = ready(fixture.snapshot(rows = listOf(fixture.row(rate = "0"))))
        amount("0", value.fiat.amount); assertEquals(1, value.fiat.pricedCount); assertEquals(1, value.fiat.holdingCount)
    }
    @Test fun unknownChangeDoesNotEraseCurrentValuationOrBecomeZeroChange() {
        val value = ready(fixture.snapshot(rows = listOf(fixture.row(change = null))))
        amount("4", value.fiat.amount); assertNull(value.fiat.change); assertNull(value.fiat.rateChange)
    }
    @Test fun signedPriceChangeRemainsSigned() {
        val value = ready(fixture.snapshot(rows = listOf(fixture.row(change = "-12.5"))))
        amount("-0.5", value.fiat.change); amount("-12.5", value.fiat.rateChange)
    }
    @Test fun hiddenAssetStillHasAnExplicitAssetDetailBalance() {
        val value = ready(fixture.snapshot(rows = listOf(fixture.row(enabled = false))))
        amount("2", value.transferable); amount("4", value.fiat.amount)
    }
    @Test fun deniedRegistryPriceRetainsCryptoButCannotValueFiat() {
        val value = ready(trusted = false)
        amount("2", value.transferable); assertNull(value.fiat.amount); assertEquals(1, value.fiat.unpricedAssetCount)
    }
    @Test fun testnetPriceIsNotUsedForFiat() {
        val value = ready(testnet = true)
        amount("2", value.transferable); assertNull(value.fiat.amount); assertEquals(1, value.fiat.unpricedAssetCount)
    }
    @Test fun detectedAssetPriceIsNotUsedForFiat() {
        val value = ready(fixture.snapshot(configs = listOf(fixture.configuration(detected = true))))
        amount("2", value.transferable); assertNull(value.fiat.amount); assertEquals(1, value.fiat.unpricedAssetCount)
    }
}
