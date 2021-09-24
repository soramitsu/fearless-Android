package jp.co.soramitsu.runtime.multiNetwork.runtime

import android.util.Log
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.runtime.ext.typesUsage
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.TypesUsage
import jp.co.soramitsu.runtime.multiNetwork.runtime.types.BaseTypeSynchronizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class RuntimeProvider(
    private val runtimeFactory: RuntimeFactory,
    private val runtimeSyncService: RuntimeSyncService,
    private val baseTypeSynchronizer: BaseTypeSynchronizer,
    chain: Chain
) : CoroutineScope by CoroutineScope(Dispatchers.Default) {

    private val chainId = chain.id

    private var typesUsage = chain.typesUsage

    private val runtimeFlow = MutableSharedFlow<ConstructedRuntime>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private var currentConstructionJob: Job? = null

    suspend fun get(): RuntimeSnapshot {
        val runtime =  runtimeFlow.first()

        return runtime.runtime
    }

    fun observe(): Flow<RuntimeSnapshot> = runtimeFlow.map { it.runtime }

    init {
        baseTypeSynchronizer.syncStatusFlow
            .onEach(::considerReconstructingRuntime)
            .launchIn(this)

        runtimeSyncService.syncResultFlow(chainId)
            .onEach(::considerReconstructingRuntime)
            .launchIn(this)

        tryLoadFromCache()
    }

    fun finish() {
        invalidateRuntime()

        cancel()
    }

    fun considerUpdatingTypesUsage(newTypesUsage: TypesUsage) {
        if (typesUsage != newTypesUsage) {
            typesUsage = newTypesUsage

            constructNewRuntime(typesUsage)
        }
    }

    private fun tryLoadFromCache() {
        constructNewRuntime(typesUsage)
    }

    private fun considerReconstructingRuntime(runtimeSyncResult: SyncResult) {
        launch {
            currentConstructionJob?.join()

            val currentVersion = runtimeFlow.replayCache.firstOrNull()

            if (
                currentVersion == null ||
                // metadata was synced and new hash is different from current one
                (runtimeSyncResult.metadataHash != null && currentVersion.metadataHash != runtimeSyncResult.metadataHash) ||
                // types were synced and new hash is different from current one
                (runtimeSyncResult.typesHash != null &&  currentVersion.ownTypesHash != runtimeSyncResult.typesHash)
            ) {
                Log.d("RX", "currentVersion is null: ${currentVersion == null}, " +
                    "metadata: ${(runtimeSyncResult.metadataHash != null && currentVersion?.metadataHash != runtimeSyncResult.metadataHash)} " +
                    "types: ${(runtimeSyncResult.typesHash != null &&  currentVersion?.ownTypesHash != runtimeSyncResult.typesHash)} " +
                    "for chain: $chainId")
                constructNewRuntime(typesUsage)
            }
        }
    }

    private fun considerReconstructingRuntime(newBaseTypesHash: String) {
        launch {
            currentConstructionJob?.join()

            val currentVersion = runtimeFlow.replayCache.firstOrNull()

            if (typesUsage == TypesUsage.OWN) {
                return@launch
            }

            if (
                currentVersion == null ||
                currentVersion.baseTypesHash != newBaseTypesHash
            ) {
                constructNewRuntime(typesUsage)
            }
        }
    }

    private fun constructNewRuntime(typesUsage: TypesUsage) {
        currentConstructionJob?.cancel()

        currentConstructionJob = launch {
            invalidateRuntime()

            runCatching {
                Log.d("RX", "Constructing runtime for $chainId")

                runtimeFactory.constructRuntime(chainId, typesUsage)?.also {
                    Log.d("RX", "Constructed runtime for $chainId")

                    runtimeFlow.emit(it)
                }
            }.onFailure {
                Log.d("RX", "Error constructing runtime for $chainId ($it)")

                when (it) {
                    ChainInfoNotInCacheException -> runtimeSyncService.cacheNotFound(chainId)
                    BaseTypesNotInCacheException -> baseTypeSynchronizer.cacheNotFound()
                    else -> it.printStackTrace()
                }
            }

            currentConstructionJob = null
        }
    }

    private fun invalidateRuntime() {
        runtimeFlow.resetReplayCache()
    }
}
