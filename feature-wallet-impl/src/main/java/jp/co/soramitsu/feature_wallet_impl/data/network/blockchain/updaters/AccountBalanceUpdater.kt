package jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.updaters

import jp.co.soramitsu.core_api.data.network.Updater
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class AccountBalanceUpdater(
    private val accountRepository: AccountRepository,
    private val walletRepository: WalletRepository
) : Updater {

    override suspend fun listenForUpdates() = withContext(Dispatchers.IO) {
        val networkType = accountRepository.getSelectedNode().networkType

        accountRepository.selectedAccountFlow()
            .filter { it.network.type == networkType }
            .distinctUntilChanged { old, new -> old.address == new.address }
            .flowOn(Dispatchers.IO)
            .collectLatest {
                val accountInfoUpdates = async { walletRepository.listenForAccountInfoUpdates(it) }
                val stakingLedgerUpdates = async { walletRepository.listenForStakingLedgerUpdates(it) }

                listOf(accountInfoUpdates, stakingLedgerUpdates).awaitAll()
            }
    }
}