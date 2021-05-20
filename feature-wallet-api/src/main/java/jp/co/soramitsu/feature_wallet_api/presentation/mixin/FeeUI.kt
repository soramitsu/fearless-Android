package jp.co.soramitsu.feature_wallet_api.presentation.mixin

import android.widget.ProgressBar
import android.widget.TextView
import jp.co.soramitsu.common.utils.setVisible
import jp.co.soramitsu.feature_wallet_api.R

class FeeViews(
    val progress: ProgressBar,
    val fiat: TextView,
    val token: TextView,
)

fun displayFeeStatus(
    feeStatus: FeeStatus,
    feeViews: FeeViews,
) = with(feeViews) {
    val context = progress.context

    when (feeStatus) {
        is FeeStatus.Loading -> feeProgressShown(true, feeViews)
        is FeeStatus.Error -> {
            feeProgressShown(false, feeViews)

            token.text = context.getString(R.string.common_error_general_title)
            fiat.text = ""
        }
        is FeeStatus.Loaded -> {
            feeProgressShown(false, feeViews)

            fiat.text = feeStatus.feeModel.displayFiat
            token.text = feeStatus.feeModel.displayToken
        }
    }
}

private fun feeProgressShown(
    shown: Boolean,
    feeViews: FeeViews,
) = with(feeViews) {
    fiat.setVisible(!shown)
    token.setVisible(!shown)

    progress.setVisible(shown)
}
