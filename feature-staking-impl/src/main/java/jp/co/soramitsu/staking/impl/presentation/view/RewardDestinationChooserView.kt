package jp.co.soramitsu.staking.impl.presentation.view

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.databinding.ViewRewardDestinationChooserBinding

class RewardDestinationChooserView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : LinearLayout(context, attrs, defStyle) {

    private val binding: ViewRewardDestinationChooserBinding

    init {
        orientation = VERTICAL

        inflate(context, R.layout.view_reward_destination_chooser, this)
        binding = ViewRewardDestinationChooserBinding.bind(this)
    }

    val learnMore
        get() = binding.rewardDestinationChooserLearnMore

    val destinationRestake
        get() = binding.rewardDestinationChooserRestake

    val destinationPayout
        get() = binding.rewardDestinationChooserPayout

    val payoutTarget
        get() = binding.rewardDestinationChooserPayoutTarget
}
