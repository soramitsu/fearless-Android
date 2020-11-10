package jp.co.soramitsu.feature_wallet_impl.presentation.transaction

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import androidx.annotation.StringRes
import com.google.android.material.bottomsheet.BottomSheetDialog
import jp.co.soramitsu.common.data.network.ExternalAnalyzer
import jp.co.soramitsu.common.utils.makeGone
import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.presentation.balance.list.changeAccount.AccountChooserPayload
import jp.co.soramitsu.feature_wallet_impl.presentation.model.TransactionModel
import kotlinx.android.synthetic.main.bottom_sheet_external_transaction_view.externalTransactionSheetCopy
import kotlinx.android.synthetic.main.bottom_sheet_external_transaction_view.externalTransactionSheetPolkascan
import kotlinx.android.synthetic.main.bottom_sheet_external_transaction_view.externalTransactionSheetSubscan
import kotlinx.android.synthetic.main.bottom_sheet_external_transaction_view.externalTransactionSheetTitle

typealias ExternalViewCallback = (ExternalAnalyzer, String) -> Unit

class ExternalActionsSheet(
    context: Context,
    private val payload: Payload,
    val onCopy: (String) -> Unit,
    val onViewExternal: ExternalViewCallback
) : BottomSheetDialog(context, R.style.BottomSheetDialog) {

    class Payload(
        @StringRes val titleRes: Int,
        @StringRes val copyLabel: Int,
        val value: String,
        val networkType: Node.NetworkType
    )

    init {
        setContentView(LayoutInflater.from(context).inflate(R.layout.bottom_sheet_external_transaction_view, null))

        externalTransactionSheetTitle.setText(payload.titleRes)

        externalTransactionSheetCopy.setText(payload.copyLabel)

        hideUnsupported()

        externalTransactionSheetCopy.setDismissingClickListener {
            onCopy(payload.value)
        }

        externalTransactionSheetSubscan.setDismissingClickListener {
            onViewExternal(ExternalAnalyzer.SUBSCAN, payload.value)
        }

        externalTransactionSheetPolkascan.setDismissingClickListener {
            onViewExternal(ExternalAnalyzer.POLKASCAN, payload.value)
        }
    }

    private fun hideUnsupported() {
        val networkType = payload.networkType

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