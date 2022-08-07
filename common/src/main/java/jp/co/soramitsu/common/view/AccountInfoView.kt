package jp.co.soramitsu.common.view

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.databinding.ViewAccountInfoBinding
import jp.co.soramitsu.common.utils.makeGone
import jp.co.soramitsu.common.utils.makeVisible
import jp.co.soramitsu.common.utils.setVisible
import jp.co.soramitsu.common.view.shape.addRipple
import jp.co.soramitsu.common.view.shape.getCutCornersStateDrawable

class AccountInfoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val binding: ViewAccountInfoBinding

    init {
        inflate(context, R.layout.view_account_info, this)
        binding = ViewAccountInfoBinding.bind(this)

        background = with(context) { addRipple(getCutCornersStateDrawable()) }

        isFocusable = true
        isClickable = true

        applyAttributes(attrs)
    }

    private fun applyAttributes(attrs: AttributeSet?) {
        attrs?.let {
            val typedArray = context.obtainStyledAttributes(attrs, R.styleable.AccountInfoView)

            val actionIcon = typedArray.getDrawable(R.styleable.AccountInfoView_accountActionIcon)
            actionIcon?.let(::setActionIcon)

            val textVisible = typedArray.getBoolean(R.styleable.AccountInfoView_textVisible, true)
            binding.accountAddressText.visibility = if (textVisible) View.VISIBLE else View.GONE

            val enabled = typedArray.getBoolean(R.styleable.AccountInfoView_enabled, true)
            isEnabled = enabled

            typedArray.recycle()
        }
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)

        binding.accountAction.setVisible(enabled)
    }

    fun setActionIcon(icon: Drawable) {
        binding.accountAction.setImageDrawable(icon)
    }

    fun setActionListener(clickListener: (View) -> Unit) {
        binding.accountAction.setOnClickListener(clickListener)
    }

    fun setWholeClickListener(listener: (View) -> Unit) {
        setOnClickListener(listener)

        setActionListener(listener)
    }

    fun setTitle(accountName: String) {
        binding.accountTitle.text = accountName
    }

    fun setText(address: String) {
        binding.accountAddressText.text = address
    }

    fun setAccountIcon(icon: Drawable) {
        binding.accountIcon.setImageDrawable(icon)
    }

    fun hideBody() {
        binding.accountAddressText.makeGone()
    }

    fun showBody() {
        binding.accountAddressText.makeVisible()
    }
}
