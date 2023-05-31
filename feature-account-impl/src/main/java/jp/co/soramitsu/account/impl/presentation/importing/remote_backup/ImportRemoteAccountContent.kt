package jp.co.soramitsu.account.impl.presentation.importing.remote_backup

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import jp.co.soramitsu.common.compose.component.BottomSheetDialog

interface ImportRemoteAccountScreenInterface

@Composable
internal fun ImportRemoteAccountContent(
    callback: ImportRemoteAccountScreenInterface
) {
    BottomSheetDialog {
        Column {
        }
    }
}
