package jp.co.soramitsu.backup.data

import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAuthIOException
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.ByteArrayContent
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import jp.co.soramitsu.backup.domain.exceptions.AuthConsentException
import jp.co.soramitsu.backup.domain.exceptions.FileNotFoundException
import jp.co.soramitsu.backup.domain.exceptions.InsufficientFilePermissionsException
import jp.co.soramitsu.backup.domain.exceptions.StorageQuotaExceededException
import jp.co.soramitsu.backup.domain.exceptions.UnauthorizedException
import jp.co.soramitsu.backup.domain.models.FileMeta
import java.util.Collections

/**
 * The Drive requests of shared_features' GoogleDriveStorage. The layout and file metadata are
 * unchanged, so 4.2.0 and the Chrome extension keep reading what this writes. Additions: listings
 * follow every page and return the modified time, listing never creates the backup folder and reads
 * every folder named like it, and x-backup's 403 reasons map to typed exceptions.
 */
internal class GoogleDriveFiles(private val drive: Drive) {

    fun createFile(
        name: String,
        description: String,
        content: String
    ): String = mapAuthErrors {
        val folderId = getParentFolder()

        val metadata: File = File()
            .setParents(Collections.singletonList(folderId))
            .setMimeType(JSON_MIME_TYPE)
            .setName(name)
            .setDescription(description)

        val mediaContent = ByteArrayContent(JSON_MIME_TYPE, content.toByteArray())

        try {
            drive.files()
                .create(metadata, mediaContent)
                .setFields("id, description, name")
                .execute()
                .id
        } catch (e: GoogleJsonResponseException) {
            throw if (e.isForbiddenBecause(STORAGE_QUOTA_EXCEEDED_REASON)) StorageQuotaExceededException(e) else e
        }
    }

    fun getFileContent(fileId: String): String = mapAuthErrors {
        try {
            drive.files()
                .get(fileId)
                .executeMediaAsInputStream()
                .bufferedReader()
                .use { it.readText() }
        } catch (e: GoogleJsonResponseException) {
            throw if (e.statusCode == NOT_FOUND_CODE) FileNotFoundException(e) else e
        }
    }

    fun listAppFiles(): List<FileMeta> = mapAuthErrors {
        val folderIds = findBackupFolders()
        if (folderIds.isEmpty()) {
            emptyList()
        } else {
            listFiles(folderIds.joinToString(separator = " or ") { "'$it' in parents" })
        }
    }

    fun listWebFiles(): List<FileMeta> = mapAuthErrors {
        listFiles("'$APP_DATA_FOLDER' in parents and mimeType != '$FOLDER_MIME_TYPE'")
    }

    fun deleteFile(fileId: String) {
        mapAuthErrors {
            try {
                drive.files().delete(fileId).execute()
            } catch (e: GoogleJsonResponseException) {
                throw if (e.statusCode == NOT_FOUND_CODE) FileNotFoundException(e) else e
            }
        }
    }

    private fun getParentFolder(): String {
        return findBackupFolders().firstOrNull() ?: createBackupFolder()
    }

    private fun findBackupFolders(): List<String> {
        return listPages(query = "name = '$BACKUP_FOLDER_NAME'", fileFields = "id").map { it.id }
    }

    private fun createBackupFolder(): String {
        val metadata: File = File()
            .setParents(Collections.singletonList(APP_DATA_FOLDER))
            .setMimeType(FOLDER_MIME_TYPE)
            .setName(BACKUP_FOLDER_NAME)

        return try {
            drive.files()
                .create(metadata)
                .setFields("id")
                .execute()
                .id
        } catch (e: GoogleJsonResponseException) {
            throw if (e.isForbiddenBecause(INSUFFICIENT_FILE_PERMISSIONS_REASON)) InsufficientFilePermissionsException(e) else e
        }
    }

    private fun listFiles(query: String): List<FileMeta> {
        return listPages(query = query, fileFields = "id, name, description, modifiedTime").map {
            FileMeta(
                fileName = it.name,
                description = it.description.orEmpty(),
                fileId = it.id,
                modifiedTime = it.modifiedTime?.value
            )
        }
    }

    // shared_features read only the first page (100 files), so later backups were not listed.
    private fun listPages(query: String, fileFields: String): List<File> {
        val files = mutableListOf<File>()
        var pageToken: String? = null
        do {
            val page = drive.files()
                .list()
                .setFields("nextPageToken, files($fileFields)")
                .setSpaces(APP_DATA_FOLDER)
                .setQ(query)
                .setPageToken(pageToken)
                .execute()
            files += page.files.orEmpty()
            pageToken = page.nextPageToken
        } while (pageToken != null)
        return files
    }

    private fun GoogleJsonResponseException.isForbiddenBecause(reason: String): Boolean {
        return statusCode == FORBIDDEN_CODE && details?.errors.orEmpty().any { it.reason == reason }
    }

    private inline fun <T> mapAuthErrors(request: () -> T): T {
        return try {
            request()
        } catch (e: UserRecoverableAuthIOException) {
            throw e.intent?.let { AuthConsentException(it, e) } ?: UnauthorizedException(e)
        } catch (e: GoogleAuthIOException) {
            throw UnauthorizedException(e)
        }
    }

    private companion object {
        const val BACKUP_FOLDER_NAME = "backupFolder"
        const val APP_DATA_FOLDER = "appDataFolder"
        const val JSON_MIME_TYPE = "text/json"
        const val FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"
        const val STORAGE_QUOTA_EXCEEDED_REASON = "storageQuotaExceeded"
        const val INSUFFICIENT_FILE_PERMISSIONS_REASON = "insufficientFilePermissions"
        const val NOT_FOUND_CODE = 404
        const val FORBIDDEN_CODE = 403
    }
}
