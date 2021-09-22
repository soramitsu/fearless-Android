package jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters.scope

import jp.co.soramitsu.core.updater.UpdateScope
import jp.co.soramitsu.core_db.dao.AccountStakingDao
import jp.co.soramitsu.core_db.model.AccountStakingLocal
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.model.accountIdIn
import jp.co.soramitsu.feature_staking_impl.data.StakingSharedState
import jp.co.soramitsu.runtime.state.chain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combineTransform

class AccountStakingScope(
    private val accountRepository: AccountRepository,
    private val accountStakingDao: AccountStakingDao,
    private val sharedStakingState: StakingSharedState
) : UpdateScope {

    override suspend fun invalidationFlow(): Flow<Any> {
        return combineTransform(
            sharedStakingState.selectedAsset,
            accountRepository.selectedMetaAccountFlow()
        ) { (chain, _), account ->
            accountStakingDao.observeDistinct(chain.id, account.accountIdIn(chain)!!)
        }
    }

    suspend fun getAccountStaking(): AccountStakingLocal {
        val chain = sharedStakingState.chain()
        val account = accountRepository.getSelectedMetaAccount()

        return accountStakingDao.get(chain.id, account.accountIdIn(chain)!!)
    }
}
