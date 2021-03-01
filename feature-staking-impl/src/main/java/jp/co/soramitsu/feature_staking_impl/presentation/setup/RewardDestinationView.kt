package jp.co.soramitsu.feature_staking_impl.presentation.setup

import android.content.Context
import android.content.res.ColorStateList
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
import kotlinx.android.synthetic.main.view_payout_target.view.payoutTargetAmountDescription
import kotlinx.android.synthetic.main.view_payout_target.view.payoutTargetAmountGain
import kotlinx.android.synthetic.main.view_payout_target.view.payoutTargetAmountToken
import kotlinx.android.synthetic.main.view_payout_target.view.payoutTargetChecked
import kotlinx.android.synthetic.main.view_payout_target.view.payoutTargetDescription
import kotlinx.android.synthetic.main.view_payout_target.view.payoutTargetName
import jp.co.soramitsu.common.R as RCommon

private val CheckedStateSet = intArrayOf(android.R.attr.state_checked)

class RewardDestinationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr), Checkable {

    private var isChecked: Boolean = false

    init {
        View.inflate(context, R.layout.view_payout_target, this)

        payoutTargetChecked.imageTintList = iconTintList()
        background = stateDrawable()

        attrs?.let(this::applyAttrs)
    }

    private fun applyAttrs(attrs: AttributeSet) {
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.PayoutTargetView)

        val checked = typedArray.getBoolean(R.styleable.PayoutTargetView_android_checked, false)
        setChecked(checked)

        val targetName = typedArray.getString(R.styleable.PayoutTargetView_targetName)
        targetName?.let(::setName)

        val targetDescription = typedArray.getString(R.styleable.PayoutTargetView_targetDescription)
        targetDescription?.let(::setDescription)

        val amountDescription = typedArray.getString(R.styleable.PayoutTargetView_targetAmountDescription)
        amountDescription?.let(::setAmountDescription)

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

    fun setDescription(description: String) {
        payoutTargetDescription.text = description
    }

    fun setAmountDescription(description: String) {
        payoutTargetAmountDescription.text = description
    }

    override fun setChecked(checked: Boolean) {
        isChecked = checked
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

    private fun iconTintList(): ColorStateList {
        val states = arrayOf(
            CheckedStateSet,
            intArrayOf()
        )

        val colors = intArrayOf(
            context.getPrimaryColor(),
            context.getColor(android.R.color.transparent)
        )

        return ColorStateList(states, colors)
    }
}