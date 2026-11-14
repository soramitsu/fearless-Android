package jp.co.soramitsu.backup.passkey

import jp.co.soramitsu.backup.domain.exceptions.UnauthorizedException
import jp.co.soramitsu.backup.oauth.FakeGoogleHttpTransport
import jp.co.soramitsu.backup.oauth.GoogleDriveAuthorization
import jp.co.soramitsu.backup.oauth.GoogleOAuthConfig
import jp.co.soramitsu.backup.oauth.GoogleOAuthSession
import jp.co.soramitsu.backup.oauth.GoogleOAuthTokenClient
import jp.co.soramitsu.backup.oauth.InMemoryTokenStore
import jp.co.soramitsu.backup.oauth.tokenResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleDriveAuthorizationTokenFetcherTest {

    private val config = GoogleOAuthConfig.create("")
    private val transport = FakeGoogleHttpTransport {
        tokenResponse(accessToken = "ya29.passkey", refreshToken = null, scope = null)
    }
    private val store = InMemoryTokenStore()
    private val authorization = GoogleDriveAuthorization(
        config,
        GoogleOAuthTokenClient(config, transport),
        store,
        ioDispatcher = Dispatchers.Unconfined
    )
    private val fetcher = GoogleDriveAuthorizationTokenFetcher(authorization)

    private fun signedInWith(vararg scopes: String) {
        store.value = GoogleOAuthSession(config.clientId, "1//synthetic-refresh", scopes.toSet(), 1L).encode()
    }

    @Test
    fun `oauth2 scope strings expand Google's short identity scopes`() {
        assertEquals(
            setOf(
                GoogleOAuthConfig.DRIVE_APPDATA_SCOPE,
                "openid",
                "https://www.googleapis.com/auth/userinfo.email"
            ),
            GoogleDriveAuthorizationTokenFetcher.requiredScopes(GoogleDrivePasskeyBackup.OAUTH_IDENTITY_APP_DATA_SCOPE)
        )
        assertTrue(runCatching { GoogleDriveAuthorizationTokenFetcher.requiredScopes("drive.appdata") }.isFailure)
        assertTrue(runCatching { GoogleDriveAuthorizationTokenFetcher.requiredScopes("oauth2: ") }.isFailure)
    }

    @Test
    fun `the backup sign-in alone does not grant the identity scopes passkey backup needs`() = runBlocking {
        signedInWith(GoogleOAuthConfig.DRIVE_APPDATA_SCOPE)

        val failure = runCatching {
            fetcher.fetchAccessToken("owner@example.com", GoogleDrivePasskeyBackup.OAUTH_IDENTITY_APP_DATA_SCOPE)
        }.exceptionOrNull()

        assertTrue(failure is UnauthorizedException)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun `a sign-in granted every requested scope supplies the access token`() = runBlocking {
        signedInWith(GoogleOAuthConfig.DRIVE_APPDATA_SCOPE, "openid", "https://www.googleapis.com/auth/userinfo.email")

        assertEquals(
            "ya29.passkey",
            fetcher.fetchAccessToken("owner@example.com", GoogleDrivePasskeyBackup.OAUTH_IDENTITY_APP_DATA_SCOPE)
        )
        assertEquals("ya29.passkey", fetcher.fetchAccessToken("owner@example.com", GoogleDrivePasskeyBackup.OAUTH_APP_DATA_SCOPE))
    }

    @Test
    fun `nobody signed in gets no token`() = runBlocking {
        val failure = runCatching {
            fetcher.fetchAccessToken("owner@example.com", GoogleDrivePasskeyBackup.OAUTH_APP_DATA_SCOPE)
        }.exceptionOrNull()

        assertTrue(failure is UnauthorizedException)
    }
}
