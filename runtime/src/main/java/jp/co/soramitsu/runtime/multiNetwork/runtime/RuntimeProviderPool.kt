package jp.co.soramitsu.runtime.multiNetwork.runtime

import java.util.concurrent.ConcurrentHashMap
import jp.co.soramitsu.common.mixin.api.NetworkStateMixin
import jp.co.soramitsu.core.runtime.RuntimeFactory
import jp.co.soramitsu.coredb.dao.ChainDao
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain

class RuntimeProviderPool(
    private val runtimeFactory: RuntimeFactory,
    private val runtimeSyncService: RuntimeSyncService,
    private val runtimeFilesCache: RuntimeFilesCache,
    private val chainDao: ChainDao,
    private val networkStateMixin: NetworkStateMixin
) {

    private val pool = ConcurrentHashMap<String, RuntimeProvider>()

    fun getRuntimeProvider(chainId: String): RuntimeProvider {
        return pool.getValue(chainId)
    }

    fun getRuntimeProviderOrNull(chainId: String): RuntimeProvider? {
        return pool.getOrDefault(chainId, null)
    }

    fun setupRuntimeProvider(chain: Chain): RuntimeProvider {
        return pool.getOrPut(chain.id) {
            RuntimeProvider(
                runtimeFactory,
                runtimeSyncService,
                runtimeFilesCache,
                chainDao,
                networkStateMixin,
                chain
            )
        }
    }

    fun removeRuntimeProvider(chainId: String) {
        pool.remove(chainId)?.apply { finish() }
    }
}
