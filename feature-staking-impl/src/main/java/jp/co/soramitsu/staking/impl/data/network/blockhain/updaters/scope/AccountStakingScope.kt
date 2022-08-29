package jp.co.soramitsu.staking.impl.data.network.blockhain.updaters.scope

import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.common.utils.combineToPair
import jp.co.soramitsu.core.updater.UpdateScope
import jp.co.soramitsu.coredb.dao.AccountStakingDao
import jp.co.soramitsu.coredb.model.AccountStakingLocal
import jp.co.soramitsu.staking.api.data.StakingSharedState
import jp.co.soramitsu.staking.api.data.StakingType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

class AccountStakingScope(
    private val accountRepository: AccountRepository,
    private val accountStakingDao: AccountStakingDao,
    private val sharedStakingState: StakingSharedState
) : UpdateScope {

    override fun invalidationFlow(): Flow<Any> {
        return combineToPair(
            sharedStakingState.selectionItem,
            accountRepository.selectedMetaAccountFlow().debounce(500)
        ).flatMapLatest { (selectionItem, account) ->

            val (chain, chainAsset) = sharedStakingState.assetWithChain.first()
            when (selectionItem.type) {
                StakingType.RELAYCHAIN -> accountStakingDao.observeDistinct(chain.id, chainAsset.id, account.accountId(chain)!!)
                StakingType.PARACHAIN -> flowOf(Unit)
                StakingType.POOL -> flowOf(Unit)
                else -> emptyFlow()
            }
        }
    }

    suspend fun getAccountStaking(): AccountStakingLocal? {
        val (chain, chainAsset) = sharedStakingState.assetWithChain.first()
        val account = accountRepository.getSelectedMetaAccount()

        return accountStakingDao.get(chain.id, chainAsset.id, account.accountId(chain)!!)
    }

    suspend fun getSelectedMetaAccount() = accountRepository.getSelectedMetaAccount()

    suspend fun getAccountId(): ByteArray? {
        val chain = sharedStakingState.assetWithChain.first().chain
        val account = accountRepository.getSelectedMetaAccount()
        return account.accountId(chain)
    }
}
