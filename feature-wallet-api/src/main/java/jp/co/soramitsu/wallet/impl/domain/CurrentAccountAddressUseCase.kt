package jp.co.soramitsu.wallet.impl.domain

import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CurrentAccountAddressUseCase(
    private val accountRepository: AccountRepository,
    private val chainsRepository: ChainsRepository
) {
    suspend operator fun invoke(chainId: ChainId): String? = withContext(Dispatchers.Default) {
        val account = accountRepository.getSelectedMetaAccount()
        val chain = chainsRepository.getChain(chainId)
        return@withContext account.address(chain)
    }
}
