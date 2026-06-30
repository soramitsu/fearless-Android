package jp.co.soramitsu.backup

import android.content.Context
import androidx.activity.result.ActivityResultLauncher
import jp.co.soramitsu.backup.domain.models.BackupAccountMeta
import jp.co.soramitsu.backup.domain.models.DecryptedBackupAccount

@Suppress("FunctionOnlyReturningConstant", "UnusedParameter")
class BackupService private constructor(
    private val context: Context?,
    private val token: String?
) {
    fun logout() {
        // The public compatibility layer does not maintain a remote backup session.
    }

    fun authorize(launcher: ActivityResultLauncher<*>): Boolean {
        return false
    }

    suspend fun getBackupAccounts(): List<BackupAccountMeta> {
        return emptyList()
    }

    suspend fun getWebBackupAccounts(): List<BackupAccountMeta> {
        return emptyList()
    }

    suspend fun saveBackupAccount(account: DecryptedBackupAccount, password: String) {
        unsupportedRemoteBackup()
    }

    suspend fun importBackupAccount(address: String, password: String): DecryptedBackupAccount {
        unsupportedRemoteBackup()
    }

    suspend fun importWebBackupAccount(address: String, name: String): DecryptedBackupAccount {
        unsupportedRemoteBackup()
    }

    suspend fun deleteBackupAccount(address: String) {
        unsupportedRemoteBackup()
    }

    private fun unsupportedRemoteBackup(): Nothing {
        throw UnsupportedOperationException(
            "Remote backup is unavailable in the public shared-features compatibility layer"
        )
    }

    companion object {
        fun create(context: Context, token: String): BackupService = BackupService(context, token)
    }
}
