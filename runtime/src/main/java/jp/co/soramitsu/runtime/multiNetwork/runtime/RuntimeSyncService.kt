package jp.co.soramitsu.runtime.multiNetwork.runtime

import android.util.Log
import jp.co.soramitsu.common.utils.md5
import jp.co.soramitsu.common.utils.newLimitedThreadPoolExecutor
import jp.co.soramitsu.common.utils.retryUntilDone
import jp.co.soramitsu.core_db.dao.ChainDao
import jp.co.soramitsu.core_db.model.chain.ChainRuntimeInfoLocal
import jp.co.soramitsu.fearless_utils.runtime.metadata.GetMetadataRequest
import jp.co.soramitsu.fearless_utils.wsrpc.executeAsync
import jp.co.soramitsu.fearless_utils.wsrpc.mappers.nonNull
import jp.co.soramitsu.fearless_utils.wsrpc.mappers.pojo
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.connection.ChainConnection
import jp.co.soramitsu.runtime.multiNetwork.runtime.types.TypesFetcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

data class SyncInfo(
    val connection: ChainConnection,
    val typesUrl: String?,
)

class SyncResult(
    val chainId: String,
    val metadataHash: FileHash?,
    val typesHash: FileHash?,
)

private const val LOG_TAG = "RuntimeSyncService"

class RuntimeSyncService(
    private val typesFetcher: TypesFetcher,
    private val runtimeFilesCache: RuntimeFilesCache,
    private val chainDao: ChainDao,
    maxConcurrentUpdates: Int = 8,
) : CoroutineScope by CoroutineScope(Dispatchers.Default) {

    private val syncDispatcher = newLimitedThreadPoolExecutor(maxConcurrentUpdates).asCoroutineDispatcher()
    private val knownChains = ConcurrentHashMap<String, SyncInfo>()

    private val syncingChains = ConcurrentHashMap<String, Job>()

    private val _syncStatusFlow = MutableSharedFlow<SyncResult>()

    fun syncResultFlow(forChain: String): Flow<SyncResult> {
        return _syncStatusFlow.filter { it.chainId == forChain }
    }

    fun applyRuntimeVersion(chainId: String) {
        launchSync(chainId)
    }

    fun registerChain(chain: Chain, connection: ChainConnection) {
        val existingSyncInfo = knownChains[chain.id]

        val newSyncInfo = SyncInfo(
            connection = connection,
            typesUrl = chain.types?.url
        )

        knownChains[chain.id] = newSyncInfo

        if (existingSyncInfo != null && existingSyncInfo != newSyncInfo) {
            launchSync(chain.id)
        }
    }

    fun unregisterChain(chainId: String) {
        knownChains.remove(chainId)

        cancelExistingSync(chainId)
    }

    // Android may clear cache files sometimes so it necessary to have force sync mechanism
    fun cacheNotFound(chainId: String) {
        if (!syncingChains.contains(chainId)) {
            launchSync(chainId)
        }
    }

    fun isSyncing(chainId: String): Boolean {
        return syncingChains.containsKey(chainId)
    }

    private fun launchSync(chainId: String) {
        cancelExistingSync(chainId)

        syncingChains[chainId] = launch(syncDispatcher) {
            sync(chainId)
        }
    }

    private suspend fun sync(chainId: String) {
        val syncInfo = knownChains[chainId]

        if (syncInfo == null) {
            Log.w(LOG_TAG, "Unknown chain with id $chainId requested to be synced")
            return
        }

        val runtimeInfo = chainDao.runtimeInfo(chainId) ?: return

        val metadataHash = if (runtimeInfo.shouldSyncMetadata()) {
            val runtimeMetadata = syncInfo.connection.socketService.executeAsync(GetMetadataRequest, mapper = pojo<String>().nonNull())

            runtimeFilesCache.saveChainMetadata(chainId, runtimeMetadata)

            chainDao.updateSyncedRuntimeVersion(chainId, runtimeInfo.remoteVersion)

            runtimeMetadata.md5()
        } else {
            null
        }

        val typesHash = syncInfo.typesUrl?.let { typesUrl ->
            retryUntilDone {
                val types = typesFetcher.getTypes(typesUrl)

                runtimeFilesCache.saveChainTypes(chainId, types)

                types.md5()
            }
        }

        syncFinished(chainId)

        _syncStatusFlow.emit(
            SyncResult(
                metadataHash = metadataHash,
                typesHash = typesHash,
                chainId = chainId
            )
        )
    }

    private fun cancelExistingSync(chainId: String) {
        syncingChains.remove(chainId)?.apply { cancel() }
    }

    private fun syncFinished(chainId: String) {
        syncingChains.remove(chainId)
    }

    private fun ChainRuntimeInfoLocal.shouldSyncMetadata() = syncedVersion < remoteVersion
}
