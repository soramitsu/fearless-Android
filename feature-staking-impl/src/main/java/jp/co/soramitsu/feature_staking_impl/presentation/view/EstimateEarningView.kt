package jp.co.soramitsu.feature_staking_impl.presentation.view

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.widget.EditText
import androidx.constraintlayout.widget.ConstraintLayout
import jp.co.soramitsu.common.view.shape.addRipple
import jp.co.soramitsu.common.view.shape.getCutCornerDrawable
import jp.co.soramitsu.feature_staking_impl.R
import kotlinx.android.synthetic.main.view_estimate_earning.view.estimateEarningAmount
import kotlinx.android.synthetic.main.view_estimate_earning.view.stakingMonthGain
import kotlinx.android.synthetic.main.view_estimate_earning.view.stakingYearGain
import kotlinx.android.synthetic.main.view_staking_amount.view.stakingAmountInput

class EstimateEarningView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : ConstraintLayout(context, attrs, defStyle) {

    val amountInput: EditText
        get() = estimateEarningAmount.stakingAmountInput

    init {
        View.inflate(context, R.layout.view_estimate_earning, this)

        with(context) {
            background = addRipple(getCutCornerDrawable(R.color.blurColor))
        }

        attrs?.let { applyAttributes(it) }
    }

    private fun applyAttributes(attributeSet: AttributeSet) {
        val typedArray = context.obtainStyledAttributes(attributeSet, R.styleable.EstimateEarningView)

        typedArray.recycle()
    }

    fun setAssetImage(image: Drawable) {
        estimateEarningAmount.setAssetImage(image)
    }

    fun setAssetImageResource(imageRes: Int) {
        estimateEarningAmount.setAssetImageResource(imageRes)
    }

    fun setAssetName(name: String) {
        estimateEarningAmount.setAssetName(name)
    }

    fun setAssetBalance(balance: String) {
        estimateEarningAmount.setAssetBalance(balance)
    }

    fun setAssetBalanceDollarAmount(dollarAmount: String) {
        estimateEarningAmount.setAssetBalanceDollarAmount(dollarAmount)
    }

    fun hideAssetBalanceDollarAmount() {
        estimateEarningAmount.hideAssetDollarAmount()
    }

    fun showAssetBalanceDollarAmount() {
        estimateEarningAmount.showAssetDollarAmount()
    }

    fun setMonthlyGainAsset(gain: String) {
        stakingMonthGain.setPeriodGain(gain)
    }

    fun setMonthlyGainFiat(gain: String) {
        stakingMonthGain.setGainFiat(gain)
    }

    fun showMonthlyGainFiat() {
        stakingMonthGain.showGainFiat()
    }

    fun hideMonthlyGainFiat() {
        stakingMonthGain.hideGainFiat()
    }

    fun setMonthlyGainPercentage(rateChange: String) {
        stakingMonthGain.setGainPercentage(rateChange)
    }

    fun setYearlyGainAsset(income: String) {
        stakingYearGain.setPeriodGain(income)
    }

    fun setYearlyGainFiat(assetRate: String) {
        stakingYearGain.setGainFiat(assetRate)
    }

    fun showYearlyGainFiat() {
        stakingYearGain.showGainFiat()
    }

    fun hideYearlyGainFiat() {
        stakingYearGain.hideGainFiat()
    }

    fun setYearlyGainPercentage(rateChange: String) {
        stakingYearGain.setGainPercentage(rateChange)
    }
}