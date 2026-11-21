package jp.co.soramitsu.backup.oauth

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

class GoogleOAuthTokenClientTest {

    private val config = GoogleOAuthConfig.create("")
    private val transport = FakeGoogleHttpTransport()
    private val client = GoogleOAuthTokenClient(config, transport)
    private val request = GoogleOAuthAuthorizationRequest.create(config, GoogleOAuthPrompt.SELECT_ACCOUNT, random = SecureRandom())

    @Test
    fun `code exchange posts the code and PKCE verifier, never a client secret`() = runBlocking {
        transport.responder = { tokenResponse() }

        val tokens = client.exchangeCode("4/synthetic-code", request)

        val sent = transport.requests.single()
        assertEquals("POST", sent.method)
        assertEquals("https://oauth2.googleapis.com/token", sent.url)
        assertEquals("application/x-www-form-urlencoded", sent.headers["Content-Type"])
        assertEquals(
            mapOf(
                "grant_type" to "authorization_code",
                "code" to "4/synthetic-code",
                "client_id" to config.clientId,
                "redirect_uri" to config.redirectUri,
                "code_verifier" to request.codeVerifier
            ),
            sent.form()
        )
        assertTrue(sent.isOneShot)
        assertEquals("ya29.synthetic-access", tokens.accessToken)
        assertEquals("1//synthetic-refresh", tokens.refreshToken)
        assertEquals(3599L, tokens.expiresInSeconds)
        assertEquals(setOf(GoogleOAuthConfig.DRIVE_APPDATA_SCOPE), tokens.grantedScopes)
        assertFalse(tokens.toString().contains("synthetic"))
        assertFalse(sent.toString().contains("synthetic-code"))
    }

    @Test
    fun `an exchange without a refresh token cannot keep the user signed in`() = runBlocking {
        transport.responder = { tokenResponse(refreshToken = null) }

        val failure = runCatching { client.exchangeCode("code", request) }.exceptionOrNull() as GoogleOAuthException

        assertEquals(GoogleOAuthException.Kind.SERVER, failure.kind)
    }

    @Test
    fun `a request made for another client is refused before any network`() = runBlocking {
        val other = GoogleOAuthConfig.create("621183184145-other0client.apps.googleusercontent.com")
        val foreign = GoogleOAuthAuthorizationRequest.create(other, GoogleOAuthPrompt.SELECT_ACCOUNT, random = SecureRandom())

        assertTrue(runCatching { client.exchangeCode("code", foreign) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun `refresh posts the refresh token and keeps an omitted scope as unknown`() = runBlocking {
        transport.responder = { tokenResponse(accessToken = "ya29.fresh", refreshToken = null, scope = null) }

        val tokens = client.refresh("1//synthetic-refresh")

        assertEquals(
            mapOf("grant_type" to "refresh_token", "refresh_token" to "1//synthetic-refresh", "client_id" to config.clientId),
            transport.requests.single().form()
        )
        assertEquals("ya29.fresh", tokens.accessToken)
        assertNull(tokens.refreshToken)
        assertNull(tokens.grantedScopes)
    }

    @Test
    fun `token endpoint errors are classified without their bodies`() = runBlocking {
        val cases = mapOf(
            oauthError(400, "invalid_grant") to GoogleOAuthException.Kind.INVALID_GRANT,
            oauthError(401, "invalid_client") to GoogleOAuthException.Kind.CLIENT_CONFIGURATION,
            oauthError(400, "unauthorized_client") to GoogleOAuthException.Kind.CLIENT_CONFIGURATION,
            oauthError(400, "invalid_request") to GoogleOAuthException.Kind.CLIENT_CONFIGURATION,
            oauthError(500, "internal_failure") to GoogleOAuthException.Kind.SERVER,
            jsonResponse(503, "<html>unavailable</html>") to GoogleOAuthException.Kind.SERVER
        )
        cases.forEach { (response, kind) ->
            transport.responder = { response }
            val failure = runCatching { client.refresh("1//synthetic-refresh") }.exceptionOrNull() as GoogleOAuthException
            assertEquals(kind, failure.kind)
            assertEquals(response.code, failure.httpCode)
            assertFalse(failure.message.orEmpty().contains("synthetic"))
        }
        assertTrue(GoogleOAuthException(GoogleOAuthException.Kind.INVALID_GRANT, 400, null).requiresSignIn)
        assertTrue(GoogleOAuthException(GoogleOAuthException.Kind.CLIENT_CONFIGURATION, 401, null).requiresSignIn)
        assertFalse(GoogleOAuthException(GoogleOAuthException.Kind.SERVER, 500, null).requiresSignIn)
    }

    @Test
    fun `malformed success responses are refused`() = runBlocking {
        listOf(
            jsonResponse(200, "not json"),
            jsonResponse(200, "{\"access_token\":\"ya29.a\",\"token_type\":\"mac\",\"expires_in\":3600}"),
            jsonResponse(200, "{\"access_token\":\"ya29.a\",\"token_type\":\"Bearer\",\"expires_in\":0}"),
            jsonResponse(200, "{\"access_token\":\"ya29.a\",\"token_type\":\"Bearer\"}"),
            jsonResponse(200, "{\"access_token\":\"bad token\",\"token_type\":\"Bearer\",\"expires_in\":3600}"),
            jsonResponse(200, "{\"token_type\":\"Bearer\",\"expires_in\":3600}"),
            jsonResponse(200, "{\"access_token\":\"ya29.a\",\"token_type\":\"Bearer\",\"expires_in\":\"3600\"}")
        ).forEach { response ->
            transport.responder = { response }
            val failure = runCatching { client.refresh("1//r") }.exceptionOrNull()
            assertTrue(failure is GoogleOAuthException && failure.kind == GoogleOAuthException.Kind.SERVER)
        }
    }

    @Test
    fun `revocation posts the token and treats an already invalid token as revoked`() = runBlocking<Unit> {
        transport.responder = { jsonResponse(200, "{}") }
        client.revoke("1//synthetic-refresh")
        assertEquals("https://oauth2.googleapis.com/revoke", transport.requests.single().url)
        assertEquals(mapOf("token" to "1//synthetic-refresh"), transport.requests.single().form())

        transport.responder = { oauthError(400, "invalid_token") }
        client.revoke("1//synthetic-refresh")

        transport.responder = { jsonResponse(503, "") }
        assertThrows(GoogleOAuthException::class.java) { runBlocking { client.revoke("1//synthetic-refresh") } }
    }
}
