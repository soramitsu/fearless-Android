package jp.co.soramitsu.feature_staking_impl.domain

import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_staking_api.domain.api.StakingInteractor
import jp.co.soramitsu.feature_staking_api.domain.model.StakingAccount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class StakingInteractorImpl(
    private val accountRepository: AccountRepository
) : StakingInteractor {

    override suspend fun getSelectedNetworkType(): Node.NetworkType {
        return accountRepository.getSelectedNode().networkType
    }

    override fun selectedAccountFlow(): Flow<StakingAccount> {
        return accountRepository.selectedAccountFlow()
            .map { mapAccountToWalletAccount(it) }
    }

    private fun mapAccountToWalletAccount(account: Account) = with(account) {
        StakingAccount(address, name, cryptoType, network)
    }
}