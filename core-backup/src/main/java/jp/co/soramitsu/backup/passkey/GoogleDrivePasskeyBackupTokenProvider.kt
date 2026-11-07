package jp.co.soramitsu.backup.passkey

import jp.co.soramitsu.backup.domain.exceptions.UnauthorizedException
import jp.co.soramitsu.backup.oauth.GoogleDriveAuthorization
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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

/**
 * Access tokens from the app's own Google sign-in ([GoogleDriveAuthorization]). The identity check
 * above reads Google's UserInfo endpoint, so passkey backup needs a sign-in that was granted
 * `openid email` as well as Drive app data; the wallet backup sign-in asks only for Drive app data.
 */
class GoogleDriveAuthorizationTokenFetcher(
    private val authorization: GoogleDriveAuthorization
) : GoogleDriveOAuthTokenFetcher {
    override suspend fun fetchAccessToken(accountName: String, oauthScope: String): String {
        val required = requiredScopes(oauthScope)
        if (!authorization.grantedScopes().containsAll(required)) throw UnauthorizedException()
        return authorization.accessToken()
    }

    companion object {
        private const val SCOPE_PREFIX = "oauth2:"
        private val SHORT_SCOPES = mapOf(
            "email" to "https://www.googleapis.com/auth/userinfo.email",
            "profile" to "https://www.googleapis.com/auth/userinfo.profile"
        )

        /** "oauth2:<scope> <scope>" as [GoogleDrivePasskeyBackup] writes it, with Google's short names expanded. */
        fun requiredScopes(oauthScope: String): Set<String> {
            require(oauthScope.startsWith(SCOPE_PREFIX)) { "OAuth scope must use the oauth2: form" }
            val scopes = oauthScope.removePrefix(SCOPE_PREFIX).split(' ').filter { it.isNotEmpty() }
            require(scopes.isNotEmpty()) { "OAuth scope is empty" }
            return scopes.map { SHORT_SCOPES[it] ?: it }.toSet()
        }
    }
}
