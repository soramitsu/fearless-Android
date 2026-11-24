package jp.co.soramitsu.staking.impl.presentation.staking.controller.confirm

import android.view.View
import androidx.lifecycle.MutableLiveData
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.impl.presentation.HiltFragmentHarness
import jp.co.soramitsu.staking.impl.presentation.StakingFragmentFixture
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfirmSetControllerFragmentTest : StakingFragmentFixture() {
    @Test
    fun displaysBothAddressesAndForwardsConfirmationAndAccountActions() {
        val vm = mockk<ConfirmSetControllerViewModel>(relaxed = true)
        every { vm.feeStatusLiveData } returns MutableLiveData(loadedFee())
        every { vm.stashAddressLiveData } returns MutableLiveData(address("Stash wallet"))
        every { vm.controllerAddressLiveData } returns MutableLiveData(address("Controller wallet"))
        val payload = ConfirmSetControllerPayload(BigDecimal.ONE, "stash", "controller", BigDecimal.TEN)
        assertSame(payload, ConfirmSetControllerFragment.getBundle(payload).getParcelable<ConfirmSetControllerPayload>(PAYLOAD_KEY))
        val fragment = ConfirmSetControllerFragment()
        HiltFragmentHarness.launch(fragment, vm)
        val root = fragment.requireView()
        assertTrue(texts(root).containsAll(listOf("Stash wallet", "Controller wallet")))
        root.findViewById<View>(R.id.confirmSetControllerStashAccount).performClick()
        root.findViewById<View>(R.id.confirmSetControllerDestinationAccount).performClick()
        root.findViewById<View>(R.id.confirmSetControllerConfirm).performClick()
        verify { vm.openStashExternalActions() }
        verify { vm.openControllerExternalActions() }
        verify { vm.confirmClicked() }
    }
}
