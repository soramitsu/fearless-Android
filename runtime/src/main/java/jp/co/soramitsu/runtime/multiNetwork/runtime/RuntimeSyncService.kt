package jp.co.soramitsu.runtime.multiNetwork.runtime

import android.util.Log
import io.ktor.util.collections.ConcurrentSet
import jp.co.soramitsu.common.mixin.api.UpdatesMixin
import jp.co.soramitsu.common.mixin.api.UpdatesProviderUi
import jp.co.soramitsu.common.utils.md5
import jp.co.soramitsu.common.utils.newLimitedThreadPoolExecutor
import jp.co.soramitsu.coredb.dao.ChainDao
import jp.co.soramitsu.coredb.model.chain.ChainRuntimeInfoLocal
import jp.co.soramitsu.coredb.model.chain.ChainTypesLocal
import jp.co.soramitsu.runtime.BuildConfig
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.connection.ConnectionPool
import jp.co.soramitsu.runtime.multiNetwork.runtime.types.TypesFetcher
import jp.co.soramitsu.shared_utils.runtime.metadata.GetMetadataRequest
import jp.co.soramitsu.shared_utils.wsrpc.executeAsync
import jp.co.soramitsu.shared_utils.wsrpc.mappers.nonNull
import jp.co.soramitsu.shared_utils.wsrpc.mappers.pojo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.ConcurrentHashMap

class SyncResult(
    val chainId: String,
    val metadataHash: FileHash?,
    val typesHash: FileHash?
)

private const val LOG_TAG = "RuntimeSyncService"

class RuntimeSyncService(
    private val typesFetcher: TypesFetcher,
    private val runtimeFilesCache: RuntimeFilesCache,
    private val chainDao: ChainDao,
    maxConcurrentUpdates: Int = 15,
    private val updatesMixin: UpdatesMixin,
    private val connectionPool: ConnectionPool
) : CoroutineScope by CoroutineScope(Dispatchers.Default), UpdatesProviderUi by updatesMixin {

    private val syncDispatcher = newLimitedThreadPoolExecutor(maxConcurrentUpdates).asCoroutineDispatcher()
    private val knownChains = ConcurrentSet<String>()

    private val syncingChains = ConcurrentHashMap<String, Job>()

    private val _syncStatusFlow = MutableSharedFlow<SyncResult>()

    fun syncResultFlow(forChain: String): Flow<SyncResult> {
        return _syncStatusFlow.filter { it.chainId == forChain }
    }

    fun applyRuntimeVersion(chainId: String) {
        launchSync(chainId)
    }

    fun registerChain(chain: Chain) {
        knownChains.add(chain.id)
    }

    fun unregisterChain(chainId: String) {
        knownChains.remove(chainId)

        cancelExistingSync(chainId)
    }

    // Android may clear cache files sometimes so it necessary to have force sync mechanism
    fun cacheNotFound(chainId: String) {
        if (!syncingChains.contains(chainId)) {
            launchSync(chainId, force = true)
        }
    }

    fun isSyncing(chainId: String): Boolean {
        return syncingChains.containsKey(chainId)
    }

    private fun launchSync(chainId: String, force: Boolean = false) {
        cancelExistingSync(chainId)

        syncingChains[chainId] = launch(syncDispatcher) {
            sync(chainId, force)
        }
    }

    private suspend fun sync(chainId: String, force: Boolean) {
        updatesMixin.startChainSyncUp(chainId)

        if (knownChains.contains(chainId).not()) {
            Log.w(LOG_TAG, "Unknown chain with id $chainId requested to be synced")
            return
        }

        val runtimeInfo = chainDao.runtimeInfo(chainId) ?: return

        val metadataHash = if (force || runtimeInfo.shouldSyncMetadata()) {
            val runtimeMetadata = connectionPool.getConnection(chainId).socketService.executeAsync(GetMetadataRequest, mapper = pojo<String>().nonNull())

            runtimeFilesCache.saveChainMetadata(chainId, runtimeMetadata)

            chainDao.updateSyncedRuntimeVersion(chainId, runtimeInfo.remoteVersion)

            runtimeMetadata.md5()
        } else {
            null
        }

        val types = chainDao.getTypes(chainId)
        val typesHash = types.md5()

        syncFinished(chainId)

        _syncStatusFlow.emit(
            SyncResult(
                metadataHash = metadataHash,
                typesHash = typesHash,
                chainId = chainId
            )
        )
    }

    suspend fun syncTypes() {
        val types = typesFetcher.getTypes(BuildConfig.TYPES_URL)
        val array = Json.decodeFromString<JsonArray>(types)
        val chainIdToTypes =
            array.mapNotNull { element ->
                val chainId = element.jsonObject["chainId"]?.jsonPrimitive?.content ?: return@mapNotNull null
                ChainTypesLocal(chainId, element.toString())
            }
        chainDao.insertTypes(chainIdToTypes)
    }

    private fun cancelExistingSync(chainId: String) {
        syncingChains.remove(chainId)?.apply { cancel() }
    }

    private suspend fun syncFinished(chainId: String) {
        updatesMixin.finishChainSyncUp(chainId)
        syncingChains.remove(chainId)
    }

    private fun ChainRuntimeInfoLocal.shouldSyncMetadata() = syncedVersion < remoteVersion
}
