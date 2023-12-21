package jp.co.soramitsu.nft.impl.domain

import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.common.utils.failure
import jp.co.soramitsu.nft.impl.data.NftRepository
import jp.co.soramitsu.nft.impl.data.model.NftCollection
import jp.co.soramitsu.nft.impl.data.model.PaginationRequest
import jp.co.soramitsu.nft.impl.presentation.filters.NftFilter
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.alchemyNftId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

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

    fun collectionItemsFlow(
        paginationRequestFlow: Flow<PaginationRequest>,
        chainSelectionFlow: Flow<String>,
        collectionSlugFlow: Flow<String>
    ): Flow<Result<NftCollection>> {
        return flow {
            val chainSelectionHelperFlow =
                chainsRepository.chainsFlow().map {
                    it.filter { it.supportNft && !it.alchemyNftId.isNullOrEmpty() }
                }.combine(chainSelectionFlow) { chains, chainSelection ->
                    chains.find { it.id == chainSelection } ?: error(
                        """
                            Chain with provided chainId of $chainSelection, is either not supported or does not support NFT operations
                        """.trimIndent()
                    )
                }

            nftRepository.nftCollectionBySlug(
                paginationRequestFlow = paginationRequestFlow,
                chainSelectionFlow = chainSelectionHelperFlow,
                collectionSlugFlow = collectionSlugFlow
            ).collect(this)
        }
    }

    private suspend fun getAllNftChains(): List<Chain> {
        return chainsRepository.getChains()
            .filter { it.supportNft && it.alchemyNftId.isNullOrEmpty().not() }
    }
}