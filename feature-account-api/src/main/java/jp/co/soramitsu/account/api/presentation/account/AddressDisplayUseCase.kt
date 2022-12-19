package jp.co.soramitsu.account.api.presentation.account

import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
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
        return withContext(Dispatchers.Default) {
            val accountId = kotlin.runCatching { address.toAccountId() }.getOrNull() ?: address.fromHex()
            accountRepository.findMetaAccount(accountId)?.name
        }
    }

    suspend fun createIdentifier(): Identifier = withContext(Dispatchers.Default) {
        val accounts = accountRepository.getAccounts().associateBy(
            keySelector = { it.address },
            valueTransform = { it.name }
        )

        Identifier(accounts)
    }
}
