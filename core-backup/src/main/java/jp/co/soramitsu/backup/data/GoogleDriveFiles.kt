package jp.co.soramitsu.backup.data

import android.content.Intent
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import jp.co.soramitsu.backup.domain.exceptions.AuthConsentException
import jp.co.soramitsu.backup.domain.exceptions.FileNotFoundException
import jp.co.soramitsu.backup.domain.exceptions.InsufficientFilePermissionsException
import jp.co.soramitsu.backup.domain.exceptions.StorageQuotaExceededException
import jp.co.soramitsu.backup.domain.exceptions.UnauthorizedException
import jp.co.soramitsu.backup.domain.models.FileMeta

/**
 * The Drive requests of shared_features' GoogleDriveStorage. The layout and file metadata are
 * unchanged, so 4.2.0, iOS and the Chrome extension keep reading what this writes: app backups are
 * `text/json` files in the `backupFolder` folder of the app data space, named by address and
 * described by wallet name. Additions: listings follow every page and return the modified time,
 * listing never creates the backup folder and reads every folder named like it, and x-backup's 403
 * reasons map to typed exceptions.
 */
internal class GoogleDriveFiles(
    private val drive: GoogleDriveApi,
    /** Opens Google's consent page again; null when no screen can show it. */
    private val consentIntent: () -> Intent?
) {

    suspend fun createFile(
        name: String,
        description: String,
        content: String
    ): String = mapAuthErrors {
        val folderId = getParentFolder()

        val metadata = JsonObject().apply {
            add("parents", JsonArray().apply { add(folderId) })
            addProperty("mimeType", JSON_MIME_TYPE)
            addProperty("name", name)
            addProperty("description", description)
        }

        try {
            drive.createMultipart(metadata, JSON_MIME_TYPE, content.toByteArray(Charsets.UTF_8), fields = "id, description, name")
        } catch (e: GoogleDriveHttpException) {
            throw if (e.isForbiddenBecause(STORAGE_QUOTA_EXCEEDED_REASON)) StorageQuotaExceededException(e) else e
        }
    }

    suspend fun getFileContent(fileId: String): String = mapAuthErrors {
        try {
            drive.downloadFile(fileId).toString(Charsets.UTF_8)
        } catch (e: GoogleDriveHttpException) {
            throw if (e.statusCode == GoogleDriveHttpException.HTTP_NOT_FOUND) FileNotFoundException(e) else e
        }
    }

    suspend fun listAppFiles(): List<FileMeta> = mapAuthErrors {
        val folderIds = findBackupFolders()
        if (folderIds.isEmpty()) {
            emptyList()
        } else {
            listFiles(folderIds.joinToString(separator = " or ") { "'$it' in parents" })
        }
    }

    suspend fun listWebFiles(): List<FileMeta> = mapAuthErrors {
        listFiles("'$APP_DATA_FOLDER' in parents and mimeType != '$FOLDER_MIME_TYPE'")
    }

    suspend fun deleteFile(fileId: String) {
        mapAuthErrors {
            try {
                drive.deleteFile(fileId)
            } catch (e: GoogleDriveHttpException) {
                throw if (e.statusCode == GoogleDriveHttpException.HTTP_NOT_FOUND) FileNotFoundException(e) else e
            }
        }
    }

    private suspend fun getParentFolder(): String {
        return findBackupFolders().firstOrNull() ?: createBackupFolder()
    }

    private suspend fun findBackupFolders(): List<String> {
        return listPages(query = "name = '$BACKUP_FOLDER_NAME'", fileFields = "id").map { it.id }
    }

    private suspend fun createBackupFolder(): String {
        val metadata = JsonObject().apply {
            add("parents", JsonArray().apply { add(APP_DATA_FOLDER) })
            addProperty("mimeType", FOLDER_MIME_TYPE)
            addProperty("name", BACKUP_FOLDER_NAME)
        }

        return try {
            drive.createMetadata(metadata, fields = "id")
        } catch (e: GoogleDriveHttpException) {
            throw if (e.isForbiddenBecause(INSUFFICIENT_FILE_PERMISSIONS_REASON)) InsufficientFilePermissionsException(e) else e
        }
    }

    private suspend fun listFiles(query: String): List<FileMeta> {
        return listPages(query = query, fileFields = "id, name, description, modifiedTime").map {
            FileMeta(
                fileName = it.name ?: throw GoogleDriveApi.MalformedDriveResponseException(),
                description = it.description.orEmpty(),
                fileId = it.id,
                modifiedTime = it.modifiedTimeMillis
            )
        }
    }

    // shared_features read only the first page (100 files), so later backups were not listed.
    private suspend fun listPages(query: String, fileFields: String): List<GoogleDriveFile> {
        val files = mutableListOf<GoogleDriveFile>()
        val seenPageTokens = mutableSetOf<String>()
        var pageToken: String? = null
        repeat(MAX_LIST_PAGES) {
            val page = drive.listFiles(
                query = query,
                fields = "nextPageToken, files($fileFields)",
                spaces = APP_DATA_FOLDER,
                pageToken = pageToken
            )
            files += page.files
            val next = page.nextPageToken ?: return files
            // A token Drive already returned would loop forever.
            if (!seenPageTokens.add(next)) throw GoogleDriveApi.MalformedDriveResponseException()
            pageToken = next
        }
        throw GoogleDriveApi.MalformedDriveResponseException()
    }

    private inline fun <T> mapAuthErrors(request: () -> T): T {
        return try {
            request()
        } catch (e: GoogleDriveHttpException) {
            // Drive access was turned off on Google's consent page: ask for it again.
            if (!e.isInsufficientScope) throw e
            throw consentIntent()?.let { AuthConsentException(it, e) } ?: UnauthorizedException(e)
        }
    }

    private companion object {
        const val BACKUP_FOLDER_NAME = "backupFolder"
        const val APP_DATA_FOLDER = "appDataFolder"
        const val JSON_MIME_TYPE = "text/json"
        const val FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"
        const val STORAGE_QUOTA_EXCEEDED_REASON = "storageQuotaExceeded"
        const val INSUFFICIENT_FILE_PERMISSIONS_REASON = "insufficientFilePermissions"

        // 1,000 pages of Drive's default 100 files; far beyond any wallet count.
        const val MAX_LIST_PAGES = 1_000
    }
}
