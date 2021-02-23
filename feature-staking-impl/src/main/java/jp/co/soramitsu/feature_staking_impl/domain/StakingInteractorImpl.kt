package jp.co.soramitsu.feature_staking_impl.domain

import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_staking_api.domain.api.StakingInteractor

class StakingInteractorImpl(
    private val accountRepository: AccountRepository
) : StakingInteractor {

    override suspend fun getSelectedNetworkType(): Node.NetworkType {
        return accountRepository.getSelectedNode().networkType
    }
}