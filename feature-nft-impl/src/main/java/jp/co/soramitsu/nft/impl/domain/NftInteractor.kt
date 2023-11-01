package jp.co.soramitsu.nft.impl.domain

import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.nft.impl.data.NftRepository
import jp.co.soramitsu.nft.impl.data.model.NftCollection
import jp.co.soramitsu.nft.impl.presentation.filters.NftFilter
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.alchemyNftId
import kotlinx.coroutines.flow.first

class NftInteractor(private val nftRepository: NftRepository, private val accountRepository: AccountRepository, private val chainRegistry: ChainRegistry) {

    suspend fun getNfts(filters: List<NftFilter>): List<NftCollection> {
        val currentAccount = accountRepository.getSelectedMetaAccount()
        val nftChains = chainRegistry.chainsById.first().filter { it.value.supportNft }.filter { it.value.alchemyNftId.isNullOrEmpty().not() }
        val allChainsCollections = nftChains.mapNotNull {
            val address = currentAccount.address(it.value) ?: return@mapNotNull null
            nftRepository.getNfts(it.value, address, filters.map { it.name.uppercase() })
        }.flatten()
        return allChainsCollections
    }
}