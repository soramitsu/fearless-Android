package jp.co.soramitsu.staking.impl.presentation.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import jp.co.soramitsu.common.utils.setVisible
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.databinding.ViewRewardDestinationViewerBinding
import jp.co.soramitsu.staking.impl.presentation.common.rewardDestination.RewardDestinationModel

class RewardDestinationViewer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : LinearLayout(context, attrs, defStyle) {

    private val binding: ViewRewardDestinationViewerBinding

    init {
        orientation = VERTICAL

        inflate(context, R.layout.view_reward_destination_viewer, this)
        binding = ViewRewardDestinationViewerBinding.bind(this)
    }

    fun showRewardDestination(rewardDestinationModel: RewardDestinationModel) {
        binding.viewRewardDestinationPayoutAccount.setVisible(rewardDestinationModel is RewardDestinationModel.Payout)
        binding.viewRewardDestinationDestination.setDividerVisible(rewardDestinationModel is RewardDestinationModel.Restake)

        when (rewardDestinationModel) {
            is RewardDestinationModel.Restake -> {
                binding.viewRewardDestinationDestination.showValue(context.getString(R.string.staking_setup_restake))
            }
            is RewardDestinationModel.Payout -> {
                binding.viewRewardDestinationDestination.showValue(context.getString(R.string.staking_payout))
                binding.viewRewardDestinationPayoutAccount.setMessage(rewardDestinationModel.destination.nameOrAddress)
                binding.viewRewardDestinationPayoutAccount.setTextIcon(rewardDestinationModel.destination.image)
            }
        }
    }

    fun setPayoutAccountClickListener(listener: (View) -> Unit) {
        binding.viewRewardDestinationPayoutAccount.setWholeClickListener(listener)
    }
}
