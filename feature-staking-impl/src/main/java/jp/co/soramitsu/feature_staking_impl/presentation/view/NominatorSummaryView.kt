package jp.co.soramitsu.feature_staking_impl.presentation.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.constraintlayout.widget.ConstraintLayout
import jp.co.soramitsu.common.utils.setCompoundDrawableTint
import jp.co.soramitsu.common.utils.setTextColorRes
import jp.co.soramitsu.common.utils.setTextOrHide
import jp.co.soramitsu.common.view.shape.getCutCornerDrawable
import jp.co.soramitsu.feature_staking_impl.R
import kotlinx.android.synthetic.main.view_nominator_summary.view.nominatorSummaryRewards
import kotlinx.android.synthetic.main.view_nominator_summary.view.nominatorSummaryRewardsFiat
import kotlinx.android.synthetic.main.view_nominator_summary.view.nominatorSummaryStaked
import kotlinx.android.synthetic.main.view_nominator_summary.view.nominatorSummaryStakedFiat
import kotlinx.android.synthetic.main.view_nominator_summary.view.nominatorSummaryStatus

class NominatorSummaryView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : ConstraintLayout(context, attrs, defStyle) {

    enum class Status(@StringRes val textRes: Int, @ColorRes val tintRes: Int) {
        ELECTION(R.string.staking_nominator_status_election, R.color.white_64),
        ACTIVE(R.string.staking_nominator_status_active, R.color.green),
        INACTIVE(R.string.staking_nominator_status_inactive, R.color.red),
        WAITING(R.string.staking_nominator_status_waiting, R.color.white_64)
    }

    init {
        View.inflate(context, R.layout.view_nominator_summary, this)

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
    }

    fun setTotalStaked(inTokens: String, inFiat: String?) {
        nominatorSummaryStaked.text = inTokens
        nominatorSummaryStakedFiat.setTextOrHide(inFiat)
    }

    fun setTotalRewards(inTokens: String, inFiat: String?) {
        nominatorSummaryRewards.text = inTokens
        nominatorSummaryRewardsFiat.setTextOrHide(inFiat)
    }
}
