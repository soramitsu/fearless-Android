package jp.co.soramitsu.feature_staking_impl.presentation.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import jp.co.soramitsu.common.utils.setCompoundDrawableTint
import jp.co.soramitsu.common.utils.setTextColorRes
import jp.co.soramitsu.common.view.shape.addRipple
import jp.co.soramitsu.common.view.shape.getCutCornerDrawable
import jp.co.soramitsu.feature_staking_impl.R
import kotlinx.android.synthetic.main.view_stake_summary.view.*

class StakeSummaryView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : LinearLayout(context, attrs, defStyle) {

    sealed class Status(@StringRes val textRes: Int, @ColorRes val tintRes: Int, val extraMessage: String?) {

        object Election : Status(R.string.staking_nominator_status_election, R.color.white_64, null)

        class Active(eraDisplay: String) : Status(R.string.staking_nominator_status_active, R.color.green, eraDisplay)

        class Inactive(eraDisplay: String) : Status(R.string.staking_nominator_status_inactive, R.color.red, eraDisplay)

        object Waiting : Status(R.string.staking_nominator_status_waiting, R.color.white_64, null)
    }

    init {
        View.inflate(context, R.layout.view_stake_summary, this)

        orientation = VERTICAL

        with(context) {
            background = addRipple(getCutCornerDrawable(R.color.blurColor))
        }
    }

    fun setElectionStatus(status: Status) {
        with(stakeSummaryStatus) {
            setCompoundDrawableTint(status.tintRes)
            setTextColorRes(status.tintRes)
            setText(status.textRes)
        }

        stakeSummaryStatusHelper.text = status.extraMessage
    }

    fun hideLoading() {
        stakeTotalStakedView.hideLoading()
        stakeTotalRewardsView.hideLoading()
    }

    fun setTotalStaked(inTokens: String) {
        stakeTotalStakedView.setBody(inTokens)
    }

    fun showTotalStakedFiat() {
        stakeTotalStakedView.showWholeExtraBlock()
    }

    fun hideTotalStakeFiat() {
        stakeTotalStakedView.makeExtraBlockInvisible()
    }

    fun setTotalStakedFiat(totalStake: String) {
        stakeTotalStakedView.setExtraBlockValueText(totalStake)
    }

    fun setTotalRewards(inTokens: String) {
        stakeTotalRewardsView.setBody(inTokens)
    }

    fun showTotalRewardsFiat() {
        stakeTotalRewardsView.showWholeExtraBlock()
    }

    fun hideTotalRewardsFiat() {
        stakeTotalRewardsView.makeExtraBlockInvisible()
    }

    fun setTotalRewardsFiat(totalRewards: String) {
        stakeTotalRewardsView.setExtraBlockValueText(totalRewards)
    }

    fun setStatusClickListener(listener: OnClickListener) {
        statusTapZone.setOnClickListener(listener)
    }

    fun setStakeInfoClickListener(listener: OnClickListener) {
        setOnClickListener(listener)
    }

    val moreActions: View
        get() = stakeMoreActions
}
