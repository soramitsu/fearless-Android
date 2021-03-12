package jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters.scope

import jp.co.soramitsu.core.updater.UpdateScope
import jp.co.soramitsu.core_db.dao.AccountStakingDao
import jp.co.soramitsu.core_db.model.AccountStakingLocal
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest

class AccountStakingScope(
    private val accountRepository: AccountRepository,
    private val accountStakingDao: AccountStakingDao
) : UpdateScope {

    override suspend fun invalidationFlow(): Flow<Any> {
        return accountRepository.selectedAccountFlow()
            .flatMapLatest { account ->
                accountStakingDao.observeDistinct(account.address)
            }
    }

    suspend fun getAccountStaking(): AccountStakingLocal {
        val account = accountRepository.getSelectedAccount()

        return accountStakingDao.get(account.address)
    }
}
