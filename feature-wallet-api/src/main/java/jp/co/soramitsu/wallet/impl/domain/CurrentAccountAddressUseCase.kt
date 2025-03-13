package jp.co.soramitsu.wallet.impl.domain

import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId

class CurrentAccountAddressUseCase(private val accountRepository: AccountRepository, private val chainsRepository: ChainsRepository) {
    suspend operator fun invoke(chainId: ChainId): String? {
        val account = accountRepository.getSelectedMetaAccount()
        val chain = chainsRepository.getChain(chainId)
        return account.address(chain)
    }
}
