package jp.co.soramitsu.backup.passkey

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class GoogleDrivePasskeyBackupTokenProviderTest {
    @Test
    fun `token provider trims account and token and requests Drive appdata oauth scope`() = runBlocking {
        val fetcher = RecordingTokenFetcher(" token-123 ")
        val provider = GoogleDrivePasskeyBackupTokenProvider(
            accountNameProvider = GoogleDriveAccountNameProvider { " user@example.com " },
            tokenFetcher = fetcher
        )

        val accessToken = provider.accessToken()

        assertEquals("token-123", accessToken)
        assertEquals("user@example.com", fetcher.accountName)
        assertEquals(GoogleDrivePasskeyBackup.OAUTH_APP_DATA_SCOPE, fetcher.oauthScope)
    }

    @Test
    fun `oauth scope is pinned to Google Drive appdata`() {
        assertEquals(
            "oauth2:${GoogleDrivePasskeyBackup.APP_DATA_SCOPE}",
            GoogleDrivePasskeyBackup.OAUTH_APP_DATA_SCOPE
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank Google account is rejected before token fetch`() {
        val fetcher = RecordingTokenFetcher("token-123")
        val provider = GoogleDrivePasskeyBackupTokenProvider(
            accountNameProvider = GoogleDriveAccountNameProvider { "   " },
            tokenFetcher = fetcher
        )

        try {
            runBlocking {
                provider.accessToken()
            }
        } finally {
            assertFalse(fetcher.wasCalled)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `malformed Google account is rejected before token fetch`() {
        val fetcher = RecordingTokenFetcher("token-123")
        val provider = GoogleDrivePasskeyBackupTokenProvider(
            accountNameProvider = GoogleDriveAccountNameProvider { "user example.com" },
            tokenFetcher = fetcher
        )

        try {
            runBlocking {
                provider.accessToken()
            }
        } finally {
            assertFalse(fetcher.wasCalled)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `Google account without email shape is rejected before token fetch`() {
        val fetcher = RecordingTokenFetcher("token-123")
        val provider = GoogleDrivePasskeyBackupTokenProvider(
            accountNameProvider = GoogleDriveAccountNameProvider { "user" },
            tokenFetcher = fetcher
        )

        try {
            runBlocking {
                provider.accessToken()
            }
        } finally {
            assertFalse(fetcher.wasCalled)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank Google access token is rejected`() {
        val provider = GoogleDrivePasskeyBackupTokenProvider(
            accountNameProvider = GoogleDriveAccountNameProvider { "user@example.com" },
            tokenFetcher = RecordingTokenFetcher("   ")
        )

        runBlocking {
            provider.accessToken()
        }
    }

    private class RecordingTokenFetcher(
        private val token: String
    ) : GoogleDriveOAuthTokenFetcher {
        var wasCalled = false
            private set
        var accountName: String? = null
            private set
        var oauthScope: String? = null
            private set

        override suspend fun fetchAccessToken(accountName: String, oauthScope: String): String {
            wasCalled = true
            this.accountName = accountName
            this.oauthScope = oauthScope
            return token
        }
    }
}
