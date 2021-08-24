package jp.co.soramitsu.runtime.multiNetwork

import jp.co.soramitsu.common.utils.diffed
import jp.co.soramitsu.common.utils.mapList
import jp.co.soramitsu.core_db.dao.ChainDao
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainSyncService
import jp.co.soramitsu.runtime.multiNetwork.chain.mapChainLocalToChain
import jp.co.soramitsu.runtime.multiNetwork.connection.ChainConnection
import jp.co.soramitsu.runtime.multiNetwork.connection.ConnectionPool
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeProvider
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeProviderPool
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeSubscriptionPool
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeSyncService
import jp.co.soramitsu.runtime.multiNetwork.runtime.types.BaseTypeSynchronizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch

class ChainService(
    val runtimeProvider: RuntimeProvider,
    val connection: ChainConnection
)

class ChainRegistry(
    private val runtimeProviderPool: RuntimeProviderPool,
    private val connectionPool: ConnectionPool,
    private val runtimeSubscriptionPool: RuntimeSubscriptionPool,
    private val chainDao: ChainDao,
    private val chainSyncService: ChainSyncService,
    private val baseTypeSynchronizer: BaseTypeSynchronizer,
    private val runtimeSyncService: RuntimeSyncService,
) : CoroutineScope by CoroutineScope(Dispatchers.Default) {

    val currentChains = chainDao.joinChainInfoFlow()
        .mapList(::mapChainLocalToChain)
        .shareIn(this, SharingStarted.Eagerly, replay = 1)

    init {
        currentChains
            .diffed()
            .onEach { (removed, addedOrModified) ->
                removed.forEach {
                    val chainId = it.id

                    runtimeProviderPool.removeRuntimeProvider(chainId)
                    runtimeSubscriptionPool.removeSubscription(chainId)
                    runtimeSyncService.unregisterChain(chainId)
                    connectionPool.removeConnection(chainId)
                }

                addedOrModified.forEach { chain ->
                    val connection = connectionPool.setupConnection(chain)

                    runtimeProviderPool.setupRuntimeProvider(chain)
                    runtimeSyncService.registerChain(chain, connection)
                    runtimeSubscriptionPool.setupRuntimeSubscription(chain, connection)
                    runtimeProviderPool.setupRuntimeProvider(chain)
                }
            }.launchIn(this)

        launch { chainSyncService.syncUp() }

        baseTypeSynchronizer.sync()
    }

    fun getService(chainId: String) = ChainService(
        runtimeProvider = runtimeProviderPool.getRuntimeProvider(chainId),
        connection = connectionPool.getConnection(chainId)
    )
}
