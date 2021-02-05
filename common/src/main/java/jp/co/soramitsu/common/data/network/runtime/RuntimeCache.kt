package jp.co.soramitsu.common.data.network.runtime

import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.interfaces.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val CURRENT_USED_RUNTIME_VERSION_MASK = "%s_CURRENT_USER_RUNTIME_VERSION"
private const val TYPE_DEFINITIONS_ACTUAL_MASK = "%s_TYPE_DEFINITIONS_ACTUAL"

private const val METADATA_FILE_MASK = "metadata_%s.cache"
private const val TYPE_DEFINITIONS_FILE_MASK = "definitions_%s.cache"

class RuntimeCache(
    private val preferences: Preferences,
    private val fileProvider: FileProvider,
) {

    fun currentRuntimeVersion(networkType: String): Int {
        val key = CURRENT_USED_RUNTIME_VERSION_MASK.format(networkType)

        return preferences.getInt(key, 0)
    }

    fun updateCurrentRuntimeVersion(networkType: String, new: Int) {
        val key = CURRENT_USED_RUNTIME_VERSION_MASK.format(networkType)

        preferences.putInt(key, new)
    }

    fun areTypeDefinitionsActual(networkType: String): Boolean {
        val key = TYPE_DEFINITIONS_ACTUAL_MASK.format(networkType)

        return preferences.getBoolean(key, false)
    }

    fun setTypeDefinitionsActual(networkType: String, new: Boolean) {
        val key = TYPE_DEFINITIONS_ACTUAL_MASK.format(networkType)

        preferences.putBoolean(key, new)
    }

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