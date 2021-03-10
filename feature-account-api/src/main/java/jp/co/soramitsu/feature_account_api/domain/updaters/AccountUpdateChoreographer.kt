package jp.co.soramitsu.feature_account_api.domain.updaters

import jp.co.soramitsu.common.data.network.StorageSubscriptionBuilder
import jp.co.soramitsu.core.updater.SubscriptionBuilder
import jp.co.soramitsu.core.updater.Updater
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.storage.subscribeUsing
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onCompletion

class AccountUpdateChoreographer(
    private val accountRepository: AccountRepository,
    private val socketService: SocketService,
    private val updaters: Array<AccountUpdater>
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