package jp.co.soramitsu.backup.domain

import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import jp.co.soramitsu.backup.domain.models.FileMeta

internal interface Storage {

    suspend fun signIn(activityResultLauncher: ActivityResultLauncher<Intent>): Boolean

    /** Uploads [content] as a new file in the app backup folder and returns its file id. */
    suspend fun saveFile(
        name: String,
        description: String,
        content: String
    ): String

    suspend fun getFileContent(fileId: String): String

    /** Backups written by the app: every file in the app backup folder. */
    suspend fun getListOfFileHeads(): List<FileMeta>

    /** Backups written by the Chrome extension: plain files in the root of the app data folder. */
    suspend fun getWebListOfFileHeads(): List<FileMeta>

    suspend fun deleteFile(fileId: String)

    suspend fun logout()
}
