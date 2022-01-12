package jp.co.soramitsu.runtime.multiNetwork.runtime

import java.util.concurrent.ConcurrentHashMap
import jp.co.soramitsu.runtime.ext.typesUsage
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.runtime.types.BaseTypeSynchronizer

class RuntimeProviderPool(
    private val runtimeFactory: RuntimeFactory,
    private val runtimeSyncService: RuntimeSyncService,
    private val baseTypeSynchronizer: BaseTypeSynchronizer
) {

    private val pool = ConcurrentHashMap<String, RuntimeProvider>()

    fun getRuntimeProvider(chainId: String): RuntimeProvider {
        return pool.getValue(chainId)
    }

    fun setupRuntimeProvider(chain: Chain): RuntimeProvider {
        val provider = pool.getOrPut(chain.id) {
            RuntimeProvider(runtimeFactory, runtimeSyncService, baseTypeSynchronizer, chain)
        }

        provider.considerUpdatingTypesUsage(chain.typesUsage)

        return provider
    }

    fun removeRuntimeProvider(chainId: String) {
        pool.remove(chainId)?.apply { finish() }
    }
}
