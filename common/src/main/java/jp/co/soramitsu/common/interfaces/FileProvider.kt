package jp.co.soramitsu.common.interfaces

import java.io.File

interface FileProvider {

    suspend fun getFileInExternalCacheStorage(fileName: String): File

    suspend fun getFileInInternalCacheStorage(fileName: String): File

    /**
     * Returns [fileName] in the internal cache directory that the app's
     * FileProvider shares (res/xml/provider_paths.xml). Use it for files that
     * are handed to another app but must never sit in shared storage, such as
     * keystore exports. [fileName] must be a plain name without a path.
     */
    suspend fun getFileInInternalShareStorage(fileName: String): File
}
