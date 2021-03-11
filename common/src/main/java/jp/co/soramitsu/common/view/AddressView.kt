package jp.co.soramitsu.common.view

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.utils.getDrawableCompat
import kotlinx.android.synthetic.main.view_account_address.view.accountAddress
import kotlinx.android.synthetic.main.view_account_address.view.accountCopy
import kotlinx.android.synthetic.main.view_account_address.view.accountIcon
import kotlinx.android.synthetic.main.view_account_address.view.accountTitle

class AddressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    init {
        View.inflate(context, R.layout.view_account_address, this)

        background = context.getDrawableCompat(R.drawable.bg_button_primary_disabled)

        applyAttributes(attrs)
    }

    private fun applyAttributes(attrs: AttributeSet?) {
        attrs?.let {
            val typedArray = context.obtainStyledAttributes(attrs, R.styleable.AddressView)

            val iconVisible = typedArray.getBoolean(R.styleable.AddressView_iconVisible, true)
            accountIcon.setVisible(iconVisible)

            val labelVisible = typedArray.getBoolean(R.styleable.AddressView_labelVisible, true)
            accountTitle.setVisible(labelVisible)

            typedArray.recycle()
        }
    }

    fun setIcon(icon: Drawable) {
        accountIcon.setImageDrawable(icon)
    }

    fun setAddress(address: String) {
        accountAddress.text = address
    }

    fun setOnCopyClickListener(listener: (View) -> Unit) {
        accountCopy.setOnClickListener(listener)
    }

    private fun View.setVisible(visible: Boolean) {
        visibility = if (visible) View.VISIBLE else View.GONE
    }
}