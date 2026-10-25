package jp.co.soramitsu.account.impl.domain

import jp.co.soramitsu.backup.domain.exceptions.BackupSecretNotVerifiedException
import jp.co.soramitsu.backup.domain.models.BackupAccountType
import jp.co.soramitsu.backup.domain.models.DecryptedBackupAccount

/**
 * Uploads [account] with every declared type the backup service verifies. The service refuses a
 * phrase or seed that does not restore this exact wallet (critique D-7); that type is left out and
 * the upload repeated with the rest, so the wallet is still backed up by a seed or the JSON of the key
 * it uses. Throws the refusal when nothing that restores the wallet is left.
 *
 * @return the types left out of the uploaded backup
 */
internal suspend fun uploadVerifiedGoogleBackup(
    account: DecryptedBackupAccount,
    upload: suspend (DecryptedBackupAccount) -> Unit
): Set<BackupAccountType> {
    val leftOut = mutableSetOf<BackupAccountType>()
    var types = account.backupAccountType

    while (true) {
        try {
            upload(account.copy(backupAccountType = types))
            return leftOut
        } catch (e: BackupSecretNotVerifiedException) {
            val refused = e.backupAccountType
            val remaining = types.filter { it != refused }
            if (refused !in types || remaining.isEmpty()) throw e
            leftOut += refused
            types = remaining
        }
    }
}
