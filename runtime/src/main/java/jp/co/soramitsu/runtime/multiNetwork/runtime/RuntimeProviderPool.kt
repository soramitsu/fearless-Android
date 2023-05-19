package jp.co.soramitsu.runtime.multiNetwork.runtime

import jp.co.soramitsu.core.runtime.RuntimeFactory
import jp.co.soramitsu.coredb.dao.ChainDao
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import java.util.concurrent.ConcurrentHashMap

class RuntimeProviderPool(
    private val runtimeFactory: RuntimeFactory,
    private val runtimeSyncService: RuntimeSyncService,
    private val runtimeFilesCache: RuntimeFilesCache,
    private val chainDao: ChainDao
) {

    private val pool = ConcurrentHashMap<String, RuntimeProvider>()

    fun getRuntimeProvider(chainId: String): RuntimeProvider {
        return pool.getValue(chainId)
    }

    fun setupRuntimeProvider(chain: Chain): RuntimeProvider {
        return pool.getOrPut(chain.id) {
            RuntimeProvider(runtimeFactory, runtimeSyncService, runtimeFilesCache, chainDao, chain)
        }
    }

    fun removeRuntimeProvider(chainId: String) {
        pool.remove(chainId)?.apply { finish() }
    }
}
