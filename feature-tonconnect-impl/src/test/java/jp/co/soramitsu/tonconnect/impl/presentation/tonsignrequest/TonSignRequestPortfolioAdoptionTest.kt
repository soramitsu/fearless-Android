package jp.co.soramitsu.tonconnect.impl.presentation.tonsignrequest

import android.graphics.drawable.PictureDrawable
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.interfaces.TotalBalanceUseCase
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.PortfolioBalance
import jp.co.soramitsu.account.api.domain.model.TotalBalance
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.model.WalletEcosystem
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.coredb.model.ConnectionSource
import jp.co.soramitsu.feature_tonconnect_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.tonconnect.api.domain.TonConnectInteractor
import jp.co.soramitsu.tonconnect.api.domain.TonConnectRouter
import jp.co.soramitsu.tonconnect.api.model.DappConfig
import jp.co.soramitsu.tonconnect.api.model.DappModel
import jp.co.soramitsu.tonconnect.api.model.TonConnectSignRequest
import jp.co.soramitsu.tonconnect.impl.presentation.featureToggleStore
import jp.co.soramitsu.tonconnect.impl.presentation.pauseTonSigning
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal

/** Requires actual Android module dependencies and generated R; excluded from pure host packet. */
@OptIn(ExperimentalCoroutinesApi::class)
class TonSignRequestPortfolioAdoptionTest {
    @get:Rule val instantTask = InstantTaskExecutorRule()
    private val dispatcher = StandardTestDispatcher()
    private val balances = MutableStateFlow<TotalBalance>(TotalBalance.AwaitingSelection)
    private val connections = MutableStateFlow(DappConfig(null, listOf(dapp(7))))
    private val repository = mockk<AccountRepository>()
    private val total = mockk<TotalBalanceUseCase>()
    private val interactor = mockk<TonConnectInteractor>()
    private val router = mockk<TonConnectRouter>(relaxed = true)
    private val resources = mockk<ResourceManager>()
    private val icons = mockk<AddressIconGenerator>()
    private val icon = mockk<PictureDrawable>()
    private val chain = mockk<Chain> {
        every { id } returns "ton"
        every { ecosystem } returns Ecosystem.Ton
        every { isTestNet } returns false
        every { name } returns "TON"
        every { this@mockk.icon } returns "ton-icon"
    }
    private val viewModels = mutableListOf<TonSignRequestViewModel>()
    private val featureToggles = featureToggleStore()

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        every { resources.getString(any()) } answers { "text-${firstArg<Int>()}" }
        every { resources.getQuantityString(any(), any(), *anyVararg()) } returns "hidden"
        every { total.observe(7) } returns balances
        coEvery { repository.getSelectedMetaAccount() } returns meta(99, "Globally selected")
        coEvery { interactor.getChain() } returns chain
        coEvery { interactor.getConnectedDapps(ConnectionSource.WEB) } answers { connections.value }
        every { interactor.getConnectedDappsFlow(ConnectionSource.WEB) } returns connections
        coEvery { icons.createWalletIcon(WalletEcosystem.Ton, AddressIconGenerator.SIZE_BIG) } returns icon
        coEvery { interactor.signMessage(any(), any(), any(), any()) } returns ("signature" to "public-key")
    }

    @After
    fun tearDown() {
        viewModels.forEach { it.coroutineContext.cancel() }
        Dispatchers.resetMain()
    }

    @Test
    fun explicitRequestWalletDiffersFromGlobalSelection() = runTest(dispatcher) {
        balances.value = ready("Request wallet")
        val vm = create(dapp(7))
        runCurrent()
        val wallet = (vm.state.value as TonSignRequestViewState).wallet
        assertEquals(7L, wallet.id)
        assertEquals("Request wallet", wallet.title)
        assertSame(icon, wallet.walletIcon)
        coVerify(exactly = 0) { repository.getSelectedMetaAccount() }
        verify(exactly = 1) { total.observe(7) }
    }

    @Test
    fun absentBindingDoesNotFallBackToGlobalWalletOrSign() = runTest(dispatcher) {
        connections.value = DappConfig(null, emptyList())
        val vm = create(dapp(null))
        runCurrent()
        vm.onPreviewClick()
        vm.onSignClick()
        runCurrent()
        val wallet = (vm.state.value as TonSignRequestViewState).wallet
        assertEquals("", wallet.title)
        assertNull(wallet.balance)
        assertNotNull(wallet.balanceCaption)
        coVerify(exactly = 0) { repository.getSelectedMetaAccount() }
        coVerify(exactly = 0) { interactor.signMessage(any(), any(), any(), any()) }
        verify(exactly = 0) { total.observe(any()) }
    }

    @Test
    fun mutableFallbackBindingChangeRevokesAndCannotSilentlyRebind() = runTest(dispatcher) {
        balances.value = ready("Bound wallet")
        val vm = create(dapp(null))
        runCurrent()
        connections.value = DappConfig(null, listOf(dapp(9)))
        runCurrent()
        var wallet = (vm.state.value as TonSignRequestViewState).wallet
        assertEquals("", wallet.title)
        assertNull(wallet.balance)
        assertNotSame(icon, wallet.walletIcon)
        connections.value = DappConfig(null, listOf(dapp(7)))
        runCurrent()
        wallet = (vm.state.value as TonSignRequestViewState).wallet
        assertEquals("", wallet.title)
        assertNull(wallet.balance)
        vm.onSignClick()
        runCurrent()
        coVerify(exactly = 0) { interactor.signMessage(any(), any(), any(), any()) }
    }

    @Test
    fun sameIdMetadataAndCurrencyReplacementRefreshesWholeCard() = runTest(dispatcher) {
        balances.value = ready("Old", "usd", "$", BigDecimal.ONE)
        val vm = create(dapp(7))
        runCurrent()
        val old = (vm.state.value as TonSignRequestViewState).wallet.balance
        balances.value = ready("New", "jpy", "JPY", BigDecimal.TEN)
        runCurrent()
        val wallet = (vm.state.value as TonSignRequestViewState).wallet
        assertEquals("New", wallet.title)
        assertNotEquals(old, wallet.balance)
        assertSame(icon, wallet.walletIcon)
    }

    @Test
    fun portfolioInvalidationClearsPreviewAndBlocksConfirmation() = runTest(dispatcher) {
        balances.value = ready("Request wallet")
        val vm = create(dapp(7))
        runCurrent()
        vm.onPreviewClick()
        runCurrent()
        assertEquals("Request wallet", (vm.state.value as TonSignPreviewViewState).wallet.title)
        balances.value = TotalBalance.Unavailable
        runCurrent()
        val wallet = (vm.state.value as TonSignPreviewViewState).wallet
        assertEquals("", wallet.title)
        assertNotSame(icon, wallet.walletIcon)
        vm.onSignClick()
        runCurrent()
        coVerify(exactly = 0) { interactor.signMessage(any(), any(), any(), any()) }
    }

    @Test
    fun confirmationCannotUseSourceReplacedDuringBindingLookup() = runTest(dispatcher) {
        balances.value = ready("Original")
        val vm = create(dapp(7))
        runCurrent()
        vm.onPreviewClick()
        runCurrent()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        coEvery { interactor.getConnectedDapps(ConnectionSource.WEB) } coAnswers {
            entered.complete(Unit)
            release.await()
            connections.value
        }
        vm.onSignClick()
        runCurrent()
        assertTrue(entered.isCompleted)
        balances.value = ready("Replacement")
        runCurrent()
        release.complete(Unit)
        runCurrent()
        coVerify(exactly = 0) { interactor.signMessage(any(), any(), any(), any()) }
        assertEquals("Replacement", (vm.state.value as TonSignPreviewViewState).wallet.title)
    }

    @Test
    fun confirmationRejectsFreshBindingMismatchBeforeSigning() = runTest(dispatcher) {
        balances.value = ready("Bound wallet")
        val vm = create(dapp(null))
        runCurrent()
        vm.onPreviewClick()
        runCurrent()
        coEvery { interactor.getConnectedDapps(ConnectionSource.WEB) } returns DappConfig(null, listOf(dapp(9)))
        vm.onSignClick()
        runCurrent()
        assertEquals("", (vm.state.value as TonSignPreviewViewState).wallet.title)
        coVerify(exactly = 0) { interactor.signMessage(any(), any(), any(), any()) }
    }

    @Test
    fun admittedBoundRequestStillSignsWithOriginalServiceArguments() = runTest(dispatcher) {
        balances.value = ready("Request wallet")
        val vm = create(dapp(7))
        runCurrent()
        vm.onPreviewClick()
        runCurrent()
        vm.onSignClick()
        runCurrent()
        coVerify(exactly = 1) { interactor.signMessage(chain, "sendTransaction", any(), 7) }
    }

    @Test
    fun failedSigningDisplaysErrorAndReleasesLoadingForRetry() = runTest(dispatcher) {
        balances.value = ready("Request wallet")
        val vm = create(dapp(7))
        runCurrent()
        vm.onPreviewClick()
        runCurrent()
        coEvery {
            interactor.signMessage(any(), any(), any(), any())
        } throws IllegalStateException("controlled signing failure")
        vm.onSignClick()
        runCurrent()
        assertFalse((vm.state.value as TonSignPreviewViewState).loading)
        assertEquals("controlled signing failure", vm.errorLiveData.value?.peekContent())
        coEvery { interactor.signMessage(any(), any(), any(), any()) } returns ("signature" to "public-key")
        vm.onSignClick()
        runCurrent()
        coVerify(exactly = 2) { interactor.signMessage(chain, "sendTransaction", any(), 7) }
    }

    @Test
    fun cancelledSigningReleasesLoadingWithoutReportingOrdinaryError() = runTest(dispatcher) {
        balances.value = ready("Request wallet")
        val vm = create(dapp(7))
        runCurrent()
        vm.onPreviewClick()
        runCurrent()
        coEvery { interactor.signMessage(any(), any(), any(), any()) } throws CancellationException("controlled cancellation")
        vm.onSignClick()
        runCurrent()
        assertFalse((vm.state.value as TonSignPreviewViewState).loading)
        assertNull(vm.errorLiveData.value)
        verify(exactly = 0) { router.backWithResult(any()) }
    }

    @Test
    fun pausedTonSigningSaysWhyAndNeverSigns() = runTest(dispatcher) {
        featureToggles.pauseTonSigning()
        balances.value = ready("Request wallet")
        val vm = create(dapp(7))
        runCurrent()
        val dialog = requireNotNull(vm.errorDialogStateLiveData.value).peekContent()
        assertEquals("text-${R.string.signing_refused_paused}", dialog.message)
        vm.onPreviewClick()
        runCurrent()
        vm.onSignClick()
        runCurrent()
        coVerify(exactly = 0) { interactor.signMessage(any(), any(), any(), any()) }

        dialog.positiveClick()
        val result = slot<Pair<String, Any?>>()
        verify { router.backWithResult(capture(result)) }
        assertEquals(TonSignRequestFragment.TON_SIGN_RESULT_KEY, result.captured.first)
        assertTrue((result.captured.second as Result<*>).isFailure)
    }

    @Test
    fun tonSigningPausedWhileTheRequestIsShownIsNotSigned() = runTest(dispatcher) {
        balances.value = ready("Request wallet")
        val vm = create(dapp(7))
        runCurrent()
        vm.onPreviewClick()
        runCurrent()
        assertNull(vm.errorDialogStateLiveData.value)

        featureToggles.pauseTonSigning()
        vm.onSignClick()
        runCurrent()

        coVerify(exactly = 0) { interactor.signMessage(any(), any(), any(), any()) }
        assertEquals("text-${R.string.signing_refused_paused}", vm.errorDialogStateLiveData.value?.peekContent()?.message)
        assertFalse((vm.state.value as TonSignPreviewViewState).loading)
    }

    @Test
    fun repeatedTapDuringBindingLookupCannotStartAnotherSigningAttempt() = runTest(dispatcher) {
        balances.value = ready("Request wallet")
        val vm = create(dapp(7))
        runCurrent()
        vm.onPreviewClick()
        runCurrent()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        coEvery { interactor.getConnectedDapps(ConnectionSource.WEB) } coAnswers {
            entered.complete(Unit)
            release.await()
            connections.value
        }
        vm.onSignClick()
        runCurrent()
        assertTrue(entered.isCompleted)
        assertTrue((vm.state.value as TonSignPreviewViewState).loading)
        vm.onSignClick()
        runCurrent()
        coVerify(exactly = 1) { interactor.getConnectedDapps(ConnectionSource.WEB) }
        release.complete(Unit)
        runCurrent()
        coVerify(exactly = 1) { interactor.signMessage(chain, "sendTransaction", any(), 7) }
        assertFalse((vm.state.value as TonSignPreviewViewState).loading)
    }

    @Test
    fun failedPreviewLookupReportsErrorAndCanRetryWithoutSigning() = runTest(dispatcher) {
        balances.value = ready("Request wallet")
        val vm = create(dapp(7))
        runCurrent()
        coEvery { interactor.getChain() } throws IllegalStateException("preview chain unavailable")
        vm.onPreviewClick()
        runCurrent()
        assertTrue(vm.state.value is TonSignRequestViewState)
        assertEquals("preview chain unavailable", vm.errorLiveData.value?.peekContent())
        coVerify(exactly = 0) { interactor.signMessage(any(), any(), any(), any()) }
        coEvery { interactor.getChain() } returns chain
        vm.onPreviewClick()
        runCurrent()
        assertTrue(vm.state.value is TonSignPreviewViewState)
    }

    @Test
    fun cancelledPreviewLookupDoesNotReportOrdinaryErrorOrSign() = runTest(dispatcher) {
        balances.value = ready("Request wallet")
        val vm = create(dapp(7))
        runCurrent()
        coEvery { interactor.getChain() } throws CancellationException("preview cancelled")
        vm.onPreviewClick()
        runCurrent()
        assertTrue(vm.state.value is TonSignRequestViewState)
        assertNull(vm.errorLiveData.value)
        coVerify(exactly = 0) { interactor.signMessage(any(), any(), any(), any()) }
    }

    private fun create(dapp: DappModel): TonSignRequestViewModel = TonSignRequestViewModel(
        SavedStateHandle(
            mapOf(
                TonSignRequestFragment.TON_SIGN_REQUEST_KEY to mockk<TonConnectSignRequest>(),
                TonSignRequestFragment.DAPP_KEY to dapp,
                TonSignRequestFragment.METHOD_KEY to "sendTransaction"
            )
        ),
        featureToggles,
        total,
        router,
        resources,
        icons,
        interactor
    ).also(viewModels::add)

    private fun ready(
        name: String,
        currency: String = "usd",
        symbol: String = "$",
        amount: BigDecimal = BigDecimal.TEN
    ): TotalBalance.Ready {
        val value = PortfolioBalance(amount, amount, null, null, 1, 1, 0, 0)
        val hidden = PortfolioBalance(BigDecimal.ZERO, BigDecimal.ZERO, null, null, 0, 0, 0, 0)
        return TotalBalance.Ready(meta(7, name), mapOf("ton" to chain), currency, symbol, true, value, hidden, value)
    }

    private fun meta(id: Long, name: String) = MetaAccount(
        id, emptyMap(), emptyMap(), null, null, null, null, null, ByteArray(32) { 1 }, true, true, null, name, true
    )

    private fun dapp(metaId: Long?) =
        DappModel("request", listOf("ton"), "Dapp", "https://app.example", null, null, null, metaId)
}
