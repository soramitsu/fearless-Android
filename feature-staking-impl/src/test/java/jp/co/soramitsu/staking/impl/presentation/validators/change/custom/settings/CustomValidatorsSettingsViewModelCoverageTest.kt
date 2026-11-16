package jp.co.soramitsu.staking.impl.presentation.validators.change.custom.settings

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.math.BigDecimal
import java.math.BigInteger
import java.util.concurrent.CopyOnWriteArrayList
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.staking.api.domain.model.RewardDestination
import jp.co.soramitsu.staking.api.domain.model.Validator
import jp.co.soramitsu.staking.impl.StakingLaneFixtures
import jp.co.soramitsu.staking.impl.domain.recommendations.settings.RecommendationSettingsProvider
import jp.co.soramitsu.staking.impl.domain.recommendations.settings.RecommendationSettingsProviderFactory
import jp.co.soramitsu.staking.impl.domain.recommendations.settings.SettingsStorage
import jp.co.soramitsu.staking.impl.domain.recommendations.settings.filters.Filters
import jp.co.soramitsu.staking.impl.domain.recommendations.settings.filters.Sorting
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.SetupStakingProcess
import jp.co.soramitsu.staking.impl.presentation.common.SetupStakingSharedState
import jp.co.soramitsu.wallet.impl.domain.TokenUseCase
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CustomValidatorsSettingsViewModelCoverageTest {

    @get:Rule
    val instant = InstantTaskExecutorRule()

    private val owned = mutableListOf<CustomValidatorsSettingsViewModel>()

    private val router = mockk<StakingRouter>(relaxed = true)
    private val factory = mockk<RecommendationSettingsProviderFactory>(relaxed = true)
    private val provider = mockk<RecommendationSettingsProvider<Validator>>(relaxed = true)
    private val tokens = mockk<TokenUseCase>(relaxed = true)
    private val storage = SettingsStorage()
    private val shared = SetupStakingSharedState()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        storage.currentFiltersSet.value = setOf(Filters.HavingOnChainIdentity, Filters.NotSlashedFilter, Filters.NotOverSubscribed)
        storage.currentSortingSet.value = setOf(Sorting.EstimatedRewards, Sorting.TotalStake, Sorting.ValidatorsOwnStake)
        storage.quickFilters = setOf(Filters.NotSlashedFilter)
        coEvery { factory.create(any(), any()) } returns provider
        coEvery { tokens.currentToken() } returns StakingLaneFixtures.token()
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

    private fun viewModel() = CustomValidatorsSettingsViewModel(
        router = router,
        recommendationSettingsProviderFactory = factory,
        tokenUseCase = tokens,
        settingsStorage = storage,
        setupStakingSharedState = shared,
        savedStateHandle = SavedStateHandle(
            mapOf(CustomValidatorsSettingsFragment.STAKING_TYPE_KEY to Asset.StakingType.RELAYCHAIN)
        )
    ).also(owned::add)

    @Test
    fun schemaHidesQuickFiltersAndShowsTheDefaults() {
        val vm = viewModel()

        waitUntil { vm.settingsSchemaLiveData.value != null }
        val schema = requireNotNull(vm.settingsSchemaLiveData.value)
        assertEquals(listOf(Filters.HavingOnChainIdentity, Filters.NotOverSubscribed), schema.filters.map { it.filter })
        assertTrue(schema.filters.all { it.checked })
        assertEquals(listOf(Sorting.EstimatedRewards), schema.sortings.filter { it.checked }.map { it.sorting })
    }

    @Test
    fun checkingAFilterTogglesIt() {
        val vm = viewModel()
        waitUntil { vm.settingsSchemaLiveData.value != null }
        val identity = requireNotNull(vm.settingsSchemaLiveData.value).filters.first()

        vm.onFilterChecked(identity)

        waitUntil { vm.settingsSchemaLiveData.value?.filters?.first()?.checked == false }
        assertEquals(Filters.HavingOnChainIdentity, vm.settingsSchemaLiveData.value?.filters?.first()?.filter)
    }

    @Test
    fun applyAndResetFollowChangesFromTheInitialSettings() {
        val vm = viewModel()
        val applyStates = CopyOnWriteArrayList<Boolean>()
        val resetStates = CopyOnWriteArrayList<Boolean>()
        vm.viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) { vm.isApplyButtonEnabled.collect { applyStates += it } }
        vm.viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) { vm.isResetButtonEnabled.collect { resetStates += it } }
        waitUntil { applyStates.isNotEmpty() && resetStates.isNotEmpty() }
        assertFalse(applyStates.last())

        vm.onSortingChecked(SettingsSchema.Sorting(0, false, Sorting.TotalStake))

        waitUntil { applyStates.last() && resetStates.last() }

        vm.reset()

        waitUntil { !applyStates.last() && !resetStates.last() }
    }

    @Test
    fun applyPassesTheSchemaAndTheEnteredAmount() {
        shared.setupStakingProcess.value = SetupStakingProcess.SelectBlockProducersStep.Validators(
            SetupStakingProcess.SelectBlockProducersStep.Payload.Full(BigDecimal.TEN, RewardDestination.Restake, "controller")
        )
        val vm = viewModel()
        vm.onSortingChecked(SettingsSchema.Sorting(0, false, Sorting.TotalStake))
        val schema = slot<SettingsSchema>()

        vm.applyChanges()

        verify(timeout = 5_000) { router.back() }
        verify { provider.settingsChanged(capture(schema), StakingLaneFixtures.planks(10)) }
        assertEquals(listOf(Sorting.TotalStake), schema.captured.sortings.filter { it.checked }.map { it.sorting })
    }

    @Test
    fun applyWithoutAnAmountUsesZero() {
        val vm = viewModel()

        vm.applyChanges()

        verify(timeout = 5_000) { router.back() }
        verify { provider.settingsChanged(any(), BigInteger.ZERO) }
    }

    @Test
    fun backGoesBack() {
        viewModel().backClicked()

        verify { router.back() }
    }
}
