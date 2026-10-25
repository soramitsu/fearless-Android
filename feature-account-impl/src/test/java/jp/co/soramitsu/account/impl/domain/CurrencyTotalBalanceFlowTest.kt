package jp.co.soramitsu.account.impl.domain

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.TotalBalance
import jp.co.soramitsu.account.impl.presentation.profile.di.ProfileModule
import jp.co.soramitsu.common.domain.SelectedFiat
import jp.co.soramitsu.coredb.dao.AssetDao
import jp.co.soramitsu.coredb.dao.TokenPriceDao
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.tokengroups.PortfolioVisibility
import jp.co.soramitsu.wallet.impl.data.repository.CurrentAccountAssetContext
import jp.co.soramitsu.wallet.impl.data.repository.CurrentAccountAssetReader
import jp.co.soramitsu.wallet.impl.data.repository.CurrentAccountAssetSnapshot
import jp.co.soramitsu.wallet.impl.data.repository.SelectedAccountAssetSnapshot
import jp.co.soramitsu.wallet.impl.domain.model.FiatValuationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CurrencyTotalBalanceFlowTest {
    private fun meta(id: Long = 7, ton: ByteArray = byteArrayOf(1, 2)) = MetaAccount(
        id, emptyMap(), emptyMap(), null, jp.co.soramitsu.core.models.CryptoType.SR25519,
        byteArrayOf(7, 8), byteArrayOf(9, 10), null, ton, true, true, null, "public-fixture", true
    )
    private fun snapshot(meta: MetaAccount = meta(), currency: String = "cad") = CurrentAccountAssetSnapshot(
        CurrentAccountAssetContext(meta, FiatValuationContext(currency)), emptyMap(), emptyList(), true
    )
    private fun useCase(reader: CurrentAccountAssetReader, accounts: AccountRepository = mockk()) =
        ProfileModule().provideTotalBalanceUseCase(accounts, reader, mockk<PortfolioVisibility>(relaxed = true))

    @Test fun initialLoadingAndCompletedEmptyHaveDifferentStates() = runTest {
        val ready = snapshot(); val reader = mockk<CurrentAccountAssetReader>()
        every { reader.observeSelectedSnapshots() } returns flowOf(
            SelectedAccountAssetSnapshot.AwaitingSelection, SelectedAccountAssetSnapshot.Loading(ready.context),
            SelectedAccountAssetSnapshot.Ready(ready)
        )
        val states = useCase(reader).observe(null).toList()
        assertSame(TotalBalance.AwaitingSelection, states[0])
        assertEquals(TotalBalance.Loading(7, "cad"), states[1])
        val complete = states[2] as TotalBalance.Ready
        assertSame(ready.context.meta, complete.meta)
        assertEquals("cad", complete.currencyId)
        assertEquals(java.math.BigDecimal.ZERO, complete.shown.amount)
    }

    @Test fun terminalReadFailureEmitsUnavailableAfterPriorReady() = runTest {
        val reader = mockk<CurrentAccountAssetReader>()
        every { reader.observeSelectedSnapshots() } returns flow {
            emit(SelectedAccountAssetSnapshot.Ready(snapshot()))
            throw IllegalStateException("public-test-read-failure")
        }
        val states = useCase(reader).observe(null).toList()
        assertTrue(states.first() is TotalBalance.Ready)
        assertSame(TotalBalance.Unavailable, states.last())
        assertEquals(2, states.size)
    }

    @Test fun oneShotKeepsCapturedCurrencyAndDoesNotConsultLaterSelection() = runTest {
        val reader = mockk<CurrentAccountAssetReader>(); val captured = snapshot(currency = "eur")
        coEvery { reader.getSnapshot(7) } returns captured
        val result = useCase(reader).invoke(7) as TotalBalance.Ready
        assertEquals("eur", result.currencyId); assertEquals("eur", result.fiatSymbol)
        assertSame(captured.context.meta, result.meta)
    }

    @Test fun cancellationIsNeverConvertedIntoAnUnavailableSuccess() = runTest {
        val reader = mockk<CurrentAccountAssetReader>()
        val cancelled = CancellationException("test-cancel")
        coEvery { reader.getSnapshot(7) } throws cancelled
        try {
            useCase(reader).invoke(7)
            fail("Cancellation must propagate")
        } catch (actual: CancellationException) {
            assertSame(cancelled, actual)
        }
    }

    private fun fixedReader(entries: Flow<MetaAccount?>): CurrentAccountAssetReader {
        val accounts = mockk<AccountRepository> { every { metaAccountStateFlow(7) } returns entries }
        val assets = mockk<AssetDao> { every { observePortfolioBalances(any()) } returns flowOf(emptyList()) }
        val chains = mockk<ChainsRepository> { every { chainsByIdFlow() } returns flowOf(emptyMap()) }
        val prices = mockk<TokenPriceDao> { every { observeTokenPricesByIds(any()) } returns flowOf(emptyList()) }
        val fiat = mockk<SelectedFiat> { every { flow() } returns flowOf("cad") }
        return ProfileModule().provideCurrentAccountAssetReader(assets, chains, accounts, prices, fiat)
    }

    @Test fun fixedWalletRemovalRevokesItsReadySnapshot() = runTest {
        val entries = MutableSharedFlow<MetaAccount?>(replay = 1).apply { tryEmit(meta()) }
        val events = mutableListOf<SelectedAccountAssetSnapshot>()
        val job = launch { fixedReader(entries).observeAccountSnapshots(7).collect { events += it } }
        try {
            advanceUntilIdle()
            assertTrue(events.last() is SelectedAccountAssetSnapshot.Ready)
            entries.emit(null); advanceUntilIdle()
            assertSame(SelectedAccountAssetSnapshot.AwaitingSelection, events.last())
        } finally { job.cancelAndJoin() }
    }

    @Test fun fixedWalletSameIdReplacementRevokesOldContextAndUsesNewMeta() = runTest {
        val original = meta(); val replacement = meta(ton = byteArrayOf(3, 4))
        assertEquals("MetaAccount equality omits TON; this path must not conflate it", original, replacement)
        val entries = MutableSharedFlow<MetaAccount?>(replay = 1).apply { tryEmit(original) }
        val events = mutableListOf<SelectedAccountAssetSnapshot>()
        val job = launch { fixedReader(entries).observeAccountSnapshots(7).collect { events += it } }
        try {
            advanceUntilIdle()
            val previous = (events.last() as SelectedAccountAssetSnapshot.Ready).snapshot
            val marker = events.size
            entries.emit(replacement); advanceUntilIdle()
            assertTrue(events[marker] is SelectedAccountAssetSnapshot.Loading)
            val current = (events.last() as SelectedAccountAssetSnapshot.Ready).snapshot
            assertSame(replacement, current.context.meta)
            assertNotSame(previous.context, current.context)
            assertSame(original, previous.context.meta)
        } finally { job.cancelAndJoin() }
    }

    @Test fun fixedWalletCollectorOwnsAndCancelsItsColdUpstream() = runTest {
        var active = 0
        val upstream = flow {
            active++
            try { emit(meta()); awaitCancellation() } finally { active-- }
        }
        repeat(2) {
            val events = mutableListOf<SelectedAccountAssetSnapshot>()
            val job = launch { fixedReader(upstream).observeAccountSnapshots(7).collect { events += it } }
            try {
                advanceUntilIdle()
                assertEquals(1, active)
                assertTrue(events.last() is SelectedAccountAssetSnapshot.Ready)
            } finally { job.cancelAndJoin() }
            assertEquals(0, active)
        }
    }
}
