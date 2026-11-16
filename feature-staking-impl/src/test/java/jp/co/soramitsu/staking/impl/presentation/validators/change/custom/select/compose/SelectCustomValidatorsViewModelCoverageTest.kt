package jp.co.soramitsu.staking.impl.presentation.validators.change.custom.select.compose

import androidx.lifecycle.SavedStateHandle
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.math.BigDecimal
import jp.co.soramitsu.common.AlertViewState
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.api.domain.model.RewardDestination
import jp.co.soramitsu.staking.api.domain.model.RootIdentity
import jp.co.soramitsu.staking.api.domain.model.Validator
import jp.co.soramitsu.staking.api.domain.model.ValidatorPrefs
import jp.co.soramitsu.staking.impl.StakingLaneFixtures
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.domain.recommendations.ValidatorRecommendator
import jp.co.soramitsu.staking.impl.domain.recommendations.ValidatorRecommendatorFactory
import jp.co.soramitsu.staking.impl.domain.recommendations.settings.RecommendationSettingsProvider
import jp.co.soramitsu.staking.impl.domain.recommendations.settings.RecommendationSettingsProviderFactory
import jp.co.soramitsu.staking.impl.domain.recommendations.settings.SettingsStorage
import jp.co.soramitsu.staking.impl.domain.recommendations.settings.filters.Filters
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.SelectValidatorFlowState.ValidatorSelectMode
import jp.co.soramitsu.staking.impl.presentation.common.SetupStakingProcess
import jp.co.soramitsu.staking.impl.presentation.common.SetupStakingProcess.ReadyToSubmit
import jp.co.soramitsu.staking.impl.presentation.common.SetupStakingProcess.ReadyToSubmit.SelectionMethod
import jp.co.soramitsu.staking.impl.presentation.common.SetupStakingSharedState
import jp.co.soramitsu.staking.impl.presentation.pools.compose.SelectableListItemState
import jp.co.soramitsu.staking.impl.presentation.validators.compose.ValidatorsListViewState
import jp.co.soramitsu.staking.impl.presentation.validators.parcel.ValidatorDetailsParcelModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SelectCustomValidatorsViewModelCoverageTest {

    private val router = mockk<StakingRouter>(relaxed = true)
    private val recommendatorFactory = mockk<ValidatorRecommendatorFactory>(relaxed = true)
    private val settingsFactory = mockk<RecommendationSettingsProviderFactory>(relaxed = true)
    private val resources = mockk<ResourceManager>(relaxed = true)
    private val settingsStorage = SettingsStorage()
    private val shared = SetupStakingSharedState()
    private val interactor = mockk<StakingInteractor>(relaxed = true)
    private val detailsCache = MutableStateFlow<Map<String, ValidatorDetailsParcelModel>>(emptyMap())
    private val amount = BigDecimal("12")

    private val alpha = elected("01", "Alpha", BigDecimal("0.15"))
    private val beta = elected("02", null, BigDecimal("0.10"))
    private val gamma = Validator("gamma-address", false, "03".repeat(32), ValidatorPrefs(BigDecimal("0.1"), false), null,
        RootIdentity("Gamma", null, null, null, null, null, null, null))

    private fun elected(byte: String, name: String?, apy: BigDecimal) = Validator(
        address = "$byte-address",
        slashed = false,
        accountIdHex = byte.repeat(32),
        prefs = ValidatorPrefs(BigDecimal("0.05"), false),
        electedInfo = Validator.ElectedInfo(
            totalStake = StakingLaneFixtures.planks(100),
            ownStake = StakingLaneFixtures.planks(10),
            nominatorStakes = emptyList(),
            apy = apy,
            isOversubscribed = false
        ),
        identity = name?.let { RootIdentity(it, null, null, null, null, null, null, null) }
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { resources.getString(any()) } answers { "s${firstArg<Int>()}" }
        every { resources.getString(any(), *anyVararg()) } answers { "f${firstArg<Int>()}" }
        every { interactor.currentAssetFlow() } returns flowOf(StakingLaneFixtures.asset())
        every { interactor.validatorDetailsCache } returns detailsCache
        coEvery { settingsFactory.createRelayChain(any()) } returns RecommendationSettingsProvider.RelayChain(256, 16)
        coEvery { recommendatorFactory.create(any()) } returns ValidatorRecommendator(listOf(alpha, beta, gamma))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(10)
        assertTrue("condition not reached", condition())
    }

    private fun fullReady(method: SelectionMethod = SelectionMethod.CUSTOM, validators: List<Validator> = listOf(alpha)) =
        ReadyToSubmit.Stash(ReadyToSubmit.Payload.Full(amount, RewardDestination.Restake, "me", validators, method))

    private fun viewModel(mode: ValidatorSelectMode, state: SetupStakingProcess = fullReady()): SelectCustomValidatorsViewModel {
        shared.setupStakingProcess.value = state
        return SelectCustomValidatorsViewModel(
            router, recommendatorFactory, settingsFactory, resources, settingsStorage, shared, interactor,
            SavedStateHandle(mapOf(SelectCustomValidatorsFragment.KEY_SELECTION_MODE to mode))
        )
    }

    private fun SelectCustomValidatorsViewModel.loadedList(): ValidatorsListViewState {
        waitUntil { state.value.listState is LoadingState.Loaded }
        return (state.value.listState as LoadingState.Loaded).data
    }

    private fun SelectCustomValidatorsViewModel.awaitSelected(count: Int): ValidatorsListViewState {
        waitUntil { (state.value.listState as? LoadingState.Loaded)?.data?.selectedItems?.size == count }
        return loadedList()
    }

    @Test
    fun customModeLoadsSegmentsWithPreviousSelection() {
        val vm = viewModel(ValidatorSelectMode.CUSTOM)

        val list = vm.awaitSelected(1)

        assertEquals("s${R.string.staking_select_custom}", vm.state.value.toolbarTitle)
        assertTrue(vm.state.value.isCustom)
        assertEquals(listOf(alpha.accountIdHex, beta.accountIdHex), list.segments[0].items.map { it.id })
        assertEquals(listOf(gamma.accountIdHex), list.segments[1].items.map { it.id })
        assertEquals(listOf(alpha.accountIdHex), list.selectedItems.map { it.id })
        assertEquals(setOf(Filters.HavingOnChainIdentity, Filters.NotSlashedFilter, Filters.NotOverSubscribed), settingsStorage.currentFiltersSet.value)
    }

    @Test
    fun selectingTogglesItems() {
        val vm = viewModel(ValidatorSelectMode.CUSTOM)
        val list = vm.awaitSelected(1)
        val alphaItem = list.segments[0].items[0]
        val betaItem = list.segments[0].items[1]

        vm.onSelected(betaItem)
        assertEquals(setOf(alpha.accountIdHex, beta.accountIdHex), vm.awaitSelected(2).selectedItems.map { it.id }.toSet())

        vm.onSelected(alphaItem)
        assertEquals(listOf(beta.accountIdHex), vm.awaitSelected(1).selectedItems.map { it.id })
    }

    @Test
    fun selectingOversubscribedItemShowsAlert() {
        val vm = viewModel(ValidatorSelectMode.CUSTOM)
        vm.awaitSelected(1)
        val alert = slot<AlertViewState>()
        every { router.openAlert(capture(alert)) } returns Unit

        vm.onSelected(
            SelectableListItemState(
                id = gamma.accountIdHex,
                title = "Gamma",
                isSelected = false,
                additionalStatuses = listOf(SelectableListItemState.SelectableListItemAdditionalStatus.OVERSUBSCRIBED)
            )
        )

        assertEquals("s${R.string.alert_oversubscribed_alert_title}", alert.captured.title)
        assertEquals("s${R.string.alert_oversubscribed_alert_message}", alert.captured.message)
        vm.awaitSelected(2)
    }

    @Test
    fun chooseInCustomModeStoresSelectionAndOpensReview() {
        val vm = viewModel(ValidatorSelectMode.CUSTOM)
        vm.awaitSelected(1)

        vm.onChooseClick()

        val ready = shared.setupStakingProcess.value as ReadyToSubmit.Stash
        assertEquals(listOf(alpha), ready.payload.blockProducers)
        assertEquals(SelectionMethod.CUSTOM, ready.payload.selectionMethod)
        verify { router.openReviewCustomValidators() }
    }

    @Test
    fun chooseFromSelectionStepCreatesReadyState() {
        val step = SetupStakingProcess.SelectBlockProducersStep.Validators(
            SetupStakingProcess.SelectBlockProducersStep.Payload.Full(amount, RewardDestination.Restake, "me")
        )
        val vm = viewModel(ValidatorSelectMode.CUSTOM, step)
        vm.loadedList()

        vm.onChooseClick()

        val ready = shared.setupStakingProcess.value as ReadyToSubmit.Stash
        assertTrue(ready.payload.blockProducers.isEmpty())
        verify { router.openReviewCustomValidators() }
    }

    @Test
    fun infoClickCachesDetailsAndOpensThem() {
        val vm = viewModel(ValidatorSelectMode.CUSTOM)
        val alphaItem = vm.awaitSelected(1).segments[0].items[0]

        vm.onInfoClick(alphaItem)
        vm.onInfoClick(alphaItem.copy(id = "ff".repeat(32)))

        assertEquals(setOf(alpha.accountIdHex), detailsCache.value.keys)
        verify(exactly = 1) { router.openValidatorDetails(any()) }
        verify { router.openValidatorDetails(alpha.accountIdHex) }
    }

    @Test
    fun searchFiltersListAndOptionsOpenSettings() {
        val vm = viewModel(ValidatorSelectMode.CUSTOM)
        vm.awaitSelected(1)

        vm.onSearchQueryInput("gam")
        vm.onOptionsClick()

        waitUntil { (vm.state.value.listState as? LoadingState.Loaded)?.data?.segments?.size == 1 }
        assertEquals("gam", vm.state.value.searchQuery)
        assertEquals(listOf(gamma.accountIdHex), vm.loadedList().segments.single().items.map { it.id })
        verify { router.openValidatorsSettings() }
    }

    @Test
    fun customNavigationOnlyGoesBack() {
        val vm = viewModel(ValidatorSelectMode.CUSTOM)

        vm.onNavigationClick()

        verify { router.back() }
        verify(exactly = 0) { router.openConfirmStaking() }
        assertTrue(shared.setupStakingProcess.value is ReadyToSubmit.Stash)
    }

    @Test
    fun recommendedModeIgnoresSelectionAndConfirmsRecommendations() {
        val vm = viewModel(ValidatorSelectMode.RECOMMENDED)
        val before = vm.loadedList()

        vm.onSelected(before.segments[0].items[0])
        vm.onChooseClick()

        assertEquals("s${R.string.staking_select_suggested}", vm.state.value.toolbarTitle)
        assertTrue(!vm.state.value.isCustom)
        val shown = before.segments.flatMap { it.items }
        assertTrue(shown.isNotEmpty() && shown.all { it.isSelected })
        assertEquals(shown.size, before.selectedItems.size)
        val ready = shared.setupStakingProcess.value as ReadyToSubmit.Stash
        assertEquals(SelectionMethod.RECOMMENDED, ready.payload.selectionMethod)
        verify { router.openConfirmStaking() }
    }

    @Test
    fun recommendedNavigationRetractsRecommendedSelection() {
        val vm = viewModel(ValidatorSelectMode.RECOMMENDED, fullReady(SelectionMethod.RECOMMENDED))

        vm.onNavigationClick()

        assertTrue(shared.setupStakingProcess.value is SetupStakingProcess.SelectBlockProducersStep.Validators)
        verify { router.openConfirmStaking() }
        verify { router.back() }
    }
}
