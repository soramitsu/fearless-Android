package jp.co.soramitsu.account.impl.presentation.account

import android.graphics.drawable.PictureDrawable
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.interfaces.TotalBalanceUseCase
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.PortfolioBalance
import jp.co.soramitsu.account.api.domain.model.TotalBalance
import jp.co.soramitsu.account.api.presentation.actions.ExternalAccountActions
import jp.co.soramitsu.account.impl.domain.account.details.AccountDetailsInteractor
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.account.impl.presentation.account.chainaccounts.ChainAccountsViewModel
import jp.co.soramitsu.account.impl.presentation.account.details.AccountDetailsViewModel
import jp.co.soramitsu.account.impl.presentation.backup_wallet.BackupWalletViewModel
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.WalletItemViewState
import jp.co.soramitsu.common.model.WalletEcosystem
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.math.BigDecimal

/** Exercises actual ViewModels and generated resource IDs; requires integrated Android modules. */
@RunWith(Parameterized::class)
class FixedWalletCardAdoptionTest(private val screen: String) {
    @get:Rule val instantTask = InstantTaskExecutorRule()
    private val dispatcher = StandardTestDispatcher()
    private val balances = MutableStateFlow<TotalBalance>(TotalBalance.AwaitingSelection)
    private val total = mockk<TotalBalanceUseCase>()
    private val details = mockk<AccountDetailsInteractor>()
    private val accounts = mockk<AccountInteractor>()
    private val wallet = mockk<WalletInteractor>()
    private val router = mockk<AccountRouter>(relaxed = true)
    private val icons = mockk<AddressIconGenerator>()
    private val icon = mockk<PictureDrawable>()
    private val resources = mockk<ResourceManager>()
    private val chains = mockk<ChainsRepository>()
    private val actions = mockk<ExternalAccountActions.Presentation>(relaxed = true)
    private val models = mutableListOf<BaseViewModel>()

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        every { total.observe(7) } returns balances
        every { resources.getString(any()) } answers { "text-${firstArg<Int>()}" }
        every { resources.getQuantityString(any(), any(), *anyVararg()) } returns "hidden"
        every { details.getChainAccountsSummaryFlow(7) } returns flowOf(emptyList())
        every { details.getChainProjectionsFlow(7, any()) } returns flowOf(emptyMap())
        every { details.getChainProjectionsFlow(7) } returns flowOf(emptyMap())
        coEvery { accounts.getMetaAccount(7) } returns ready("Backup capability").meta
        every { accounts.isGoogleBackupAvailable() } returns false
        coEvery { accounts.getSupportedBackupTypes(7) } returns emptySet()
        coEvery { accounts.googleBackupAddressForWallet(7) } returns "public-backup-address"
        coEvery { icons.createWalletIcon(any(), any()) } returns icon
    }

    @After fun teardown() {
        models.forEach { it.coroutineContext.cancel() }
        Dispatchers.resetMain()
    }

    @Test fun sameIdNameAndCurrencyChangesReplaceWholeCard() = runTest(dispatcher) {
        balances.value = ready("First", "usd", "$")
        val vm = create(); runCurrent()
        val first = card(vm)!!
        assertEquals("First", first.title); assertSame(icon, first.walletIcon)
        assertNotNull(first.balance)
        balances.value = ready("Second", "jpy", "¥"); runCurrent()
        val second = card(vm)!!
        assertEquals("Second", second.title); assertNotNull(second.balance)
        assertNotEquals(first.balance, second.balance)
        verify(exactly = 1) { total.observe(7) }
        coVerify(exactly = 0) { total.invoke(any()) }
    }

    @Test fun unavailableClearsOldNameIconAndCompleteMoney() = runTest(dispatcher) {
        balances.value = ready("First")
        val vm = create(); runCurrent(); assertNotNull(card(vm)!!.balance)
        balances.value = TotalBalance.Unavailable; runCurrent()
        val current = card(vm)!!
        assertEquals("", current.title); assertNull(current.balance)
        assertNotNull(current.balanceCaption); assertNotSame(icon, current.walletIcon)
    }

    @Test fun lateOldIconCannotReappearAfterMetadataReplacement() = runTest(dispatcher) {
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val oldIcon = mockk<PictureDrawable>(); var calls = 0
        coEvery { icons.createWalletIcon(any(), any()) } coAnswers {
            if (calls++ == 0) withContext(NonCancellable) { entered.complete(Unit); release.await(); oldIcon } else icon
        }
        balances.value = ready("Old")
        val vm = create(); runCurrent(); assertTrue(entered.isCompleted)
        try {
            balances.value = ready("New", "jpy", "¥"); runCurrent()
            assertEquals("New", card(vm)!!.title); assertNotSame(oldIcon, card(vm)!!.walletIcon)
        } finally { release.complete(Unit) }
        runCurrent()
        assertEquals("New", card(vm)!!.title); assertSame(icon, card(vm)!!.walletIcon)
    }

    @Test fun cancellationEndsTheSingleFixedWalletObserver() = runTest(dispatcher) {
        var starts = 0; var stops = 0
        every { total.observe(7) } returns flow {
            starts++
            try { emit(ready("First")); CompletableDeferred<Unit>().await() } finally { stops++ }
        }
        val vm = create(); runCurrent(); assertEquals(1, starts)
        vm.coroutineContext.cancel(); runCurrent(); assertEquals(1, stops)
    }

    @Test fun loadingReplacementCannotKeepPriorCompleteAmount() = runTest(dispatcher) {
        balances.value = ready("First")
        val vm = create(); runCurrent()
        balances.value = TotalBalance.Loading(7, "jpy"); runCurrent()
        val current = card(vm)!!
        assertEquals("", current.title); assertNull(current.balance); assertNotNull(current.balanceCaption)
        assertNotSame(icon, current.walletIcon)
    }

    @Test fun ordinaryObserverFailureClearsCardAndReportsWithoutEscapingScope() = runTest(dispatcher) {
        val release = CompletableDeferred<Unit>()
        every { total.observe(7) } returns flow {
            emit(ready("First")); release.await(); throw IllegalStateException("portfolio read failed")
        }
        val vm = create(); runCurrent(); assertNotNull(card(vm)!!.balance)
        release.complete(Unit); runCurrent()
        val current = card(vm)!!
        assertEquals("", current.title); assertNull(current.balance); assertNotSame(icon, current.walletIcon)
        assertEquals("portfolio read failed", vm.errorLiveData.value?.peekContent())
    }

    private fun create(): BaseViewModel = when (screen) {
        "details" -> AccountDetailsViewModel(details, wallet, router, icons, resources, total, chains, actions,
            SavedStateHandle(mapOf("ACCOUNT_ADDRESS_KEY" to 7L)))
        "chains" -> ChainAccountsViewModel(details, wallet, router, icons, resources, total, chains, actions,
            SavedStateHandle(mapOf("ACCOUNT_ID_KEY" to 7L, "ACCOUNT_TYPE_KEY" to WalletEcosystem.Substrate)))
        else -> BackupWalletViewModel(router, SavedStateHandle(mapOf("ACCOUNT_ID_KEY" to 7L)),
            accounts, details, icons, total, resources)
    }.also(models::add)

    private fun card(vm: BaseViewModel): WalletItemViewState? = when (vm) {
        is AccountDetailsViewModel -> vm.state.value.walletItem
        is ChainAccountsViewModel -> vm.state.value.walletItem
        is BackupWalletViewModel -> vm.state.value.walletItem
        else -> error("Unexpected ViewModel")
    }

    private fun ready(name: String, currency: String = "usd", symbol: String = "$"): TotalBalance.Ready {
        val key = ByteArray(32) { 1 }
        val meta = MetaAccount(7, emptyMap(), emptyMap(), key, CryptoType.SR25519, key,
            null, null, null, true, true, null, name, true)
        val balance = PortfolioBalance(BigDecimal.TEN, BigDecimal.TEN, null, null, 1, 1, 0, 0)
        val hidden = PortfolioBalance(BigDecimal.ZERO, BigDecimal.ZERO, null, null, 0, 0, 0, 0)
        return TotalBalance.Ready(meta, emptyMap(), currency, symbol, true, balance, hidden, balance)
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun screens() = listOf(arrayOf("details"), arrayOf("chains"), arrayOf("backup"))
    }
}

