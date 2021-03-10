package jp.co.soramitsu.feature_account_api.domain.updaters

import jp.co.soramitsu.common.data.updaters.ScopeChoreographer
import jp.co.soramitsu.core.updater.ScopedUpdater
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

class AccountUpdateChoreographer(
    private val accountRepository: AccountRepository,
    socketService: SocketService,
    updaters: Array<ScopedUpdater<String>>
) : ScopeChoreographer<String>(socketService, updaters) {

    override suspend fun scopeKeyFlow(): Flow<String> {
        val networkType = accountRepository.getSelectedNode().networkType

        return accountRepository.selectedAccountFlow()
            .filter { it.network.type == networkType }
            .map { it.address }
            .distinctUntilChanged()
    }
}