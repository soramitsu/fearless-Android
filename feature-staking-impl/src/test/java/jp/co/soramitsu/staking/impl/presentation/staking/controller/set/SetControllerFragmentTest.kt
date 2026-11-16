package jp.co.soramitsu.staking.impl.presentation.staking.controller.set

import android.view.View
import androidx.lifecycle.MutableLiveData
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.impl.presentation.HiltFragmentHarness
import jp.co.soramitsu.staking.impl.presentation.StakingFragmentFixture
import jp.co.soramitsu.wallet.api.presentation.mixin.fee.FeeStatus
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetControllerFragmentTest : StakingFragmentFixture() {
    @Test
    fun rendersAccountChoicesAndUpdatesEligibilityAndWarnings() {
        val vm = mockk<SetControllerViewModel>(relaxed = true)
        val enabled = MutableLiveData(true)
        val warning = MutableLiveData(false)
        every { vm.stashAccountModel } returns MutableLiveData(address("Stash wallet"))
        every { vm.controllerAccountModel } returns MutableLiveData(address("Controller wallet"))
        every { vm.feeLiveData } returns MutableLiveData(FeeStatus.Error)
        every { vm.isContinueButtonAvailable } returns enabled
        every { vm.isControllerSelectionEnabled } returns MutableStateFlow(true)
        every { vm.showNotStashAccountWarning } returns warning
        val fragment = SetControllerFragment()
        HiltFragmentHarness.launch(fragment, vm)
        val root = fragment.requireView()
        assertTrue(texts(root).containsAll(listOf("Stash wallet", "Controller wallet")))
        root.findViewById<View>(R.id.setControllerStashAccount).performClick()
        root.findViewById<View>(R.id.setControllerDestinationAccount).performClick()
        root.findViewById<View>(R.id.setControllerContinueBtn).performClick()
        root.findViewById<View>(R.id.setControllerLearnMore).performClick()
        verify { vm.openExternalActions() }
        verify { vm.openAccounts() }
        verify { vm.continueClicked() }
        verify { vm.onMoreClicked() }
        enabled.value = false
        warning.value = true
        assertFalse(root.findViewById<View>(R.id.setControllerContinueBtn).isEnabled)
        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.setControllerNotStashWarning).visibility)
    }
}
