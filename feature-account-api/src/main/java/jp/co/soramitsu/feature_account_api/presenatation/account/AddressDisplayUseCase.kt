package jp.co.soramitsu.feature_account_api.presenatation.account

import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository

class AddressDisplayUseCase(
    private val accountRepository: AccountRepository
) {

    suspend operator fun invoke(address: String): String? {
        return accountRepository.getAccountOrNull(address)?.name
    }
}
