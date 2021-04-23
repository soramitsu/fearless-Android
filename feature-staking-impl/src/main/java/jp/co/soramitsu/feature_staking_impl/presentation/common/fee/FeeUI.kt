package jp.co.soramitsu.feature_staking_impl.presentation.common.fee

import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import jp.co.soramitsu.common.utils.setVisible
import jp.co.soramitsu.feature_staking_impl.R

class FeeViews(
    val progress: ProgressBar,
    val fiat: TextView,
    val token: TextView
)

fun displayFeeStatus(
    feeStatus: FeeStatus,
    feeViews: FeeViews,
    hiddenState: Int = View.GONE
) = with(feeViews) {
    val context = progress.context

    when (feeStatus) {
        is FeeStatus.Loading -> feeProgressShown(true, feeViews, hiddenState)
        is FeeStatus.Error -> {
            feeProgressShown(false, feeViews, hiddenState)

            token.text = context.getString(R.string.common_error_general_title)
            fiat.text = ""
        }
        is FeeStatus.Loaded -> {
            feeProgressShown(false, feeViews, hiddenState)

            fiat.text = feeStatus.feeModel.displayFiat
            token.text = feeStatus.feeModel.displayToken
        }
    }
}

private fun feeProgressShown(
    shown: Boolean,
    feeViews: FeeViews,
    hiddenState: Int
) = with(feeViews) {
    fiat.setVisible(!shown, falseState = hiddenState)
    token.setVisible(!shown, falseState = hiddenState)

    progress.setVisible(shown, falseState = hiddenState)
}
