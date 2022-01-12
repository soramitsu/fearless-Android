package jp.co.soramitsu.runtime.multiNetwork.runtime

import jp.co.soramitsu.common.interfaces.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val DEFAULT_FILE_NAME = "default"

private const val METADATA_FILE_MASK = "metadata_%s"
private const val TYPE_DEFINITIONS_FILE_MASK = "definitions_%s"

class RuntimeFilesCache(
    private val fileProvider: FileProvider,
) {

    suspend fun getBaseTypes(): String {
        return readCacheFile(TYPE_DEFINITIONS_FILE_MASK.format(DEFAULT_FILE_NAME))
    }

    suspend fun getChainTypes(chainId: String): String {
        return readCacheFile(TYPE_DEFINITIONS_FILE_MASK.format(chainId))
    }

    suspend fun getChainMetadata(chainId: String): String {
        return readCacheFile(METADATA_FILE_MASK.format(chainId))
    }

    suspend fun saveBaseTypes(types: String) {
        writeToCacheFile(TYPE_DEFINITIONS_FILE_MASK.format(DEFAULT_FILE_NAME), types)
    }

    suspend fun saveChainTypes(chainId: String, types: String) {
        val fileName = TYPE_DEFINITIONS_FILE_MASK.format(chainId)

        writeToCacheFile(fileName, types)
    }

    suspend fun saveChainMetadata(chainId: String, metadata: String) {
        val fileName = METADATA_FILE_MASK.format(chainId)

        writeToCacheFile(fileName, metadata)
    }

    private suspend fun writeToCacheFile(name: String, content: String) {
        return withContext(Dispatchers.IO) {
            val file = fileProvider.getFileInInternalCacheStorage(name)

            file.writeText(content)
        }
    }

    private suspend fun readCacheFile(name: String): String {
        return withContext(Dispatchers.IO) {
            val file = fileProvider.getFileInInternalCacheStorage(name)

            file.readText()
        }
    }
}
