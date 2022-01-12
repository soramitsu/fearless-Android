package jp.co.soramitsu.runtime.multiNetwork.runtime

import jp.co.soramitsu.core_db.dao.ChainDao
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.connection.ChainConnection
import kotlinx.coroutines.cancel
import java.util.concurrent.ConcurrentHashMap

class RuntimeSubscriptionPool(
    private val chainDao: ChainDao,
    private val runtimeSyncService: RuntimeSyncService
) {

    private val pool = ConcurrentHashMap<String, RuntimeVersionSubscription>()

    fun getRuntimeSubscription(chainId: String) = pool.getValue(chainId)

    fun setupRuntimeSubscription(chain: Chain, connection: ChainConnection): RuntimeVersionSubscription {
        return pool.getOrPut(chain.id) {
            RuntimeVersionSubscription(chain.id, connection, chainDao, runtimeSyncService)
        }
    }

    fun removeSubscription(chainId: String) {
        pool.remove(chainId)?.apply { cancel() }
    }
}
