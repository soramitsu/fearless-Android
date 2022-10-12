package jp.co.soramitsu.wallet.impl.data.network.blockchain.updaters

import android.util.Log
import jp.co.soramitsu.account.api.domain.updaters.AccountUpdateScope
import jp.co.soramitsu.common.data.network.StorageSubscriptionBuilder
import jp.co.soramitsu.core.updater.UpdateSystem
import jp.co.soramitsu.core.updater.Updater
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.storage.subscribeUsing
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.getSocketOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onCompletion

class BalancesUpdateSystem(
    private val chainRegistry: ChainRegistry,
    private val paymentUpdaterFactory: PaymentUpdaterFactory,
    private val accountUpdateScope: AccountUpdateScope
) : UpdateSystem {

    override fun start(): Flow<Updater.SideEffect> {
        return accountUpdateScope.invalidationFlow().flatMapLatest {
            val chains = chainRegistry.currentChains.first()

            val mergedFlow = chains.map { chain ->
                flow {
                    val updater = paymentUpdaterFactory.create(chain)
                    val socket = chainRegistry.getSocketOrNull(chain.id) ?: return@flow

                    val subscriptionBuilder = StorageSubscriptionBuilder.create(socket)

                    kotlin.runCatching {
                        updater.listenForUpdates(subscriptionBuilder)
                            .catch { logError(chain, it) }
                    }.onSuccess { updaterFlow ->
                        val cancellable = socket.subscribeUsing(subscriptionBuilder.build())

                        updaterFlow.onCompletion { cancellable.cancel() }

                        emitAll(updaterFlow)
                    }.onFailure {
                        logError(chain, it)
                    }
                }
            }.merge()
            mergedFlow
        }.flowOn(Dispatchers.Default)
    }

    private fun logError(chain: Chain, error: Throwable) {
        Log.e("BalancesUpdateSystem", "Failed to subscribe to balances in ${chain.name}: ${error.message}", error)
    }
}
