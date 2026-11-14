package jp.co.soramitsu.backup.oauth

import jp.co.soramitsu.backup.domain.exceptions.UnauthorizedException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.security.SecureRandom

/** Bearer tokens for Drive requests. */
interface GoogleDriveAccessTokenSource {
    /** A token that is valid for at least a minute; throws [UnauthorizedException] when nobody is signed in. */
    suspend fun accessToken(): String

    /** Drive answered 401 to [token]: the next [accessToken] call must not return it again. */
    suspend fun onAccessTokenRejected(token: String)
}

/** The scope this session was granted does not include Drive app data, so backups cannot be used. */
class GoogleDriveScopeNotGrantedException : Exception("Google Drive app data access was not granted")

/**
 * The app's Google sign-in for Drive backup: an OAuth 2.0 authorization-code grant with PKCE, a
 * refresh token kept only in [store] (the app's encrypted preferences), and short-lived access
 * tokens kept only in memory.
 */
class GoogleDriveAuthorization(
    val config: GoogleOAuthConfig,
    private val tokenClient: GoogleOAuthTokenClient,
    private val store: GoogleOAuthTokenStore,
    private val random: SecureRandom = SecureRandom(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : GoogleDriveAccessTokenSource {

    private val mutex = Mutex()

    @Volatile
    private var cachedAccessToken: CachedAccessToken? = null

    fun newAuthorizationRequest(
        prompt: GoogleOAuthPrompt,
        scopes: List<String> = listOf(GoogleOAuthConfig.DRIVE_APPDATA_SCOPE)
    ): GoogleOAuthAuthorizationRequest = GoogleOAuthAuthorizationRequest.create(config, prompt, scopes, random)

    /** True when a refresh token for this client is stored; it may still have been revoked at Google. */
    suspend fun hasSession(): Boolean = mutex.withLock { loadSession() != null }

    /** The scopes the stored session was granted; empty when signed out. */
    suspend fun grantedScopes(): Set<String> = mutex.withLock { loadSession()?.grantedScopes.orEmpty() }

    /**
     * Exchanges the redirect's code for tokens and keeps them. Fails without keeping anything when
     * the user turned Drive access off on the consent screen.
     */
    suspend fun completeAuthorization(
        request: GoogleOAuthAuthorizationRequest,
        response: GoogleOAuthAuthorizationResponse.Code
    ) {
        require(request.clientId == config.clientId) { "Authorization request was made for another OAuth client" }
        mutex.withLock {
            val tokens = tokenClient.exchangeCode(response.code, request)
            val grantedScopes = tokens.grantedScopes ?: request.scopes.toSet()
            if (GoogleOAuthConfig.DRIVE_APPDATA_SCOPE !in grantedScopes) throw GoogleDriveScopeNotGrantedException()
            val session = GoogleOAuthSession(
                clientId = config.clientId,
                refreshToken = requireNotNull(tokens.refreshToken),
                grantedScopes = grantedScopes,
                createdAtMillis = clock()
            )
            cachedAccessToken = null
            withContext(ioDispatcher) { store.write(session.encode()) }
            cachedAccessToken = CachedAccessToken(tokens.accessToken, expiresAt(tokens))
        }
    }

    override suspend fun accessToken(): String = mutex.withLock {
        cachedAccessToken?.takeIf { it.expiresAtMillis > clock() }?.let { return@withLock it.token }

        val session = loadSession() ?: throw UnauthorizedException()
        val tokens = refreshOrForget(session)
        val grantedScopes = tokens.grantedScopes ?: session.grantedScopes
        if (GoogleOAuthConfig.DRIVE_APPDATA_SCOPE !in grantedScopes) {
            clearSession()
            throw UnauthorizedException()
        }
        tokens.refreshToken?.takeIf { it != session.refreshToken }?.let { rotated ->
            withContext(ioDispatcher) {
                store.write(
                    GoogleOAuthSession(config.clientId, rotated, grantedScopes, session.createdAtMillis).encode()
                )
            }
        }
        CachedAccessToken(tokens.accessToken, expiresAt(tokens)).also { cachedAccessToken = it }.token
    }

    // A revoked or expired grant, or a client Google no longer accepts, means signing in again.
    private suspend fun refreshOrForget(session: GoogleOAuthSession): GoogleOAuthTokenResponse {
        return try {
            tokenClient.refresh(session.refreshToken)
        } catch (e: GoogleOAuthException) {
            if (!e.requiresSignIn) throw e
            clearSession()
            throw UnauthorizedException(e)
        }
    }

    override suspend fun onAccessTokenRejected(token: String) {
        mutex.withLock {
            if (cachedAccessToken?.token == token) cachedAccessToken = null
        }
    }

    /**
     * Forgets the session on this device, like the native sign-out did. The grant stays at Google:
     * iOS and the browser extension use the same project, and revoking would sign them out too.
     */
    suspend fun signOut() {
        mutex.withLock { clearSession() }
    }

    /** Revokes the grant at Google, then forgets it here. Signs the account out of every client of the project. */
    suspend fun revokeAccess() {
        val session = mutex.withLock { loadSession().also { clearSession() } } ?: return
        try {
            tokenClient.revoke(session.refreshToken)
        } catch (ignored: IOException) {
            // Best effort: the local session is gone either way.
        }
    }

    private suspend fun loadSession(): GoogleOAuthSession? {
        val stored = withContext(ioDispatcher) { store.read() } ?: return null
        val session = GoogleOAuthSession.decode(stored)
        // A token issued to another client cannot be refreshed with this one.
        if (session == null || session.clientId != config.clientId) {
            clearSession()
            return null
        }
        return session
    }

    private suspend fun clearSession() {
        cachedAccessToken = null
        withContext(ioDispatcher) { store.clear() }
    }

    private fun expiresAt(tokens: GoogleOAuthTokenResponse): Long =
        clock() + (tokens.expiresInSeconds * MILLIS_PER_SECOND - EXPIRY_MARGIN_MILLIS).coerceAtLeast(0)

    private class CachedAccessToken(val token: String, val expiresAtMillis: Long)

    private companion object {
        const val MILLIS_PER_SECOND = 1000L
        const val EXPIRY_MARGIN_MILLIS = 60_000L
    }
}
