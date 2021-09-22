package jp.co.soramitsu.runtime.multiNetwork

import jp.co.soramitsu.common.utils.diffed
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.utils.mapList
import jp.co.soramitsu.core_db.dao.ChainDao
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainSyncService
import jp.co.soramitsu.runtime.multiNetwork.chain.mapChainLocalToChain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch

data class ChainService(
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
        .diffed()
        .map { (removed, addedOrModified, all) ->
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

            all
        }
        .filter { it.isNotEmpty() }
        .distinctUntilChanged()
        .inBackground()
        .shareIn(this, SharingStarted.Eagerly, replay = 1)

    val chainsById = currentChains.map { chains -> chains.associateBy { it.id } }
        .inBackground()
        .shareIn(this, SharingStarted.Eagerly, replay = 1)

    init {
        launch { chainSyncService.syncUp() }

        baseTypeSynchronizer.sync()
    }

    fun getConnection(chainId: String) = connectionPool.getConnection(chainId)

    fun getRuntimeProvider(chainId: String) = runtimeProviderPool.getRuntimeProvider(chainId)

    suspend fun getChain(chainId: String) = chainsById.first().getValue(chainId)
}

suspend fun ChainRegistry.chainWithAsset(chainId: String, assetId: Int) : Pair<Chain, Chain.Asset> {
    val chain = chainsById.first().getValue(chainId)

    return chain to chain.assetsById.getValue(assetId)
}

suspend fun ChainRegistry.getRuntime(chainId: String) = getRuntimeProvider(chainId).get()

suspend fun ChainRegistry.getSocket(chainId: String) = getConnection(chainId).socketService

fun ChainRegistry.getService(chainId: String) = ChainService(
    runtimeProvider = getRuntimeProvider(chainId),
    connection = getConnection(chainId)
)
