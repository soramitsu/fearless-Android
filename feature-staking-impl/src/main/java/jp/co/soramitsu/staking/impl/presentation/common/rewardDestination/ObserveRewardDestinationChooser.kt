package jp.co.soramitsu.staking.impl.presentation.common.rewardDestination

import androidx.core.view.isVisible
import jp.co.soramitsu.common.address.AddressChooserBottomSheetDialog
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.utils.setVisible
import jp.co.soramitsu.common.view.LabeledTextView
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.impl.presentation.view.RewardDestinationChooserView
import jp.co.soramitsu.staking.impl.presentation.view.RewardDestinationView

fun <V> BaseFragment<V>.observeRewardDestinationChooser(
    viewModel: V,
    chooser: RewardDestinationChooserView
) where V : BaseViewModel, V : RewardDestinationMixin {
    viewModel.rewardDestinationModelFlow.observe {
        chooser.findViewById<LabeledTextView>(R.id.rewardDestinationChooserPayoutTarget).setVisible(it is RewardDestinationModel.Payout)
        chooser.findViewById<RewardDestinationView>(R.id.rewardDestinationChooserRestake).isChecked = it is RewardDestinationModel.Restake
        chooser.findViewById<RewardDestinationView>(R.id.rewardDestinationChooserPayout).isChecked = it is RewardDestinationModel.Payout

        if (it is RewardDestinationModel.Payout) {
            chooser.findViewById<LabeledTextView>(R.id.rewardDestinationChooserPayoutTarget).apply {
                setMessage(it.destination.nameOrAddress)
                setTextIcon(it.destination.image)
            }
        }
    }

    viewModel.rewardReturnsLiveData.observe {
        chooser.findViewById<RewardDestinationView>(R.id.rewardDestinationChooserPayout).apply {
            setPercentageGain(it.payout.gain)
            setTokenAmount(it.payout.amount)
            setFiatAmount(it.payout.fiatAmount)
        }

        chooser.findViewById<RewardDestinationView>(R.id.rewardDestinationChooserRestake).apply {
            setPercentageGain(it.restake.gain)
            setTokenAmount(it.restake.amount)
            setFiatAmount(it.restake.fiatAmount)
        }
    }

    viewModel.showDestinationChooserEvent.observeEvent {
        AddressChooserBottomSheetDialog(
            requireContext(),
            it,
            { address -> viewModel.payoutDestinationChanged(address, viewModel) },
            R.string.staking_setup_reward_payout_account
        ).show()
    }

    chooser.destinationPayout.setOnClickListener { viewModel.payoutClicked(viewModel) }
    chooser.destinationRestake.setOnClickListener { viewModel.restakeClicked(viewModel) }
    chooser.payoutTarget.setWholeClickListener { viewModel.payoutTargetClicked(viewModel) }
    chooser.learnMore.setOnClickListener { viewModel.learnMoreClicked(viewModel) }

    viewModel.canRestake.observe {
        chooser.destinationRestake.isVisible = it
    }
}
