package jp.co.soramitsu.runtime

import jp.co.soramitsu.common.interfaces.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val METADATA_FILE_MASK = "metadata_%s.cache"
private const val TYPE_DEFINITIONS_FILE_MASK = "definitions_%s.cache"

class RuntimeCache(
    private val fileProvider: FileProvider
) {

    suspend fun saveRuntimeMetadata(networkType: String, metadataEncoded: String) {
        val fileName = METADATA_FILE_MASK.format(networkType)

        writeToCacheFile(fileName, metadataEncoded)
    }

    suspend fun getRuntimeMetadata(networkType: String): String? {
        val fileName = METADATA_FILE_MASK.format(networkType)

        return readCacheFile(fileName)
    }

    suspend fun saveTypeDefinitions(networkType: String, definitions: String) {
        val fileName = TYPE_DEFINITIONS_FILE_MASK.format(networkType)

        return writeToCacheFile(fileName, definitions)
    }

    suspend fun getTypeDefinitions(networkType: String): String? {
        val fileName = TYPE_DEFINITIONS_FILE_MASK.format(networkType)

        return readCacheFile(fileName)
    }

    private suspend fun writeToCacheFile(name: String, content: String) {
        return withContext(Dispatchers.IO) {
            val file = fileProvider.getFileInInternalCacheStorage(name)

            file.writeText(content)
        }
    }

    private suspend fun readCacheFile(name: String): String? {
        return withContext(Dispatchers.IO) {
            val file = fileProvider.getFileInInternalCacheStorage(name)

            val content = file.readText()

            if (content.isEmpty()) null else content
        }
    }
}