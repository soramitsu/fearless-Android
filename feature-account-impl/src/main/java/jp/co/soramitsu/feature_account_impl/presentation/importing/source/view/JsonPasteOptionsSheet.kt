package jp.co.soramitsu.feature_account_impl.presentation.importing.source.view

import android.content.Context
import android.os.Bundle
import jp.co.soramitsu.common.view.bottomSheet.list.fixed.FixedListBottomSheet
import jp.co.soramitsu.common.view.bottomSheet.list.fixed.item
import jp.co.soramitsu.feature_account_impl.R

class JsonPasteOptionsSheet(
    context: Context,
    val onPaste: () -> Unit,
    val onOpenFile: () -> Unit
) : FixedListBottomSheet(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTitle(R.string.recovery_json)

        item(icon = R.drawable.ic_copy_24, titleRes = R.string.import_json_paste) {
            onPaste()
        }

        item(icon = R.drawable.ic_file_upload, titleRes = R.string.common_choose_file) {
            onOpenFile()
        }
    }
}