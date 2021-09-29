package jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters.scope

import jp.co.soramitsu.common.utils.combineToPair
import jp.co.soramitsu.core.updater.UpdateScope
import jp.co.soramitsu.core_db.dao.AccountStakingDao
import jp.co.soramitsu.core_db.model.AccountStakingLocal
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.model.accountIdIn
import jp.co.soramitsu.feature_staking_impl.data.StakingSharedState
import jp.co.soramitsu.runtime.state.chainAndAsset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest

class AccountStakingScope(
    private val accountRepository: AccountRepository,
    private val accountStakingDao: AccountStakingDao,
    private val sharedStakingState: StakingSharedState
) : UpdateScope {

    override fun invalidationFlow(): Flow<Any> {
        return combineToPair(
            sharedStakingState.assetWithChain,
            accountRepository.selectedMetaAccountFlow()
        ).flatMapLatest { (chainWithAsset, account) ->
            val (chain, chainAsset) = chainWithAsset

            accountStakingDao.observeDistinct(chain.id, chainAsset.id, account.accountIdIn(chain)!!)
        }
    }

    suspend fun getAccountStaking(): AccountStakingLocal {
        val (chain, chainAsset) = sharedStakingState.chainAndAsset()
        val account = accountRepository.getSelectedMetaAccount()

        return accountStakingDao.get(chain.id, chainAsset.id, account.accountIdIn(chain)!!)
    }
}
