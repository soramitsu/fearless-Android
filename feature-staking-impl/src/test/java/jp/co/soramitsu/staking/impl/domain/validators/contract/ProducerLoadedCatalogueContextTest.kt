package jp.co.soramitsu.staking.impl.domain.validators.contract

import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.runtime.state.SingleAssetSharedState
import jp.co.soramitsu.staking.impl.presentation.validators.change.custom.search.SearchBlockProducersState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

@OptIn(ExperimentalCoroutinesApi::class)
class ProducerLoadedCatalogueContextTest {
    @get:Rule val environment: TestRule = RuleChain.outerRule(SearchContractEnvironment()).around(ProducerScreenArchitectureRule())

    @Test fun walletChangeRefreshesAlreadyLoadedRowsWithTheSameSelectedAddresses() = runTest {
        val f = ProducerCatalogueRetryFixture()
        try {
            loaded(f)
            val previous = f.screen.data.process.setupStakingProcess.value
            val calls = f.calls.get()
            f.load = { freshRows(f) }
            f.screen.data.wallets.value = wallet(77)
            assertFresh(f, previous, calls)
        } finally {
            f.close()
        }
    }

    @Test fun chainChangeRefreshesAlreadyLoadedRowsWithTheSameSelectedAddresses() = runTest {
        val f = ProducerCatalogueRetryFixture()
        try {
            loaded(f)
            val previous = f.screen.data.process.setupStakingProcess.value
            val calls = f.calls.get()
            f.load = { freshRows(f) }
            val replacement = ValidatorCatalogueFixture("replacement-relay")
            f.screen.data.selection.value = SingleAssetSharedState.AssetWithChain(replacement.chain, replacement.asset)
            assertFresh(f, previous, calls)
        } finally {
            f.close()
        }
    }

    @Test fun assetChangeRefreshesAlreadyLoadedRowsWithTheSameSelectedAddresses() = runTest {
        val f = ProducerCatalogueRetryFixture()
        try {
            loaded(f)
            val previous = f.screen.data.process.setupStakingProcess.value
            val calls = f.calls.get()
            f.load = { freshRows(f) }
            val replacement = mock<Asset> {
                on { id } doReturn "other-staking-asset"
                on { staking } doReturn Asset.StakingType.RELAYCHAIN
            }
            f.screen.data.selection.value = SingleAssetSharedState.AssetWithChain(f.screen.data.chain, replacement)
            assertFresh(f, previous, calls)
        } finally {
            f.close()
        }
    }

    @Test fun accountReplacementWithinTheSameWalletRefreshesAlreadyLoadedRows() = runTest {
        val f = ProducerCatalogueRetryFixture()
        try {
            f.screen.data.wallets.value = wallet(11, 1)
            loaded(f)
            val previous = f.screen.data.process.setupStakingProcess.value
            val calls = f.calls.get()
            f.load = { freshRows(f) }
            f.screen.data.wallets.value = wallet(11, 2)
            assertFresh(f, previous, calls)
        } finally {
            f.close()
        }
    }

    @Test fun newerContextCancelsItsPredecessorsPendingRefreshAndPublishesOnlyCurrentRows() = runTest {
        val f = ProducerCatalogueRetryFixture()
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        try {
            loaded(f)
            val previous = f.screen.data.process.setupStakingProcess.value
            f.load = {
                started.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    cancelled.complete(Unit)
                }
            }
            f.screen.data.wallets.value = wallet(21)
            withContext(Dispatchers.Default) { withTimeout(3_000) { started.await() } }
            val calls = f.calls.get()
            f.load = { freshRows(f) }
            f.screen.data.wallets.value = wallet(22)
            assertFresh(f, previous, calls)
            withContext(Dispatchers.Default) { withTimeout(3_000) { cancelled.await() } }
        } finally {
            f.close()
        }
    }

    @Test fun equivalentAccountBytesDoNotEmitAnotherContextButChangedBytesDo() = runTest {
        val f = ProducerSearchFixture()
        f.defaults()
        f.wallets.value = wallet(31, 1)
        val events = mutableListOf<Unit>()
        val collector = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            f.interactor.observeSelectionContext().collect { events += it }
        }
        try {
            runCurrent()
            assertEquals(1, events.size)
            f.wallets.value = wallet(31, 1)
            runCurrent()
            assertEquals(1, events.size)
            f.wallets.value = wallet(31, 2)
            runCurrent()
            assertEquals(2, events.size)
        } finally {
            collector.cancel()
            f.lifecycle.destroy()
        }
    }

    private suspend fun loaded(fixture: ProducerCatalogueRetryFixture) {
        fixture.start()
        fixture.screen.await {
            it is SearchBlockProducersState.Success && it.blockProducers.map { row -> row.selected } == listOf(true, false)
        }
    }

    private fun freshRows(fixture: ProducerCatalogueRetryFixture) =
        fixture.screen.catalogue.take(2).mapIndexed { index, row ->
            row.copy(name = "Al refreshed $index", rewardsPercent = "9%")
        }

    private suspend fun assertFresh(
        fixture: ProducerCatalogueRetryFixture,
        previous: Any,
        previousCalls: Int
    ) {
        val state = fixture.screen.await {
            it is SearchBlockProducersState.Success && it.blockProducers.firstOrNull()?.name == "Al refreshed 0"
        } as SearchBlockProducersState.Success
        assertEquals(previousCalls + 1, fixture.calls.get())
        assertSame(previous, fixture.screen.data.process.setupStakingProcess.value)
        assertEquals("al", fixture.screen.model.enteredQuery.value)
        assertEquals(fixture.screen.addresses.take(2), state.blockProducers.map { it.address })
        assertEquals(listOf(true, false), state.blockProducers.map { it.selected })
        assertEquals(listOf("9%", "9%"), state.blockProducers.map { it.rewardsPercent })
        assertNull(fixture.screen.model.errorDialogStateLiveData.value)
    }

    private fun wallet(id: Long, marker: Byte = 1) = mock<MetaAccount> {
        on { this.id } doReturn id
        on { substrateAccountId } doReturn ByteArray(32) { marker }
        on { chainAccounts } doReturn emptyMap()
    }
}
