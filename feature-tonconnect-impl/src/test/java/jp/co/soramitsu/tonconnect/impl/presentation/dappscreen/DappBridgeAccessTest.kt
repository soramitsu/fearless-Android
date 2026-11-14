package jp.co.soramitsu.tonconnect.impl.presentation.dappscreen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DappBridgeAccessTest {

    @Test
    fun shouldIssueAFreshUnguessableTokenPerPage() {
        val tokens = List(100) { DappBridgeAccess.newPageToken() }

        assertEquals(tokens.size, tokens.toSet().size)
        tokens.forEach { token ->
            assertEquals(64, token.length)
            assertTrue(token, DappBridgeAccess.isWellFormedToken(token))
        }
    }

    @Test
    fun shouldAcceptACall_onlyWithThePageToken() {
        val pageToken = DappBridgeAccess.newPageToken()

        assertTrue(DappBridgeAccess.isTokenValid(pageToken, pageToken))
        assertFalse(DappBridgeAccess.isTokenValid(DappBridgeAccess.newPageToken(), pageToken))
        assertFalse(DappBridgeAccess.isTokenValid(pageToken.dropLast(1), pageToken))
        assertFalse(DappBridgeAccess.isTokenValid("", pageToken))
        assertFalse(DappBridgeAccess.isTokenValid(null, pageToken))
        // Before the trusted page has loaded, or on a page of another origin, there is no token.
        assertFalse(DappBridgeAccess.isTokenValid("", null))
        assertFalse(DappBridgeAccess.isTokenValid(pageToken, null))
    }

    @Test
    fun shouldTrustOnlyPagesOfTheDappOrigin() {
        listOf(
            "https://app.example.com",
            "https://app.example.com/swap?from=TON",
            "https://APP.example.com:443/#/pool"
        ).forEach { page -> assertTrue(page, DappBridgeAccess.isTrustedPage(page, ORIGIN)) }

        listOf(
            "https://evil.example.com/",
            "https://app.example.com.evil.io/",
            "https://app.example.com:8443/",
            "http://app.example.com/",
            "about:blank",
            "data:text/html,hi",
            null
        ).forEach { page -> assertFalse(page.toString(), DappBridgeAccess.isTrustedPage(page, ORIGIN)) }

        assertFalse(DappBridgeAccess.isTrustedPage("https://app.example.com/", null))
    }

    @Test
    fun shouldAuthorize_onlyTheCurrentTokenOnATrustedPage() {
        val pageToken = DappBridgeAccess.newPageToken()

        assertTrue(DappBridgeAccess.isAuthorized(pageToken, pageToken, "https://app.example.com/", ORIGIN))
        assertFalse(DappBridgeAccess.isAuthorized(pageToken, pageToken, "https://evil.example.com/", ORIGIN))
        assertFalse(DappBridgeAccess.isAuthorized(pageToken, DappBridgeAccess.newPageToken(), "https://app.example.com/", ORIGIN))
    }

    @Test
    fun shouldRejectTokens_thatCouldBreakOutOfTheInjectedScript() {
        assertFalse(DappBridgeAccess.isWellFormedToken("';alert(1);'"))
        assertFalse(DappBridgeAccess.isWellFormedToken("A".repeat(64)))
        assertFalse(DappBridgeAccess.isWellFormedToken("a".repeat(63)))
        assertNotEquals(DappBridgeAccess.newPageToken(), DappBridgeAccess.newPageToken())
    }

    private companion object {
        const val ORIGIN = "https://app.example.com"
    }
}
