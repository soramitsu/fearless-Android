package jp.co.soramitsu.feature_wallet_impl.presentation.beacon.main

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.TextView
import jp.co.soramitsu.common.view.bottomSheet.list.fixed.FixedListBottomSheet
import jp.co.soramitsu.feature_wallet_impl.R

class PermissionRequestBottomSheet(
    context: Context,
    private val dAppName: String,
    private val onApprove: () -> Unit,
    private val onDeny: () -> Unit
) : FixedListBottomSheet(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTitle(dAppName)

        item(R.layout.item_text_header) { view ->//todo fix
            view.textHeader.text = context.getString(R.string.common_connect)
            view.setDismissingClickListener { onApprove() }
        }

        item(R.layout.item_text_header) { view ->//todo fix
            view.textHeader.setText(R.string.common_cancel)
            view.setDismissingClickListener { onDeny() }
        }

        setCancelable(false)
    }

    private val View.textHeader
        get() = findViewById<TextView>(R.id.textHeader)
}
