package jp.co.soramitsu.feature_account_impl.presentation.view.account

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import jp.co.soramitsu.feature_account_impl.R
import kotlinx.android.synthetic.main.view_account_info.view.accountAddressText
import kotlinx.android.synthetic.main.view_account_info.view.accountIcon
import kotlinx.android.synthetic.main.view_account_info.view.accountTitle
import kotlinx.android.synthetic.main.view_account_info.view.copyIcon

class AccountInfoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    init {
        View.inflate(context, R.layout.view_account_info, this)

        background = resources.getDrawable(R.drawable.bg_input_shape_filled_selector)
        isFocusable = true
        isClickable = true
    }

    fun setOnCopyClickListener(clickListener: () -> Unit) {
        copyIcon.setOnClickListener { clickListener() }
    }

    fun setAccountName(accountName: String) {
        accountTitle.text = accountName
    }

    fun setAccountAddress(address: String) {
        accountAddressText.text = address
    }

    fun setAccountIcon(icon: Drawable) {
        accountIcon.setImageDrawable(icon)
    }
}