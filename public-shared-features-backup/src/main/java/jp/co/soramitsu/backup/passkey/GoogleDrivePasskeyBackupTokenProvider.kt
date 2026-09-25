package jp.co.soramitsu.backup.passkey

import android.accounts.Account
import android.content.Context
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import jp.co.soramitsu.backup.domain.exceptions.AuthConsentException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

fun interface GoogleDriveAccountNameProvider {
    suspend fun accountName(): String
}

fun interface GoogleDriveOAuthTokenFetcher {
    suspend fun fetchAccessToken(accountName: String, oauthScope: String): String
}

class GoogleDrivePasskeyBackupTokenProvider(
    private val accountNameProvider: GoogleDriveAccountNameProvider,
    private val tokenFetcher: GoogleDriveOAuthTokenFetcher,
    private val identityVerifier: GoogleDriveIdentityVerifier = GoogleDriveUserInfoIdentityVerifier()
) : GoogleDriveAccessTokenProvider {
    private val mutex = Mutex()
    private var selectedSubject: String? = null

    override suspend fun accessToken(): GoogleDriveAccountAccess = mutex.withLock {
        currentCoroutineContext().ensureActive()
        val accountName = GoogleDrivePasskeyBackup.requireAccountName(accountNameProvider.accountName())
        val accessToken = GoogleDriveAccountAccess.requireAccessToken(
            tokenFetcher.fetchAccessToken(
                accountName = accountName,
                oauthScope = GoogleDrivePasskeyBackup.OAUTH_IDENTITY_APP_DATA_SCOPE
            )
        )
        currentCoroutineContext().ensureActive()
        val identity = identityVerifier.verify(accessToken)
        currentCoroutineContext().ensureActive()
        GoogleDrivePasskeyBackup.requireMatchingAccountName(
            expected = accountName,
            actual = accountNameProvider.accountName(),
            ceremony = "account selection"
        )
        currentCoroutineContext().ensureActive()
        val pinnedSubject = selectedSubject
        if (pinnedSubject == null) {
            GoogleDrivePasskeyBackup.requireMatchingAccountName(accountName, identity.email, "initial identity")
        } else {
            require(identity.subject == pinnedSubject) { "Google Drive selected account changed" }
        }
        selectedSubject = identity.subject
        GoogleDriveAccountAccess(identity.subject, identity.email, accessToken)
    }
}

class GoogleAuthUtilDriveOAuthTokenFetcher(
    private val context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : GoogleDriveOAuthTokenFetcher {
    override suspend fun fetchAccessToken(accountName: String, oauthScope: String): String {
        return withContext(dispatcher) {
            try {
                GoogleAuthUtil.getToken(
                    context,
                    Account(accountName, GoogleAuthUtil.GOOGLE_ACCOUNT_TYPE),
                    oauthScope
                )
            } catch (e: UserRecoverableAuthException) {
                val consentIntent = e.intent ?: throw e
                throw AuthConsentException(consentIntent)
            }
        }
    }
}
