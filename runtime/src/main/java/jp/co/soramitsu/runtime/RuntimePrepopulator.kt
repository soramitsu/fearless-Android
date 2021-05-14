package jp.co.soramitsu.runtime

import android.content.Context
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.utils.readAssetFile
import jp.co.soramitsu.core_db.dao.RuntimeDao
import jp.co.soramitsu.core_db.model.RuntimeCacheEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val RUNTIME_CACHE_ENTRIES = listOf(
    predefinedEntry("kusama", 2030),
    predefinedEntry("westend", 9000),
    predefinedEntry("polkadot", 30)
)

private const val PREDEFINED_METADATA_MASK = "metadata/%s"
private const val PREDEFINED_TYPES_MASK = "types/%s.json"

private fun predefinedEntry(networkName: String, runtimeVersion: Int) = RuntimeCacheEntry(
    networkName = networkName,
    latestKnownVersion = runtimeVersion,
    latestAppliedVersion = runtimeVersion,
    typesVersion = runtimeVersion
)

private const val PREPOPULATED_FLAG = "PREPOPULATED_FLAG"

class RuntimePrepopulator(
    private val context: Context,
    private val runtimeDao: RuntimeDao,
    private val preferences: Preferences,
    private val runtimeCache: RuntimeCache
) {

    suspend fun maybePrepopulateCache(): Unit = withContext(Dispatchers.IO) {
        if (!preferences.contains(PREPOPULATED_FLAG)) {
            forcePrepopulateCache()

            preferences.putBoolean(PREPOPULATED_FLAG, true)
        }
    }

    suspend fun forcePrepopulateCache() {
        saveTypes("default")

        RUNTIME_CACHE_ENTRIES.forEach {
            val networkType = it.networkName

            saveMetadata(networkType)

            saveTypes(networkType)

            runtimeDao.insertRuntimeCacheEntry(it)
        }
    }

    private suspend fun saveMetadata(networkName: String) {
        val predefinedMetadataFileName = PREDEFINED_METADATA_MASK.format(networkName)
        val predefinedMetadata = context.readAssetFile(predefinedMetadataFileName)
        runtimeCache.saveRuntimeMetadata(networkName, predefinedMetadata)
    }

    private suspend fun saveTypes(networkName: String) {
        val predefinedTypesFileName = PREDEFINED_TYPES_MASK.format(networkName)
        val predefinedTypes = context.readAssetFile(predefinedTypesFileName)
        runtimeCache.saveTypeDefinitions(networkName, predefinedTypes)
    }
}
