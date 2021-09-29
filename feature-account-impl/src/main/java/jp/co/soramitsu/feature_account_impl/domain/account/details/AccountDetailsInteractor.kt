package jp.co.soramitsu.feature_account_impl.domain.account.details

import jp.co.soramitsu.common.list.GroupedList
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AccountDetailsInteractor(
    private val accountRepository: AccountRepository,
    private val chainRegistry: ChainRegistry
) {

    suspend fun getChainProjections(): GroupedList<ChainProjection.From, ChainProjection> = withContext(Dispatchers.Default) {
        val chain
    }
}
