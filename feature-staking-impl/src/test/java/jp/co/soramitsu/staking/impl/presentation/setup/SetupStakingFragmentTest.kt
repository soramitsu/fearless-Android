package jp.co.soramitsu.staking.impl.presentation.setup

import android.os.Looper
import android.view.View
import androidx.lifecycle.MutableLiveData
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.databinding.FragmentSetupStakingBinding
import jp.co.soramitsu.staking.impl.presentation.HiltFragmentHarness
import jp.co.soramitsu.staking.impl.presentation.StakingFragmentFixture
import jp.co.soramitsu.staking.impl.presentation.common.rewardDestination.RewardDestinationEstimations
import jp.co.soramitsu.staking.impl.presentation.common.rewardDestination.RewardDestinationModel
import jp.co.soramitsu.staking.impl.presentation.staking.main.model.RewardEstimation
import jp.co.soramitsu.wallet.api.presentation.mixin.fee.FeeStatus
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.Shadows.shadowOf

class SetupStakingFragmentTest : StakingFragmentFixture() {
    @Test
    fun setupDisplaysEstimatesBindsAmountAndSwitchesRewardControlsByStakingType() {
        val vm = mockk<SetupStakingViewModel>(relaxed = true)
        val type = MutableStateFlow(Asset.StakingType.RELAYCHAIN)
        val amount = MutableStateFlow("10")
        val destination = MutableStateFlow<RewardDestinationModel>(RewardDestinationModel.Restake)
        every { vm.isInputFocused } returns MutableStateFlow(false)
        every { vm.showNextProgress } returns MutableLiveData(false)
        every { vm.assetModelsFlow } returns MutableStateFlow(asset)
        every { vm.currentStakingType } returns type
        every { vm.enteredAmountFlow } returns amount
        every { vm.enteredFiatAmountFlow } returns MutableLiveData("$20")
        every { vm.feeLiveData } returns MutableLiveData(FeeStatus.Error)
        every { vm.currentAccountAddressModel } returns MutableLiveData(address("My wallet"))
        every { vm.rewardDestinationModelFlow } returns destination
        every { vm.canRestake } returns MutableStateFlow(true)
        every { vm.rewardReturnsLiveData } returns MutableLiveData(RewardDestinationEstimations(
            RewardEstimation("2 DOT", "$4", "20%"), RewardEstimation("1 DOT", "$2", "10%")))
        val fragment = SetupStakingFragment()
        val activity = HiltFragmentHarness.launch(fragment, vm) { it.imageLoader = mockk(relaxed = true) }
        val binding = FragmentSetupStakingBinding.bind(fragment.requireView())
        assertTrue(texts(binding.root).containsAll(listOf("DOT", "100 DOT available", "2 DOT", "1 DOT", "$20")))
        assertEquals(View.VISIBLE, binding.setupStakingRewardDestinationChooser.visibility)
        assertEquals(View.GONE, binding.setupStakingPayoutViewer.visibility)
        binding.setupStakingAmountField.amountInput.setText("12")
        assertEquals("12", amount.value)
        binding.setupStakingNext.performClick()
        verify { vm.nextClicked() }
        binding.setupStakingRewardDestinationChooser.destinationRestake.performClick()
        binding.setupStakingRewardDestinationChooser.destinationPayout.performClick()
        verify { vm.restakeClicked(vm) }
        verify { vm.payoutClicked(vm) }
        destination.value = RewardDestinationModel.Payout(address("Reward wallet"))
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(texts(binding.root).contains("Reward wallet"))
        binding.setupStakingRewardDestinationChooser.payoutTarget.performClick()
        binding.setupStakingRewardDestinationChooser.learnMore.performClick()
        verify { vm.payoutTargetClicked(vm) }
        verify { vm.learnMoreClicked(vm) }
        type.value = Asset.StakingType.PARACHAIN
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(View.GONE, binding.setupStakingRewardDestinationChooser.visibility)
        assertEquals(View.VISIBLE, binding.setupStakingPayoutViewer.visibility)
        activity.onBackPressedDispatcher.onBackPressed()
        verify { vm.backClicked() }
    }
}
