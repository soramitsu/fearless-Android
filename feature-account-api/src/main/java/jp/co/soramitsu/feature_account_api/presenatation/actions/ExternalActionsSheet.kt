package jp.co.soramitsu.feature_account_api.presenatation.actions

import android.content.Context
import android.os.Bundle
import androidx.annotation.StringRes
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.data.network.ExternalAnalyzer
import jp.co.soramitsu.common.view.bottomSheet.list.fixed.FixedListBottomSheet
import jp.co.soramitsu.common.view.bottomSheet.list.fixed.item
import jp.co.soramitsu.core.model.Node

typealias ExternalViewCallback = (ExternalAnalyzer, String, Node.NetworkType) -> Unit
typealias CopyCallback = (String) -> Unit

open class ExternalActionsSheet(
    context: Context,
    private val payload: Payload,
    val onCopy: CopyCallback,
    val onViewExternal: ExternalViewCallback
) : FixedListBottomSheet(context) {

    class Payload(
        @StringRes val copyLabel: Int,
        val content: ExternalAccountActions.Payload,
        val forceForbid: Set<ExternalAnalyzer> = emptySet(),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTitle(payload.content.value)

        val value = payload.content.value
        val networkType = payload.content.networkType

        item(R.drawable.ic_copy_24, payload.copyLabel) {
            onCopy(value)
        }

        if (ExternalAnalyzer.POLKASCAN.isSupported(payload)) {
            item(R.drawable.ic_globe_24, R.string.transaction_details_view_polkascan) {
                onViewExternal(ExternalAnalyzer.POLKASCAN, value, networkType)
            }
        }

        if (ExternalAnalyzer.SUBSCAN.isSupported(payload)) {
            item(R.drawable.ic_globe_24, R.string.transaction_details_view_subscan) {
                onViewExternal(ExternalAnalyzer.SUBSCAN, value, networkType)
            }
        }
    }

    private fun ExternalAnalyzer.isSupported(payload: Payload): Boolean {
        return isNetworkSupported(payload.content.networkType) and (this !in payload.forceForbid)
    }
}
