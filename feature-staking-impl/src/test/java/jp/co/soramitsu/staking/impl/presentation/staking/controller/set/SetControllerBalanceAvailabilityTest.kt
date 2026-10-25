package jp.co.soramitsu.staking.impl.presentation.staking.controller.set

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import android.graphics.drawable.PictureDrawable
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import java.math.BigDecimal
import java.math.BigInteger
import java.util.concurrent.CopyOnWriteArrayList
import jp.co.soramitsu.account.api.presentation.actions.ExternalAccountActions
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.AddressModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.validation.Validation
import jp.co.soramitsu.common.validation.ValidationExecutor
import jp.co.soramitsu.common.validation.ValidationStatus
import jp.co.soramitsu.common.validation.ValidationSystem
import jp.co.soramitsu.core.models.Asset as ChainAsset
import jp.co.soramitsu.wallet.impl.domain.model.AssetAmountState
import jp.co.soramitsu.staking.api.domain.model.StakingState
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.domain.staking.controller.ControllerInteractor
import jp.co.soramitsu.staking.impl.domain.validations.controller.SetControllerValidationFailure
import jp.co.soramitsu.staking.impl.domain.validations.controller.SetControllerValidationPayload
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.staking.controller.confirm.ConfirmSetControllerPayload
import jp.co.soramitsu.staking.impl.scenarios.relaychain.StakingRelayChainScenarioInteractor
import jp.co.soramitsu.wallet.api.presentation.mixin.fee.FeeStatus
import jp.co.soramitsu.wallet.api.presentation.model.FeeModel
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class SetControllerBalanceAvailabilityTest {
    private class ValidationCancelled(val marker: Any) : CancellationException("controller validation cancelled")
    @get:Rule val instant = InstantTaskExecutorRule()
    private val owned = mutableListOf<SetControllerViewModel>()

    @Before fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() {
        runBlocking { withTimeout(5_000) { owned.forEach { it.viewModelScope.coroutineContext.job.cancelAndJoin() } } }
        Dispatchers.resetMain()
    }

    private inner class Fixture(
        free: AssetAmountState<BigInteger>,
        private val validationGate: CompletableDeferred<Unit>? = null,
        private val cancellation: CancellationException? = null
    ) {
        val config = mock<ChainAsset> {
            on { chainId } doReturn "fixture"
            on { id } doReturn "native"
            on { precision } doReturn 2
            on { symbol } doReturn "UNIT"
        }
        val initial = Asset.createEmpty(config, 7, byteArrayOf(1), minSupportedVersion = null)
            .copy(freeInPlanksState = free)
        val assets = MutableStateFlow(initial)
        val validated = CopyOnWriteArrayList<SetControllerValidationPayload>()
        val navigated = CopyOnWriteArrayList<ConfirmSetControllerPayload>()
        val entered = CompletableDeferred<Unit>()
        val navigation = CompletableDeferred<ConfirmSetControllerPayload>()
        val resources = mock<ResourceManager> {
            on { getString(any()) } doAnswer { "text-${it.arguments[0]}" }
        }
        val staking = mock<StakingInteractor> {
            on { currentAssetFlow() } doReturn assets
            onBlocking { getChain("fixture") } doSuspendableAnswer { awaitCancellation() }
        }
        val controller = mock<ControllerInteractor> {
            onBlocking { isControllerFeatureDeprecated(anyOrNull()) } doSuspendableAnswer { awaitCancellation() }
        }
        val state = mock<StakingState.Stash.None> {
            on { stashAddress } doReturn "stash"
            on { controllerAddress } doReturn "old-controller"
            on { accountAddress } doReturn "stash"
        }
        val relay = mock<StakingRelayChainScenarioInteractor> {
            on { stakingStateFlow("fixture") } doReturn flowOf(state)
        }
        val router = mock<StakingRouter> {
            on { openConfirmSetController(any()) } doAnswer {
                val payload = it.getArgument<ConfirmSetControllerPayload>(0)
                navigated += payload
                navigation.complete(payload)
                Unit
            }
        }
        val validator = object : Validation<SetControllerValidationPayload, SetControllerValidationFailure> {
            override suspend fun validate(value: SetControllerValidationPayload): ValidationStatus<SetControllerValidationFailure> {
                validated += value
                entered.complete(Unit)
                validationGate?.await()
                cancellation?.let { throw it }
                return ValidationStatus.Valid()
            }
        }
        val browserEvent = MutableLiveData<jp.co.soramitsu.common.utils.Event<String>>()
        val externalEvent = MutableLiveData<jp.co.soramitsu.common.utils.Event<ExternalAccountActions.Payload>>()
        val externalActions = mock<ExternalAccountActions.Presentation> {
            on { openBrowserEvent } doReturn browserEvent
            on { showExternalActionsEvent } doReturn externalEvent
        }
        val icon = mock<PictureDrawable>()
        val icons = mock<AddressIconGenerator> {
            onBlocking { createAddressIcon(any(), any(), any()) } doReturn icon
        }
        val vm = SetControllerViewModel(
            controller, staking, relay, icons, router, externalActions, mock(), resources, mock(),
            ValidationExecutor(resources), ValidationSystem(validator),
            SavedStateHandle(mapOf(SetControllerFragment.CHAIN_ID_KEY to "fixture"))
        ).also {
            owned += it
            it.payoutControllerChanged(AddressModel("new-controller", mock()))
            it.feeLiveData.value = FeeStatus.Loaded(FeeModel(BigDecimal.ONE, initial.token))
        }

        fun heldAction(): Job {
            val previous = vm.viewModelScope.coroutineContext.job.children.toSet()
            vm.continueClicked()
            return vm.viewModelScope.coroutineContext.job.children.single { it !in previous }
        }
    }

    @Test fun unknownBalanceStopsBeforeValidationAndConfirmation() {
        val f = Fixture(AssetAmountState.Missing)
        f.vm.continueClicked()
        assertTrue(f.validated.isEmpty())
        assertTrue(f.navigated.isEmpty())
        assertNotNull(f.vm.errorWithTitleLiveData.value)
    }

    @Test fun knownZeroAndPositiveBalancesKeepTheirExactNonNullConfirmationAmount() = runBlocking {
        for (planks in listOf(BigInteger.ZERO, BigInteger.valueOf(1_000))) {
            val f = Fixture(AssetAmountState.Known(planks))
            f.vm.continueClicked()
            val payload = withTimeout(5_000) { f.navigation.await() }
            val amount = BigDecimal(planks).movePointLeft(2)
            assertEquals(0, amount.compareTo(payload.transferable))
            assertEquals(0, amount.compareTo(requireNotNull(f.validated.single().transferable)))
            assertEquals("stash", payload.stashAddress)
            assertEquals("new-controller", payload.controllerAddress)
            assertNull(f.vm.errorWithTitleLiveData.value)
        }
    }

    @Test fun unavailableBalanceDuringValidationRevokesThePendingConfirmation() = runBlocking {
        val release = CompletableDeferred<Unit>()
        val f = Fixture(AssetAmountState.Known(BigInteger.valueOf(1_000)), release)
        val action = f.heldAction()
        try {
            withTimeout(5_000) { f.entered.await() }
            f.assets.value = f.initial.copy(freeInPlanksState = AssetAmountState.Missing)
            release.complete(Unit)
            withTimeout(5_000) { action.join() }
            assertEquals(1, f.validated.size)
            assertTrue(f.navigated.isEmpty())
            assertNull(f.vm.errorWithTitleLiveData.value)
        } finally { release.complete(Unit) }
    }

    @Test fun validationCancellationTerminatesTheActionWithoutNavigationOrUserError() = runBlocking {
        val release = CompletableDeferred<Unit>()
        val reason = ValidationCancelled(Any())
        val f = Fixture(AssetAmountState.Known(BigInteger.valueOf(1_000)), release, reason)
        val action = f.heldAction()
        val completion = CompletableDeferred<Throwable?>()
        action.invokeOnCompletion { completion.complete(it) }
        try {
            withTimeout(5_000) { f.entered.await() }
            release.complete(Unit)
            withTimeout(5_000) { action.join() }
            assertTrue(action.isCancelled)
            assertSame(reason, completion.await())
            assertTrue(f.navigated.isEmpty())
            assertNull(f.vm.errorWithTitleLiveData.value)
        } finally { release.complete(Unit) }
    }
}
