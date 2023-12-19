package jp.co.soramitsu.nft.impl.domain

import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.common.utils.failure
import jp.co.soramitsu.nft.impl.data.NftRepository
import jp.co.soramitsu.nft.impl.data.model.NftCollection
import jp.co.soramitsu.nft.impl.presentation.filters.NftFilter
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.alchemyNftId

class NftInteractor(
    private val nftRepository: NftRepository,
    private val accountRepository: AccountRepository,
    private val chainsRepository: ChainsRepository
) {

    suspend fun getNfts(
        filters: List<NftFilter>,
        selectedChainId: String?,
        metaAccountId: Long
    ): Map<Chain, Result<List<NftCollection>>> {
        val metaAccount = accountRepository.getMetaAccount(metaAccountId)
        val nftChains = getAllNftChains()

        val filtered = if (selectedChainId.isNullOrEmpty()) {
            nftChains
        } else {
            nftChains.filter { it.id == selectedChainId }
        }

        val allChainsCollections = filtered.map { chain ->
            val address = metaAccount.address(chain)
                ?: return@map chain to Result.failure("Cannot find address for current wallet in ${chain.name}")

            val nftsResult = runCatching {
                nftRepository.getNfts(
                    chain,
                    address,
                    filters.map { it.name.uppercase() })
            }
            chain to nftsResult
        }.toMap()

        return allChainsCollections
    }

    private suspend fun getAllNftChains(): List<Chain> {
        return chainsRepository.getChains()
            .filter { it.supportNft && it.alchemyNftId.isNullOrEmpty().not() }
    }
}