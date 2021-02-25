package jp.co.soramitsu.feature_staking_impl.presentation.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import jp.co.soramitsu.common.utils.makeGone
import jp.co.soramitsu.common.utils.makeVisible
import jp.co.soramitsu.feature_staking_impl.R
import kotlinx.android.synthetic.main.view_staking_gain.view.stakingGainAssetRate
import kotlinx.android.synthetic.main.view_staking_gain.view.stakingGainAssetRateChange
import kotlinx.android.synthetic.main.view_staking_gain.view.stakingGainTitle
import kotlinx.android.synthetic.main.view_staking_gain.view.stakingGainValue

class StakingGainView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : LinearLayout(context, attrs, defStyle) {

    init {
        View.inflate(context, R.layout.view_staking_gain, this)

        orientation = VERTICAL

        attrs?.let { applyAttributes(it) }
    }

    private fun applyAttributes(attributeSet: AttributeSet) {
        val typedArray = context.obtainStyledAttributes(attributeSet, R.styleable.StakingGainView)

        val title = typedArray.getString(R.styleable.StakingGainView_titleText)
        title?.let { setGainPeriodTitle(title) }

        typedArray.recycle()
    }

    fun setGainPeriodTitle(title: String) {
        stakingGainTitle.text = title
    }

    fun setPeriodGain(gain: String) {
        stakingGainValue.text = gain
    }

    fun setGainFiat(assetRate: String) {
        stakingGainAssetRate.text = assetRate
    }

    fun showGainFiat() {
        stakingGainAssetRate.makeVisible()
    }

    fun hideGainFiat() {
        stakingGainAssetRate.makeGone()
    }

    fun setGainPercentage(rateChange: String) {
        stakingGainAssetRateChange.text = rateChange
    }
}