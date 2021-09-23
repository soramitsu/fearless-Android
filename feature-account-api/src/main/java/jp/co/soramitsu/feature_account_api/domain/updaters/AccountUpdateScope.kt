package jp.co.soramitsu.feature_account_api.domain.updaters

import jp.co.soramitsu.core.updater.UpdateScope
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import kotlinx.coroutines.flow.Flow

class AccountUpdateScope(
    private val accountRepository: AccountRepository
) : UpdateScope {

    override suspend fun invalidationFlow(): Flow<Any> {
        return accountRepository.selectedMetaAccountFlow()
    }

    suspend fun getAccount() = accountRepository.getSelectedMetaAccount()
}
