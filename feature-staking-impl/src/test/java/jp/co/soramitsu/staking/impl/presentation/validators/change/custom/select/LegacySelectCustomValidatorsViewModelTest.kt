package jp.co.soramitsu.staking.impl.presentation.validators.change.custom.select

import io.mockk.verify
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.impl.domain.recommendations.settings.filters.BlockProducerFilters
import jp.co.soramitsu.staking.impl.domain.recommendations.settings.sortings.BlockProducersSorting.ValidatorSorting
import jp.co.soramitsu.staking.impl.presentation.common.SetupStakingProcess.ReadyToSubmit
import jp.co.soramitsu.staking.impl.presentation.validators.LegacySelectionFixture
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LegacySelectCustomValidatorsViewModelTest : LegacySelectionFixture() {
    private fun model() = track(SelectCustomValidatorsViewModel(
        router, validatorFactory, settingsFactory, icons, interactor, relay, resources, shared, tokens, settings))

    @Test
    fun restoresSelectionAndTogglesRowsWithoutDiscardingOtherValidators(): Unit = runBlocking {
        shared.setupStakingProcess.value = ready()
        val vm = model()
        val rows = vm.validatorModelsFlow.await { it.size == 2 && it[0].isChecked == true }
        assertEquals(listOf("Alpha", "Beta"), rows.map { it.title })
        assertTrue(vm.buttonState.await().enabled)
        assertEquals("s${R.string.staking_custom_header_validators_title}:2, 2", vm.selectedTitle.await())
        vm.validatorClicked(rows[1])
        assertTrue(vm.validatorModelsFlow.await { it.all { row -> row.isChecked == true } }.all { it.isChecked == true })
        assertFalse(vm.fillWithRecommendedEnabled.await { !it })
        vm.validatorClicked(rows[0])
        assertEquals(listOf(false, true), vm.validatorModelsFlow.await { it[0].isChecked == false }.map { it.isChecked })
        vm.deselectAll()
        assertFalse(vm.buttonState.await { !it.enabled }.enabled)
        assertFalse(vm.deselectAllEnabled.await { !it })
    }

    @Test
    fun fillsRecommendationsAndPersistsThemBeforeReviewSearchAndBack(): Unit = runBlocking {
        shared.setupStakingProcess.value = ready(emptyList())
        val vm = model()
        assertFalse(vm.buttonState.await().enabled)
        vm.fillRestWithRecommended()
        vm.validatorModelsFlow.await { it.count { row -> row.isChecked == true } == 2 }
        vm.nextClicked()
        assertEquals(listOf(alpha, beta), shared.get<ReadyToSubmit.Stash>().payload.blockProducers)
        assertEquals(ReadyToSubmit.SelectionMethod.CUSTOM, shared.get<ReadyToSubmit.Stash>().payload.selectionMethod)
        vm.searchClicked()
        vm.settingsClicked()
        vm.backClicked()
        verify { router.openReviewCustomValidators() }
        verify { router.openSearchCustomValidators() }
        verify { router.openCustomValidatorsSettingsFromValidator() }
        verify { router.back() }
    }

    @Test
    fun sortingChangesHeadersAndInformationCachesTheSelectedValidator(): Unit = runBlocking {
        shared.setupStakingProcess.value = ready()
        val vm = model()
        val row = vm.validatorModelsFlow.await { it.isNotEmpty() }.first()
        vm.validatorInfoClicked(row)
        assertEquals(setOf(alpha.accountIdHex), cache.value.keys)
        verify { router.openValidatorDetails(alpha.accountIdHex) }
        val sortings = listOf(
            ValidatorSorting.APYSorting to R.string.staking_rewards_apy,
            ValidatorSorting.TotalStakeSorting to R.string.staking_validator_total_stake,
            ValidatorSorting.ValidatorOwnStakeSorting to R.string.staking_filter_title_own_stake
        )
        for ((sorting, title) in sortings) {
            validatorSettings.setCustomSettings(listOf(BlockProducerFilters.ValidatorFilter.HasIdentity), sorting)
            assertEquals("s$title", vm.scoringHeader.await { it == "s$title" })
            assertTrue(vm.clearFiltersEnabled.await { it })
        }
        validatorSettings.setCustomSettings(emptyList(), ValidatorSorting.APYSorting)
        assertFalse(vm.clearFiltersEnabled.await { !it })
    }
}
