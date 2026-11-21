package jp.co.soramitsu.staking.impl.presentation.validators.current

import android.graphics.Picture
import android.graphics.drawable.PictureDrawable
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.viewModelScope
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.list.GroupedList
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.toHexAccountId
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.runtime.ext.addressOf
import jp.co.soramitsu.staking.api.data.StakingType
import jp.co.soramitsu.staking.api.domain.model.NominatedValidator
import jp.co.soramitsu.staking.api.domain.model.Nominations
import jp.co.soramitsu.staking.api.domain.model.RootIdentity
import jp.co.soramitsu.staking.api.domain.model.StakingState
import jp.co.soramitsu.staking.api.domain.model.Validator
import jp.co.soramitsu.staking.impl.StakingLaneFixtures
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.domain.validators.current.CurrentValidatorsInteractor
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.SetupStakingProcess
import jp.co.soramitsu.staking.impl.presentation.common.SetupStakingProcess.ReadyToSubmit.SelectionMethod
import jp.co.soramitsu.staking.impl.presentation.common.SetupStakingSharedState
import jp.co.soramitsu.staking.impl.presentation.validators.current.model.NominatedValidatorModel
import jp.co.soramitsu.staking.impl.presentation.validators.current.model.NominatedValidatorStatusModel
import jp.co.soramitsu.staking.impl.presentation.validators.current.model.NominatedValidatorStatusModel.TitleConfig
import jp.co.soramitsu.staking.impl.presentation.validators.parcel.ValidatorDetailsParcelModel
import jp.co.soramitsu.staking.impl.scenarios.relaychain.StakingRelayChainScenarioInteractor
import jp.co.soramitsu.wallet.api.presentation.formatters.formatCryptoFromPlanks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CurrentValidatorsViewModelCoverageTest {

    @get:Rule
    val instant = InstantTaskExecutorRule()

    private val owned = mutableListOf<CurrentValidatorsViewModel>()

    private val chainAsset = StakingLaneFixtures.chainAsset()
    private val chain = StakingLaneFixtures.chain(chainAsset)
    private val asset = StakingLaneFixtures.asset(chainAsset)
    private val stash = ByteArray(32) { 6 }
    private val nominator = StakingState.Stash.Nominator(
        chain,
        stash,
        stash,
        stash,
        Nominations(emptyList(), BigInteger.ONE, false)
    )

    private val router = mockk<StakingRouter>(relaxed = true)
    private val resources = mockk<ResourceManager>(relaxed = true)
    private val interactor = mockk<StakingInteractor>(relaxed = true)
    private val scenario = mockk<StakingRelayChainScenarioInteractor>(relaxed = true)
    private val icons = mockk<AddressIconGenerator>(relaxed = true)
    private val currentValidators = mockk<CurrentValidatorsInteractor>(relaxed = true)
    private val sharedState = SetupStakingSharedState()
    private val detailsCache = MutableStateFlow<Map<String, ValidatorDetailsParcelModel>>(emptyMap())

    private val active = validator(1, "Active one", oversubscribed = true)
    private val elected = validator(2, null, oversubscribed = false)
    private val inactive = validator(3, "Inactive one", oversubscribed = false)
    private val waiting = validator(4, "Waiting one", oversubscribed = false)
    private val nomination = StakingLaneFixtures.planks(3)

    private val grouped: GroupedList<NominatedValidator.Status.Group, NominatedValidator> = linkedMapOf(
        NominatedValidator.Status.Group.Active(1) to listOf(
            NominatedValidator(active, NominatedValidator.Status.Active(nomination, willUserBeRewarded = false))
        ),
        NominatedValidator.Status.Group.Elected(1) to listOf(NominatedValidator(elected, NominatedValidator.Status.Elected)),
        NominatedValidator.Status.Group.Inactive(1) to listOf(NominatedValidator(inactive, NominatedValidator.Status.Inactive)),
        NominatedValidator.Status.Group.WaitingForNextEra(16, 1) to listOf(
            NominatedValidator(waiting, NominatedValidator.Status.WaitingForNextEra)
        )
    )

    private fun validator(seed: Int, name: String?, oversubscribed: Boolean): Validator {
        val address = chain.addressOf(ByteArray(32) { seed.toByte() })
        return Validator(
            address = address,
            slashed = seed == 3,
            accountIdHex = address.toHexAccountId(),
            prefs = null,
            electedInfo = Validator.ElectedInfo(
                totalStake = StakingLaneFixtures.planks(100),
                ownStake = StakingLaneFixtures.planks(10),
                nominatorStakes = emptyList(),
                apy = BigDecimal("0.1"),
                isOversubscribed = oversubscribed
            ),
            identity = name?.let { RootIdentity(it, null, null, null, null, null, null, null) }
        )
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { resources.getString(any()) } answers { "s${firstArg<Int>()}" }
        every { resources.getString(any(), *anyVararg()) } answers {
            "f${firstArg<Int>()}:${secondArg<Array<Any>>().joinToString()}"
        }
        every { interactor.currentAssetFlow() } returns flowOf(asset)
        every { interactor.selectedChainFlow() } returns flowOf(chain)
        every { interactor.validatorDetailsCache } returns detailsCache
        every { scenario.selectedAccountStakingStateFlow() } returns flowOf(nominator)
        coEvery { currentValidators.nominatedValidatorsFlow(any<StakingState.Stash>()) } returns flowOf(grouped)
        coEvery { icons.createAddressIcon(any<ByteArray>(), any(), any()) } returns PictureDrawable(Picture())
    }

    @After
    fun tearDown() {
        runBlocking { withTimeout(5_000) { owned.forEach { it.viewModelScope.coroutineContext.job.cancelAndJoin() } } }
        Dispatchers.resetMain()
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(10)
        assertTrue("condition not reached", condition())
    }

    private fun viewModel() = CurrentValidatorsViewModel(
        router = router,
        resourceManager = resources,
        stakingInteractor = interactor,
        stakingRelayChainScenarioInteractor = scenario,
        iconGenerator = icons,
        currentValidatorsInteractor = currentValidators,
        setupStakingSharedState = sharedState
    ).also(owned::add)

    private fun CurrentValidatorsViewModel.awaitModels(): List<Any?> {
        waitUntil { currentValidatorModelsLiveData.value is LoadingState.Loaded }
        return (currentValidatorModelsLiveData.value as LoadingState.Loaded).data
    }

    @Test
    fun groupsAreShownWithHeadersAndValidatorRows() {
        val models = viewModel().awaitModels()

        assertEquals(8, models.size)
        assertEquals(
            NominatedValidatorStatusModel(
                TitleConfig("f${R.string.staking_your_elected_format}:1", R.color.green),
                "s${R.string.staking_your_allocated_description}"
            ),
            models[0]
        )
        assertEquals(
            NominatedValidatorStatusModel(null, "s${R.string.staking_your_not_allocated_description}"),
            models[2]
        )
        assertEquals(
            NominatedValidatorStatusModel(
                TitleConfig("f${R.string.staking_your_not_elected_format}:1", R.color.black1),
                "s${R.string.staking_your_inactive_description}"
            ),
            models[4]
        )
        assertEquals(
            NominatedValidatorStatusModel(
                TitleConfig("f${R.string.staking_custom_header_validators_title}:1, 16", R.color.black1),
                "s${R.string.staking_your_validators_changing_title}"
            ),
            models[6]
        )
    }

    @Test
    fun validatorRowsShowNominationIdentityAndFlags() {
        val models = viewModel().awaitModels()

        val activeRow = models[1] as NominatedValidatorModel
        val amount = nomination.formatCryptoFromPlanks(chainAsset)
        assertEquals("f${R.string.staking_your_nominated_format}:$amount", activeRow.nominated)
        assertEquals(active.address, activeRow.addressModel.address)
        assertEquals("Active one", activeRow.addressModel.name)
        assertTrue(activeRow.isOversubscribed)
        assertFalse(activeRow.isSlashed)

        val electedRow = models[3] as NominatedValidatorModel
        assertNull(electedRow.nominated)
        assertNull(electedRow.addressModel.name)
        assertEquals(elected.address, electedRow.addressModel.address)

        val inactiveRow = models[5] as NominatedValidatorModel
        assertTrue(inactiveRow.isSlashed)
        assertFalse(inactiveRow.isOversubscribed)
    }

    @Test
    fun anActiveValidatorThatWillNotRewardRaisesTheWarning() {
        val vm = viewModel()

        waitUntil { vm.shouldShowOversubscribedNoRewardWarning.replayCache.isNotEmpty() }
        assertEquals(listOf(true), vm.shouldShowOversubscribedNoRewardWarning.replayCache)
    }

    @Test
    fun aFailureWithoutMessageShowsTheGeneralMessage() {
        coEvery { currentValidators.nominatedValidatorsFlow(any<StakingState.Stash>()) } returns
            flow<GroupedList<NominatedValidator.Status.Group, NominatedValidator>> { throw IllegalStateException() }
        val vm = viewModel()

        waitUntil { vm.errorDialogStateLiveData.value != null }
        val failure = requireNotNull(vm.errorDialogStateLiveData.value?.peekContent())
        assertEquals("s${jp.co.soramitsu.common.R.string.common_error_general_title}", failure.title)
        assertEquals("s${jp.co.soramitsu.common.R.string.common_error_general_message}", failure.message)
    }

    @Test
    fun changeFromTheInitialStateStartsCustomSelectionWithCurrentValidators() {
        sharedState.set(SetupStakingProcess.Initial(StakingType.RELAYCHAIN))
        val vm = viewModel()
        vm.awaitModels()

        vm.changeClicked()

        verify(timeout = 5_000) { router.openStartChangeValidators() }
        val state = sharedState.setupStakingProcess.value as SetupStakingProcess.ReadyToSubmit.Stash
        assertEquals(SelectionMethod.CUSTOM, state.payload.selectionMethod)
        assertEquals(listOf(active, elected, inactive, waiting), state.payload.blockProducers)
        assertTrue(state.payload is SetupStakingProcess.ReadyToSubmit.Payload.ExistingStash)
    }

    @Test
    fun changeFromTheValidatorsStepKeepsItsPayload() {
        sharedState.set(
            SetupStakingProcess.SelectBlockProducersStep.Validators(
                SetupStakingProcess.SelectBlockProducersStep.Payload.ExistingStash
            )
        )
        val vm = viewModel()
        vm.awaitModels()

        vm.changeClicked()

        verify(timeout = 5_000) { router.openStartChangeValidators() }
        val state = sharedState.setupStakingProcess.value as SetupStakingProcess.ReadyToSubmit.Stash
        assertEquals(SelectionMethod.CUSTOM, state.payload.selectionMethod)
        assertEquals(4, state.payload.blockProducers.size)
    }

    @Test
    fun changeFromAnyOtherStateDoesNothing() {
        val other = SetupStakingProcess.SetupStep.Stash(BigDecimal.ONE)
        sharedState.set(other)
        val vm = viewModel()
        vm.awaitModels()

        vm.changeClicked()

        verify(exactly = 0) { router.openStartChangeValidators() }
        assertSame(other, sharedState.setupStakingProcess.value)
    }

    @Test
    fun validatorInfoCachesTheDetailsAndOpensThem() {
        val vm = viewModel()
        vm.awaitModels()

        vm.validatorInfoClicked(inactive.address)

        verify(timeout = 5_000) { router.openValidatorDetails(inactive.accountIdHex) }
        val details = requireNotNull(detailsCache.value[inactive.accountIdHex])
        assertEquals(inactive.accountIdHex, details.accountIdHex)
    }

    @Test
    fun backGoesBack() {
        viewModel().backClicked()

        verify { router.back() }
    }
}
