package jp.co.soramitsu.backup.oauth

import jp.co.soramitsu.backup.GoogleSignInResult.Failure
import jp.co.soramitsu.backup.oauth.GoogleDriveSignInFlow.Step
import jp.co.soramitsu.backup.passkey.GoogleDriveHttpRequest
import jp.co.soramitsu.backup.passkey.GoogleDriveHttpResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.security.SecureRandom

/** The sign-in screen's state machine: browser once, one redirect, PKCE exchange, stored session. */
class GoogleDriveSignInFlowTest {

    private val config = GoogleOAuthConfig.create("")
    private val transport = FakeGoogleHttpTransport { tokenResponse() }
    private val store = InMemoryTokenStore()
    private val authorization = GoogleDriveAuthorization(
        config,
        GoogleOAuthTokenClient(config, transport),
        store,
        random = SecureRandom(),
        ioDispatcher = Dispatchers.Unconfined
    )
    private val flow = GoogleDriveSignInFlow(authorization)

    private fun openBrowser(prompt: GoogleOAuthPrompt = GoogleOAuthPrompt.SELECT_ACCOUNT): String {
        flow.begin(prompt)
        val step = flow.onResume(redirect = null)
        return (step as Step.OpenBrowser).url
    }

    private fun stateOf(url: String) = requireNotNull(url.toHttpUrl().queryParameter("state"))

    @Test
    fun `the browser opens once and the code is exchanged with its verifier`() = runBlocking {
        val url = openBrowser()
        val state = stateOf(url)

        val step = flow.onResume("${config.redirectUri}?state=$state&code=4/synthetic")

        assertTrue(step is Step.Exchange)
        assertEquals(null, flow.exchange(step as Step.Exchange))
        val form = transport.requests.single().form()
        assertEquals("4/synthetic", form["code"])
        assertEquals(
            url.toHttpUrl().queryParameter("code_challenge"),
            GoogleOAuthPkce.challengeS256(requireNotNull(form["code_verifier"]))
        )
        assertTrue(authorization.hasSession())
        assertFalse(flow.hasPendingRequest)
    }

    @Test
    fun `returning from the browser without a redirect is a cancellation`() {
        openBrowser()

        assertEquals(Step.Finish(Failure.CANCELLED), flow.onResume(redirect = null))
        assertFalse(flow.hasPendingRequest)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun `a redirect is consumed once and cannot be replayed`() {
        val state = stateOf(openBrowser())
        val redirect = "${config.redirectUri}?state=$state&code=4/synthetic"

        assertTrue(flow.onResume(redirect) is Step.Exchange)
        assertEquals(Step.Finish(Failure.INVALID_RESPONSE), flow.onResume(redirect))
    }

    @Test
    fun `a forged or foreign redirect never reaches the token endpoint`() {
        openBrowser()

        assertEquals(
            Step.Finish(Failure.INVALID_RESPONSE),
            flow.onResume("${config.redirectUri}?state=${"A".repeat(43)}&code=4/stolen")
        )
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun `nothing pending means nothing happens`() {
        assertEquals(Step.Finish(Failure.INVALID_RESPONSE), flow.onResume("${config.redirectUri}?state=x&code=y"))
        assertEquals(Step.Finish(Failure.INVALID_RESPONSE), flow.onResume(null))
    }

    @Test
    fun `the pending request survives the process being killed while the browser is open`() = runBlocking {
        val state = stateOf(openBrowser(GoogleOAuthPrompt.CONSENT))
        val saved = flow.save()

        val restored = GoogleDriveSignInFlow(authorization).apply { restore(saved) }
        val step = restored.onResume("${config.redirectUri}?state=$state&code=4/synthetic")

        assertNull(restored.exchange(step as Step.Exchange))
        assertTrue(authorization.hasSession())
    }

    @Test
    fun `a restored flow that already opened the browser treats a plain resume as cancellation`() {
        openBrowser()
        val restored = GoogleDriveSignInFlow(authorization).apply { restore(flow.save()) }

        assertEquals(Step.Finish(Failure.CANCELLED), restored.onResume(null))
    }

    @Test
    fun `Google's error redirects map to what the user can do next`() {
        val cases = mapOf(
            "access_denied" to Failure.CANCELLED,
            "invalid_request" to Failure.CONFIGURATION,
            "unauthorized_client" to Failure.CONFIGURATION,
            "invalid_scope" to Failure.CONFIGURATION,
            "temporarily_unavailable" to Failure.NETWORK,
            "server_error" to Failure.NETWORK,
            "something_else" to Failure.INVALID_RESPONSE
        )
        cases.forEach { (error, failure) ->
            val state = stateOf(openBrowser())
            assertEquals(error, Step.Finish(failure), flow.onResume("${config.redirectUri}?state=$state&error=$error"))
        }
    }

    @Test
    fun `exchange failures map to network, configuration, scope and storage problems`() = runBlocking {
        val responders = listOf<(GoogleDriveHttpRequest) -> GoogleDriveHttpResponse>(
            { throw IOException("offline") },
            { oauthError(500, "internal_failure") },
            { oauthError(401, "invalid_client") },
            { oauthError(400, "invalid_grant") },
            { tokenResponse(scope = "openid") }
        )
        val failures = listOf(
            Failure.NETWORK,
            Failure.NETWORK,
            Failure.CONFIGURATION,
            Failure.INVALID_RESPONSE,
            Failure.DRIVE_ACCESS_DENIED
        )
        responders.zip(failures).forEach { (responder, failure) ->
            transport.responder = responder
            val state = stateOf(openBrowser())
            val step = flow.onResume("${config.redirectUri}?state=$state&code=4/synthetic") as Step.Exchange
            assertEquals(failure, flow.exchange(step))
        }
        assertFalse(authorization.hasSession())

        val failingStore = object : GoogleOAuthTokenStore {
            override fun read(): String? = null
            override fun write(value: String) = error("storage unavailable")
            override fun clear() = Unit
        }
        val failingFlow = GoogleDriveSignInFlow(
            GoogleDriveAuthorization(config, GoogleOAuthTokenClient(config, transport), failingStore, ioDispatcher = Dispatchers.Unconfined)
        )
        transport.responder = { tokenResponse() }
        failingFlow.begin(GoogleOAuthPrompt.SELECT_ACCOUNT)
        val url = (failingFlow.onResume(null) as Step.OpenBrowser).url
        val step = failingFlow.onResume("${config.redirectUri}?state=${stateOf(url)}&code=4/synthetic") as Step.Exchange
        assertEquals(Failure.STORAGE, failingFlow.exchange(step))
    }

    @Test
    fun `saved state keeps the verifier only while a request is pending`() {
        assertEquals(mapOf("browserOpened" to "false"), flow.save())

        openBrowser()
        assertNotNull(flow.save()["codeVerifier"])

        flow.onResume(null)
        assertEquals(mapOf("browserOpened" to "true"), flow.save())
    }
}
