package jp.co.soramitsu.wallet.impl.domain

import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId

class CurrentAccountAddressUseCase(private val accountRepository: AccountRepository, private val chainRegistry: ChainRegistry) {
    suspend operator fun invoke(chainId: ChainId): String? {
        val account = accountRepository.getSelectedMetaAccount()
        val chain = chainRegistry.getChain(chainId)
        return account.address(chain)
    }
}
