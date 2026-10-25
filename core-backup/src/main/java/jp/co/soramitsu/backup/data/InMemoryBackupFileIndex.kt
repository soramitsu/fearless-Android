package jp.co.soramitsu.backup.data

import jp.co.soramitsu.backup.domain.models.BackupAccountMeta
import jp.co.soramitsu.backup.domain.models.FileMeta
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Drive file ids of the backups listed in this session.
 *
 * shared_features kept them in the DataStore `backup_preferences`, but cleared and rebuilt it on
 * every listing, so nothing depended on it surviving a restart. The file 4.2.0 left behind holds
 * only file ids and is never read, written or deleted.
 */
internal class InMemoryBackupFileIndex {

    private val mutex = Mutex()

    // Address -> the app backups filed under it, newest first.
    private var appFiles: Map<String, List<FileMeta>> = emptyMap()

    // File id of the Substrate JSON (or orphan file) -> the extension backup it starts.
    private var webEntries: Map<String, WebBackupEntry> = emptyMap()

    /** Replaces the app backups and returns them grouped by address in listing order, newest first. */
    suspend fun replaceAppFiles(files: List<FileMeta>): Map<String, List<FileMeta>> = mutex.withLock {
        files.groupBy(::appBackupAddress)
            .mapValues { (_, sameAddress) -> sameAddress.sortedByDescending { it.modifiedTime ?: Long.MIN_VALUE } }
            .also { appFiles = it }
    }

    suspend fun appFiles(address: String): List<FileMeta> = mutex.withLock {
        appFiles[address].orEmpty()
    }

    suspend fun addAppFile(address: String, file: FileMeta) = mutex.withLock {
        appFiles = appFiles + (address to listOf(file) + appFiles[address].orEmpty())
    }

    suspend fun removeAppFile(address: String, fileId: String) = mutex.withLock {
        val remaining = appFiles[address].orEmpty().filterNot { it.fileId == fileId }
        appFiles = if (remaining.isEmpty()) appFiles - address else appFiles + (address to remaining)
    }

    suspend fun replaceWebEntries(entries: List<WebBackupEntry>) = mutex.withLock {
        webEntries = entries.associateBy { it.substrateFileId }
    }

    suspend fun webEntry(fileId: String): WebBackupEntry? = mutex.withLock {
        webEntries[fileId]
    }

    suspend fun webEntries(address: String): List<WebBackupEntry> = mutex.withLock {
        webEntries.values.filter { it.meta.address == address }
    }

    suspend fun clear() = mutex.withLock {
        appFiles = emptyMap()
        webEntries = emptyMap()
    }

    companion object {
        // App backups are named "<address>.json".
        fun appBackupAddress(file: FileMeta): String = file.fileName.substringBefore(".json")
    }
}

internal data class WebBackupEntry(
    val meta: BackupAccountMeta,
    val substrateFileId: String,
    val ethereumFileId: String?
)
