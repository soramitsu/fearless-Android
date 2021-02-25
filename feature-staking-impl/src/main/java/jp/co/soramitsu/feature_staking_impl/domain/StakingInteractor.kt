package jp.co.soramitsu.feature_staking_impl.domain

import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_staking_api.domain.model.StakingAccount
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.WalletAccount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

class StakingInteractor(
    private val walletRepository: WalletRepository,
    private val accountRepository: AccountRepository
) {

    fun getCurrentAsset() = accountRepository.selectedAccountFlow()
        .flatMapLatest { walletRepository.assetsFlow(mapAccountToWalletAccount(it)) }
        .filter { it.isNotEmpty() }
        .map { it.first() }

    suspend fun getSelectedNetworkType(): Node.NetworkType {
        return accountRepository.getSelectedNode().networkType
    }

    fun selectedAccountFlow(): Flow<StakingAccount> {
        return accountRepository.selectedAccountFlow()
            .map { mapAccountToStakingAccount(it) }
    }

    private fun mapAccountToStakingAccount(account: Account) = with(account) {
        StakingAccount(address, name, cryptoType, network)
    }

    private fun mapAccountToWalletAccount(account: Account) = with(account) {
        WalletAccount(address, name, cryptoType, network)
    }
}