package jp.co.soramitsu.feature_account_api.presenatation.account

import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// TODO adopt for meta account logic
class AddressDisplayUseCase(
    private val accountRepository: AccountRepository
) {

    class Identifier(private val addressToName: Map<String, String?>) {

        fun nameOrAddress(address: String): String {
            return addressToName[address] ?: address
        }
    }

    suspend operator fun invoke(address: String): String? {
        return accountRepository.getAccountOrNull(address)?.name
    }

    suspend fun createIdentifier(): Identifier = withContext(Dispatchers.Default) {
        val accounts = accountRepository.getAccounts().associateBy(
            keySelector = { it.address },
            valueTransform = { it.name }
        )

        Identifier(accounts)
    }
}
