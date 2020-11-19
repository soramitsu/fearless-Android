package jp.co.soramitsu.feature_wallet_impl.presentation.send.recipient

import android.content.Context
import android.os.Bundle
import jp.co.soramitsu.common.view.bottomSheet.FixedListBottomSheet
import jp.co.soramitsu.common.view.bottomSheet.item
import jp.co.soramitsu.feature_wallet_impl.R

class QrCodeSourceChooserBottomSheet(
    context: Context,
    val cameraClicked: () -> Unit,
    val galleryClicked: () -> Unit
) : FixedListBottomSheet(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTitle(R.string.contacts_scan)

        item(icon = R.drawable.ic_camera_white_24, titleRes = R.string.invoice_scan_camera) {
            cameraClicked()
        }

        item(icon = R.drawable.ic_file_upload, titleRes = R.string.invoice_scan_gallery) {
            galleryClicked()
        }
    }
}