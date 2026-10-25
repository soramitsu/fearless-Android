package jp.co.soramitsu.account.api.presentation.backup

import android.content.Intent
import java.io.IOException
import jp.co.soramitsu.account.api.domain.interfaces.AccountAlreadyExistsException
import jp.co.soramitsu.account.api.domain.model.EthereumOnlyGoogleBackupException
import jp.co.soramitsu.backup.GoogleSignInResult
import jp.co.soramitsu.backup.domain.exceptions.AuthConsentException
import jp.co.soramitsu.backup.domain.exceptions.BackupSecretNotVerifiedException
import jp.co.soramitsu.backup.domain.exceptions.BackupUnavailableException
import jp.co.soramitsu.backup.domain.exceptions.BackupWrongPasswordException
import jp.co.soramitsu.backup.domain.exceptions.DecodingException
import jp.co.soramitsu.backup.domain.exceptions.DecryptionException
import jp.co.soramitsu.backup.domain.exceptions.FileNotFoundException
import jp.co.soramitsu.backup.domain.exceptions.InsufficientFilePermissionsException
import jp.co.soramitsu.backup.domain.exceptions.StorageQuotaExceededException
import jp.co.soramitsu.backup.domain.exceptions.UnauthorizedException
import jp.co.soramitsu.backup.domain.models.BackupAccountType
import jp.co.soramitsu.fearless_utils.encrypt.json.JsonSeedDecodingException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock

class GoogleBackupIssueTest {

    @Test
    fun `every backup failure maps to the step the user can take`() {
        val expected = mapOf(
            DecryptionException() to GoogleBackupIssue.WRONG_PASSWORD,
            BackupWrongPasswordException() to GoogleBackupIssue.WRONG_PASSWORD,
            JsonSeedDecodingException.IncorrectPasswordException() to GoogleBackupIssue.WRONG_PASSWORD,
            AccountAlreadyExistsException() to GoogleBackupIssue.ALREADY_ON_DEVICE,
            DecodingException() to GoogleBackupIssue.UNSUPPORTED_BACKUP,
            JsonSeedDecodingException.InvalidJsonException() to GoogleBackupIssue.UNSUPPORTED_BACKUP,
            JsonSeedDecodingException.UnsupportedEncryptionTypeException() to GoogleBackupIssue.UNSUPPORTED_BACKUP,
            EthereumOnlyGoogleBackupException() to GoogleBackupIssue.ETHEREUM_ONLY_BACKUP,
            FileNotFoundException() to GoogleBackupIssue.BACKUP_NOT_FOUND,
            UnauthorizedException() to GoogleBackupIssue.SIGN_IN_REQUIRED,
            AuthConsentException(mock<Intent>()) to GoogleBackupIssue.SIGN_IN_REQUIRED,
            InsufficientFilePermissionsException(IOException("403")) to GoogleBackupIssue.SIGN_IN_REQUIRED,
            BackupUnavailableException() to GoogleBackupIssue.UNAVAILABLE,
            StorageQuotaExceededException(IOException("403")) to GoogleBackupIssue.STORAGE_FULL,
            BackupSecretNotVerifiedException(BackupAccountType.PASSPHRASE) to GoogleBackupIssue.SECRET_NOT_VERIFIED,
            IOException("offline") to GoogleBackupIssue.NO_CONNECTION,
            IllegalStateException("anything else") to GoogleBackupIssue.UNKNOWN
        )

        expected.forEach { (failure, issue) ->
            assertEquals(failure.javaClass.simpleName, issue, failure.toGoogleBackupIssue())
        }
    }

    @Test
    fun `a missing backup is not mistaken for a connection problem`() {
        // The backup module's FileNotFoundException is not java.io's, which is an IOException.
        assertEquals(GoogleBackupIssue.BACKUP_NOT_FOUND, FileNotFoundException(IOException("404")).toGoogleBackupIssue())
    }

    @Test
    fun `a failure wrapped by another exception is still recognised`() {
        val wrapped = IllegalStateException("import failed", RuntimeException(DecryptionException()))

        assertEquals(GoogleBackupIssue.WRONG_PASSWORD, wrapped.toGoogleBackupIssue())
        assertEquals(GoogleBackupIssue.SIGN_IN_REQUIRED, UnauthorizedException(IOException("auth")).toGoogleBackupIssue())
    }

    @Test
    fun `sign-in results explain everything but success and backing out`() {
        assertNull(GoogleSignInResult.Success.toGoogleBackupIssue())
        assertNull(GoogleSignInResult.Cancelled.toGoogleBackupIssue())
        assertEquals(GoogleBackupIssue.UNAVAILABLE, GoogleSignInResult.ConfigurationError.toGoogleBackupIssue())
        assertEquals(GoogleBackupIssue.NO_CONNECTION, GoogleSignInResult.Network.toGoogleBackupIssue())
        assertEquals(GoogleBackupIssue.UNKNOWN, GoogleSignInResult.Other(13).toGoogleBackupIssue())
    }

    @Test
    fun `only issues that can pass on their own are offered as retries`() {
        val retryable = GoogleBackupIssue.entries.filter { it.isRetryable }.toSet()

        assertEquals(setOf(GoogleBackupIssue.NO_CONNECTION, GoogleBackupIssue.STORAGE_FULL, GoogleBackupIssue.UNKNOWN), retryable)
        assertFalse(GoogleBackupIssue.WRONG_PASSWORD.isRetryable)
        assertTrue(GoogleBackupIssue.entries.all { it.titleRes != 0 && it.messageRes != 0 })
    }
}
