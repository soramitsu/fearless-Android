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
import kotlinx.android.synthetic.main.view_validator_info.view.validatorEstimatedReward
import kotlinx.android.synthetic.main.view_validator_info.view.validatorNominatorsView
import kotlinx.android.synthetic.main.view_validator_info.view.validatorStatusView
import kotlinx.android.synthetic.main.view_validator_info.view.validatorTotalStakeView

sealed class Error(@StringRes val errorDescription: Int, @DrawableRes val errorIcon: Int) {
    object OversubscribedUnpaid : Error(R.string.staking_validator_my_oversubscribed_message, R.drawable.ic_warning_filled)
    object OversubscribedPaid : Error(R.string.staking_validator_other_oversubscribed_message, R.drawable.ic_warning_filled)
    object Slashed : Error(R.string.staking_validator_slashed_desc, R.drawable.ic_status_error_16)
}

class ValidatorInfoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : LinearLayout(context, attrs, defStyle) {

    init {
        View.inflate(context, R.layout.view_validator_info, this)

        orientation = VERTICAL
    }

    private val totalStakeFields = listOf(validatorTotalStakeView, validatorNominatorsView, validatorNominatorsView, validatorEstimatedReward)

    fun setTotalStakeValue(value: String) {
        validatorTotalStakeView.setBody(value)
    }

    fun setTotalStakeValueFiat(fiat: String?) {
        validatorTotalStakeView.setExtraOrHide(fiat)
    }

    fun setNominatorsCount(count: String, maxNominations: String?) {
        validatorNominatorsView.setBody(
            if (maxNominations == null)
                count.format()
            else
                context.getString(
                    R.string.staking_max_format, count.format(), maxNominations.format()
                )
        )
    }

    fun setEstimatedRewardApy(reward: String) {
        validatorEstimatedReward.setBody(reward)
    }

    fun setTotalStakeClickListener(clickListener: () -> Unit) {
        validatorTotalStakeView.setOnClickListener { clickListener() }
    }

    fun hideActiveStakeFields() {
        totalStakeFields.forEach(ValidatorInfoItemView::makeGone)
    }

    fun showActiveStakeFields() {
        totalStakeFields.forEach(ValidatorInfoItemView::makeVisible)
    }

    fun setStatus(statusText: String, @ColorRes statusColorRes: Int) {
        validatorStatusView.setBodyOrHide(statusText)
        validatorStatusView.setBodyIconResource(R.drawable.ic_status_indicator, statusColorRes)
    }

    fun setErrors(error: List<Error>) {
        for (err in error) {
            when (err) {
                is Error.OversubscribedUnpaid -> validatorStatusView.setDescription(context.getString(err.errorDescription), err.errorIcon)
                is Error.OversubscribedPaid -> validatorStatusView.setDescription(context.getString(err.errorDescription), err.errorIcon)
                is Error.Slashed -> validatorNominatorsView.setDescription(context.getString(err.errorDescription), err.errorIcon)
            }
        }
    }
}
