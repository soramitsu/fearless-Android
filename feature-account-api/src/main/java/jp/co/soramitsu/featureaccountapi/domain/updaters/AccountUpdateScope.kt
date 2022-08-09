package jp.co.soramitsu.featureaccountapi.domain.updaters

import jp.co.soramitsu.core.updater.UpdateScope
import jp.co.soramitsu.featureaccountapi.domain.interfaces.AccountRepository
import jp.co.soramitsu.featureaccountapi.domain.model.MetaAccount
import kotlinx.coroutines.flow.Flow

class AccountUpdateScope(
    private val accountRepository: AccountRepository
) : UpdateScope {

    override fun invalidationFlow(): Flow<MetaAccount> {
        return accountRepository.selectedMetaAccountFlow()
    }

    suspend fun getAccount() = accountRepository.getSelectedMetaAccount()
}
