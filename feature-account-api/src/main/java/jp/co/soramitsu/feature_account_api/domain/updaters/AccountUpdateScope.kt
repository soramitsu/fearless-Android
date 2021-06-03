package jp.co.soramitsu.feature_account_api.domain.updaters

import jp.co.soramitsu.core.updater.UpdateScope
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.model.Account
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

class AccountUpdateScope(
    private val accountRepository: AccountRepository
) : UpdateScope {

    override suspend fun invalidationFlow(): Flow<Any> {
        val networkType = accountRepository.getSelectedNodeOrDefault().networkType

        return accountRepository.selectedAccountFlow()
            .filter { it.network.type == networkType }
            .map { it.address }
            .distinctUntilChanged()
    }

    suspend fun getAccount(): Account = accountRepository.getSelectedAccount()
}
