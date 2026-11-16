package jp.co.soramitsu.staking.impl.presentation.validators.change.recommended

import io.mockk.verify
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.impl.presentation.common.SetupStakingProcess
import jp.co.soramitsu.staking.impl.presentation.common.SetupStakingProcess.ReadyToSubmit
import jp.co.soramitsu.staking.impl.presentation.validators.LegacySelectionFixture
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RecommendedValidatorsViewModelTest : LegacySelectionFixture() {
    private fun model() = track(RecommendedValidatorsViewModel(
        router, validatorFactory, settingsFactory, icons, interactor, relay, resources, shared, tokens))

    @Test
    fun recommendationsAreRenderedCachedAndCommittedWithTheirSelectionMethod(): Unit = runBlocking {
        shared.setupStakingProcess.value = ready(emptyList())
        val vm = model()
        val rows = vm.recommendedValidatorModels.await { it.size == 2 }
        assertEquals(listOf("Alpha", "Beta"), rows.map { it.title })
        assertEquals("s${R.string.staking_custom_header_validators_title}:2, 2", vm.selectedTitle.await())
        vm.validatorInfoClicked(rows.first())
        assertEquals(setOf(alpha.accountIdHex), cache.value.keys)
        vm.nextClicked()
        shared.setupStakingProcess.await { (it as? ReadyToSubmit.Stash)?.payload?.selectionMethod == ReadyToSubmit.SelectionMethod.RECOMMENDED }
        assertEquals(listOf(alpha, beta), shared.get<ReadyToSubmit.Stash>().payload.blockProducers)
        verify { router.openConfirmStaking() }
        verify { router.openValidatorDetails(alpha.accountIdHex) }
        vm.backClicked()
        assertTrue(shared.setupStakingProcess.value is SetupStakingProcess.SelectBlockProducersStep.Validators)
        verify { router.back() }
    }

    @Test
    fun backPreservesAnExistingCustomSelection() {
        val original = ready()
        shared.setupStakingProcess.value = original
        model().backClicked()
        assertSame(original, shared.setupStakingProcess.value)
        verify { router.back() }
    }
}
