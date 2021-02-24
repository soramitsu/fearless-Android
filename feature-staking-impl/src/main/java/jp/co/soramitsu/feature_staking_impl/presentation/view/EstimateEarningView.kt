package jp.co.soramitsu.feature_staking_impl.presentation.view

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import jp.co.soramitsu.common.view.shape.addRipple
import jp.co.soramitsu.common.view.shape.getCutCornerDrawable
import jp.co.soramitsu.feature_staking_impl.R
import kotlinx.android.synthetic.main.view_estimate_earning.view.estimateEarningAmount
import kotlinx.android.synthetic.main.view_estimate_earning.view.stakingMonthGain
import kotlinx.android.synthetic.main.view_estimate_earning.view.stakingYearGain

class EstimateEarningView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : ConstraintLayout(context, attrs, defStyle) {

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

    fun setAssetName(name: String) {
        estimateEarningAmount.setAssetName(name)
    }

    fun setAssetBalance(balance: String) {
        estimateEarningAmount.setAssetBalance(balance)
    }

    fun setAssetBalanceDollarAmount(dollarAmount: String) {
        estimateEarningAmount.setAssetBalanceDollarAmount(dollarAmount)
    }

    fun setMonthlyPeriodIncome(income: String) {
        stakingMonthGain.setPeriodIncome(income)
    }

    fun setMonthlyAssetRate(assetRate: String) {
        stakingMonthGain.setAssetRate(assetRate)
    }

    fun setMonthlyAssetRateChange(rateChange: String) {
        stakingMonthGain.setAssetRateChange(rateChange)
    }

    fun setYearlyPeriodIncome(income: String) {
        stakingYearGain.setPeriodIncome(income)
    }

    fun setYearlyAssetRate(assetRate: String) {
        stakingYearGain.setAssetRate(assetRate)
    }

    fun setYearlyAssetRateChange(rateChange: String) {
        stakingYearGain.setAssetRateChange(rateChange)
    }
}