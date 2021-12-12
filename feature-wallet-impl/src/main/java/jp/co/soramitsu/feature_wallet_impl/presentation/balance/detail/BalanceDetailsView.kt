package jp.co.soramitsu.feature_wallet_impl.presentation.balance.detail

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import jp.co.soramitsu.common.utils.dp
import jp.co.soramitsu.common.utils.updatePadding
import jp.co.soramitsu.common.view.shape.getCutCornerDrawable
import jp.co.soramitsu.feature_wallet_impl.R
import kotlinx.android.synthetic.main.view_balance_details.view.balanceDetailsLocked
import kotlinx.android.synthetic.main.view_balance_details.view.balanceDetailsLockedCaption
import kotlinx.android.synthetic.main.view_balance_details.view.balanceDetailsLockedFiat
import kotlinx.android.synthetic.main.view_balance_details.view.balanceDetailsTotal
import kotlinx.android.synthetic.main.view_balance_details.view.balanceDetailsTotalFiat
import kotlinx.android.synthetic.main.view_balance_details.view.balanceDetailsTransferable
import kotlinx.android.synthetic.main.view_balance_details.view.balanceDetailsTransferableFiat

class BalanceDetailsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : ConstraintLayout(context, attrs, defStyle) {

    init {
        View.inflate(context, R.layout.view_balance_details, this)

        background = context.getCutCornerDrawable(R.color.blurColor)
        updatePadding(start = 16.dp(context), end = 16.dp(context))
    }

    val total: TextView
        get() = balanceDetailsTotal

    val totalFiat: TextView
        get() = balanceDetailsTotalFiat

    val transferable: TextView
        get() = balanceDetailsTransferable

    val transferableFiat: TextView
        get() = balanceDetailsTransferableFiat

    val locked: TextView
        get() = balanceDetailsLocked

    val lockedFiat: TextView
        get() = balanceDetailsLockedFiat

    val lockedTitle: TextView
        get() = balanceDetailsLockedCaption
}
