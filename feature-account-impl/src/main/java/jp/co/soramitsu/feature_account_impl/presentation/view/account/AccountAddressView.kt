package jp.co.soramitsu.feature_account_impl.presentation.view.account

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import jp.co.soramitsu.common.utils.getDrawableCompat
import jp.co.soramitsu.feature_account_impl.R
import kotlinx.android.synthetic.main.view_account_address.view.accountAddress
import kotlinx.android.synthetic.main.view_account_address.view.accountCopy

class AccountAddressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {
    init {
        View.inflate(context, R.layout.view_account_address, this)

        background = context.getDrawableCompat(R.drawable.bg_button_primary_disabled)
    }

    fun setAddress(address: String) {
        accountAddress.text = address
    }

    fun setOnCopyClickListener(listener: OnClickListener) {
        accountCopy.setOnClickListener(listener)
    }
}