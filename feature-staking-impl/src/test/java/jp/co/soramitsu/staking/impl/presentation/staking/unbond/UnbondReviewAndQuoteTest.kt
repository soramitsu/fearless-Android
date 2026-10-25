package jp.co.soramitsu.staking.impl.presentation.staking.unbond

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.common.data.storage.WalletSelectionEpoch
import jp.co.soramitsu.staking.api.data.StakingAssetSelection
import jp.co.soramitsu.staking.api.domain.model.StakingState
import jp.co.soramitsu.staking.impl.presentation.staking.unbond.confirm.ConfirmUnbondPayload
import jp.co.soramitsu.staking.impl.presentation.staking.unbond.confirm.ConfirmUnbondViewModel
import jp.co.soramitsu.staking.impl.presentation.staking.unbond.select.SelectUnbondViewModel
import jp.co.soramitsu.wallet.api.presentation.mixin.fee.FeeStatus
import jp.co.soramitsu.wallet.impl.domain.model.AssetAmountState
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.mockito.kotlin.*

/** Actual caller composition and delayed quote publication; all network/service boundaries remain controlled. */
@OptIn(ExperimentalCoroutinesApi::class)
class UnbondReviewAndQuoteTest {
    @get:Rule val instant = InstantTaskExecutorRule()
    private val dispatcher = StandardTestDispatcher()
    private val owned = mutableListOf<UnbondAuthorityFixture>()
    private class Cancelled(val marker: Any) : CancellationException("unbond cancelled")
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() {
        owned.forEach { it.releaseAndCancel() }
        try { runBlocking { owned.forEach { it.close() } } } finally { Dispatchers.resetMain() }
    }
    private suspend fun fixture(confirm: Boolean = false, fiat: Boolean = false) =
        UnbondAuthorityFixture(dispatcher, confirmScreen = confirm, trustedFiat = fiat)
            .also { owned += it; it.start() }
    private suspend fun routed(f: UnbondAuthorityFixture): ConfirmUnbondPayload {
        f.click(submit = false); f.finishActions()
        return f.routed.single()
    }

    @Test fun actualSelectReviewCanConfirmTheSameAmountWalletFeeAndCollator() = runBlocking<Unit> {
        val f = fixture(); val payload = routed(f); val confirm = f.confirm(payload)
        f.await { confirm.feeStatusLiveData.value is FeeStatus.Loaded }
        confirm.confirmClicked(); f.await { f.submitted.isNotEmpty() }
        assertEquals(listOf("fixture-hash"), f.submitted)
        assertEquals(BigInteger.valueOf(100), f.calls.single().amount)
        assertSame(f.initialState, f.calls.single().state)
    }

    @Test fun changedSourceAfterSelectNavigationCannotAuthorizeTheOldConfirmation() = runBlocking<Unit> {
        val f = fixture(); val payload = routed(f)
        f.assets.value = f.initial.copy(bondedInPlanksState = AssetAmountState.Missing)
        dispatcher.scheduler.runCurrent()
        val confirm = f.confirm(payload); confirm.confirmClicked()
        f.await { confirm.errorDialogStateLiveData.value != null }
        assertTrue(f.calls.isEmpty()); assertTrue(f.submitted.isEmpty())
        assertSame(FeeStatus.Error, confirm.feeStatusLiveData.value)
    }

    @Test fun scalarAmountFeeOrCollatorMismatchIsRejectedBeforeAttachingTheReview() = runBlocking<Unit> {
        for (field in listOf("amount", "fee", "collator")) {
            val f = fixture(); val payload = routed(f)
            val altered = ConfirmUnbondPayload(if (field == "amount") BigDecimal.TEN else payload.amount,
                if (field == "fee") BigDecimal.TEN else payload.fee,
                if (field == "collator") f.collator else payload.collatorAddress, payload.review)
            val rejected = f.confirm(altered); rejected.confirmClicked()
            f.await { rejected.errorDialogStateLiveData.value != null }
            assertTrue(f.submitted.isEmpty()); assertTrue(f.validated.size == 1)
            // Rejection did not consume the legitimate in-process review's one attachment.
            val original = f.confirm(payload)
            f.await { original.feeStatusLiveData.value is FeeStatus.Loaded }
        }
    }

    @Test fun restoredUnboundConfirmationShowsUnavailableAndReturnsToReviewWithoutSigning() = runBlocking<Unit> {
        val f = fixture(); val confirm = f.confirm(ConfirmUnbondPayload(BigDecimal.ONE, BigDecimal.ONE, null))
        f.await { confirm.assetModelFlow.value != null && confirm.feeStatusLiveData.value != null }
        assertEquals("Unavailable", confirm.assetModelFlow.value?.assetBalance)
        assertSame(FeeStatus.Error, confirm.feeStatusLiveData.value)
        confirm.confirmClicked()
        val error = requireNotNull(confirm.errorDialogStateLiveData.value).peekContent()
        assertNotNull(error.positiveButtonText)
        error.positiveClick()
        verify(f.router).back()
        assertTrue(f.calls.isEmpty()); assertTrue(f.submitted.isEmpty())
    }

    @Test fun walletOrSelectionInvalidationImmediatelyRevokesDisplayedConfirmationFeeAndFiat() = runBlocking<Unit> {
        for (wallet in listOf(true, false)) {
            val f = fixture(confirm = true, fiat = true); val vm = f.vm as ConfirmUnbondViewModel
            f.await { vm.amountFiatFLow.value != null && vm.feeStatusLiveData.value is FeeStatus.Loaded }
            if (wallet) { WalletSelectionEpoch.beginChange(); WalletSelectionEpoch.endChange() }
            else f.selection.changed(StakingAssetSelection.RelayChainStaking("other", "native"))
            f.await { vm.feeStatusLiveData.value === FeeStatus.Error && vm.amountFiatFLow.value == null && vm.originAddressModelLiveData.value == null }
            vm.confirmClicked()
            assertTrue(f.calls.isEmpty()); assertTrue(f.submitted.isEmpty())
        }
    }

    @Test fun walletABAWithNoFreshSourcePublicationsCannotRecaptureAnOldFeeOrQuote() = runBlocking<Unit> {
        val f = fixture(fiat = true); val vm = f.vm as SelectUnbondViewModel
        f.await { vm.enteredFiatAmountFlow.value != null }
        val requests = f.feeRequests.size
        repeat(2) { WalletSelectionEpoch.beginChange(); WalletSelectionEpoch.endChange() }
        dispatcher.scheduler.advanceTimeBy(501); dispatcher.scheduler.runCurrent()
        assertEquals(requests, f.feeRequests.size)
        f.await { vm.enteredFiatAmountFlow.value == null && vm.assetModelFlow.value?.assetBalance == "Unavailable" }
        f.click(); f.finishActions()
        assertTrue(f.calls.isEmpty()); assertTrue(f.submitted.isEmpty())
    }

    @Test fun feeCompletionCannotAdmitADifferentSelectedWalletWithTheSameAccount() = runBlocking<Unit> {
        val f = fixture(); val foreign = mock<jp.co.soramitsu.account.api.domain.model.MetaAccount> {
            on { id } doReturn 8L
            on { substrateAccountId } doReturn f.account
            on { chainAccounts } doReturn emptyMap()
        }
        whenever(f.interactor.getCurrentMetaAccount()).thenReturn(foreign)
        (f.vm as SelectUnbondViewModel).enteredAmountFlow.value = "2"
        dispatcher.scheduler.advanceTimeBy(501); dispatcher.scheduler.runCurrent()
        f.completeFees(); f.click(); f.finishActions()
        assertTrue(f.calls.isEmpty()); assertTrue(f.submitted.isEmpty())
        assertSame(FeeStatus.Error, f.fees.feeLiveData.value)
    }

    @Test fun invalidationInsideSuspendingScenarioCannotReadmitItsCompletedBuilder() = runBlocking<Unit> {
        for (confirm in listOf(false, true)) {
            val f = fixture(confirm); val entered = CompletableDeferred<Unit>(); val release = f.gate()
            f.scenarioBuild = { entered.complete(Unit); withContext(NonCancellable) { release.await() } }
            f.click(); f.await { entered.isCompleted }
            f.assets.value = f.initial.copy(bondedInPlanksState = AssetAmountState.Missing)
            dispatcher.scheduler.runCurrent(); release.complete(Unit); f.finishActions()
            // The scenario itself returned normally; the caller's post-callback guard still revoked submission.
            assertEquals(1, f.calls.size); assertTrue(f.submitted.isEmpty())
        }
    }

    @Test fun feeConstructorAndSubmissionPreserveExactCancellationReasons() = runBlocking<Unit> {
        val f = fixture(); val feeReason = Cancelled(Any())
        f.estimateFailure = feeReason
        assertSame(feeReason, runCatching { f.feeRequests.single()(f.initial.token) }.exceptionOrNull())
        f.estimateFailure = null
        val submitReason = Cancelled(Any()); f.submissionFailure = submitReason
        f.click(); f.finishActions()
        assertTrue(f.submitted.isEmpty()); assertSame(submitReason, f.actionCompletions.single())
    }

    @Test fun quoteFailureClearsValuesAndALaterSourcePublicationRecovers() = runBlocking<Unit> {
        val f = fixture(fiat = true); val vm = f.vm as SelectUnbondViewModel
        f.await { vm.enteredFiatAmountFlow.value != null }
        f.quoteRead = { throw IllegalStateException("quote unavailable") }
        f.assets.value = f.initial.copy(sortIndex = 1)
        f.await { vm.assetModelFlow.value?.assetBalance == "Unavailable" && vm.enteredFiatAmountFlow.value == null &&
            vm.errorLiveData.value?.peekContent() == "quote unavailable" }
        f.quoteRead = { BigDecimal.TEN }
        f.assets.value = f.initial.copy(sortIndex = 2)
        f.await { vm.enteredFiatAmountFlow.value != null }
        assertNotEquals("Unavailable", vm.assetModelFlow.value?.assetBalance)
    }

    @Test fun invalidationClearsBeforeNonCancellableOldQuoteCleanupAndRejectsItsLateValue() = runBlocking<Unit> {
        val f = fixture(fiat = true); val vm = f.vm as SelectUnbondViewModel
        f.await { vm.enteredFiatAmountFlow.value != null }
        val entered = CompletableDeferred<Unit>(); val release = f.gate()
        f.quoteRead = { entered.complete(Unit); withContext(NonCancellable) { release.await() }; BigDecimal.TEN }
        f.assets.value = f.initial.copy(sortIndex = 1); f.await { entered.isCompleted }
        WalletSelectionEpoch.beginChange(); WalletSelectionEpoch.endChange()
        f.await { vm.enteredFiatAmountFlow.value == null && vm.assetModelFlow.value?.assetBalance == "Unavailable" }
        assertFalse(release.isCompleted)
        release.complete(Unit); dispatcher.scheduler.runCurrent()
        assertNull(vm.enteredFiatAmountFlow.value)
        assertEquals("Unavailable", vm.assetModelFlow.value?.assetBalance)
    }

    @Test fun quickChoiceCannotUseAnInvalidatedOrFailedQuoteAndRecoversOnFreshSource() = runBlocking<Unit> {
        val f = fixture(); val vm = f.vm as SelectUnbondViewModel
        whenever(f.quick.calculateStakingQuickInputs(any(), any(), any(), any())).thenReturn(mapOf(0.5 to BigDecimal("2")))
        f.assets.value = f.initial.copy(sortIndex = 1)
        f.await { org.mockito.Mockito.mockingDetails(f.quick).invocations.count { it.method.name == "calculateStakingQuickInputs" } >= 2 }
        dispatcher.scheduler.runCurrent()
        f.quoteRead = { throw IllegalStateException("quote unavailable") }
        f.assets.value = f.initial.copy(sortIndex = 2)
        f.await { vm.assetModelFlow.value?.assetBalance == "Unavailable" && vm.errorLiveData.value?.peekContent() == "quote unavailable" }
        vm.onQuickAmountInput(0.5); assertEquals("1", vm.enteredAmountFlow.value)
        f.quoteRead = { BigDecimal.TEN }; f.assets.value = f.initial.copy(sortIndex = 3)
        f.await { org.mockito.Mockito.mockingDetails(f.quick).invocations.count { it.method.name == "calculateStakingQuickInputs" } >= 3 }
        dispatcher.scheduler.runCurrent(); vm.onQuickAmountInput(0.5)
        assertEquals(0, BigDecimal("2").compareTo(BigDecimal(vm.enteredAmountFlow.value)))
        vm.onAmountInputFocusChanged(true); dispatcher.scheduler.runCurrent(); assertTrue(vm.isInputFocused.value)
    }
    @Test fun absentAndNegativeQuotesStayUnknownWhileExplicitZeroRemainsAvailable() = runBlocking<Unit> {
        val f = fixture(); val vm = f.vm as SelectUnbondViewModel
        var reads = 0
        for ((index, amount) in listOf<BigDecimal?>(null, BigDecimal.valueOf(-1)).withIndex()) {
            f.quoteRead = { reads++; amount }; f.assets.value = f.initial.copy(sortIndex = index + 1)
            f.await { reads == index + 1 && vm.assetModelFlow.value?.assetBalance == "Unavailable" }
        }
        f.quoteRead = { BigDecimal.ZERO }; f.assets.value = f.initial.copy(sortIndex = 3)
        f.await { vm.assetModelFlow.value?.assetBalance != "Unavailable" }
        assertNotNull(vm.assetModelFlow.value)
    }

    @Test fun cancelledQuoteRetainsItsRequestCancellationReasonAndClearsPresentation() = runBlocking<Unit> {
        val f = fixture(fiat = true); val vm = f.vm as SelectUnbondViewModel
        f.await { vm.enteredFiatAmountFlow.value != null }
        val reason = Cancelled(Any()); val completions = java.util.concurrent.CopyOnWriteArrayList<Throwable>()
        f.quoteRead = {
            currentCoroutineContext().job.invokeOnCompletion { cause -> cause?.let { completions += it } }
            throw reason
        }
        f.assets.value = f.initial.copy(sortIndex = 1)
        f.await { completions.isNotEmpty() && vm.enteredFiatAmountFlow.value == null }
        assertSame(reason, completions.single())
        assertEquals("Unavailable", vm.assetModelFlow.value?.assetBalance)
    }

}
