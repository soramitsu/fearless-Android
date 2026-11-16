package jp.co.soramitsu.staking.impl.presentation.collators.change.custom.select

import io.mockk.slot
import io.mockk.verify
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.impl.domain.recommendations.settings.sortings.BlockProducersSorting.CollatorSorting
import jp.co.soramitsu.staking.impl.presentation.common.SetupStakingProcess.ReadyToSubmit
import jp.co.soramitsu.staking.impl.presentation.validators.LegacySelectionFixture
import jp.co.soramitsu.staking.impl.presentation.validators.parcel.CollatorDetailsParcelModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SelectCustomCollatorsViewModelTest : LegacySelectionFixture() {
    private fun model() = track(SelectCustomCollatorsViewModel(router, collatorFactory, settingsFactory, icons,
        resources, shared, tokens, settings))

    @Test
    fun selectingOneCollatorReplacesThePreviousChoiceAndPersistsIt(): Unit = runBlocking {
        shared.setupStakingProcess.value = collatorStep()
        val vm = model()
        val rows = (vm.collatorModelsFlow.await { it is LoadingState.Loaded } as LoadingState.Loaded).data
        assertEquals(listOf("Alice", "Bob"), rows.map { it.title })
        assertFalse(vm.buttonState.await().enabled)
        vm.collatorClicked(rows[0])
        assertTrue(vm.buttonState.await().enabled)
        vm.collatorClicked(rows[1])
        val selected = (vm.collatorModelsFlow.await {
            it is LoadingState.Loaded && it.data[1].isChecked == true
        } as LoadingState.Loaded).data
        assertEquals(listOf(false, true), selected.map { it.isChecked })
        vm.nextClicked()
        assertEquals(listOf(bob), shared.get<ReadyToSubmit.Parachain>().payload.blockProducers)
        assertEquals(ReadyToSubmit.SelectionMethod.CUSTOM, shared.get<ReadyToSubmit.Parachain>().payload.selectionMethod)
        verify { router.openConfirmStaking() }
        vm.deselectAll()
        assertFalse(vm.buttonState.await().enabled)
    }

    @Test
    fun informationAndNavigationCarryTheChosenCollator(): Unit = runBlocking {
        shared.setupStakingProcess.value = collatorStep()
        val vm = model()
        val row = (vm.collatorModelsFlow.await { it is LoadingState.Loaded } as LoadingState.Loaded).data.first()
        val details = slot<CollatorDetailsParcelModel>()
        vm.collatorInfoClicked(row)
        verify { router.openCollatorDetails(capture(details)) }
        assertEquals(alice.address, details.captured.accountIdHex)
        assertEquals("Alice", details.captured.identity?.display)
        vm.collatorClicked(row)
        vm.searchClicked()
        assertEquals(listOf(alice), shared.get<ReadyToSubmit.Parachain>().payload.blockProducers)
        vm.settingsClicked()
        vm.backClicked()
        verify { router.openSearchCustomCollators() }
        verify { router.openCustomValidatorsSettingsFromCollator() }
        verify { router.back() }
    }

    @Test
    fun sortingPublishesEverySupportedColumnAndFilterState(): Unit = runBlocking {
        shared.setupStakingProcess.value = collatorStep()
        val vm = model()
        assertEquals("s${R.string.staking_custom_header_collators_title}:2, 2", vm.selectedTitle.await())
        val sortings = listOf(
            CollatorSorting.APYSorting to R.string.staking_rewards_apy,
            CollatorSorting.CollatorsOwnStakeSorting to R.string.collator_staking_sorting_own_stake,
            CollatorSorting.DelegationsSorting to R.string.collator_staking_sorting_delegations,
            CollatorSorting.EffectiveAmountBondedSorting to R.string.collator_staking_sorting_effective_amount_bonded,
            CollatorSorting.MinimumBondSorting to R.string.collator_staking_sorting_minimum_bond
        )
        for ((sorting, title) in sortings) {
            collatorSettings.setCustomSettings(emptyList(), sorting)
            assertEquals("s$title", vm.scoringHeader.await { it == "s$title" })
        }
        assertFalse(vm.clearFiltersEnabled.await { !it })
        vm.havingOnChainIdentityFilterClicked()
        vm.relevantBondFilterCLicked()
        assertTrue(settings.schema.await().filters.any { it.checked })
        vm.clearFilters()
        assertTrue(settings.schema.await().filters.any { it.checked })
    }

    @Test
    fun readyStateRestoresThePreviouslySelectedCollator(): Unit = runBlocking {
        shared.setupStakingProcess.value = collatorStep().next(listOf(bob), ReadyToSubmit.SelectionMethod.CUSTOM)
        val vm = model()
        val rows = (vm.collatorModelsFlow.await {
            it is LoadingState.Loaded && it.data.any { row -> row.isChecked == true }
        } as LoadingState.Loaded).data
        assertEquals(bob.address, rows.single { it.isChecked == true }.accountIdHex)
        assertTrue(vm.buttonState.await().enabled)
    }
}
