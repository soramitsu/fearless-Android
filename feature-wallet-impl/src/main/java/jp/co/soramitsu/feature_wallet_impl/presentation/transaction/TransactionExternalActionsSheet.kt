package jp.co.soramitsu.feature_wallet_impl.presentation.transaction

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import com.google.android.material.bottomsheet.BottomSheetDialog
import jp.co.soramitsu.common.data.network.ExternalAnalyzer
import jp.co.soramitsu.common.utils.makeGone
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.presentation.model.TransactionModel
import kotlinx.android.synthetic.main.bottom_sheet_external_transaction_view.externalTransactionSheetCopy
import kotlinx.android.synthetic.main.bottom_sheet_external_transaction_view.externalTransactionSheetPolkascan
import kotlinx.android.synthetic.main.bottom_sheet_external_transaction_view.externalTransactionSheetSubscan

class TransactionExternalActionsSheet(
    context: Context,
    val model: TransactionModel,
    handler: Handler
) : BottomSheetDialog(context, R.style.BottomSheetDialog) {

    interface Handler {
        fun copyHashClicked(hash: String)

        fun externalViewClicked(analyzer: ExternalAnalyzer)
    }

    init {
        setContentView(LayoutInflater.from(context).inflate(R.layout.bottom_sheet_external_transaction_view, null))

        hideUnsupported()

        externalTransactionSheetCopy.setDismissingClickListener {
            handler.copyHashClicked(model.hash)
        }

        externalTransactionSheetSubscan.setDismissingClickListener {
            handler.externalViewClicked(ExternalAnalyzer.SUBSCAN)
        }

        externalTransactionSheetPolkascan.setDismissingClickListener {
            handler.externalViewClicked(ExternalAnalyzer.POLKASCAN)
        }
    }

    private fun hideUnsupported() {
        val networkType = model.token.networkType

        if (!ExternalAnalyzer.SUBSCAN.isNetworkSupported(networkType)) {
            externalTransactionSheetSubscan.makeGone()
        }

        if (!ExternalAnalyzer.POLKASCAN.isNetworkSupported(networkType)) {
            externalTransactionSheetPolkascan.makeGone()
        }
    }

    private inline fun View.setDismissingClickListener(crossinline listener: (View) -> Unit) {
        setOnClickListener {
            listener.invoke(it)

            dismiss()
        }
    }
}