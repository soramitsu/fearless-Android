package jp.co.soramitsu.nft.impl.domain

import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.nft.impl.data.NftRepository
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import kotlinx.coroutines.flow.first

class NftInteractor(private val nftRepository: NftRepository, private val accountRepository: AccountRepository, private val chainRegistry: ChainRegistry) {



    suspend fun getNfts() {
        val currentAccount = accountRepository.getSelectedMetaAccount()
        val nftChains = chainRegistry.chainsById.first().filter { it.value.supportNft }
        nftChains.forEach {
            val address = currentAccount.address(it.value) ?: return@forEach
            val response = nftRepository.getNfts(it.value, address)
            hashCode()
        }

    }
}