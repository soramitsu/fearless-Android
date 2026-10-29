package jp.co.soramitsu.backup.data

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import android.content.Intent
import android.text.TextUtils
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.Task
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.testing.http.MockHttpTransport
import com.google.api.client.testing.http.MockLowLevelHttpRequest
import com.google.api.client.testing.http.MockLowLevelHttpResponse
import com.google.api.services.drive.DriveScopes
import jp.co.soramitsu.backup.domain.exceptions.UnauthorizedException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

/** Actual storage, generated Drive requests and OAuth credential handlers; only platform services and HTTP are fixtures. */
@Suppress("DEPRECATION")
class GoogleDriveStorageCompatibilityTest {
    @Test
    fun `backup upload list download and delete retain app data scope and bearer authorization`() =
        withAndroidFramework {
            val context = mock<Context>()
            val accountManager = mock<AccountManager>()
            val account = mock<Account>()
            Account::class.java.getField("name").set(account, "fixture@example.invalid")
            val signedIn = mock<GoogleSignInAccount> {
                on { this.account } doReturn account
                on { isExpired } doReturn false
            }
            val transport = BackupTransport()
            org.mockito.kotlin.whenever(context.applicationContext).thenReturn(context)
            mockStatic(AccountManager::class.java).use { accounts ->
                mockStatic(GoogleSignIn::class.java).use { signIn ->
                    mockStatic(GoogleAuthUtil::class.java).use { auth ->
                        accounts.`when`<AccountManager> { AccountManager.get(context) }.thenReturn(accountManager)
                        signIn.`when`<GoogleSignInAccount> { GoogleSignIn.getLastSignedInAccount(context) }.thenReturn(signedIn)
                        auth.`when`<String> {
                            GoogleAuthUtil.getToken(context, "fixture@example.invalid", "oauth2: ${DriveScopes.DRIVE_APPDATA}")
                        }.thenReturn("synthetic-access-token")
                        val storage = GoogleDriveStorage(context, "", transport)
                        val content = """{"wallet":"synthetic encrypted backup"}"""

                        assertEquals("backup-1", storage.saveFile("fixture.json", "Fixture", content))
                        assertEquals("backup-1", storage.getListOfFileHeads().single().fileId)
                        assertEquals(content, storage.getFileContent("backup-1"))
                        storage.deleteFile("backup-1")
                        assertTrue(storage.getListOfFileHeads().isEmpty())
                        assertTrue(transport.requests > 5)
                        assertTrue(transport.authorizations.all { it == "Bearer synthetic-access-token" })
                        auth.verify({
                            GoogleAuthUtil.getToken(context, "fixture@example.invalid", "oauth2: ${DriveScopes.DRIVE_APPDATA}")
                        }, org.mockito.Mockito.atLeastOnce())
                    }
                }
            }
        }

    @Test
    fun `existing unexpired Google session needs no sign in launch`() = withAndroidFramework {
        val context = mock<Context> { on { applicationContext } doReturn it }
        val launcher = mock<ActivityResultLauncher<Intent>>()
        val account = mock<GoogleSignInAccount> { on { isExpired } doReturn false }
        mockStatic(GoogleSignIn::class.java).use { signIn ->
            signIn.`when`<GoogleSignInAccount> { GoogleSignIn.getLastSignedInAccount(context) }.thenReturn(account)
            assertTrue(GoogleDriveStorage(context, "", BackupTransport()).signIn(launcher))
            verify(launcher, never()).launch(any())
        }
    }

    @Test
    fun `silent sign in succeeds without opening consent`() = withAndroidFramework {
        val context = mock<Context> { on { applicationContext } doReturn it }
        val launcher = mock<ActivityResultLauncher<Intent>>()
        val task = completedTask(success = true)
        val client = mock<GoogleSignInClient> { on { silentSignIn() } doReturn task }
        mockStatic(GoogleSignIn::class.java).use { signIn ->
            signIn.`when`<GoogleSignInClient> {
                GoogleSignIn.getClient(org.mockito.kotlin.eq(context), any<GoogleSignInOptions>())
            }.thenReturn(client)
            assertTrue(GoogleDriveStorage(context, "", BackupTransport()).signIn(launcher))
            verify(launcher, never()).launch(any())
        }
    }

    @Test
    fun `failed or cancelled silent sign in launches the native sign in intent`() = withAndroidFramework {
        for (cancelled in listOf(false, true)) {
            val context = mock<Context> { on { applicationContext } doReturn it }
            val intent = mock<Intent>()
            val launcher = mock<ActivityResultLauncher<Intent>>()
            val task = completedTask(success = false, cancelled = cancelled)
            val client = mock<GoogleSignInClient> {
                on { silentSignIn() } doReturn task
                on { signInIntent } doReturn intent
            }
            mockStatic(GoogleSignIn::class.java).use { signIn ->
                signIn.`when`<GoogleSignInClient> {
                    GoogleSignIn.getClient(org.mockito.kotlin.eq(context), any<GoogleSignInOptions>())
                }.thenReturn(client)
                assertFalse(GoogleDriveStorage(context, "", BackupTransport()).signIn(launcher))
                verify(launcher).launch(intent)
            }
        }
    }

    @Test
    fun `failed silent authorization blocks backup HTTP instead of reporting success`() = withAndroidFramework {
        val context = mock<Context> { on { applicationContext } doReturn it }
        val failure = IllegalStateException("synthetic sign-in failure")
        val task = completedTask(success = false, failure = failure)
        val client = mock<GoogleSignInClient> { on { silentSignIn() } doReturn task }
        val transport = BackupTransport()
        mockStatic(GoogleSignIn::class.java).use { signIn ->
            signIn.`when`<GoogleSignInClient> {
                GoogleSignIn.getClient(org.mockito.kotlin.eq(context), any<GoogleSignInOptions>())
            }.thenReturn(client)
            try {
                GoogleDriveStorage(context, "", transport).saveFile("fixture.json", "Fixture", "{}")
                fail("unauthorized backup must fail")
            } catch (error: UnauthorizedException) {
                assertSame(failure, error.cause)
            }
            assertEquals(0, transport.requests)
        }
    }

    @Test
    fun `expired bearer token is cleared and retried once while repeated unauthorized response fails`() =
        withAndroidFramework {
            for (refreshSucceeds in listOf(true, false)) {
                val context = mock<Context>()
                org.mockito.kotlin.whenever(context.applicationContext).thenReturn(context)
                val accountManager = mock<AccountManager>()
                val account = mock<Account>()
                Account::class.java.getField("name").set(account, "fixture@example.invalid")
                val signedIn = mock<GoogleSignInAccount> {
                    on { this.account } doReturn account
                    on { isExpired } doReturn false
                }
                val authorizations = mutableListOf<String?>()
                val transport = object : MockHttpTransport() {
                    override fun buildRequest(method: String, url: String) = object : MockLowLevelHttpRequest(url) {
                        override fun execute(): MockLowLevelHttpResponse {
                            authorizations += getFirstHeaderValue("Authorization")
                            val accepted = refreshSucceeds && authorizations.size == 2
                            return MockLowLevelHttpResponse().setContentType("application/json")
                                .setStatusCode(if (accepted) 200 else 401)
                                .setContent(if (accepted) """{"files":[]}""" else """{"error":{"code":401,"message":"invalid token"}}""")
                        }
                    }
                }
                mockStatic(AccountManager::class.java).use { accounts ->
                    mockStatic(GoogleSignIn::class.java).use { signIn ->
                        mockStatic(GoogleAuthUtil::class.java).use { auth ->
                            accounts.`when`<AccountManager> { AccountManager.get(context) }.thenReturn(accountManager)
                            signIn.`when`<GoogleSignInAccount> { GoogleSignIn.getLastSignedInAccount(context) }.thenReturn(signedIn)
                            auth.`when`<String> {
                                GoogleAuthUtil.getToken(context, "fixture@example.invalid", "oauth2: ${DriveScopes.DRIVE_APPDATA}")
                            }.thenReturn("expired-synthetic-token", "fresh-synthetic-token")
                            val storage = GoogleDriveStorage(context, "", transport)
                            if (refreshSucceeds) {
                                assertTrue(storage.getListOfFileHeads().isEmpty())
                            } else {
                                try {
                                    storage.getListOfFileHeads()
                                    fail("a repeated unauthorized response must fail")
                                } catch (error: GoogleJsonResponseException) {
                                    assertEquals(401, error.statusCode)
                                }
                            }
                            assertEquals(listOf("Bearer expired-synthetic-token", "Bearer fresh-synthetic-token"), authorizations)
                            auth.verify { GoogleAuthUtil.clearToken(context, "expired-synthetic-token") }
                        }
                    }
                }
            }
        }

    // Google Play Services initializes these Android primitives before its mocked account APIs.
    // Keep every static mock on the IO thread used by the actual storage adapter.
    private fun withAndroidFramework(block: suspend CoroutineScope.() -> Unit) = runBlocking(Dispatchers.IO) {
        mockStatic(Log::class.java).use {
            mockStatic(TextUtils::class.java).use { text ->
                text.`when`<Boolean> { TextUtils.isEmpty(org.mockito.kotlin.anyOrNull()) }
                    .thenAnswer { invocation -> invocation.getArgument<CharSequence?>(0).isNullOrEmpty() }
                block()
            }
        }
    }

    private fun completedTask(
        success: Boolean,
        cancelled: Boolean = false,
        failure: Exception? = null
    ): Task<GoogleSignInAccount> {
        val task = mock<Task<GoogleSignInAccount>>()
        org.mockito.kotlin.whenever(task.isSuccessful).thenReturn(success)
        org.mockito.kotlin.whenever(task.isCanceled).thenReturn(cancelled)
        org.mockito.kotlin.whenever(task.exception).thenReturn(failure)
        doAnswer { invocation ->
            invocation.getArgument<OnCompleteListener<GoogleSignInAccount>>(0).onComplete(task)
            task
        }.`when`(task).addOnCompleteListener(any())
        return task
    }

    private class BackupTransport : MockHttpTransport() {
        var requests = 0
        val authorizations = mutableListOf<String?>()
        private var stored: String? = null

        override fun buildRequest(method: String, url: String) = object : MockLowLevelHttpRequest(url) {
            override fun execute(): MockLowLevelHttpResponse {
                requests++
                authorizations += getFirstHeaderValue("Authorization")
                val response = MockLowLevelHttpResponse().setContentType("application/json")
                return when {
                    url.contains("uploadType=resumable") -> response.addHeader("Location", "https://www.googleapis.com/upload/drive/v3/files?upload_id=fixture")
                    url.contains("upload_id=fixture") -> {
                        stored = contentAsString
                        response.setContent("""{"id":"backup-1"}""")
                    }
                    method == "DELETE" -> {
                        stored = null
                        response.setStatusCode(204)
                    }
                    url.contains("/download/") -> response.setContent(stored ?: error("backup missing"))
                    url.contains("backupFolder") -> response.setContent("""{"files":[{"id":"folder-1"}]}""")
                    stored != null -> response.setContent("""{"files":[{"id":"backup-1","name":"fixture.json","description":"Fixture"}]}""")
                    else -> response.setContent("""{"files":[]}""")
                }
            }
        }
    }
}
