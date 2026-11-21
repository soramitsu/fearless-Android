package jp.co.soramitsu.account.impl.presentation.google_signin

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import java.io.IOException
import jp.co.soramitsu.backup.GoogleSignInResult
import jp.co.soramitsu.backup.oauth.GoogleDriveAuthorization
import jp.co.soramitsu.backup.oauth.GoogleOAuthConfig
import jp.co.soramitsu.backup.oauth.GoogleOAuthPrompt
import jp.co.soramitsu.backup.oauth.GoogleOAuthTokenClient
import jp.co.soramitsu.backup.oauth.GoogleOAuthTokenStore
import jp.co.soramitsu.backup.passkey.GoogleDriveHttpRequest
import jp.co.soramitsu.backup.passkey.GoogleDriveHttpResponse
import jp.co.soramitsu.backup.passkey.GoogleDriveHttpTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GoogleDriveSignInViewModelTest {
    @get:Rule val instantTaskRule = InstantTaskExecutorRule()
    private val dispatcher = StandardTestDispatcher()

    private val config = GoogleOAuthConfig.create("")
    private var tokenAnswer: () -> GoogleDriveHttpResponse = {
        GoogleDriveHttpResponse(
            200,
            """{"access_token":"ya29.synthetic","token_type":"Bearer","expires_in":3599,"refresh_token":"1//synthetic","scope":"${GoogleOAuthConfig.DRIVE_APPDATA_SCOPE}"}"""
                .toByteArray()
        )
    }
    private val requests = mutableListOf<GoogleDriveHttpRequest>()
    private val transport = object : GoogleDriveHttpTransport {
        override suspend fun execute(request: GoogleDriveHttpRequest): GoogleDriveHttpResponse {
            requests += request
            return tokenAnswer()
        }
    }
    private val store = object : GoogleOAuthTokenStore {
        var value: String? = null
        override fun read(): String? = value
        override fun write(value: String) {
            this.value = value
        }
        override fun clear() {
            value = null
        }
    }
    private val authorization = GoogleDriveAuthorization(
        config,
        GoogleOAuthTokenClient(config, transport),
        store,
        ioDispatcher = Dispatchers.Unconfined
    )

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun stateOf(url: String) = requireNotNull(url.toHttpUrl().queryParameter("state"))

    @Test
    fun `first resume opens Google's page once and the redirect stores the session`() = runTest(dispatcher) {
        val viewModel = GoogleDriveSignInViewModel(authorization, SavedStateHandle())
        viewModel.start(GoogleOAuthPrompt.SELECT_ACCOUNT)

        val url = requireNotNull(viewModel.onResume(null))
        assertEquals("select_account", url.toHttpUrl().queryParameter("prompt"))
        assertNull(viewModel.onResume("${config.redirectUri}?state=${stateOf(url)}&code=4/synthetic"))
        runCurrent()

        assertEquals(GoogleDriveSignInViewModel.Outcome.Success, viewModel.outcome.value)
        assertNotNull(store.value)
        assertEquals(1, requests.size)
    }

    @Test
    fun `leaving Google's page without a redirect cancels`() = runTest(dispatcher) {
        val viewModel = GoogleDriveSignInViewModel(authorization, SavedStateHandle())
        viewModel.start(GoogleOAuthPrompt.CONSENT)
        viewModel.onResume(null)

        assertNull(viewModel.onResume(null))

        assertEquals(
            GoogleDriveSignInViewModel.Outcome.Failed(GoogleSignInResult.Failure.CANCELLED),
            viewModel.outcome.value
        )
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `a recreated screen continues the saved sign-in instead of starting another`() = runTest(dispatcher) {
        val savedState = SavedStateHandle()
        val first = GoogleDriveSignInViewModel(authorization, savedState)
        first.start(GoogleOAuthPrompt.SELECT_ACCOUNT)
        val url = requireNotNull(first.onResume(null))

        val recreated = GoogleDriveSignInViewModel(authorization, savedState)
        recreated.start(GoogleOAuthPrompt.SELECT_ACCOUNT)
        recreated.onResume("${config.redirectUri}?state=${stateOf(url)}&code=4/synthetic")
        runCurrent()

        assertEquals(GoogleDriveSignInViewModel.Outcome.Success, recreated.outcome.value)
    }

    @Test
    fun `a failed exchange reports why and keeps nothing`() = runTest(dispatcher) {
        tokenAnswer = { throw IOException("offline") }
        val viewModel = GoogleDriveSignInViewModel(authorization, SavedStateHandle())
        viewModel.start(GoogleOAuthPrompt.SELECT_ACCOUNT)
        val url = requireNotNull(viewModel.onResume(null))

        viewModel.onResume("${config.redirectUri}?state=${stateOf(url)}&code=4/synthetic")
        runCurrent()

        assertEquals(
            GoogleDriveSignInViewModel.Outcome.Failed(GoogleSignInResult.Failure.NETWORK),
            viewModel.outcome.value
        )
        assertNull(store.value)
    }

    @Test
    fun `no browser is a configuration failure and nothing more happens`() = runTest(dispatcher) {
        val viewModel = GoogleDriveSignInViewModel(authorization, SavedStateHandle())
        viewModel.start(GoogleOAuthPrompt.SELECT_ACCOUNT)
        viewModel.onResume(null)

        viewModel.onBrowserUnavailable()

        assertEquals(
            GoogleDriveSignInViewModel.Outcome.Failed(GoogleSignInResult.Failure.CONFIGURATION),
            viewModel.outcome.value
        )
        assertNull(viewModel.onResume("${config.redirectUri}?state=x&code=y"))
    }
}
