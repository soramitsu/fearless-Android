package jp.co.soramitsu.nft.impl.domain

import jp.co.soramitsu.nft.domain.NFTInteractor
import jp.co.soramitsu.nft.impl.data.NftCollection
import jp.co.soramitsu.nft.data.models.requests.PaginationRequest
import jp.co.soramitsu.nft.impl.data.Nft
import jp.co.soramitsu.nft.domain.models.NFTFilter
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

class NftInteractor(
    private val nftInteractor: NFTInteractor,
    private val chainsRepository: ChainsRepository
) {

    private val loadedCollectionsByContractAddress = mutableMapOf<String, NftCollection>()

    suspend fun getNfts(
        filters: List<NFTFilter>,
        selectedChainId: String?,
        metaAccountId: Long
    ): Map<Chain, Result<List<NftCollection>>> {
        val result = nftInteractor.userOwnedNFTsFlow(
            paginationRequestFlow = flow { emit(PaginationRequest.NextPage) },
            chainSelectionFlow = flow { emit(selectedChainId) }
        ).first()

        return result.groupBy { it.chainId }.mapKeys { (chainId, _) ->
            chainsRepository.getChain(chainId)
        }.mapValues { (_, collections) ->
            Result.success(
                collections.map { collection ->
                    NftCollection(
                        contractAddress = collection.contractAddress!!, // TODO remove nullability!!
                        name = collection.collectionName,
                        image = collection.imageUrl,
                        description = collection.description,
                        chainId = collection.chainId,
                        chainName = collection.chainName,
                        type = collection.type,
                        nfts = collection.tokens.map {
                             Nft(
                                 title = "${collection.collectionName} ${it.tokenId}",
                                 description = "",
                                 thumbnail = collection.imageUrl,
                                 owned = "Mine",
                                 tokenId = it.tokenId
                             )
                        },
                        collectionSize = collection.collectionSize
                    ).also {
                        loadedCollectionsByContractAddress[it.contractAddress] = it
                    }
                }
            )
        }
    }

    fun getCollection(contractAddress: String): NftCollection {
        return loadedCollectionsByContractAddress[contractAddress] // TODO load from alchemy
            ?: throw IllegalStateException(
                """
                    Can't find collection with contract address: $contractAddress
                """.trimIndent()
            )
    }
}