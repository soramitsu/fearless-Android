package jp.co.soramitsu.backup.passkey

import android.accounts.Account
import android.content.Context
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import jp.co.soramitsu.backup.domain.exceptions.AuthConsentException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun interface GoogleDriveAccountNameProvider {
    suspend fun accountName(): String
}

fun interface GoogleDriveOAuthTokenFetcher {
    suspend fun fetchAccessToken(accountName: String, oauthScope: String): String
}

class GoogleDrivePasskeyBackupTokenProvider(
    private val accountNameProvider: GoogleDriveAccountNameProvider,
    private val tokenFetcher: GoogleDriveOAuthTokenFetcher
) : GoogleDriveAccessTokenProvider {
    override suspend fun accessToken(): String {
        val accountName = GoogleDrivePasskeyBackup.requireAccountName(accountNameProvider.accountName())

        val accessToken = tokenFetcher.fetchAccessToken(
            accountName = accountName,
            oauthScope = GoogleDrivePasskeyBackup.OAUTH_APP_DATA_SCOPE
        ).trim()
        require(accessToken.isNotEmpty()) {
            "Google Drive access token is required for passkey backup"
        }

        return accessToken
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
