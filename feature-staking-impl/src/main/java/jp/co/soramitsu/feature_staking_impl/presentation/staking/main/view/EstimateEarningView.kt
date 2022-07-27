package jp.co.soramitsu.feature_staking_impl.presentation.staking.main.view

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.widget.EditText
import androidx.constraintlayout.widget.ConstraintLayout
import coil.ImageLoader
import jp.co.soramitsu.common.view.shape.addRipple
import jp.co.soramitsu.common.view.shape.getCutCornerDrawable
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.databinding.ViewEstimateEarningBinding
import jp.co.soramitsu.feature_staking_impl.presentation.staking.main.model.RewardEstimation

class EstimateEarningView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : ConstraintLayout(context, attrs, defStyle) {

    private val binding: ViewEstimateEarningBinding

    val amountInput: EditText
        get() = binding.estimateEarningAmount.amountInput

    init {
        inflate(context, R.layout.view_estimate_earning, this)
        binding = ViewEstimateEarningBinding.bind(this)

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
        binding.estimateEarningAmount.setAssetImage(image)
    }

    fun setAssetImageResource(imageRes: Int) {
        binding.estimateEarningAmount.setAssetImageResource(imageRes)
    }

    fun setAssetImageUrl(imageUrl: String, imageLoader: ImageLoader) {
        binding.estimateEarningAmount.setAssetImageUrl(imageUrl, imageLoader)
    }

    fun setAssetName(name: String) {
        binding.estimateEarningAmount.setAssetName(name)
    }

    fun setAssetBalance(balance: String) {
        binding.estimateEarningAmount.setAssetBalance(balance)
    }

    fun setAssetBalanceFiatAmount(fiatAmount: String?) {
        binding.estimateEarningAmount.setAssetBalanceFiatAmount(fiatAmount)
    }

    fun hideAssetBalanceFiatAmount() {
        binding.estimateEarningAmount.hideAssetFiatAmount()
    }

    fun showAssetBalanceFiatAmount() {
        binding.estimateEarningAmount.showAssetFiatAmount()
    }

    fun showReturnsLoading() {
        binding.stakingMonthGain.showLoading()
        binding.stakingYearGain.showLoading()
    }

    fun hideReturnsLoading() {
        binding.stakingMonthGain.hideLoading()
        binding.stakingYearGain.hideLoading()
    }

    fun populateMonthEstimation(estimation: RewardEstimation) {
        populateEstimationView(binding.stakingMonthGain, estimation)
    }

    fun populateYearEstimation(estimation: RewardEstimation) {
        populateEstimationView(binding.stakingYearGain, estimation)
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
        get() = binding.stakeMoreActions
}
