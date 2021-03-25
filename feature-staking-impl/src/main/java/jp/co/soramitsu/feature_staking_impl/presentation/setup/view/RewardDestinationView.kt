package jp.co.soramitsu.feature_staking_impl.presentation.setup.view

import android.content.Context
import android.graphics.drawable.StateListDrawable
import android.util.AttributeSet
import android.util.StateSet
import android.view.View
import android.widget.Checkable
import androidx.constraintlayout.widget.ConstraintLayout
import jp.co.soramitsu.common.utils.getPrimaryColor
import jp.co.soramitsu.common.view.shape.getCutCornerDrawable
import jp.co.soramitsu.common.view.shape.getCutCornerDrawableFromColors
import jp.co.soramitsu.feature_staking_impl.R
import kotlinx.android.synthetic.main.view_payout_target.view.payoutTargetAmountGain
import kotlinx.android.synthetic.main.view_payout_target.view.payoutTargetAmountToken
import kotlinx.android.synthetic.main.view_payout_target.view.payoutTargetCheck
import kotlinx.android.synthetic.main.view_payout_target.view.payoutTargetName
import jp.co.soramitsu.common.R as RCommon

private val CheckedStateSet = intArrayOf(android.R.attr.state_checked)

class RewardDestinationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ConstraintLayout(context, attrs, defStyleAttr), Checkable {

    private var isChecked: Boolean = false

    init {
        View.inflate(context, R.layout.view_payout_target, this)

        background = stateDrawable()
//        payoutTargetCheck.buttonTintList = radioButtonStateList()

        attrs?.let(this::applyAttrs)
    }

    private fun applyAttrs(attrs: AttributeSet) {
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.RewardDestinationView)

        val checked = typedArray.getBoolean(R.styleable.RewardDestinationView_android_checked, false)
        setChecked(checked)

        val targetName = typedArray.getString(R.styleable.RewardDestinationView_targetName)
        targetName?.let(::setName)

        typedArray.recycle()
    }

    fun setName(name: String) {
        payoutTargetName.text = name
    }

    fun setTokenAmount(amount: String) {
        payoutTargetAmountToken.text = amount
    }

    fun setPercentageGain(gain: String) {
        payoutTargetAmountGain.text = gain
    }

    override fun setChecked(checked: Boolean) {
        isChecked = checked
        payoutTargetCheck.isChecked = checked
        refreshDrawableState()
    }

    override fun isChecked(): Boolean = isChecked

    override fun toggle() {
        isChecked = !isChecked
        refreshDrawableState()
    }

    override fun onCreateDrawableState(extraSpace: Int): IntArray {
        val drawableState = super.onCreateDrawableState(extraSpace + 1)

        if (isChecked()) {
            mergeDrawableStates(drawableState, CheckedStateSet)
        }

        return drawableState
    }

    private fun stateDrawable() = StateListDrawable().apply {
        addState(CheckedStateSet, context.getCutCornerDrawableFromColors(strokeColor = context.getPrimaryColor()))
        addState(StateSet.WILD_CARD, context.getCutCornerDrawable(strokeColorRes = RCommon.color.gray2))
    }
}
