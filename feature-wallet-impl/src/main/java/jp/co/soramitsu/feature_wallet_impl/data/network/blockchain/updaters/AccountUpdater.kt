package jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.updaters

import jp.co.soramitsu.core.updater.Updater
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.model.Account
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest

abstract class AccountUpdater(
    private val accountRepository: AccountRepository
) : Updater {
    abstract suspend fun listenForUpdates(account: Account): Flow<Updater.SideEffect>

    override suspend fun listenForUpdates(): Flow<Updater.SideEffect> {
        val networkType = accountRepository.getSelectedNode().networkType

        return accountRepository.selectedAccountFlow()
            .filter { it.network.type == networkType }
            .distinctUntilChanged { old, new -> old.address == new.address }
            .flatMapLatest(this::listenForUpdates)
    }
}