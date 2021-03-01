package jp.co.soramitsu.feature_staking_impl.presentation.view

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.widget.EditText
import androidx.constraintlayout.widget.ConstraintLayout
import jp.co.soramitsu.common.utils.makeGone
import jp.co.soramitsu.common.utils.makeVisible
import jp.co.soramitsu.common.view.shape.getCutCornerDrawable
import jp.co.soramitsu.feature_staking_impl.R
import kotlinx.android.synthetic.main.view_staking_amount.view.stakingAmountInput
import kotlinx.android.synthetic.main.view_staking_amount.view.stakingAssetBalance
import kotlinx.android.synthetic.main.view_staking_amount.view.stakingAssetDollarAmount
import kotlinx.android.synthetic.main.view_staking_amount.view.stakingAssetImage
import kotlinx.android.synthetic.main.view_staking_amount.view.stakingAssetToken

class StakingAmountView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : ConstraintLayout(context, attrs, defStyle) {

    val amountInput: EditText
        get() = stakingAmountInput

    init {
        View.inflate(context, R.layout.view_staking_amount, this)

        with(context) {
            background = getCutCornerDrawable(
                R.color.blurColor,
                R.color.white
            )
        }

//        attrs?.let { applyAttributes(it) }
    }

    private fun applyAttributes(attributeSet: AttributeSet) {
        val typedArray = context.obtainStyledAttributes(attributeSet, R.styleable.StakingAmountView)

        typedArray.recycle()
    }

    fun setAssetImage(image: Drawable) {
        stakingAssetImage.setImageDrawable(image)
    }

    fun setAssetImageResource(imageRes: Int) {
        stakingAssetImage.setImageResource(imageRes)
    }

    fun setAssetName(name: String) {
        stakingAssetToken.text = name
    }

    fun setAssetBalance(balance: String) {
        stakingAssetBalance.text = balance
    }

    fun setAssetBalanceDollarAmount(dollarAmount: String) {
        stakingAssetDollarAmount.text = dollarAmount
    }

    fun hideAssetDollarAmount() {
        stakingAssetDollarAmount.makeGone()
    }

    fun showAssetDollarAmount() {
        stakingAssetDollarAmount.makeVisible()
    }
}