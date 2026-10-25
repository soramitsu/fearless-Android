package jp.co.soramitsu.backup.passkey

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleDrivePasskeyBackupTokenProviderTest {
    @Test
    fun `token provider verifies same bearer with openid email and appdata consent`() = runBlocking {
        val fetcher = RecordingTokenFetcher(" token-123 ")
        var verifiedToken: String? = null
        val provider = GoogleDrivePasskeyBackupTokenProvider(
            accountNameProvider = GoogleDriveAccountNameProvider { " user@example.com " },
            tokenFetcher = fetcher,
            identityVerifier = GoogleDriveIdentityVerifier {
                verifiedToken = it
                identity()
            }
        )
        val access = provider.accessToken()
        assertEquals("token-123", access.accessToken)
        assertEquals("user@example.com", access.accountName)
        assertEquals("stable-google-subject", access.subject)
        assertEquals("user@example.com", fetcher.accountName)
        assertEquals("token-123", verifiedToken)
        assertEquals("oauth2:https://www.googleapis.com/auth/drive.appdata openid email", fetcher.oauthScope)
    }

    @Test
    fun `legacy appdata scope remains distinct from explicit identity consent`() {
        assertEquals("oauth2:${GoogleDrivePasskeyBackup.APP_DATA_SCOPE}", GoogleDrivePasskeyBackup.OAUTH_APP_DATA_SCOPE)
        assertTrue(GoogleDrivePasskeyBackup.OAUTH_IDENTITY_APP_DATA_SCOPE.endsWith(" openid email"))
        assertFalse(GoogleDrivePasskeyBackup.OAUTH_IDENTITY_APP_DATA_SCOPE.contains("profile"))
    }

    @Test
    fun `malformed selected account is rejected before token fetch`() = runBlocking {
        for (name in listOf("", "   ", "user", "user example.com", "user\n@example.com")) {
            val fetcher = RecordingTokenFetcher("token-123")
            val provider = provider(name = { name }, fetcher = fetcher)
            assertTrue(runCatching { provider.accessToken() }.isFailure)
            assertFalse(fetcher.wasCalled)
        }
    }

    @Test
    fun `blank or header unsafe access token is rejected before identity network`() = runBlocking {
        for (token in listOf("", "   ", "bearer\nsecret", "token; injection", "x".repeat(4097))) {
            val provider = GoogleDrivePasskeyBackupTokenProvider(
                GoogleDriveAccountNameProvider { "user@example.com" }, RecordingTokenFetcher(token),
                GoogleDriveIdentityVerifier { throw AssertionError("Unvalidated bearer reached identity request") }
            )
            assertTrue(runCatching { provider.accessToken() }.exceptionOrNull() is IllegalArgumentException)
        }
    }

    @Test
    fun `initial verified email must match selected account before subject pin`() = runBlocking {
        var email = "other@example.com"
        val provider = provider(verify = { identity(email = email) })
        assertTrue(runCatching { provider.accessToken() }.isFailure)
        email = "user@example.com"
        assertEquals("stable-google-subject", provider.accessToken().subject)
    }

    @Test
    fun `sub survives email rename while original selector alias may remain`() = runBlocking {
        var email = "user@example.com"
        val provider = provider(verify = { identity(email = email) })
        val original = provider.accessToken()
        email = "renamed@example.com"
        val renamed = provider.accessToken()
        assertEquals(original.subject, renamed.subject)
        assertEquals("renamed@example.com", renamed.accountName)
    }

    @Test
    fun `same email with different subject cannot replace pinned account`() = runBlocking {
        var subject = "stable-google-subject"
        val provider = provider(verify = { identity(subject = subject) })
        provider.accessToken()
        subject = "another-google-subject"
        assertTrue(runCatching { provider.accessToken() }.isFailure)
        subject = "stable-google-subject"
        assertEquals(subject, provider.accessToken().subject)
    }

    @Test
    fun `account selection change during token or userinfo fetch fails closed`() = runBlocking {
        for (stage in listOf("token", "identity")) {
            var selected = "user@example.com"
            val provider = GoogleDrivePasskeyBackupTokenProvider(
                GoogleDriveAccountNameProvider { selected },
                GoogleDriveOAuthTokenFetcher { _, _ ->
                    if (stage == "token") selected = "other@example.com"
                    "token-123"
                },
                GoogleDriveIdentityVerifier {
                    if (stage == "identity") selected = "other@example.com"
                    identity()
                }
            )
            assertTrue(runCatching { provider.accessToken() }.isFailure)
        }
    }

    @Test
    fun `concurrent account switch cannot repin first accepted subject`() = runBlocking {
        val reached = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var selected = "user@example.com"
        var first = true
        val provider = provider(name = { selected }, verify = {
            if (first) {
                first = false
                reached.complete(Unit)
                release.await()
                identity()
            } else {
                identity(subject = "another-google-subject", email = selected)
            }
        })
        val accepted = async { provider.accessToken() }
        reached.await()
        val queued = async { runCatching { provider.accessToken() } }
        release.complete(Unit)
        assertEquals("stable-google-subject", accepted.await().subject)
        selected = "other@example.com"
        assertTrue(queued.await().isFailure)
    }

    @Test
    fun `cancelled proof does not expose access or pin a subject`() = runBlocking {
        val reached = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var first = true
        val provider = provider(verify = {
            if (first) {
                first = false
                reached.complete(Unit)
                release.await()
            }
            identity()
        })
        val task = async { provider.accessToken() }
        reached.await()
        task.cancelAndJoin()
        assertTrue(task.isCancelled)
        assertEquals("stable-google-subject", provider.accessToken().subject)
    }

    @Test
    fun `missing identity proof has no email based fallback`() = runBlocking {
        val provider = provider(verify = { throw IllegalStateException("identity unavailable") })
        assertTrue(runCatching { provider.accessToken() }.isFailure)
    }

    private fun provider(
        name: suspend () -> String = { "user@example.com" },
        fetcher: GoogleDriveOAuthTokenFetcher = RecordingTokenFetcher("token-123"),
        verify: suspend (String) -> GoogleDriveVerifiedIdentity = { identity() }
    ) = GoogleDrivePasskeyBackupTokenProvider(
        GoogleDriveAccountNameProvider { name() }, fetcher, GoogleDriveIdentityVerifier { verify(it) }
    )

    private fun identity(subject: String = "stable-google-subject", email: String = "user@example.com") =
        GoogleDriveVerifiedIdentity(subject, email, true)

    private class RecordingTokenFetcher(private val token: String) : GoogleDriveOAuthTokenFetcher {
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
