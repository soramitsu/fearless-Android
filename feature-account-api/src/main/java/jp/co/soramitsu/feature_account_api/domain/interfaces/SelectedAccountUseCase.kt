package jp.co.soramitsu.feature_account_api.domain.interfaces

import jp.co.soramitsu.feature_account_api.domain.model.Account
import kotlinx.coroutines.flow.Flow

@Deprecated("Use meta accounts instead")
class SelectedAccountUseCase(
    private val accountRepository: AccountRepository
) {

    // TODO use meta account
    fun selectedAccountFlow(): Flow<Account> = accountRepository.selectedAccountFlow()
}
