package jp.co.soramitsu.staking.impl.presentation.staking.rewardDestination.confirm

import android.view.View
import androidx.lifecycle.MutableLiveData
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.impl.presentation.HiltFragmentHarness
import jp.co.soramitsu.staking.impl.presentation.StakingFragmentFixture
import jp.co.soramitsu.staking.impl.presentation.common.rewardDestination.RewardDestinationModel
import jp.co.soramitsu.staking.impl.presentation.staking.rewardDestination.confirm.parcel.ConfirmRewardDestinationPayload
import jp.co.soramitsu.staking.impl.presentation.staking.rewardDestination.confirm.parcel.RewardDestinationParcelModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfirmRewardDestinationFragmentTest : StakingFragmentFixture() {
    @Test
    fun showsPayoutAndRestakeDestinationsAndForwardsAccountActions() {
        val vm = mockk<ConfirmRewardDestinationViewModel>(relaxed = true)
        val destination = MutableLiveData<RewardDestinationModel>(RewardDestinationModel.Payout(address("Reward wallet")))
        every { vm.showNextProgress } returns MutableLiveData(false)
        every { vm.rewardDestinationLiveData } returns destination
        every { vm.originAccountModelLiveData } returns MutableLiveData(address("My wallet"))
        every { vm.feeLiveData } returns MutableLiveData(loadedFee())
        val payload = ConfirmRewardDestinationPayload(BigDecimal.ONE, RewardDestinationParcelModel.Restake)
        assertSame(payload, ConfirmRewardDestinationFragment.getBundle(payload).getParcelable<ConfirmRewardDestinationPayload>(KEY_PAYLOAD))
        val fragment = ConfirmRewardDestinationFragment()
        HiltFragmentHarness.launch(fragment, vm)
        val root = fragment.requireView()
        assertTrue(texts(root).containsAll(listOf("My wallet", "Reward wallet")))
        root.findViewById<View>(R.id.confirmRewardDestinationOriginAccount).performClick()
        root.findViewById<View>(R.id.viewRewardDestinationPayoutAccount).performClick()
        root.findViewById<View>(R.id.confirmRewardDestinationConfirm).performClick()
        verify { vm.originAccountClicked() }
        verify { vm.payoutAccountClicked() }
        verify { vm.confirmClicked() }
        destination.value = RewardDestinationModel.Restake
        assertEquals(View.GONE, root.findViewById<View>(R.id.viewRewardDestinationPayoutAccount).visibility)
        assertTrue(texts(root).contains(fragment.getString(R.string.staking_setup_restake)))
    }
}
