package jp.co.soramitsu.staking.impl.presentation.validators.change.start

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.verify
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.api.data.StakingType
import jp.co.soramitsu.staking.impl.presentation.common.SetupStakingProcess
import jp.co.soramitsu.staking.impl.presentation.validators.LegacySelectionFixture
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StartChangeCollatorsViewModelCoverageTest : LegacySelectionFixture() {

    @Before
    fun stubCollatorLoading() {
        coEvery { collatorFactory.awaitBlockCreatorsLoading(any()) } returns Unit
    }

    private fun model() = track(
        StartChangeCollatorsViewModel(
            router = router,
            collatorRecommendatorFactory = collatorFactory,
            setupStakingSharedState = shared,
            resourceManager = resources,
            stakingParachainScenarioInteractor = parachain,
            stakingInteractor = interactor,
            recommendationSettingsProviderFactory = settingsFactory
        )
    )

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(10)
        assertTrue("condition not reached", condition())
    }

    @Test
    fun aCollatorStepOffersCustomSelectionWithTheCollatorCount(): Unit = runBlocking {
        shared.setupStakingProcess.value = collatorStep()
        val vm = model()

        val texts = vm.customCollatorsTexts.await()

        assertEquals("s${R.string.staking_select_custom}", texts.title)
        assertEquals("2", texts.badge)
    }

    @Test
    fun aReadyParachainSelectionAlsoOffersCustomSelection(): Unit = runBlocking {
        shared.setupStakingProcess.value = collatorStep().next(listOf(alice), SetupStakingProcess.ReadyToSubmit.SelectionMethod.CUSTOM)
        val vm = model()

        val texts = vm.customCollatorsTexts.await()

        assertEquals("s${R.string.staking_select_custom}", texts.title)
        assertEquals("2", texts.badge)
    }

    @Test
    fun aReadyRelaySelectionOffersToUpdateTheList(): Unit = runBlocking {
        shared.setupStakingProcess.value = ready(listOf(alpha))
        val vm = model()

        val texts = vm.customCollatorsTexts.await()

        assertEquals("s${R.string.staking_custom_validators_update_list}", texts.title)
        assertEquals("s${R.string.staking_max_format}:1, 2", texts.badge)
        coVerify { parachain.maxDelegationsPerDelegator() }
    }

    @Test
    fun recommendedCollatorsOpenWhenTheAmountHasSuggestions() {
        shared.setupStakingProcess.value = collatorStep()
        val vm = model()
        waitUntil { !vm.collatorsLoading.value }

        vm.goToRecommendedClicked()

        verify(timeout = 5_000) { router.openRecommendedCollators() }
        assertNull(vm.errorLiveData.value)
    }

    @Test
    fun withoutAnAmountThereAreNoSuggestionsAndAnErrorIsShown() {
        shared.setupStakingProcess.value = SetupStakingProcess.Initial(StakingType.PARACHAIN)
        val vm = model()
        waitUntil { !vm.collatorsLoading.value }

        vm.goToRecommendedClicked()

        waitUntil { vm.errorLiveData.value != null }
        assertEquals("s${R.string.staking_recommended_collators_empty_text}", vm.errorLiveData.value?.peekContent())
        verify(exactly = 0) { router.openRecommendedCollators() }
    }

    @Test
    fun customBackAndFeatureIdsAreWired() {
        val vm = model()

        vm.goToCustomClicked()
        vm.backClicked()

        verify { router.openSelectCustomCollators() }
        verify { router.back() }
        assertEquals(
            listOf(R.string.staking_recommended_feature_1, R.string.staking_recommended_feature_2),
            vm.getRecommendedFeaturesIds()
        )
    }
}
