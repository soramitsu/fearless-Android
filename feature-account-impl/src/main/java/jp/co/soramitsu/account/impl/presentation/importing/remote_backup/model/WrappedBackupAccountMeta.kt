package jp.co.soramitsu.account.impl.presentation.importing.remote_backup.model

import androidx.annotation.StringRes
import jp.co.soramitsu.backup.domain.models.BackupAccountMeta
import jp.co.soramitsu.common.R

enum class BackupOrigin {
    WEB, APP
}

/**
 * Browser extension backups open with the password set in the extension, not in the app, so the
 * backup list says where each of them came from. App backups need no label.
 */
@get:StringRes
val BackupOrigin.labelRes: Int?
    get() = when (this) {
        BackupOrigin.WEB -> R.string.google_backup_origin_extension
        BackupOrigin.APP -> null
    }

class WrappedBackupAccountMeta(
    val backupMeta: BackupAccountMeta,
    val origin: BackupOrigin = BackupOrigin.APP
)
