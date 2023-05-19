package jp.co.soramitsu.wallet.impl.data.network.blockchain.updaters

import android.util.Log
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.common.data.network.StorageSubscriptionBuilder
import jp.co.soramitsu.core.updater.UpdateSystem
import jp.co.soramitsu.core.updater.Updater
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.getSocketOrNull
import jp.co.soramitsu.shared_utils.wsrpc.request.runtime.storage.subscribeUsing
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onCompletion

class BalancesUpdateSystem(
    private val chainRegistry: ChainRegistry,
    private val paymentUpdaterFactory: PaymentUpdaterFactory,
    private val accountRepository: AccountRepository
) : UpdateSystem {

    override fun start(): Flow<Updater.SideEffect> {
        return accountRepository.allMetaAccountsFlow().map { accounts -> accounts.sortedByDescending { it.isSelected } }
            .flatMapLatest { accounts ->
                val chains = chainRegistry.currentChains.first()

                val mergedFlow = accounts.map {
                    chains.map { chain ->
                        flow {
                            val updater = paymentUpdaterFactory.create(chain, it)
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
                }.merge()

                mergedFlow
            }
    }

    private fun logError(chain: Chain, error: Throwable) {
        Log.e("BalancesUpdateSystem", "Failed to subscribe to balances in ${chain.name}: ${error.message}", error)
    }
}
