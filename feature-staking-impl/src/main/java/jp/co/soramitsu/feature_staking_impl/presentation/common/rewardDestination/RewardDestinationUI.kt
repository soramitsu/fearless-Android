package jp.co.soramitsu.feature_staking_impl.presentation.common.rewardDestination

import jp.co.soramitsu.common.address.AddressChooserBottomSheetDialog
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.utils.setVisible
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.presentation.view.RewardDestinationChooserView
import kotlinx.android.synthetic.main.view_reward_destination_chooser.view.rewardDestinationChooserPayout
import kotlinx.android.synthetic.main.view_reward_destination_chooser.view.rewardDestinationChooserPayoutTarget
import kotlinx.android.synthetic.main.view_reward_destination_chooser.view.rewardDestinationChooserRestake

fun <V> BaseFragment<V>.observeRewardDestinationChooser(
    viewModel: V,
    chooser: RewardDestinationChooserView,
) where V : BaseViewModel, V : RewardDestinationMixin {
    viewModel.rewardDestinationModelFlow.observe {
        chooser.rewardDestinationChooserPayoutTarget.setVisible(it is RewardDestinationModel.Payout)
        chooser.rewardDestinationChooserRestake.isChecked = it is RewardDestinationModel.Restake
        chooser.rewardDestinationChooserPayout.isChecked = it is RewardDestinationModel.Payout

        if (it is RewardDestinationModel.Payout) {
            chooser.rewardDestinationChooserPayoutTarget.setMessage(it.destination.nameOrAddress)
            chooser.rewardDestinationChooserPayoutTarget.setTextIcon(it.destination.image)
        }
    }

    viewModel.rewardReturnsLiveData.observe {
        chooser.rewardDestinationChooserPayout.setPercentageGain(it.payout.gain)
        chooser.rewardDestinationChooserPayout.setTokenAmount(it.payout.amount)
        chooser.rewardDestinationChooserPayout.setFiatAmount(it.payout.fiatAmount)

        chooser.rewardDestinationChooserRestake.setPercentageGain(it.restake.gain)
        chooser.rewardDestinationChooserRestake.setTokenAmount(it.restake.amount)
        chooser.rewardDestinationChooserRestake.setFiatAmount(it.restake.fiatAmount)
    }

    viewModel.showDestinationChooserEvent.observeEvent {
        AddressChooserBottomSheetDialog(
            requireContext(),
            it,
            viewModel::payoutDestinationChanged,
            R.string.staking_setup_reward_payout_account
        ).show()
    }

    chooser.destinationPayout.setOnClickListener { viewModel.payoutClicked(viewModel) }
    chooser.destinationRestake.setOnClickListener { viewModel.restakeClicked() }
    chooser.payoutTarget.setWholeClickListener { viewModel.payoutTargetClicked(viewModel) }
    chooser.learnMore.setOnClickListener { viewModel.learnMoreClicked() }
}
