package jp.co.soramitsu.feature_wallet_impl.presentation.send.recipient

import android.content.Context
import android.os.Bundle
import jp.co.soramitsu.common.view.bottomSheet.list.fixed.FixedListBottomSheet
import jp.co.soramitsu.common.view.bottomSheet.list.fixed.item
import jp.co.soramitsu.feature_wallet_impl.R

class QrCodeSourceChooserBottomSheet(
    context: Context,
    val cameraClicked: () -> Unit,
    val galleryClicked: () -> Unit
) : FixedListBottomSheet(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTitle(R.string.invoice_scan_qr_code_title)

        item(icon = R.drawable.ic_file_upload, titleRes = R.string.invoice_scan_upload) {
            galleryClicked()
        }

        item(icon = R.drawable.ic_scan_qr, titleRes = R.string.contacts_scan) {
            cameraClicked()
        }
    }
}