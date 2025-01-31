package jp.co.soramitsu.account.api.presentation.account

import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.shared_utils.extensions.fromHex
import jp.co.soramitsu.shared_utils.ss58.SS58Encoder.toAccountId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// TODO adopt for meta account logic
class AddressDisplayUseCase(
    private val accountRepository: AccountRepository
) {

    suspend operator fun invoke(address: String): String? {
        return withContext(Dispatchers.Default) {
            val accountId = kotlin.runCatching { address.toAccountId() }.getOrNull() ?: address.fromHex()
            accountRepository.findMetaAccount(accountId)?.name
        }
    }
}
