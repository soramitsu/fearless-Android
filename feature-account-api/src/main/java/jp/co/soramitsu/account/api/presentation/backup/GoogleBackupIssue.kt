package jp.co.soramitsu.account.api.presentation.backup

import androidx.annotation.StringRes
import java.io.IOException
import jp.co.soramitsu.account.api.domain.interfaces.AccountAlreadyExistsException
import jp.co.soramitsu.account.api.domain.model.EthereumOnlyGoogleBackupException
import jp.co.soramitsu.backup.GoogleSignInResult
import jp.co.soramitsu.backup.domain.exceptions.AuthConsentException
import jp.co.soramitsu.backup.domain.exceptions.BackupSecretNotVerifiedException
import jp.co.soramitsu.backup.domain.exceptions.BackupUnavailableException
import jp.co.soramitsu.backup.domain.exceptions.DecodingException
import jp.co.soramitsu.backup.domain.exceptions.DecryptionException
import jp.co.soramitsu.backup.domain.exceptions.FileNotFoundException
import jp.co.soramitsu.backup.domain.exceptions.InsufficientFilePermissionsException
import jp.co.soramitsu.backup.domain.exceptions.StorageQuotaExceededException
import jp.co.soramitsu.backup.domain.exceptions.UnauthorizedException
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.emptyClick
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.fearless_utils.encrypt.json.JsonSeedDecodingException

/**
 * Why a Google Drive backup action failed, named by what the user can do next. Callers pair every
 * issue with an action that works in the app (try again, sign in, refresh, or another way to back up
 * or import), so a failure is never a dead end.
 */
enum class GoogleBackupIssue(
    @StringRes val titleRes: Int,
    @StringRes val messageRes: Int
) {
    WRONG_PASSWORD(R.string.google_backup_wrong_password_title, R.string.google_backup_wrong_password_message),
    ALREADY_ON_DEVICE(R.string.google_backup_already_imported_title, R.string.google_backup_already_imported_message),
    UNSUPPORTED_BACKUP(R.string.google_backup_unsupported_title, R.string.google_backup_unsupported_message),
    ETHEREUM_ONLY_BACKUP(R.string.google_backup_ethereum_only_title, R.string.google_backup_ethereum_only_message),
    BACKUP_NOT_FOUND(R.string.google_backup_not_found_title, R.string.google_backup_not_found_message),
    NO_CONNECTION(R.string.google_backup_no_connection_title, R.string.google_backup_no_connection_message),
    SIGN_IN_REQUIRED(R.string.google_backup_sign_in_title, R.string.google_backup_sign_in_message),
    UNAVAILABLE(R.string.google_backup_unavailable_title, R.string.google_backup_unavailable_message),
    STORAGE_FULL(R.string.google_backup_storage_full_title, R.string.google_backup_storage_full_message),
    SECRET_NOT_VERIFIED(R.string.google_backup_not_verified_title, R.string.google_backup_not_verified_message),
    UNKNOWN(R.string.google_backup_failed_title, R.string.google_backup_failed_message);

    /** Repeating the same request can succeed without the user changing anything. */
    val isRetryable: Boolean
        get() = this == NO_CONNECTION || this == STORAGE_FULL || this == UNKNOWN
}

/** Looks through wrappers, so a Drive failure reported inside another exception is still recognised. */
fun Throwable.toGoogleBackupIssue(): GoogleBackupIssue {
    return generateSequence(this) { it.cause.takeIf { cause -> cause !== it } }
        .take(MAX_CAUSE_DEPTH)
        .map(::directGoogleBackupIssue)
        .firstOrNull { it != GoogleBackupIssue.UNKNOWN }
        ?: GoogleBackupIssue.UNKNOWN
}

/** Null when the user signed in or backed out themselves, which needs no explanation. */
fun GoogleSignInResult.toGoogleBackupIssue(): GoogleBackupIssue? = when (this) {
    GoogleSignInResult.Success, GoogleSignInResult.Cancelled -> null
    // DEVELOPER_ERROR: this installation's signing certificate is not registered with Google.
    GoogleSignInResult.ConfigurationError -> GoogleBackupIssue.UNAVAILABLE
    GoogleSignInResult.Network -> GoogleBackupIssue.NO_CONNECTION
    is GoogleSignInResult.Other -> GoogleBackupIssue.UNKNOWN
}

fun BaseViewModel.showGoogleBackupIssue(
    resourceManager: ResourceManager,
    issue: GoogleBackupIssue,
    message: String = resourceManager.getString(issue.messageRes),
    @StringRes positiveButtonRes: Int = R.string.common_ok,
    positiveClick: () -> Unit = emptyClick,
    @StringRes negativeButtonRes: Int? = null,
    negativeClick: () -> Unit = emptyClick
) {
    showError(
        title = resourceManager.getString(issue.titleRes),
        message = message,
        positiveButtonText = resourceManager.getString(positiveButtonRes),
        negativeButtonText = negativeButtonRes?.let(resourceManager::getString),
        positiveClick = positiveClick,
        negativeClick = negativeClick
    )
}

private const val MAX_CAUSE_DEPTH = 8

private fun directGoogleBackupIssue(throwable: Throwable): GoogleBackupIssue = when (throwable) {
    is DecryptionException,
    is JsonSeedDecodingException.IncorrectPasswordException -> GoogleBackupIssue.WRONG_PASSWORD

    is AccountAlreadyExistsException -> GoogleBackupIssue.ALREADY_ON_DEVICE

    is DecodingException,
    is JsonSeedDecodingException.InvalidJsonException,
    is JsonSeedDecodingException.UnsupportedEncryptionTypeException -> GoogleBackupIssue.UNSUPPORTED_BACKUP

    is EthereumOnlyGoogleBackupException -> GoogleBackupIssue.ETHEREUM_ONLY_BACKUP
    is FileNotFoundException -> GoogleBackupIssue.BACKUP_NOT_FOUND

    is UnauthorizedException,
    is AuthConsentException,
    is InsufficientFilePermissionsException -> GoogleBackupIssue.SIGN_IN_REQUIRED

    is BackupUnavailableException -> GoogleBackupIssue.UNAVAILABLE
    is StorageQuotaExceededException -> GoogleBackupIssue.STORAGE_FULL
    is BackupSecretNotVerifiedException -> GoogleBackupIssue.SECRET_NOT_VERIFIED
    // Drive's own HTTP failures (5xx, rate limits) are IOExceptions too; retrying is right for them.
    is IOException -> GoogleBackupIssue.NO_CONNECTION
    else -> GoogleBackupIssue.UNKNOWN
}
