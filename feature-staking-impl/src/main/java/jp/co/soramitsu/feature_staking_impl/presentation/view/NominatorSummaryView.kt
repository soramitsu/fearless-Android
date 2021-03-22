package jp.co.soramitsu.feature_staking_impl.presentation.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import jp.co.soramitsu.common.utils.setCompoundDrawableTint
import jp.co.soramitsu.common.utils.setTextColorRes
import jp.co.soramitsu.common.view.shape.getCutCornerDrawable
import jp.co.soramitsu.feature_staking_impl.R
import kotlinx.android.synthetic.main.view_nominator_summary.view.nominatorSummaryStatus
import kotlinx.android.synthetic.main.view_nominator_summary.view.nominatorSummaryStatusHelper
import kotlinx.android.synthetic.main.view_nominator_summary.view.nominatorTotalRewardsView
import kotlinx.android.synthetic.main.view_nominator_summary.view.nominatorTotalStakedView
import kotlinx.android.synthetic.main.view_nominator_summary.view.statusTapZone

class NominatorSummaryView @JvmOverloads constructor(
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
        View.inflate(context, R.layout.view_nominator_summary, this)

        orientation = VERTICAL

        with(context) {
            background = getCutCornerDrawable(R.color.blurColor)
        }
    }

    fun setElectionStatus(status: Status) {
        with(nominatorSummaryStatus) {
            setCompoundDrawableTint(status.tintRes)
            setTextColorRes(status.tintRes)
            setText(status.textRes)
        }

        nominatorSummaryStatusHelper.text = status.extraMessage
    }

    fun hideLoading() {
        nominatorTotalStakedView.hideLoading()
        nominatorTotalRewardsView.hideLoading()
    }

    fun setTotalStaked(inTokens: String) {
        nominatorTotalStakedView.setBody(inTokens)
    }

    fun showTotalStakedFiat() {
        nominatorTotalStakedView.showWholeExtraBlock()
    }

    fun hideTotalStakeFiat() {
        nominatorTotalStakedView.makeExtraBlockInvisible()
    }

    fun setTotalStakedFiat(totalStake: String) {
        nominatorTotalStakedView.setExtraBlockValueText(totalStake)
    }

    fun setTotalRewards(inTokens: String) {
        nominatorTotalRewardsView.setBody(inTokens)
    }

    fun showTotalRewardsFiat() {
        nominatorTotalRewardsView.showWholeExtraBlock()
    }

    fun hideTotalRewardsFiat() {
        nominatorTotalRewardsView.makeExtraBlockInvisible()
    }

    fun setTotalRewardsFiat(totalRewards: String) {
        nominatorTotalRewardsView.setExtraBlockValueText(totalRewards)
    }

    fun setStatusClickListener(listener: OnClickListener) {
        statusTapZone.setOnClickListener(listener)
    }
}
