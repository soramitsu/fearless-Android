package jp.co.soramitsu.feature_staking_impl.presentation.validators.details.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import jp.co.soramitsu.common.utils.makeGone
import jp.co.soramitsu.common.utils.makeVisible
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

    fun setTotalStakeValue(value: String) {
        validatorTotalStakeView.setBody(value)
    }

    fun setTotalStakeValueFiat(value: String) {
        validatorTotalStakeView.setExtra(value)
    }

    fun showTotalStakeFiat() {
        validatorTotalStakeView.makeVisible()
    }

    fun hideTotalStakeFiat() {
        validatorTotalStakeView.makeGone()
    }

    fun setNominatorsCount(count: String) {
        validatorNominatorsView.setBody(count)
    }

    fun setEstimatedRewardApy(reward: String) {
        validatorEstimatedReward.setBody(reward)
    }
}