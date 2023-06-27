package jp.co.soramitsu.wallet.impl.data.network.blockchain.updaters

import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.common.data.network.StorageSubscriptionBuilder
import jp.co.soramitsu.common.data.network.rpc.BulkRetriever
import jp.co.soramitsu.common.utils.diffed
import jp.co.soramitsu.core.updater.UpdateSystem
import jp.co.soramitsu.core.updater.Updater
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.getSocket
import jp.co.soramitsu.runtime.multiNetwork.getSocketOrNull
import jp.co.soramitsu.shared_utils.wsrpc.SocketService
import jp.co.soramitsu.shared_utils.wsrpc.request.runtime.storage.subscribeUsing
import jp.co.soramitsu.wallet.api.data.cache.AssetCache
import jp.co.soramitsu.wallet.api.data.cache.updateAsset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext

class BalancesUpdateSystem(
    private val chainRegistry: ChainRegistry,
    private val paymentUpdaterFactory: PaymentUpdaterFactory,
    private val accountRepository: AccountRepository,
    private val bulkRetriever: BulkRetriever,
    private val assetCache: AssetCache
) : UpdateSystem {

    private val subscriptionBuilders = ConcurrentHashMap<String, StorageSubscriptionBuilder>()
    private val subscriptions = ConcurrentHashMap<String, SocketService.Cancellable>()

    private fun subscribeFlow(): Flow<Updater.SideEffect> {
        return combine(
            chainRegistry.syncedChains,
            accountRepository.selectedMetaAccountFlow()
        ) { chains, metaAccount -> chains to metaAccount }.flatMapLatest { (chains, selectedAccount) ->
            chains.map { chain ->
                flow {
                    val updaterKey = "${chain.id}:${selectedAccount.id}"
                    val updater = paymentUpdaterFactory.create(chain, selectedAccount)
                    val socket = chainRegistry.getSocketOrNull(chain.id) ?: return@flow
                    val subscriptionBuilder = subscriptionBuilders.getOrPut(chain.id) {
                        StorageSubscriptionBuilder.create(socket)
                    }
                    kotlin.runCatching {
                        updater.listenForUpdates(subscriptionBuilder)
                            .catch { logError(chain, it) }
                    }.onSuccess { updaterFlow ->
                        val cancellable = subscriptions.getOrPut(updaterKey) {
                            withContext(Dispatchers.IO) { socket.subscribeUsing(subscriptionBuilder.build()) }
                        }
                        updaterFlow.onCompletion {
                            cancellable.cancel()
                        }
                        emitAll(updaterFlow)
                    }.onFailure {
                        logError(chain, it)
                    }
                }
            }.merge()
        }
    }

    private fun singleUpdateFlow(): Flow<Unit> {
        return combine(
            chainRegistry.syncedChains,
            accountRepository.allMetaAccountsFlow().diffed()
        ) { chains, (removed, addedOrModified, _) ->
            chains.forEach singleChainUpdate@{ chain ->
                runCatching {
                    removed.forEach { account ->
                        val updaterKey = "${chain.id}:${account.id}"
                        subscriptions.remove(updaterKey)
                    }

                    val runtime = runCatching { chainRegistry.getRuntime(chain.id) }.getOrNull() ?: return@singleChainUpdate
                    val runtimeVersion = chainRegistry.getRemoteRuntimeVersion(chain.id) ?: 0
                    val socketService = runCatching { chainRegistry.getSocket(chain.id) }.getOrNull() ?: return@singleChainUpdate
                    val storageKeyToMapId = addedOrModified.filter { it.isSelected.not() }.mapNotNull { metaAccount ->
                        val accountId = metaAccount.accountId(chain) ?: return@mapNotNull null

                        chain.assets.mapNotNull { asset ->
                            constructBalanceKey(runtime, asset, accountId)?.let { key ->
                                key to Triple(
                                    asset,
                                    metaAccount.id,
                                    accountId
                                )
                            }
                        }
                    }.flatten().toMap()
                    val queryResults = withContext(Dispatchers.IO) { bulkRetriever.queryKeys(socketService, storageKeyToMapId.keys.toList()) }
                    queryResults.mapKeys { (fullKey, _) -> storageKeyToMapId[fullKey]!! }
                        .mapValues { (triple, hexRaw) -> handleBalanceResponse(runtime, triple.first.typeExtra, hexRaw, runtimeVersion) }
                        .toList()
                        .forEach {
                            val (asset, metaId, accountId) = it.first
                            val balanceData = it.second
                            assetCache.updateAsset(metaId, accountId, asset, balanceData.getOrNull())
                        }
                }.onFailure { return@singleChainUpdate }
            }
        }.onStart { emit(Unit) }.flowOn(Dispatchers.Default)
    }

    override fun start(): Flow<Updater.SideEffect> {
        return combine(subscribeFlow(), singleUpdateFlow()) { sideEffect, _ -> sideEffect }
    }

    private fun logError(chain: Chain, error: Throwable) {
        Log.e("BalancesUpdateSystem", "Failed to subscribe to balances in ${chain.name}: ${error.message}", error)
    }
}
