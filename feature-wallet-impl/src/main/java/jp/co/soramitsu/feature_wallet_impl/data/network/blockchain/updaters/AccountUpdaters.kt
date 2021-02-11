package jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.updaters

import jp.co.soramitsu.core_api.data.network.Updater
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow

class AccountBalanceUpdater(
    accountRepository: AccountRepository,
    private val walletRepository: WalletRepository
) : AccountUpdater(accountRepository) {

    override suspend fun listenForUpdates(account: Account): Flow<Updater.SideEffect> = flow {
        walletRepository.listenForAccountInfoUpdates(account)
    }
}

class StakingLedgerUpdater(
    accountRepository: AccountRepository,
    private val walletRepository: WalletRepository
) : AccountUpdater(accountRepository) {

    override suspend fun listenForUpdates(account: Account): Flow<Updater.SideEffect> = flow {
        walletRepository.listenForStakingLedgerUpdates(account)
    }
}

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