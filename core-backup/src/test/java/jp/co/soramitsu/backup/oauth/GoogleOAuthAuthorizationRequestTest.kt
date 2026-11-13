package jp.co.soramitsu.backup.oauth

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

class GoogleOAuthAuthorizationRequestTest {

    private val config = GoogleOAuthConfig.create("")

    @Test
    fun `S256 challenge matches the RFC 7636 appendix B example`() {
        assertEquals(
            "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            GoogleOAuthPkce.challengeS256("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk")
        )
    }

    @Test
    fun `verifier and state are fresh 256-bit values in the RFC 7636 alphabet`() {
        val random = SecureRandom()
        val first = GoogleOAuthAuthorizationRequest.create(config, GoogleOAuthPrompt.SELECT_ACCOUNT, random = random)
        val second = GoogleOAuthAuthorizationRequest.create(config, GoogleOAuthPrompt.SELECT_ACCOUNT, random = random)

        assertEquals(43, first.codeVerifier.length)
        assertEquals(43, first.state.length)
        assertTrue(GoogleOAuthPkce.isValidVerifier(first.codeVerifier))
        assertNotEquals(first.codeVerifier, second.codeVerifier)
        assertNotEquals(first.state, second.state)
        assertNotEquals(first.state, first.codeVerifier)
    }

    @Test
    fun `authorization URL asks for a code with PKCE, state and only the Drive app data scope`() {
        val request = GoogleOAuthAuthorizationRequest.create(config, GoogleOAuthPrompt.SELECT_ACCOUNT, random = SecureRandom())

        val url = request.authorizationUrl().toHttpUrl()

        assertEquals("https", url.scheme)
        assertEquals("accounts.google.com", url.host)
        assertEquals("/o/oauth2/v2/auth", url.encodedPath)
        assertEquals(config.clientId, url.queryParameter("client_id"))
        assertEquals(config.redirectUri, url.queryParameter("redirect_uri"))
        assertEquals("code", url.queryParameter("response_type"))
        assertEquals("https://www.googleapis.com/auth/drive.appdata", url.queryParameter("scope"))
        assertEquals(request.state, url.queryParameter("state"))
        assertEquals(GoogleOAuthPkce.challengeS256(request.codeVerifier), url.queryParameter("code_challenge"))
        assertEquals("S256", url.queryParameter("code_challenge_method"))
        assertEquals("select_account", url.queryParameter("prompt"))
        // The verifier only ever goes to the token endpoint.
        assertFalse(request.authorizationUrl().contains(request.codeVerifier))
        assertNull(url.queryParameter("client_secret"))
        assertNull(url.queryParameter("access_type"))
    }

    @Test
    fun `consent prompt asks Google to show the Drive permission again`() {
        val request = GoogleOAuthAuthorizationRequest.create(config, GoogleOAuthPrompt.CONSENT, random = SecureRandom())

        assertEquals("consent", request.authorizationUrl().toHttpUrl().queryParameter("prompt"))
    }

    @Test
    fun `extra scopes are space separated in one parameter`() {
        val request = GoogleOAuthAuthorizationRequest.create(
            config,
            GoogleOAuthPrompt.CONSENT,
            scopes = listOf(GoogleOAuthConfig.DRIVE_APPDATA_SCOPE, "openid", "email", "openid"),
            random = SecureRandom()
        )

        assertEquals(
            "https://www.googleapis.com/auth/drive.appdata openid email",
            request.authorizationUrl().toHttpUrl().queryParameter("scope")
        )
    }

    @Test
    fun `saved request round-trips and anything else reads as no pending request`() {
        val request = GoogleOAuthAuthorizationRequest.create(config, GoogleOAuthPrompt.CONSENT, random = SecureRandom())

        val restored = requireNotNull(GoogleOAuthAuthorizationRequest.fromMap(request.toMap()))

        assertEquals(request.clientId, restored.clientId)
        assertEquals(request.redirectUri, restored.redirectUri)
        assertEquals(request.scopes, restored.scopes)
        assertEquals(request.state, restored.state)
        assertEquals(request.codeVerifier, restored.codeVerifier)
        assertEquals(request.prompt, restored.prompt)
        assertEquals(GoogleOAuthAuthorizationRequest.KEYS, request.toMap().keys)

        assertNull(GoogleOAuthAuthorizationRequest.fromMap(emptyMap()))
        assertNull(GoogleOAuthAuthorizationRequest.fromMap(request.toMap() + ("state" to "short")))
        assertNull(GoogleOAuthAuthorizationRequest.fromMap(request.toMap() + ("codeVerifier" to "x".repeat(42))))
        assertNull(GoogleOAuthAuthorizationRequest.fromMap(request.toMap() + ("prompt" to "NONE")))
        assertNull(GoogleOAuthAuthorizationRequest.fromMap(request.toMap() - "clientId"))
    }

    @Test
    fun `invalid verifiers are refused`() {
        assertThrows(IllegalArgumentException::class.java) { GoogleOAuthPkce.challengeS256("too-short") }
        assertThrows(IllegalArgumentException::class.java) { GoogleOAuthPkce.challengeS256("a".repeat(129)) }
        assertThrows(IllegalArgumentException::class.java) { GoogleOAuthPkce.challengeS256("a".repeat(42) + "+") }
    }

    @Test
    fun `string form never shows the state or verifier`() {
        val request = GoogleOAuthAuthorizationRequest.create(config, GoogleOAuthPrompt.CONSENT, random = SecureRandom())

        assertFalse(request.toString().contains(request.state))
        assertFalse(request.toString().contains(request.codeVerifier))
    }
}
