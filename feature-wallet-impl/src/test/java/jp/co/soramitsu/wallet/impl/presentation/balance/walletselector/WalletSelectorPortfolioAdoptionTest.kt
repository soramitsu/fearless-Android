package jp.co.soramitsu.wallet.impl.presentation.balance.walletselector

import android.graphics.drawable.PictureDrawable
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.account.api.domain.PendulumPreInstalledAccountsScenario
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.interfaces.NomisScoreInteractor
import jp.co.soramitsu.account.api.domain.interfaces.TotalBalanceUseCase
import jp.co.soramitsu.account.api.domain.model.LightMetaAccount
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.NomisScoreData
import jp.co.soramitsu.account.api.domain.model.PortfolioBalance
import jp.co.soramitsu.account.api.domain.model.TotalBalance
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.WalletSelectorViewState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.balance.walletselector.light.WalletSelectorViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.math.BigDecimal

@RunWith(Parameterized::class)
class WalletSelectorPortfolioAdoptionTest(private val screen: String) {
    @get:Rule val instantTask = InstantTaskExecutorRule()
    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<AccountRepository>()
    private val accounts = mockk<AccountInteractor>()
    private val scores = mockk<NomisScoreInteractor>()
    private val total = mockk<TotalBalanceUseCase>()
    private val router = mockk<WalletRouter>(relaxed = true)
    private val icons = mockk<AddressIconGenerator>()
    private val icon = mockk<PictureDrawable>()
    private val resources = mockk<ResourceManager>()
    private val pendulum = mockk<PendulumPreInstalledAccountsScenario>()
    private val roster = MutableStateFlow<List<LightMetaAccount>>(listOf(ready("First").meta))
    private val balances = MutableStateFlow<TotalBalance>(ready("First"))
    private val scoreRows = MutableStateFlow<List<NomisScoreData>>(emptyList())
    private val models = mutableListOf<BaseViewModel>()

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        every { repository.lightMetaAccountsFlow() } returns roster
        every { total.observe(7) } returns balances
        every { scores.observeNomisScores() } returns scoreRows
        every { resources.getString(any()) } answers { "text-${firstArg<Int>()}" }
        every { resources.getQuantityString(any(), any(), *anyVararg()) } returns "hidden"
        coEvery { icons.createWalletIcon(any(), any()) } returns icon
    }

    @After fun teardown() {
        models.forEach { it.coroutineContext.cancel() }
        Dispatchers.resetMain()
    }

    @Test fun currentMetadataAndCurrencyReplaceSelectedCardTogether() = runTest(dispatcher) {
        val vm = create(); runCurrent(); val first = state(vm).selectedWallet!!
        assertEquals("First", first.title); assertNotNull(first.balance)
        balances.value = ready("Second", "jpy", "¥"); runCurrent()
        val current = state(vm)
        assertEquals("Second", current.wallets.single().title)
        assertEquals(current.wallets.single(), current.selectedWallet)
        assertNotEquals(first.balance, current.selectedWallet!!.balance)
    }

    @Test fun metadataOnlyRosterUpdateDoesNotAddBalanceOrScoreObservers() = runTest(dispatcher) {
        val vm = create(); runCurrent()
        roster.value = listOf(ready("Renamed roster only").meta); runCurrent()
        verify(exactly = 1) { total.observe(7) }
        verify(exactly = 1) { scores.observeNomisScores() }
        assertEquals("First", state(vm).wallets.single().title)
        balances.value = ready("Captured new name"); runCurrent()
        assertEquals("Captured new name", state(vm).wallets.single().title)
    }

    @Test fun removingWalletClearsRowsAndSelection() = runTest(dispatcher) {
        val vm = create(); runCurrent(); assertEquals(1, state(vm).wallets.size)
        roster.value = emptyList(); runCurrent()
        assertTrue(state(vm).wallets.isEmpty()); assertNull(state(vm).selectedWallet)
        balances.value = ready("Late removed wallet"); runCurrent()
        assertTrue(state(vm).wallets.isEmpty())
    }

    @Test fun missingScoreClearsPreviouslyRenderedScore() = runTest(dispatcher) {
        val score = mockk<NomisScoreData> { every { metaId } returns 7L; every { this@mockk.score } returns 73 }
        scoreRows.value = listOf(score)
        val vm = create(); runCurrent(); assertEquals(73, state(vm).wallets.single().score)
        scoreRows.value = emptyList(); runCurrent()
        assertNull(state(vm).wallets.single().score)
    }

    @Test fun unavailablePortfolioClearsNameIconMoneyAndScore() = runTest(dispatcher) {
        scoreRows.value = listOf(mockk { every { metaId } returns 7L; every { score } returns 73 })
        val vm = create(); runCurrent(); assertNotNull(state(vm).wallets.single().balance)
        balances.value = TotalBalance.Unavailable; runCurrent()
        val row = state(vm).wallets.single()
        assertEquals("", row.title); assertNull(row.balance); assertNull(row.score)
        assertNotNull(row.balanceCaption); assertNotSame(icon, row.walletIcon)
    }

    @Test fun cancellationClosesBalanceAndScoreObservers() = runTest(dispatcher) {
        var balanceStops = 0; var scoreStops = 0
        every { total.observe(7) } returns flow {
            try { emit(ready("First")); CompletableDeferred<Unit>().await() } finally { balanceStops++ }
        }
        every { scores.observeNomisScores() } returns flow {
            try { emit(emptyList()); CompletableDeferred<Unit>().await() } finally { scoreStops++ }
        }
        val vm = create(); runCurrent(); assertEquals(1, state(vm).wallets.size)
        vm.coroutineContext.cancel(); runCurrent()
        assertEquals(1, balanceStops); assertEquals(1, scoreStops)
    }

    private fun create(): BaseViewModel = (if (screen == "full") {
        SelectWalletViewModel(repository, icons, accounts, scores, router, total, resources, pendulum)
    } else {
        WalletSelectorViewModel(repository, icons, router, total, scores, resources,
            SavedStateHandle(mapOf("tag" to "test")))
    }).also(models::add)

    private fun state(vm: BaseViewModel): WalletSelectorViewState = when (vm) {
        is SelectWalletViewModel -> vm.state.value
        is WalletSelectorViewModel -> vm.state.value
        else -> error("Unexpected ViewModel")
    }

    private fun ready(name: String, currency: String = "usd", symbol: String = "$"): TotalBalance.Ready {
        val key = ByteArray(32) { 1 }
        val meta = MetaAccount(7, emptyMap(), emptyMap(), key, CryptoType.SR25519, key,
            null, null, null, true, true, null, name, true)
        val amount = PortfolioBalance(BigDecimal.TEN, BigDecimal.TEN, null, null, 1, 1, 0, 0)
        val hidden = PortfolioBalance(BigDecimal.ZERO, BigDecimal.ZERO, null, null, 0, 0, 0, 0)
        return TotalBalance.Ready(meta, emptyMap(), currency, symbol, true, amount, hidden, amount)
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun screens() = listOf(arrayOf("full"), arrayOf("light"))
    }
}

