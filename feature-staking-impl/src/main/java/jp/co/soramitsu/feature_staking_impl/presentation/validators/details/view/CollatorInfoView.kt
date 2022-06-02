package jp.co.soramitsu.feature_staking_impl.presentation.validators.details.view

import android.content.Context
import android.provider.Settings.Global.getString
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import jp.co.soramitsu.common.utils.format
import jp.co.soramitsu.common.utils.makeGone
import jp.co.soramitsu.common.utils.makeVisible
import jp.co.soramitsu.feature_staking_impl.R
import kotlinx.android.synthetic.main.view_collator_info.view.collatorEstimatedReward
import kotlinx.android.synthetic.main.view_collator_info.view.collatorNominatorsView
import kotlinx.android.synthetic.main.view_collator_info.view.collatorStatusView
import kotlinx.android.synthetic.main.view_collator_info.view.collatorTotalStakeView

class CollatorInfoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : LinearLayout(context, attrs, defStyle) {

    init {
        View.inflate(context, R.layout.view_collator_info, this)

        orientation = VERTICAL
    }

    private val totalStakeFields = listOf(collatorTotalStakeView, collatorNominatorsView, collatorNominatorsView, collatorEstimatedReward)

    fun setTotalStakeValue(value: String) {
        collatorTotalStakeView.setBody(value)
    }

    fun setTotalStakeValueFiat(fiat: String?) {
        collatorTotalStakeView.setExtraOrHide(fiat)
    }

    fun setNominatorsCount(count: String, maxNominations: String?) {
        collatorNominatorsView.setBody(
            if (maxNominations == null)
                count.format()
            else
                context.getString(
                    R.string.staking_max_format, count.format(), maxNominations.format()
                )
        )
    }

    fun setEstimatedRewardApy(reward: String) {
        collatorEstimatedReward.setBody(reward)
    }

    fun setTotalStakeClickListener(clickListener: () -> Unit) {
        collatorTotalStakeView.setOnClickListener { clickListener() }
    }

    fun hideActiveStakeFields() {
        totalStakeFields.forEach(ValidatorInfoItemView::makeGone)
    }

    fun showActiveStakeFields() {
        totalStakeFields.forEach(ValidatorInfoItemView::makeVisible)
    }

    fun setStatus(statusText: String, @ColorRes statusColorRes: Int) {
        collatorStatusView.setBodyOrHide(statusText)
        collatorStatusView.setBodyIconResource(R.drawable.ic_status_indicator, statusColorRes)
    }

    fun setErrors(error: List<Error>) {
        for (err in error) {
            when (err) {
                is Error.OversubscribedUnpaid -> collatorStatusView.setDescription(context.getString(err.errorDescription), err.errorIcon)
                is Error.OversubscribedPaid -> collatorStatusView.setDescription(context.getString(err.errorDescription), err.errorIcon)
                is Error.Slashed -> collatorNominatorsView.setDescription(context.getString(err.errorDescription), err.errorIcon)
            }
        }
    }
}
