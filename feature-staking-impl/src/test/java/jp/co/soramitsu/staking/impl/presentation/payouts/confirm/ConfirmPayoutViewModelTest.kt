package jp.co.soramitsu.staking.impl.presentation.payouts.confirm

import android.graphics.drawable.PictureDrawable
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.validation.CompositeValidation
import jp.co.soramitsu.common.validation.ValidationExecutor
import jp.co.soramitsu.common.validation.ValidationSystem
import jp.co.soramitsu.core.extrinsic.ExtrinsicService
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAddress
import jp.co.soramitsu.runtime.assethub.AssetHubStaking
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.staking.api.data.StakingSharedState
import jp.co.soramitsu.staking.api.domain.model.RewardDestination
import jp.co.soramitsu.staking.api.domain.model.StakingState
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.domain.payout.PayoutInteractor
import jp.co.soramitsu.staking.impl.domain.validations.payout.MakePayoutPayload
import jp.co.soramitsu.staking.impl.domain.validations.payout.PayoutValidationFailure
import jp.co.soramitsu.staking.impl.presentation.payouts.confirm.model.ConfirmPayoutPayload
import jp.co.soramitsu.staking.impl.presentation.payouts.model.PendingPayoutParcelable
import jp.co.soramitsu.staking.impl.scenarios.relaychain.StakingRelayChainScenarioInteractor
import jp.co.soramitsu.wallet.api.presentation.mixin.fee.FeeLoaderMixin
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import java.math.BigDecimal
import java.math.BigInteger
import java.util.concurrent.TimeUnit
import jp.co.soramitsu.core.models.Asset as CoreAsset

/**
 * A payout review that no longer matches the staking selection (the selection moved to another network while the
 * review was open or validating) is refused before signing: the confirmation clears its progress and shows why,
 * and the app does not close.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConfirmPayoutViewModelTest {
    @get:Rule val instant = InstantTaskExecutorRule()
    private val dispatcher = StandardTestDispatcher()
    private val owned = mutableListOf<ConfirmPayoutViewModel>()
    private val account = ByteArray(32) { 4 }

    private fun chain(id: String) = mock<Chain> {
        on { this.id } doReturn id
        on { ecosystem } doReturn Ecosystem.Substrate
    }

    private val reviewed = chain(AssetHubStaking.POLKADOT)
    private val config = mock<CoreAsset> {
        on { chainId } doReturn AssetHubStaking.POLKADOT
        on { id } doReturn "dot"
        on { symbol } doReturn "DOT"
        on { precision } doReturn 10
        on { staking } doReturn CoreAsset.StakingType.RELAYCHAIN
    }
    private val asset = Asset.createEmpty(config, 7, account, minSupportedVersion = null)
    private val extrinsics = mock<ExtrinsicService>()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }

    @After fun tearDown() {
        // Background (flowOn Default) collectors must finish on the test Main dispatcher before it is reset.
        val jobs = owned.map { it.viewModelScope.coroutineContext.job.apply { cancel() } }
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (jobs.any { !it.isCompleted } && System.nanoTime() < deadline) {
            dispatcher.scheduler.runCurrent()
            Thread.sleep(1)
        }
        Dispatchers.resetMain()
    }

    private fun confirmation(selected: Chain): ConfirmPayoutViewModel {
        val stash = StakingState.Stash.None(reviewed, account, account, account)
        val resources = mock<ResourceManager> { on { getString(any()) } doAnswer { "fixture-${it.getArgument<Int>(0)}" } }
        val shared = mock<StakingSharedState> { onBlocking { chain() } doReturn selected }
        val fees = mock<FeeLoaderMixin.Presentation> {
            on { requireFee(any(), any()) } doAnswer { it.getArgument<(BigDecimal) -> Unit>(0).invoke(BigDecimal.ONE) }
        }
        val icons = mock<AddressIconGenerator> {
            onBlocking { createAddressIcon(any(), any(), any()) } doReturn mock<PictureDrawable>()
        }
        val payouts = ConfirmPayoutPayload(
            listOf(
                PendingPayoutParcelable(
                    PendingPayoutParcelable.ValidatorInfoParcelable(ByteArray(32) { 2 }.toAddress(0), null),
                    era = BigInteger.TEN, amountInPlanks = BigInteger.TEN, createdAt = 0L, timeLeft = 0L,
                    closeToExpire = false, page = BigInteger.ZERO
                )
            ),
            totalRewardInPlanks = BigInteger.TEN
        )
        return ConfirmPayoutViewModel(
            interactor = mock<StakingInteractor> { on { currentAssetFlow() } doReturn flowOf(asset) },
            relayChainInteractor = mock<StakingRelayChainScenarioInteractor> {
                on { selectedAccountStakingStateFlow() } doReturn flowOf(stash)
                onBlocking { getRewardDestination(any()) } doReturn RewardDestination.Restake
            },
            payoutInteractor = PayoutInteractor(shared, extrinsics),
            router = mock(),
            addressModelGenerator = icons,
            chainRegistry = mock(),
            externalAccountActions = mock(),
            feeLoaderMixin = fees,
            addressDisplayUseCase = mock(),
            validationSystem = ValidationSystem(CompositeValidation<MakePayoutPayload, PayoutValidationFailure>(emptyList())),
            validationExecutor = ValidationExecutor(resources),
            resourceManager = resources,
            savedStateHandle = SavedStateHandle(mapOf(ConfirmPayoutFragment.KEY_PAYOUTS to payouts)),
            soraRewardScenario = mock()
        ).also(owned::add)
    }

    @Test fun `a review of another network clears progress and shows why`() {
        val vm = confirmation(selected = chain(AssetHubStaking.KUSAMA))
        dispatcher.scheduler.runCurrent()

        vm.submitClicked()
        // The payout is checked on the IO dispatcher; wait for its result to reach the screen.
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (vm.errorLiveData.value == null && System.nanoTime() < deadline) {
            dispatcher.scheduler.runCurrent()
            Thread.sleep(1)
        }

        assertEquals(PayoutInteractor.STALE_REVIEW, vm.errorLiveData.value?.peekContent())
        assertEquals(false, vm.showNextProgress.value)
        verifyNoInteractions(extrinsics)
    }
}
