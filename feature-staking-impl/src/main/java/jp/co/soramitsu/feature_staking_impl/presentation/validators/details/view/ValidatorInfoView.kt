package jp.co.soramitsu.feature_staking_impl.presentation.validators.details.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import jp.co.soramitsu.feature_staking_impl.R
import kotlinx.android.synthetic.main.view_validator_info.view.validatorEstimatedReward
import kotlinx.android.synthetic.main.view_validator_info.view.validatorNominatorsView
import kotlinx.android.synthetic.main.view_validator_info.view.validatorTotalStakeView

class ValidatorInfoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : ConstraintLayout(context, attrs, defStyle) {

    init {
        View.inflate(context, R.layout.view_validator_info, this)
    }

    fun setTotalShakeValue(value: String) {
        validatorTotalStakeView.setBody(value)
    }

    fun setTotalShakeValueFiat(value: String) {
        validatorTotalStakeView.setExtra(value)
    }

    fun setNominatorsCount(count: String) {
        validatorNominatorsView.setBody(count)
    }

    fun setEstimatedRewardApy(reward: String) {
        validatorEstimatedReward.setBody(reward)
    }
}