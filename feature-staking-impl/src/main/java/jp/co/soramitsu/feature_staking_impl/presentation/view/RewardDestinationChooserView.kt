package jp.co.soramitsu.feature_staking_impl.presentation.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import jp.co.soramitsu.feature_staking_impl.R
import kotlinx.android.synthetic.main.view_reward_destination_chooser.view.rewardDestinationChooserLearnMore
import kotlinx.android.synthetic.main.view_reward_destination_chooser.view.rewardDestinationChooserPayout
import kotlinx.android.synthetic.main.view_reward_destination_chooser.view.rewardDestinationChooserPayoutTarget
import kotlinx.android.synthetic.main.view_reward_destination_chooser.view.rewardDestinationChooserRestake

class RewardDestinationChooserView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : LinearLayout(context, attrs, defStyle) {

    init {
        orientation = VERTICAL

        View.inflate(context, R.layout.view_reward_destination_chooser, this)
    }

    val learnMore
        get() = rewardDestinationChooserLearnMore

    val destinationRestake
        get() = rewardDestinationChooserRestake

    val destinationPayout
        get() = rewardDestinationChooserPayout

    val payoutTarget
        get() = rewardDestinationChooserPayoutTarget
}
