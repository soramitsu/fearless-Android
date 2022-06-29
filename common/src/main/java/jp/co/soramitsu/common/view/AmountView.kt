package jp.co.soramitsu.common.view

import android.content.Context
import android.graphics.drawable.Drawable
import android.text.InputType
import android.util.AttributeSet
import android.view.View
import android.widget.EditText
import androidx.constraintlayout.widget.ConstraintLayout
import coil.ImageLoader
import coil.load
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.databinding.ViewAccountInfoBinding
import jp.co.soramitsu.common.databinding.ViewStakingAmountBinding
import jp.co.soramitsu.common.utils.makeGone
import jp.co.soramitsu.common.utils.makeVisible
import jp.co.soramitsu.common.utils.setTextOrHide
import jp.co.soramitsu.common.view.shape.getCutCornerDrawable
import jp.co.soramitsu.common.view.shape.getCutCornersStateDrawable

class AmountView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : ConstraintLayout(context, attrs, defStyle) {

    private val binding: ViewStakingAmountBinding

    val amountInput: EditText
        get() = binding.stakingAmountInput

    init {
        inflate(context, R.layout.view_staking_amount, this)
        binding = ViewStakingAmountBinding.bind(this)

        setBackground()

        applyAttributes(attrs)
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)

        amountInput.isEnabled = enabled
        amountInput.inputType = if (enabled) InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL else InputType.TYPE_NULL
    }

    override fun childDrawableStateChanged(child: View?) {
        refreshDrawableState()
    }

    // Make this view be aware of amountInput state changes (i.e. state_focused)
    override fun onCreateDrawableState(extraSpace: Int): IntArray {
        val fieldState: IntArray? = amountInput.drawableState

        val need = fieldState?.size ?: 0

        val selfState = super.onCreateDrawableState(extraSpace + need)

        return mergeDrawableStates(selfState, fieldState)
    }

    private fun setBackground() {
        background = context.getCutCornersStateDrawable(
            focusedDrawable = context.getCutCornerDrawable(
                R.color.blurColor,
                R.color.white
            ),
            idleDrawable = context.getCutCornerDrawable(
                R.color.blurColor,
                R.color.white_40
            )
        )
    }

    private fun applyAttributes(attributeSet: AttributeSet?) {
        attributeSet?.let {
            val typedArray = context.obtainStyledAttributes(attributeSet, R.styleable.AmountView)

            val enabled = typedArray.getBoolean(R.styleable.AmountView_android_enabled, true)
            isEnabled = enabled

            typedArray.recycle()
        }
    }

    fun setAssetImage(image: Drawable) {
        binding.stakingAssetImage.setImageDrawable(image)
    }

    fun setAssetImageResource(imageRes: Int) {
        binding.stakingAssetImage.setImageResource(imageRes)
    }

    fun setAssetImageUrl(imageUrl: String, imageLoader: ImageLoader) {
        binding.stakingAssetImage.load(imageUrl, imageLoader)
    }

    fun setAssetName(name: String) {
        binding.stakingAssetToken.text = name
    }

    fun setAssetBalance(balance: String) {
        binding.stakingAssetBalance.text = balance
    }

    fun setAssetBalanceFiatAmount(fiatAmount: String?) {
        binding.stakingAssetFiatAmount.setTextOrHide(fiatAmount)
    }

    fun hideAssetFiatAmount() {
        binding.stakingAssetFiatAmount.makeGone()
    }

    fun showAssetFiatAmount() {
        binding.stakingAssetFiatAmount.makeVisible()
    }
}
