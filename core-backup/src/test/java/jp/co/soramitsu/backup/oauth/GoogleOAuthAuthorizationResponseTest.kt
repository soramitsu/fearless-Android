package jp.co.soramitsu.backup.oauth

import jp.co.soramitsu.backup.oauth.GoogleOAuthAuthorizationResponse.Reason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

class GoogleOAuthAuthorizationResponseTest {

    private val config = GoogleOAuthConfig.create("")
    private val request = GoogleOAuthAuthorizationRequest.create(config, GoogleOAuthPrompt.SELECT_ACCOUNT, random = SecureRandom())
    private val redirect = config.redirectUri

    private fun parse(uri: String) = GoogleOAuthAuthorizationResponse.parse(request, uri)

    @Test
    fun `code with the pending state is accepted, including Google's slash and extra parameters`() {
        val response = parse(
            "$redirect?state=${request.state}&iss=https://accounts.google.com&code=4/0AbCd-EF_gh&" +
                "scope=https://www.googleapis.com/auth/drive.appdata&authuser=0&prompt=consent"
        )

        assertTrue(response is GoogleOAuthAuthorizationResponse.Code)
        assertEquals("4/0AbCd-EF_gh", (response as GoogleOAuthAuthorizationResponse.Code).code)
        assertFalse(response.toString().contains("0AbCd"))
    }

    @Test
    fun `percent-encoded code is decoded`() {
        val response = parse("$redirect?code=4%2F0AbC&state=${request.state}")

        assertEquals("4/0AbC", (response as GoogleOAuthAuthorizationResponse.Code).code)
    }

    @Test
    fun `missing, different or repeated state is never acted on`() {
        assertEquals(GoogleOAuthAuthorizationResponse.Rejected(Reason.STATE_MISMATCH), parse("$redirect?code=abc"))
        assertEquals(
            GoogleOAuthAuthorizationResponse.Rejected(Reason.STATE_MISMATCH),
            parse("$redirect?code=abc&state=${request.state.reversed()}")
        )
        assertEquals(
            GoogleOAuthAuthorizationResponse.Rejected(Reason.STATE_MISMATCH),
            parse("$redirect?code=abc&state=")
        )
        assertEquals(
            GoogleOAuthAuthorizationResponse.Rejected(Reason.MALFORMED_QUERY),
            parse("$redirect?code=abc&state=${request.state}&state=${request.state}")
        )
        assertEquals(
            GoogleOAuthAuthorizationResponse.Rejected(Reason.MALFORMED_QUERY),
            parse("$redirect?code=abc&code=def&state=${request.state}")
        )
    }

    @Test
    fun `only the configured custom-scheme redirect path is accepted`() {
        val query = "?code=abc&state=${request.state}"
        listOf(
            "com.example.evil:/oauth2redirect$query",
            "${config.redirectScheme}://evil.example/oauth2redirect$query",
            "${config.redirectScheme}:/other$query",
            "${config.redirectScheme}:oauth2redirect$query",
            "https://accounts.google.com/oauth2redirect$query"
        ).forEach { uri ->
            assertEquals(uri, GoogleOAuthAuthorizationResponse.Rejected(Reason.WRONG_REDIRECT), parse(uri))
        }
        assertTrue(parse("${config.redirectScheme.uppercase()}:/oauth2redirect$query") is GoogleOAuthAuthorizationResponse.Code)
    }

    @Test
    fun `another issuer is a mix-up and is rejected`() {
        assertEquals(
            GoogleOAuthAuthorizationResponse.Rejected(Reason.WRONG_ISSUER),
            parse("$redirect?code=abc&state=${request.state}&iss=https://evil.example")
        )
    }

    @Test
    fun `access denied is a cancellation and other errors are reported`() {
        assertEquals(GoogleOAuthAuthorizationResponse.Denied, parse("$redirect?error=access_denied&state=${request.state}"))
        assertEquals(
            GoogleOAuthAuthorizationResponse.Error("invalid_request"),
            parse("$redirect?error=invalid_request&state=${request.state}")
        )
    }

    @Test
    fun `a response with both or neither of code and error is malformed`() {
        assertEquals(
            GoogleOAuthAuthorizationResponse.Rejected(Reason.MALFORMED_RESPONSE),
            parse("$redirect?code=abc&error=access_denied&state=${request.state}")
        )
        assertEquals(GoogleOAuthAuthorizationResponse.Rejected(Reason.MALFORMED_RESPONSE), parse("$redirect?state=${request.state}"))
        assertEquals(
            GoogleOAuthAuthorizationResponse.Rejected(Reason.MALFORMED_RESPONSE),
            parse("$redirect?code=${"a".repeat(2049)}&state=${request.state}")
        )
        assertEquals(
            GoogleOAuthAuthorizationResponse.Rejected(Reason.MALFORMED_RESPONSE),
            parse("$redirect?code=a%0Ab&state=${request.state}")
        )
    }

    @Test
    fun `unparseable or oversized URIs are rejected`() {
        assertEquals(GoogleOAuthAuthorizationResponse.Rejected(Reason.MALFORMED_URI), parse("$redirect?code=a b"))
        assertEquals(GoogleOAuthAuthorizationResponse.Rejected(Reason.MALFORMED_URI), parse("$redirect?code=%zz&state=${request.state}"))
        assertEquals(GoogleOAuthAuthorizationResponse.Rejected(Reason.MALFORMED_URI), parse("$redirect?code=${"a".repeat(9000)}"))
    }
}
