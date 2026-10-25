package jp.co.soramitsu.backup.domain.exceptions

import jp.co.soramitsu.backup.domain.models.BackupAccountType

// shared_features declared these as Throwable; callers catch Exception, so they extend Exception here.

class UnauthorizedException(e: Exception? = null) : Exception(e)

/** The backup password is wrong, or an encrypted field is not valid hex. */
open class DecryptionException(e: Exception? = null) : Exception(e)

/**
 * A wrong password on a backup whose seeds are checked before its phrase. shared_features reported
 * this as an IllegalArgumentException ("SEED is selected but ... == null").
 */
class BackupWrongPasswordException(e: Exception? = null) : DecryptionException(e)

/** The file is not a backup this app can read, for example one written by a newer version. */
class DecodingException(e: Exception? = null) : Exception(e)

class FileNotFoundException(e: Exception? = null) : Exception(e)

class StorageQuotaExceededException(e: Exception) : Exception(e)

class InsufficientFilePermissionsException(e: Exception) : Exception(e)

/** This build has no Google OAuth client configured, so Drive sign-in cannot succeed. */
class BackupUnavailableException : Exception("Google Drive backup is not configured in this build")

/**
 * The recovery material declared as [backupAccountType] does not restore the wallet the backup is
 * filed under, so nothing was uploaded.
 */
class BackupSecretNotVerifiedException(
    val backupAccountType: BackupAccountType,
    cause: Throwable? = null
) : Exception("$backupAccountType backup does not restore this wallet", cause)
