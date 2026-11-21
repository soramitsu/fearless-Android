package jp.co.soramitsu.staking.impl.presentation.payouts.confirm

import android.view.View
import androidx.lifecycle.MutableLiveData
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigInteger
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.impl.presentation.HiltFragmentHarness
import jp.co.soramitsu.staking.impl.presentation.StakingFragmentFixture
import jp.co.soramitsu.staking.impl.presentation.payouts.confirm.model.ConfirmPayoutPayload
import jp.co.soramitsu.wallet.api.presentation.mixin.fee.FeeStatus
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfirmPayoutFragmentTest : StakingFragmentFixture() {
    @Test
    fun showsPayoutTotalAndAccountRolesAndForwardsTheirActions() {
        val vm = mockk<ConfirmPayoutViewModel>(relaxed = true)
        every { vm.feeLiveData } returns MutableLiveData(FeeStatus.Error)
        every { vm.showNextProgress } returns MutableLiveData(false)
        every { vm.initiatorAddressModel } returns MutableLiveData(address("Controller wallet"))
        every { vm.rewardDestinationModel } returns MutableLiveData(address("Reward wallet"))
        every { vm.totalRewardDisplay } returns MutableLiveData("10 DOT" to "$20")
        val payload = ConfirmPayoutPayload(emptyList(), BigInteger.TEN)
        assertSame(payload, ConfirmPayoutFragment.getBundle(payload).getParcelable<ConfirmPayoutPayload>(ConfirmPayoutFragment.KEY_PAYOUTS))
        val fragment = ConfirmPayoutFragment()
        HiltFragmentHarness.launch(fragment, vm)
        val root = fragment.requireView()
        assertTrue(texts(root).containsAll(listOf("Controller wallet", "Reward wallet", "10 DOT", "$20")))
        root.findViewById<View>(R.id.confirmPayoutOriginAccount).performClick()
        root.findViewById<View>(R.id.confirmPayoutRewardDestination).performClick()
        root.findViewById<View>(R.id.confirmPayoutConfirm).performClick()
        verify { vm.controllerClicked() }
        verify { vm.rewardDestinationClicked() }
        verify { vm.submitClicked() }
    }
}
