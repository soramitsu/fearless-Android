package jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.updaters

import jp.co.soramitsu.common.data.network.StorageSubscriptionBuilder
import jp.co.soramitsu.core.updater.UpdateSystem
import jp.co.soramitsu.core.updater.Updater
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.storage.subscribeUsing
import jp.co.soramitsu.feature_account_api.domain.updaters.AccountUpdateScope
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.getSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onCompletion

class BalancesUpdateSystem(
    private val chainRegistry: ChainRegistry,
    private val paymentUpdaterFactory: PaymentUpdaterFactory,
    private val accountUpdateScope: AccountUpdateScope,
) : UpdateSystem {

    override fun start(): Flow<Updater.SideEffect> {
        return accountUpdateScope.invalidationFlow().flatMapLatest {
            val chains = chainRegistry.currentChains.first()

            val mergedFlow = chains.map { chain ->
                val updater = paymentUpdaterFactory.create(chain.id)
                val socket = chainRegistry.getSocket(chain.id)

                val subscriptionBuilder = StorageSubscriptionBuilder.create(socket)

                val updaterFlow = updater.listenForUpdates(subscriptionBuilder)
                    .flowOn(Dispatchers.Default)

                val cancellable = socket.subscribeUsing(subscriptionBuilder.build())

                updaterFlow.onCompletion { cancellable.cancel() }
            }.merge()

            mergedFlow
        }.flowOn(Dispatchers.Default)
    }
}
