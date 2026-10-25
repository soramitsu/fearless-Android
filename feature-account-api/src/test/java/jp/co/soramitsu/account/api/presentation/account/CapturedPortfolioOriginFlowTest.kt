package jp.co.soramitsu.account.api.presentation.account

import android.graphics.drawable.PictureDrawable
import io.mockk.every
import jp.co.soramitsu.core.models.Ecosystem
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import jp.co.soramitsu.account.api.domain.model.TotalBalance
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAddress
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class CapturedPortfolioOriginFlowTest {
    @Test fun nonSubstrateCatalogCannotPublishASyntheticOriginIcon() = runTest {
        val chain = originChain()
        every { chain.ecosystem } returns Ecosystem.Ethereum
        val source = originReady(chains = mapOf("captured-chain" to chain))
        val generator = mockk<AddressIconGenerator>()
        val result = capturedPortfolioOriginFlow(flowOf(source), "captured-chain", generator).toList().last()
        assertSame(source, result.source); assertNull(result.detail)
        coVerify(exactly = 0) { generator.createAddressIcon(any(), any(), any()) }
    }

    @Test fun ethereumStyleSubstrateCatalogCannotPublishASyntheticOriginIcon() = runTest {
        val chain = originChain()
        every { chain.isEthereumBased } returns true
        val source = originReady(chains = mapOf("captured-chain" to chain))
        val generator = mockk<AddressIconGenerator>()
        val result = capturedPortfolioOriginFlow(flowOf(source), "captured-chain", generator).toList().last()
        assertSame(source, result.source); assertNull(result.detail)
        coVerify(exactly = 0) { generator.createAddressIcon(any(), any(), any()) }
    }

    @Test fun malformedCapturedAccountCannotBecomeAnAllZeroIcon() = runTest {
        val source = originReady(meta = originMeta(key = ByteArray(3) { 1 }))
        val generator = mockk<AddressIconGenerator>()
        val result = capturedPortfolioOriginFlow(flowOf(source), "captured-chain", generator).toList().last()
        assertSame(source, result.source); assertNull(result.detail)
        coVerify(exactly = 0) { generator.createAddressIcon(any(), any(), any()) }
    }


    @Test fun usesActualCapturedSubstrateAddressNameAndGeneratedIcon() = runTest {
        val key = ByteArray(32) { (it + 1).toByte() }
        val source = originReady(meta = originMeta("Captured name", key))
        val icon = mockk<PictureDrawable>()
        val generator = mockk<AddressIconGenerator>()
        coEvery { generator.createAddressIcon(any(), AddressIconGenerator.SIZE_SMALL, any()) } returns icon
        val values = capturedPortfolioOriginFlow(flowOf(source), "captured-chain", generator).toList()
        val result = values.last()
        assertSame(source, result.source)
        assertEquals(key.toAddress(0), result.detail?.address)
        assertEquals("Captured name", result.detail?.name)
        assertSame(icon, result.detail?.image)
        coVerify(exactly = 1) { generator.createAddressIcon(match { it.contentEquals(key) }, 18, any()) }
    }

    @Test fun capturedCatalogPrefixDeterminesTheAddress() = runTest {
        val source = originReady(chains = mapOf("captured-chain" to originChain(2)))
        val generator = mockk<AddressIconGenerator>()
        coEvery { generator.createAddressIcon(any(), any(), any()) } returns mockk()
        val result = capturedPortfolioOriginFlow(flowOf(source), "captured-chain", generator).toList().last()
        assertEquals(source.meta.address(source.chainsById.getValue("captured-chain")), result.detail?.address)
        assertEquals(ByteArray(32) { 1 }.toAddress(2), result.detail?.address)
    }

    @Test fun missingCapturedChainNeverUsesAnotherChainOrGlobalState() = runTest {
        val source = originReady()
        val generator = mockk<AddressIconGenerator>()
        val result = capturedPortfolioOriginFlow(flowOf(source), "missing-chain", generator).toList().last()
        assertSame(source, result.source); assertNull(result.detail)
        coVerify(exactly = 0) { generator.createAddressIcon(any(), any(), any()) }
    }

    @Test fun walletWithoutAddressRetainsValueButHasNoOriginIcon() = runTest {
        val source = originReady(meta = originMeta(key = null))
        val generator = mockk<AddressIconGenerator>()
        val result = capturedPortfolioOriginFlow(flowOf(source), "captured-chain", generator).toList().last()
        assertSame(source, result.source); assertNull(result.detail)
        coVerify(exactly = 0) { generator.createAddressIcon(any(), any(), any()) }
    }

    @Test fun iconFailureCannotPublishAnOldIconOrTurnBalanceIntoZero() = runTest {
        val source = originReady(amount = null, subtotal = null)
        val generator = mockk<AddressIconGenerator>()
        coEvery { generator.createAddressIcon(any(), any(), any()) } throws IllegalStateException("controlled icon failure")
        val result = capturedPortfolioOriginFlow(flowOf(source), "captured-chain", generator).toList().last()
        assertSame(source, result.source); assertNull(result.detail)
        assertNull((result.source as TotalBalance.Ready).shown.amount)
    }

    @Test fun unavailablePortfolioNeverInvokesTheIconGenerator() = runTest {
        val generator = mockk<AddressIconGenerator>()
        val result = capturedPortfolioOriginFlow(flowOf(TotalBalance.Unavailable), "captured-chain", generator).toList().last()
        assertSame(TotalBalance.Unavailable, result.source); assertNull(result.detail)
        coVerify(exactly = 0) { generator.createAddressIcon(any(), any(), any()) }
    }
}
