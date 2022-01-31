package jp.co.soramitsu.feature_wallet_impl.presentation.beacon.main

import android.content.Context
import android.os.Bundle
import android.widget.TextView
import jp.co.soramitsu.common.utils.format
import jp.co.soramitsu.common.view.bottomSheet.list.fixed.FixedListBottomSheet
import jp.co.soramitsu.common.view.bottomSheet.list.fixed.item
import jp.co.soramitsu.feature_wallet_impl.R
import kotlinx.android.synthetic.main.item_sheet_currency.view.itemCurrencyLabel
import kotlinx.android.synthetic.main.item_sheet_currency.view.itemCurrencyValue

class PermissionRequestBottomSheet(
    context: Context,
    private val dAppName: String,
    private val onApprove: () -> Unit,
    private val onDeny: () -> Unit
) : FixedListBottomSheet(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTitle(dAppName)

//        item(titleRes = R.string.common_connect) {
//            onApprove()
//        }
        item(TextView(context)) { view ->//todo fix
            view.itemCurrencyLabel.text = "common_connect"
            view.setOnClickListener { onApprove() }
        }

        item(TextView(context)) { view ->//todo fix
            view.itemCurrencyLabel.setText(R.string.common_cancel)
            view.setOnClickListener { onDeny() }
        }

//        item(titleRes = R.string.common_cancel) {
//            onDeny()
//        }

        setCancelable(false)
    }
}
