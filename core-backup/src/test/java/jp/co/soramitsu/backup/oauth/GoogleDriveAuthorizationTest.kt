package jp.co.soramitsu.backup.oauth

import jp.co.soramitsu.backup.domain.exceptions.UnauthorizedException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.security.SecureRandom

class GoogleDriveAuthorizationTest {

    private val config = GoogleOAuthConfig.create("")
    private val transport = FakeGoogleHttpTransport()
    private val store = InMemoryTokenStore()
    private var now = 1_000_000L
    private val authorization = GoogleDriveAuthorization(
        config = config,
        tokenClient = GoogleOAuthTokenClient(config, transport),
        store = store,
        random = SecureRandom(),
        clock = { now },
        ioDispatcher = Dispatchers.Unconfined
    )

    private suspend fun signIn(scope: String? = GoogleOAuthConfig.DRIVE_APPDATA_SCOPE) {
        val request = authorization.newAuthorizationRequest(GoogleOAuthPrompt.SELECT_ACCOUNT)
        transport.responder = { tokenResponse(accessToken = "ya29.first", scope = scope) }
        val response = GoogleOAuthAuthorizationResponse.parse(request, "${config.redirectUri}?code=4/c&state=${request.state}")
        authorization.completeAuthorization(request, response as GoogleOAuthAuthorizationResponse.Code)
    }

    @Test
    fun `nobody signed in means no token and no network`() = runBlocking {
        assertFalse(authorization.hasSession())
        assertTrue(runCatching { authorization.accessToken() }.exceptionOrNull() is UnauthorizedException)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun `completed sign-in keeps only the refresh token and serves the access token from memory`() = runBlocking {
        signIn()

        assertTrue(authorization.hasSession())
        assertEquals(setOf(GoogleOAuthConfig.DRIVE_APPDATA_SCOPE), authorization.grantedScopes())
        val stored = requireNotNull(GoogleOAuthSession.decode(requireNotNull(store.value)))
        assertEquals(config.clientId, stored.clientId)
        assertEquals("1//synthetic-refresh", stored.refreshToken)
        assertFalse(requireNotNull(store.value).contains("ya29.first"))

        assertEquals("ya29.first", authorization.accessToken())
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun `Drive access turned off on the consent page keeps nothing`() = runBlocking {
        val failure = runCatching { signIn(scope = "openid") }.exceptionOrNull()

        assertTrue(failure is GoogleDriveScopeNotGrantedException)
        assertNull(store.value)
        assertFalse(authorization.hasSession())
    }

    @Test
    fun `an expired access token is refreshed once and reused until it nears expiry`() = runBlocking {
        signIn()
        now += 3_600_000L
        transport.responder = { tokenResponse(accessToken = "ya29.second", refreshToken = null, scope = null) }

        assertEquals("ya29.second", authorization.accessToken())
        assertEquals("ya29.second", authorization.accessToken())
        assertEquals(2, transport.requests.size)
        assertEquals("refresh_token", transport.requests.last().form()["grant_type"])

        // The cached token is dropped a minute before Google's expiry.
        now += 3_599_000L - 60_000L
        transport.responder = { tokenResponse(accessToken = "ya29.third", refreshToken = null) }
        assertEquals("ya29.third", authorization.accessToken())
    }

    @Test
    fun `a rejected token is not served again`() = runBlocking {
        signIn()
        transport.responder = { tokenResponse(accessToken = "ya29.replacement", refreshToken = null) }

        authorization.onAccessTokenRejected("ya29.other")
        assertEquals("ya29.first", authorization.accessToken())

        authorization.onAccessTokenRejected("ya29.first")
        assertEquals("ya29.replacement", authorization.accessToken())
    }

    @Test
    fun `revoked or expired consent ends the session and asks for sign-in`() = runBlocking {
        signIn()
        now += 3_600_000L
        transport.responder = { oauthError(400, "invalid_grant") }

        val failure = runCatching { authorization.accessToken() }.exceptionOrNull()

        assertTrue(failure is UnauthorizedException)
        assertTrue(failure?.cause is GoogleOAuthException)
        assertNull(store.value)
        assertFalse(authorization.hasSession())
    }

    @Test
    fun `a client Google no longer accepts also ends the session`() = runBlocking {
        signIn()
        now += 3_600_000L
        transport.responder = { oauthError(401, "invalid_client") }

        assertTrue(runCatching { authorization.accessToken() }.exceptionOrNull() is UnauthorizedException)
        assertNull(store.value)
    }

    @Test
    fun `network and server failures keep the session for a retry`() = runBlocking {
        signIn()
        now += 3_600_000L
        transport.responder = { throw IOException("offline") }
        assertTrue(runCatching { authorization.accessToken() }.exceptionOrNull() is IOException)

        transport.responder = { jsonResponse(503, "") }
        val failure = runCatching { authorization.accessToken() }.exceptionOrNull()
        assertTrue(failure is GoogleOAuthException && failure !is UnauthorizedException)

        assertTrue(authorization.hasSession())
        transport.responder = { tokenResponse(accessToken = "ya29.back", refreshToken = null) }
        assertEquals("ya29.back", authorization.accessToken())
    }

    @Test
    fun `a refresh that drops the Drive scope ends the session`() = runBlocking {
        signIn()
        now += 3_600_000L
        transport.responder = { tokenResponse(refreshToken = null, scope = "openid") }

        assertTrue(runCatching { authorization.accessToken() }.exceptionOrNull() is UnauthorizedException)
        assertNull(store.value)
    }

    @Test
    fun `a rotated refresh token replaces the stored one`() = runBlocking {
        signIn()
        now += 3_600_000L
        transport.responder = { tokenResponse(accessToken = "ya29.rotated", refreshToken = "1//rotated") }

        authorization.accessToken()

        assertEquals("1//rotated", GoogleOAuthSession.decode(requireNotNull(store.value))?.refreshToken)
    }

    @Test
    fun `sessions of another client or unreadable sessions are discarded`() = runBlocking {
        store.value = GoogleOAuthSession(
            "621183184145-other0client.apps.googleusercontent.com",
            "1//other",
            setOf(GoogleOAuthConfig.DRIVE_APPDATA_SCOPE),
            1L
        ).encode()
        assertFalse(authorization.hasSession())
        assertNull(store.value)

        store.value = "{not json"
        assertFalse(authorization.hasSession())
        assertNull(store.value)

        store.value = "{\"version\":2,\"clientId\":\"${config.clientId}\",\"refreshToken\":\"r\",\"scopes\":[],\"createdAtMillis\":1}"
        assertFalse(authorization.hasSession())
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun `sign-out forgets the session on this device without revoking it at Google`() = runBlocking {
        signIn()

        authorization.signOut()

        assertNull(store.value)
        assertFalse(authorization.hasSession())
        assertTrue(runCatching { authorization.accessToken() }.exceptionOrNull() is UnauthorizedException)
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun `revoking forgets the session first and survives an offline revocation endpoint`() = runBlocking {
        signIn()
        transport.responder = { _ ->
            assertNull(store.value)
            throw IOException("offline")
        }

        authorization.revokeAccess()

        assertEquals("https://oauth2.googleapis.com/revoke", transport.requests.last().url)
        assertEquals("1//synthetic-refresh", transport.requests.last().form()["token"])
        assertFalse(authorization.hasSession())

        // Nothing to revoke: no request.
        authorization.revokeAccess()
        assertEquals(2, transport.requests.size)
    }

    @Test
    fun `concurrent callers share one refresh`() = runBlocking {
        signIn()
        now += 3_600_000L
        transport.responder = { tokenResponse(accessToken = "ya29.shared", refreshToken = null) }

        val tokens = (1..5).map { async { authorization.accessToken() } }.awaitAll()

        assertEquals(List(5) { "ya29.shared" }, tokens)
        assertEquals(2, transport.requests.size)
    }

    @Test
    fun `an authorization request of another client is refused`() = runBlocking {
        val other = GoogleOAuthConfig.create("621183184145-other0client.apps.googleusercontent.com")
        val foreign = GoogleOAuthAuthorizationRequest.create(other, GoogleOAuthPrompt.SELECT_ACCOUNT, random = SecureRandom())
        val code = GoogleOAuthAuthorizationResponse.parse(foreign, "${other.redirectUri}?code=c&state=${foreign.state}")

        val failure = runCatching {
            authorization.completeAuthorization(foreign, code as GoogleOAuthAuthorizationResponse.Code)
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(transport.requests.isEmpty())
    }
}
