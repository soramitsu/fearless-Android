package jp.co.soramitsu.backup.data

import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import jp.co.soramitsu.backup.domain.Storage
import jp.co.soramitsu.backup.domain.models.FileMeta
import jp.co.soramitsu.backup.oauth.GoogleDriveAuthorization
import jp.co.soramitsu.backup.oauth.GoogleOAuthPrompt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Google sign-in for the Drive app data scope through the app's own OAuth 2.0 client: the browser
 * shows Google's page and the session lives in [authorization]. The requests live in [GoogleDriveFiles].
 */
internal class GoogleDriveStorage(
    private val authorization: GoogleDriveAuthorization,
    private val files: GoogleDriveFiles,
    /** The screen that runs Google's sign-in page in the browser and stores the result. */
    private val signInIntent: (GoogleOAuthPrompt) -> Intent?
) : Storage {

    override suspend fun saveFile(
        name: String,
        description: String,
        content: String
    ): String {
        return withContext(Dispatchers.IO) {
            files.createFile(name, description, content)
        }
    }

    override suspend fun signIn(activityResultLauncher: ActivityResultLauncher<Intent>): Boolean {
        if (authorization.hasSession()) return true

        // The launcher may be unregistered because its screen is gone; the caller gets false either way.
        signInIntent(GoogleOAuthPrompt.SELECT_ACCOUNT)?.let { intent ->
            runCatching { activityResultLauncher.launch(intent) }
        }
        return false
    }

    override suspend fun hasSession(): Boolean = authorization.hasSession()

    override suspend fun getFileContent(fileId: String): String {
        return withContext(Dispatchers.IO) {
            files.getFileContent(fileId)
        }
    }

    override suspend fun getListOfFileHeads(): List<FileMeta> {
        return withContext(Dispatchers.IO) {
            files.listAppFiles()
        }
    }

    override suspend fun getWebListOfFileHeads(): List<FileMeta> {
        return withContext(Dispatchers.IO) {
            files.listWebFiles()
        }
    }

    override suspend fun deleteFile(fileId: String) {
        withContext(Dispatchers.IO) {
            files.deleteFile(fileId)
        }
    }

    /** Forgets the account on this device so the next sign-in can pick one; see [GoogleDriveAuthorization.signOut]. */
    override suspend fun logout() {
        authorization.signOut()
    }
}
