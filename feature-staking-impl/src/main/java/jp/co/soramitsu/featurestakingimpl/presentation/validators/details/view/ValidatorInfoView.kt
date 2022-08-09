package jp.co.soramitsu.featurestakingimpl.presentation.validators.details.view

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import jp.co.soramitsu.common.utils.makeGone
import jp.co.soramitsu.common.utils.makeVisible
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.databinding.ViewValidatorInfoBinding

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

    private val binding: ViewValidatorInfoBinding

    init {
        inflate(context, R.layout.view_validator_info, this)
        binding = ViewValidatorInfoBinding.bind(this)

        orientation = VERTICAL
    }

    private val totalStakeFields = listOf(
        binding.validatorTotalStakeView,
        binding.validatorNominatorsView,
        binding.validatorNominatorsView,
        binding.validatorEstimatedReward
    )

    fun setTotalStakeValue(value: String) {
        binding.validatorTotalStakeView.setBody(value)
    }

    fun setTotalStakeValueFiat(fiat: String?) {
        binding.validatorTotalStakeView.setExtraOrHide(fiat)
    }

    fun setNominatorsCount(count: String, maxNominations: String?) {
        binding.validatorNominatorsView.setBody(
            if (maxNominations == null) {
                count.format()
            } else {
                context.getString(R.string.staking_max_format, count.format(), maxNominations.format())
            }
        )
    }

    fun setEstimatedRewardApy(reward: String) {
        binding.validatorEstimatedReward.setBody(reward)
    }

    fun setTotalStakeClickListener(clickListener: () -> Unit) {
        binding.validatorTotalStakeView.setOnClickListener { clickListener() }
    }

    fun hideActiveStakeFields() {
        totalStakeFields.forEach(ValidatorInfoItemView::makeGone)
    }

    fun showActiveStakeFields() {
        totalStakeFields.forEach(ValidatorInfoItemView::makeVisible)
    }

    fun setStatus(statusText: String, @ColorRes statusColorRes: Int) {
        binding.validatorStatusView.setBodyOrHide(statusText)
        binding.validatorStatusView.setBodyIconResource(R.drawable.ic_status_indicator, statusColorRes)
    }

    fun setErrors(error: List<Error>) {
        for (err in error) {
            when (err) {
                is Error.OversubscribedUnpaid -> binding.validatorStatusView.setDescription(context.getString(err.errorDescription), err.errorIcon)
                is Error.OversubscribedPaid -> binding.validatorStatusView.setDescription(context.getString(err.errorDescription), err.errorIcon)
                is Error.Slashed -> binding.validatorNominatorsView.setDescription(context.getString(err.errorDescription), err.errorIcon)
            }
        }
    }
}
