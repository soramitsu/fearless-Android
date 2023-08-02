package jp.co.soramitsu.account.impl.presentation.importing.remote_backup.model

import jp.co.soramitsu.backup.domain.models.BackupAccountMeta

enum class BackupOrigin {
    WEB, APP
}

class WrappedBackupAccountMeta(
    val backupMeta: BackupAccountMeta,
    val origin: BackupOrigin = BackupOrigin.APP
)
