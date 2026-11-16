package jp.co.soramitsu.staking.impl.presentation.validators.change.recommended

import io.mockk.slot
import io.mockk.verify
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.impl.presentation.common.SetupStakingProcess.ReadyToSubmit
import jp.co.soramitsu.staking.impl.presentation.validators.LegacySelectionFixture
import jp.co.soramitsu.staking.impl.presentation.validators.parcel.CollatorDetailsParcelModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RecommendedCollatorsViewModelTest : LegacySelectionFixture() {
    private fun model() = track(RecommendedCollatorsViewModel(router, collatorFactory, settingsFactory, icons,
        interactor, parachain, resources, shared, tokens))

    @Test
    fun suggestionsUseTheEnteredAmountAndSaveOnlyTheChosenCollator(): Unit = runBlocking {
        shared.setupStakingProcess.value = collatorStep()
        val vm = model()
        val rows = (vm.recommendedCollatorModels.await { it is LoadingState.Loaded } as LoadingState.Loaded).data
        assertEquals(listOf("Alice", "Bob"), rows.map { it.title })
        assertEquals("s${R.string.staking_suggested_collators_title}", vm.toolbarTitle)
        assertEquals("s${R.string.staking_custom_header_validators_title}:2, 2", vm.selectedTitle.await())
        vm.collatorClicked(rows[1])
        val selected = (vm.recommendedCollatorModels.await {
            it is LoadingState.Loaded && it.data.any { row -> row.isChecked == true }
        } as LoadingState.Loaded).data
        assertEquals(bob.address, selected.single { it.isChecked == true }.address)
        vm.nextClicked()
        assertEquals(listOf(bob), shared.get<ReadyToSubmit.Parachain>().payload.blockProducers)
        assertEquals(ReadyToSubmit.SelectionMethod.RECOMMENDED, shared.get<ReadyToSubmit.Parachain>().payload.selectionMethod)
        verify { router.openConfirmStaking() }
    }

    @Test
    fun detailsIncludeIdentityAndBackPreservesTheSetupStep(): Unit = runBlocking {
        val step = collatorStep()
        shared.setupStakingProcess.value = step
        val vm = model()
        val rows = (vm.recommendedCollatorModels.await { it is LoadingState.Loaded } as LoadingState.Loaded).data
        val details = slot<CollatorDetailsParcelModel>()
        vm.collatorInfoClicked(rows[0])
        verify { router.openCollatorDetails(capture(details)) }
        assertEquals(alice.address, details.captured.accountIdHex)
        assertEquals("Alice", details.captured.identity?.display)
        vm.backClicked()
        assertTrue(shared.setupStakingProcess.value === step)
        verify { router.back() }
    }
}
