package jp.co.soramitsu.staking.impl.presentation.view

import android.content.Context
import android.graphics.drawable.StateListDrawable
import android.util.AttributeSet
import android.util.StateSet
import android.widget.Checkable
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import jp.co.soramitsu.common.utils.getPrimaryColor
import jp.co.soramitsu.common.utils.setTextOrHide
import jp.co.soramitsu.common.view.shape.getCutCornerDrawable
import jp.co.soramitsu.common.view.shape.getCutCornerDrawableFromColors
import jp.co.soramitsu.common.view.shape.getDisabledDrawable
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.databinding.ViewPayoutTargetBinding
import jp.co.soramitsu.common.R as RCommon

private val CheckedStateSet = intArrayOf(android.R.attr.state_checked)

class RewardDestinationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr), Checkable {

    private var isChecked: Boolean = false

    private val binding: ViewPayoutTargetBinding

    init {
        inflate(context, R.layout.view_payout_target, this)
        binding = ViewPayoutTargetBinding.bind(this)

        background = stateDrawable()

        attrs?.let(this::applyAttrs)
    }

    private fun applyAttrs(attrs: AttributeSet) {
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.RewardDestinationView)

        val checked = typedArray.getBoolean(R.styleable.RewardDestinationView_android_checked, false)
        setChecked(checked)

        val targetName = typedArray.getString(R.styleable.RewardDestinationView_targetName)
        targetName?.let(::setName)

        val enabled = typedArray.getBoolean(R.styleable.RewardDestinationView_enabled, true)
        isEnabled = enabled

        typedArray.recycle()
    }

    fun setName(name: String) {
        binding.payoutTargetName.text = name
    }

    fun setTokenAmount(amount: String) {
        binding.payoutTargetAmountToken.text = amount
    }

    fun setPercentageGain(gain: String) {
        binding.payoutTargetAmountGain.text = gain
    }

    fun setFiatAmount(amount: String?) {
        binding.payoutTargetAmountFiat.setTextOrHide(amount)
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)

        binding.payoutTargetCheck.isVisible = enabled
        binding.payoutTargetCheckedDisabled.isVisible = !enabled
    }

    override fun setChecked(checked: Boolean) {
        isChecked = checked
        binding.payoutTargetCheck.isChecked = checked
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
        addState(intArrayOf(-android.R.attr.state_enabled), context.getDisabledDrawable())
        addState(StateSet.WILD_CARD, context.getCutCornerDrawable(strokeColorRes = RCommon.color.gray2))
    }
}
