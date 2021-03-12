package jp.co.soramitsu.common.interfaces

import java.io.File

interface FileProvider {

    suspend fun getFileInExternalCacheStorage(fileName: String): File

    suspend fun getFileInInternalCacheStorage(fileName: String): File
}
