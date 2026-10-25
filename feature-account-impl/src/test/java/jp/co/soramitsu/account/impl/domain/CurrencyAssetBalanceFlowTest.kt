package jp.co.soramitsu.account.impl.domain

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.account.api.domain.model.AssetBalance
import jp.co.soramitsu.account.impl.presentation.profile.di.ProfileModule
import jp.co.soramitsu.runtime.multiNetwork.tokengroups.PortfolioVisibility
import jp.co.soramitsu.wallet.impl.data.repository.CurrentAccountAssetReader
import jp.co.soramitsu.wallet.impl.data.repository.SelectedAccountAssetSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class CurrencyAssetBalanceFlowTest {
    private fun useCase(reader: CurrentAccountAssetReader) = ProfileModule().provideAssetBalanceUseCase(reader,
        mockk<PortfolioVisibility> {
            every { isTestnet(any()) } returns false
            every { hasTrustedPrice(any(), any()) } returns true
        })

    @Test fun selectedAccountInitialLoadingAndReadyRemainDistinct() = runTest {
        val source = AssetBalanceFixtures.snapshot(); val reader = mockk<CurrentAccountAssetReader>()
        every { reader.observeSelectedSnapshots() } returns flowOf(SelectedAccountAssetSnapshot.AwaitingSelection,
            SelectedAccountAssetSnapshot.Loading(source.context), SelectedAccountAssetSnapshot.Ready(source))
        val states = useCase(reader).observe(null, "chain", "asset").toList()
        assertSame(AssetBalance.AwaitingSelection, states[0]); assertEquals(AssetBalance.Loading(7, "cad", "chain", "asset"), states[1])
        assertSame(source.context.meta, (states[2] as AssetBalance.Ready).meta)
        verify(exactly = 0) { reader.observeAccountSnapshots(any()) }
    }
    @Test fun fixedAccountObservationUsesRequestedAccountAndRetainsDeletion() = runTest {
        val reader = mockk<CurrentAccountAssetReader>(); val source = AssetBalanceFixtures.snapshot()
        every { reader.observeAccountSnapshots(7) } returns flowOf(SelectedAccountAssetSnapshot.Ready(source), SelectedAccountAssetSnapshot.AwaitingSelection)
        val states = useCase(reader).observe(7, "chain", "asset").toList()
        assertSame(source.context.meta, (states[0] as AssetBalance.Ready).meta); assertSame(AssetBalance.AwaitingSelection, states[1])
        verify(exactly = 0) { reader.observeSelectedSnapshots() }
    }
    @Test fun oneShotUsesCapturedHistoricalAccountAndCurrency() = runTest {
        val reader = mockk<CurrentAccountAssetReader>(); val source = AssetBalanceFixtures.snapshot(currency = "eur")
        coEvery { reader.getSnapshot(7) } returns source
        val result = useCase(reader).invoke(7, "chain", "asset") as AssetBalance.Ready
        assertSame(source.context.meta, result.meta); assertEquals("eur", result.currencyId)
        assertEquals("eur", result.fiatSymbol); assertNull(result.fiat.amount)
    }
    @Test fun failedHistoricalReadBecomesUnavailable() = runTest {
        val reader = mockk<CurrentAccountAssetReader>(); coEvery { reader.getSnapshot(7) } throws IllegalStateException("public-fixture-failure")
        assertSame(AssetBalance.Unavailable, useCase(reader).invoke(7, "chain", "asset"))
    }
    @Test fun historicalCancellationPropagates() = runTest {
        val reader = mockk<CurrentAccountAssetReader>(); val cancelled = CancellationException("public-fixture-cancel")
        coEvery { reader.getSnapshot(7) } throws cancelled
        try { useCase(reader).invoke(7, "chain", "asset"); fail("Cancellation must propagate") }
        catch (actual: CancellationException) { assertSame(cancelled, actual) }
    }
    @Test fun terminalObservationFailureClearsPriorReadyValue() = runTest {
        val reader = mockk<CurrentAccountAssetReader>()
        every { reader.observeSelectedSnapshots() } returns flow { emit(SelectedAccountAssetSnapshot.Ready(AssetBalanceFixtures.snapshot())); throw IllegalStateException("public-fixture-failure") }
        val states = useCase(reader).observe(null, "chain", "asset").toList()
        assertEquals(2, states.size); assertTrue(states[0] is AssetBalance.Ready); assertSame(AssetBalance.Unavailable, states[1])
    }
    @Test fun observationCancellationPropagatesWithoutUnavailableValue() = runTest {
        val reader = mockk<CurrentAccountAssetReader>(); val cancelled = CancellationException("public-fixture-cancel")
        every { reader.observeSelectedSnapshots() } returns flow { emit(SelectedAccountAssetSnapshot.AwaitingSelection); throw cancelled }
        val states = mutableListOf<AssetBalance>()
        try { useCase(reader).observe(null, "chain", "asset").toList(states); fail("Cancellation must propagate") }
        catch (actual: CancellationException) { assertSame(cancelled, actual) }
        assertEquals(listOf(AssetBalance.AwaitingSelection), states)
    }
    @Test fun newSameIdAccountRevisionUsesNewCapturedMetadataAndCurrency() = runTest {
        val old = AssetBalanceFixtures.snapshot(); val replacement = AssetBalanceFixtures.snapshot(account = AssetBalanceFixtures.meta(name = "replacement"), currency = "usd")
        val reader = mockk<CurrentAccountAssetReader>()
        every { reader.observeSelectedSnapshots() } returns flowOf(SelectedAccountAssetSnapshot.Ready(old),
            SelectedAccountAssetSnapshot.Loading(replacement.context), SelectedAccountAssetSnapshot.Ready(replacement))
        val states = useCase(reader).observe(null, "chain", "asset").toList()
        assertSame(old.context.meta, (states[0] as AssetBalance.Ready).meta)
        assertEquals(AssetBalance.Loading(7, "usd", "chain", "asset"), states[1])
        assertSame(replacement.context.meta, (states[2] as AssetBalance.Ready).meta); assertEquals("usd", (states[2] as AssetBalance.Ready).currencyId)
    }
}
