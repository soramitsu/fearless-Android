package jp.co.soramitsu.backup.data

import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import jp.co.soramitsu.backup.BackupService
import jp.co.soramitsu.backup.domain.exceptions.UnauthorizedException
import jp.co.soramitsu.backup.domain.models.BackupAccountMeta
import jp.co.soramitsu.backup.oauth.GoogleDriveAuthorization
import jp.co.soramitsu.backup.oauth.GoogleOAuthConfig
import jp.co.soramitsu.backup.oauth.GoogleOAuthPrompt
import jp.co.soramitsu.backup.oauth.GoogleOAuthSession
import jp.co.soramitsu.backup.oauth.GoogleOAuthTokenClient
import jp.co.soramitsu.backup.oauth.InMemoryTokenStore
import jp.co.soramitsu.backup.oauth.tokenResponse
import jp.co.soramitsu.backup.passkey.GoogleDriveHttpRequest
import jp.co.soramitsu.backup.passkey.GoogleDriveHttpResponse
import jp.co.soramitsu.backup.passkey.GoogleDriveHttpTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

/** The actual storage, OAuth session and Drive requests together; only Google's servers are fakes. */
class GoogleDriveStorageCompatibilityTest {

    private val config = GoogleOAuthConfig.create("")
    private val drive = FakeDrive()
    private val oauthRequests = mutableListOf<GoogleDriveHttpRequest>()
    private val transport = object : GoogleDriveHttpTransport {
        override suspend fun execute(request: GoogleDriveHttpRequest): GoogleDriveHttpResponse {
            if (!request.url.startsWith("https://oauth2.googleapis.com/")) return drive.execute(request)
            oauthRequests += request
            return tokenResponse(accessToken = "ya29.synthetic", refreshToken = null)
        }
    }
    private val store = InMemoryTokenStore()
    private val authorization = GoogleDriveAuthorization(
        config,
        GoogleOAuthTokenClient(config, transport),
        store,
        ioDispatcher = Dispatchers.Unconfined
    )
    private val signInIntent = mock<Intent>()
    private val prompts = mutableListOf<GoogleOAuthPrompt>()
    private val storage = GoogleDriveStorage(
        authorization,
        GoogleDriveFiles(GoogleDriveApi(transport, authorization)) { null }
    ) { prompt ->
        prompts += prompt
        signInIntent
    }

    private fun signedIn() {
        store.value = GoogleOAuthSession(config.clientId, "1//synthetic-refresh", setOf(GoogleOAuthConfig.DRIVE_APPDATA_SCOPE), 1L).encode()
    }

    @Test
    fun `backup upload list download and delete use the stored session's bearer`() = runBlocking {
        signedIn()
        val content = """{"wallet":"synthetic encrypted backup"}"""

        val fileId = storage.saveFile("fixture.json", "Fixture", content)
        assertEquals(fileId, storage.getListOfFileHeads().single().fileId)
        assertEquals(content, storage.getFileContent(fileId))
        storage.deleteFile(fileId)

        assertTrue(storage.getListOfFileHeads().isEmpty())
        assertTrue(drive.requests.size > 5)
        assertTrue(drive.requests.all { it.headers["Authorization"] == "Bearer ya29.synthetic" })
        // One refresh served every request.
        assertEquals(1, oauthRequests.size)
    }

    @Test
    fun `an existing session needs no sign-in screen`() = runBlocking {
        signedIn()
        val launcher = mock<ActivityResultLauncher<Intent>>()

        assertTrue(storage.signIn(launcher))

        verify(launcher, never()).launch(any())
        assertTrue(oauthRequests.isEmpty())
    }

    @Test
    fun `asking whether a session exists opens no sign-in screen`() = runBlocking {
        assertFalse(storage.hasSession())
        assertTrue(prompts.isEmpty())

        signedIn()

        assertTrue(storage.hasSession())
        assertTrue(prompts.isEmpty())
        assertTrue(oauthRequests.isEmpty())
    }

    @Test
    fun `without a session the browser sign-in opens with the account chooser`() = runBlocking {
        val launcher = mock<ActivityResultLauncher<Intent>>()

        assertFalse(storage.signIn(launcher))

        verify(launcher).launch(signInIntent)
        assertEquals(listOf(GoogleOAuthPrompt.SELECT_ACCOUNT), prompts)
    }

    @Test
    fun `a launcher whose screen is gone still reports not signed in`() = runBlocking {
        val launcher = mock<ActivityResultLauncher<Intent>> {
            on { launch(any()) } doThrow IllegalStateException("unregistered")
        }

        assertFalse(storage.signIn(launcher))
    }

    @Test
    fun `failed authorization blocks backup HTTP instead of reporting success`() = runBlocking {
        val failure = runCatching { storage.saveFile("fixture.json", "Fixture", "{}") }.exceptionOrNull()

        assertTrue(failure is UnauthorizedException)
        assertTrue(drive.requests.isEmpty())
        assertTrue(oauthRequests.isEmpty())
    }

    @Test
    fun `logout forgets the session here without contacting Google`() = runBlocking {
        signedIn()

        storage.logout()

        assertNull(store.value)
        assertTrue(oauthRequests.isEmpty())
        assertTrue(drive.requests.isEmpty())
    }

    @Test
    fun `backups other apps and versions left in Drive are listed as before`() = runBlocking {
        signedIn()
        // 4.2.0 Android and iOS: app backups named by address in the backupFolder folder.
        drive.add(StoredDriveFile("folder-420", "backupFolder", FOLDER, listOf("appDataFolder")))
        drive.add(
            StoredDriveFile(
                "android-420", "5Gaddress.json", "text/json", listOf("folder-420"), "Old wallet",
                modifiedTime = "2025-01-01T00:00:00.000Z"
            )
        )
        drive.add(
            StoredDriveFile(
                "ios-1", "0xethaddress.json", "application/json", listOf("folder-420"), "iPhone wallet",
                modifiedTime = "2025-06-01T00:00:00.000Z"
            )
        )
        // Chrome extension: polkadot-js JSON in the app data root, described by address[/eth file id].
        drive.add(StoredDriveFile("ext-sub", "Extension wallet.json", "application/json", listOf("appDataFolder"), "5Gext/ext-eth"))
        drive.add(StoredDriveFile("ext-eth", "Extension wallet eth.json", "application/json", listOf("appDataFolder"), "0xext"))
        // Passkey backups are not wallets.
        drive.add(StoredDriveFile("passkey-1", "fearless-passkey-backup-k.bin", "application/octet-stream", listOf("appDataFolder")))
        val service = BackupService.create(authorization, transport) { null }

        assertEquals(
            listOf(BackupAccountMeta("Old wallet", "5Gaddress", "android-420"), BackupAccountMeta("iPhone wallet", "0xethaddress", "ios-1")),
            service.getBackupAccounts()
        )
        assertEquals(listOf(BackupAccountMeta("Extension wallet", "5Gext", "ext-sub")), service.getWebBackupAccounts())

        // A new backup lands beside them, in the same folder and with the same metadata shape.
        val fileId = storage.saveFile("5Gaddress.json", "Old wallet", "{}")
        val written = drive.files.getValue(fileId)
        assertEquals(listOf("folder-420"), written.parents)
        assertEquals("text/json", written.mimeType)
        assertEquals("5Gaddress.json", written.name)
        assertEquals("Old wallet", written.description)
        assertEquals(1, drive.files.values.count { it.name == "backupFolder" })
        val copies = service.getBackupCopies().filter { copy -> copy.address == "5Gaddress" }
        assertEquals(listOf(fileId, "android-420"), copies.map { copy -> copy.fileId })
    }

    private companion object {
        const val FOLDER = "application/vnd.google-apps.folder"
    }
}
