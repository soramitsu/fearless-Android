package jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.updaters

import jp.co.soramitsu.common.data.network.StorageSubscriptionBuilder
import jp.co.soramitsu.core.updater.SideEffectScope
import jp.co.soramitsu.core.updater.SubscriptionBuilder
import jp.co.soramitsu.core.updater.Updater
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.storage.subscribeUsing
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.model.Account
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onCompletion

interface AccountUpdater : SideEffectScope {

    fun listenAccountUpdates(
        accountSubscriptionBuilder: SubscriptionBuilder,
        account: Account
    ): Flow<Updater.SideEffect>
}

class AccountUpdateChoreographer(
    private val accountRepository: AccountRepository,
    private val socketService: SocketService,
    private val updaters: List<AccountUpdater>
) : Updater {

    override suspend fun listenForUpdates(
        storageSubscriptionBuilder: SubscriptionBuilder
    ): Flow<Updater.SideEffect> {
        val networkType = accountRepository.getSelectedNode().networkType

        return accountRepository.selectedAccountFlow()
            .filter { it.network.type == networkType }
            .distinctUntilChanged { old, new -> old.address == new.address }
            .flatMapLatest { account ->
                val accountSubscriptionBuilder = StorageSubscriptionBuilder.create()

                val flows = updaters.map { it.listenAccountUpdates(accountSubscriptionBuilder, account) }

                val cancellable = socketService.subscribeUsing(accountSubscriptionBuilder.proxy.build())

                flows.merge().onCompletion { cancellable.cancel() }
            }
    }
}