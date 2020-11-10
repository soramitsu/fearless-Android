package jp.co.soramitsu.common.view.bottomSheet

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.data.network.ExternalAnalyzer
import jp.co.soramitsu.common.utils.setDrawableStart
import jp.co.soramitsu.feature_account_api.domain.model.Node
import kotlinx.android.synthetic.main.item_sheet_external_action.view.itemExternalActionContent

typealias ExternalViewCallback = (ExternalAnalyzer, String) -> Unit
typealias CopyCallback = (String) -> Unit

open class ExternalActionsSheet(
    context: Context,
    private val payload: Payload,
    val onCopy: CopyCallback,
    val onViewExternal: ExternalViewCallback
) : FixedListBottomSheet(context) {

    class Payload(
        @StringRes val titleRes: Int,
        @StringRes val copyLabel: Int,
        val value: String,
        val networkType: Node.NetworkType
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTitle(payload.titleRes)

        val networkType = payload.networkType

        item(R.drawable.ic_copy_24, payload.copyLabel) {
            onCopy(payload.value)
        }

        if (ExternalAnalyzer.POLKASCAN.isNetworkSupported(networkType)) {
            item(R.drawable.ic_globe_24, R.string.transaction_details_view_polkascan) {
                onViewExternal(ExternalAnalyzer.POLKASCAN, payload.value)
            }
        }

        if (ExternalAnalyzer.SUBSCAN.isNetworkSupported(networkType)) {
            item(R.drawable.ic_globe_24, R.string.transaction_details_view_subscan) {
                onViewExternal(ExternalAnalyzer.SUBSCAN, payload.value)
            }
        }
    }

    protected fun item(@DrawableRes icon: Int, @StringRes titleRes: Int, onClick: (View) -> Unit) {
        item(R.layout.item_sheet_external_action) { view ->
            view.itemExternalActionContent.setText(titleRes)
            view.itemExternalActionContent.setDrawableStart(icon, widthInDp = 24, tint = R.color.white)

            view.setDismissingClickListener(onClick)
        }
    }
}