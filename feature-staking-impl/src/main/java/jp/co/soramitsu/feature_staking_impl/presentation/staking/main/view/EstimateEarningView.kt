package jp.co.soramitsu.feature_staking_impl.presentation.staking.main.view

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.widget.EditText
import androidx.constraintlayout.widget.ConstraintLayout
import jp.co.soramitsu.common.view.shape.addRipple
import jp.co.soramitsu.common.view.shape.getCutCornerDrawable
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.presentation.staking.main.model.RewardEstimation
import kotlinx.android.synthetic.main.view_estimate_earning.view.*

class EstimateEarningView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : ConstraintLayout(context, attrs, defStyle) {

    val amountInput: EditText
        get() = estimateEarningAmount.amountInput

    init {
        View.inflate(context, R.layout.view_estimate_earning, this)

        with(context) {
            background = addRipple(getCutCornerDrawable(R.color.blurColor))
        }

//        attrs?.let { applyAttributes(it) }
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

    fun setAssetBalanceDollarAmount(dollarAmount: String?) {
        estimateEarningAmount.setAssetBalanceDollarAmount(dollarAmount)
    }

    fun hideAssetBalanceDollarAmount() {
        estimateEarningAmount.hideAssetDollarAmount()
    }

    fun showAssetBalanceDollarAmount() {
        estimateEarningAmount.showAssetDollarAmount()
    }

    fun showReturnsLoading() {
        stakingMonthGain.showLoading()
        stakingYearGain.showLoading()
    }

    fun hideReturnsLoading() {
        stakingMonthGain.hideLoading()
        stakingYearGain.hideLoading()
    }

    fun populateMonthEstimation(estimation: RewardEstimation) {
        populateEstimationView(stakingMonthGain, estimation)
    }

    fun populateYearEstimation(estimation: RewardEstimation) {
        populateEstimationView(stakingYearGain, estimation)
    }

    private fun populateEstimationView(view: StakingInfoView, estimation: RewardEstimation) {
        view.setBody(estimation.amount)
        if (estimation.fiatAmount == null) {
            view.hideExtraBlockValue()
        } else {
            view.showExtraBlockValue()
            view.setExtraBlockValueText(estimation.fiatAmount)
        }
        view.setExtraBlockAdditionalText(estimation.gain)
    }

    val infoActions: View
        get() = stakeMoreActions
}
