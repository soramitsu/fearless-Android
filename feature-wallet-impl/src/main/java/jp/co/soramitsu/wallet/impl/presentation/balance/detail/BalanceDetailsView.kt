package jp.co.soramitsu.wallet.impl.presentation.balance.detail

import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import jp.co.soramitsu.common.utils.dp
import jp.co.soramitsu.common.utils.updatePadding
import jp.co.soramitsu.common.view.shape.getCutCornerDrawable
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.databinding.ViewBalanceDetailsBinding

class BalanceDetailsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : ConstraintLayout(context, attrs, defStyle) {

    private val binding: ViewBalanceDetailsBinding

    init {
        inflate(context, R.layout.view_balance_details, this)
        binding = ViewBalanceDetailsBinding.bind(this)

        background = context.getCutCornerDrawable(R.color.blurColor)
        updatePadding(start = 16.dp(context), end = 16.dp(context))
    }

    val total: TextView
        get() = binding.balanceDetailsTotal

    val totalFiat: TextView
        get() = binding.balanceDetailsTotalFiat

    val transferable: TextView
        get() = binding.balanceDetailsTransferable

    val transferableFiat: TextView
        get() = binding.balanceDetailsTransferableFiat

    val locked: TextView
        get() = binding.balanceDetailsLocked

    val lockedFiat: TextView
        get() = binding.balanceDetailsLockedFiat

    val lockedTitle: TextView
        get() = binding.balanceDetailsLockedCaption
}
