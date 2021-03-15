package jp.co.soramitsu.feature_staking_impl.presentation.validators.details.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import jp.co.soramitsu.feature_staking_impl.R
import kotlinx.android.synthetic.main.view_validator_info.view.validatorEstimatedReward
import kotlinx.android.synthetic.main.view_validator_info.view.validatorNominatorsView
import kotlinx.android.synthetic.main.view_validator_info.view.validatorTotalStakeView

class ValidatorInfoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : LinearLayout(context, attrs, defStyle) {

    init {
        View.inflate(context, R.layout.view_validator_info, this)

        orientation = VERTICAL
    }

    fun setTotalStakeValue(value: String) {
        validatorTotalStakeView.setBody(value)
    }

    fun setTotalStakeValueFiat(fiat: String) {
        validatorTotalStakeView.showExtra()
        validatorTotalStakeView.setExtra(fiat)
    }

    fun hideTotalStakeFiatView() {
        validatorTotalStakeView.hideExtra()
    }

    fun setNominatorsCount(count: String) {
        validatorNominatorsView.setBody(count)
    }

    fun setEstimatedRewardApy(reward: String) {
        validatorEstimatedReward.setBody(reward)
    }

    fun setTotalStakeClickListener(clickListener: () -> Unit) {
        validatorTotalStakeView.setOnClickListener { clickListener() }
    }
}
