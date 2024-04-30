package jp.co.soramitsu.runtime.multiNetwork.runtime

import jp.co.soramitsu.common.data.network.runtime.binding.cast
import jp.co.soramitsu.common.domain.NetworkStateService
import jp.co.soramitsu.core.runtime.RuntimeFactory
import jp.co.soramitsu.coredb.dao.ChainDao
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class RuntimeProviderPool(
    private val runtimeFactory: RuntimeFactory,
    private val runtimeSyncService: RuntimeSyncService,
    private val runtimeFilesCache: RuntimeFilesCache,
    private val chainDao: ChainDao,
    private val networkStateService: NetworkStateService
) {

    private val poolStateFlow =
        MutableStateFlow<MutableMap<String, RuntimeProvider>>(mutableMapOf())

    suspend fun awaitRuntimeProvider(chainId: String): RuntimeProvider {
        return poolStateFlow.map { it.getOrDefault(chainId, null) }.first { it != null }.cast()
    }

    fun getRuntimeProviderOrNull(chainId: String): RuntimeProvider? {
        return poolStateFlow.value.getOrDefault(chainId, null)
    }

    fun setupRuntimeProvider(chain: Chain): RuntimeProvider {
        if (poolStateFlow.value.containsKey(chain.id)) {
            return poolStateFlow.value.getValue(chain.id)
        } else {
            poolStateFlow.update { prev ->
                prev.also {
                    it[chain.id] = RuntimeProvider(
                        runtimeFactory,
                        runtimeSyncService,
                        runtimeFilesCache,
                        chainDao,
                        networkStateService,
                        chain
                    )
                }
            }
            return poolStateFlow.value.getValue(chain.id)
        }
    }

    fun removeRuntimeProvider(chainId: String) {
        poolStateFlow.update { prev ->
            prev.also {
                it.remove(chainId)?.apply { finish() }
            }
        }
    }
}
