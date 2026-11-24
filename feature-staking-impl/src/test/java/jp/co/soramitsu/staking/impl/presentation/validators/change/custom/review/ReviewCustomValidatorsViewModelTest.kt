package jp.co.soramitsu.staking.impl.presentation.validators.change.custom.review

import io.mockk.coEvery
import io.mockk.verify
import jp.co.soramitsu.common.view.ButtonState
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
class ReviewCustomValidatorsViewModelTest : LegacySelectionFixture() {
    private fun model() = track(ReviewCustomValidatorsViewModel(router, icons, interactor, relay, resources, shared, tokens))

    @Test
    fun editModeAndOverflowDisableContinue(): Unit = runBlocking {
        shared.setupStakingProcess.value = ready(listOf(alpha, beta))
        coEvery { relay.maxValidatorsPerNominator() } returns 1
        val vm = model()
        val overflow = vm.selectionStateFlow.await()
        assertTrue(overflow.isOverflow)
        assertEquals(ButtonState.DISABLED, overflow.buttonState)
        shared.setupStakingProcess.value = ready()
        assertFalse(vm.selectionStateFlow.await { !it.isOverflow }.isOverflow)
        vm.isInEditMode.value = true
        assertEquals(ButtonState.DISABLED, vm.selectionStateFlow.await().buttonState)
        vm.isInEditMode.value = false
        assertEquals(ButtonState.NORMAL, vm.selectionStateFlow.await().buttonState)
    }

    @Test
    fun deletingRowsUpdatesTheSelectionAndReturnsWhenEmpty(): Unit = runBlocking {
        shared.setupStakingProcess.value = ready(listOf(alpha, beta))
        val vm = model()
        val rows = vm.selectedValidatorModels.await { it.size == 2 }
        assertEquals(listOf("Alpha", "Beta"), rows.map { it.title })
        vm.validatorInfoClicked(rows[0])
        assertEquals(setOf(alpha.accountIdHex), cache.value.keys)
        vm.deleteClicked(rows[0])
        vm.selectedValidatorModels.await { it.size == 1 }
        assertEquals(listOf(beta), shared.get<ReadyToSubmit.Stash>().payload.blockProducers)
        verify(exactly = 0) { router.back() }
        vm.deleteClicked(rows[1])
        shared.setupStakingProcess.await { (it as? ReadyToSubmit.Stash)?.payload?.blockProducers?.isEmpty() == true }
        verify { router.back() }
    }

    @Test
    fun continueKeepsTheReviewedSelectionAndNavigates(): Unit = runBlocking {
        shared.setupStakingProcess.value = ready()
        val vm = model()
        vm.selectedValidatorModels.await { it.size == 1 }
        vm.nextClicked()
        vm.backClicked()
        assertEquals(listOf(alpha), shared.get<ReadyToSubmit.Stash>().payload.blockProducers)
        verify { router.openConfirmStaking() }
        verify { router.back() }
    }
}
