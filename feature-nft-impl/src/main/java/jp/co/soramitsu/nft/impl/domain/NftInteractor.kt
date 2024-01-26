package jp.co.soramitsu.nft.impl.domain

import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.nft.domain.NFTInteractor
import jp.co.soramitsu.nft.impl.data.NftCollection
import jp.co.soramitsu.nft.data.pagination.PaginationRequest
import jp.co.soramitsu.nft.domain.NFTTransferInteractor
import jp.co.soramitsu.nft.domain.models.NFTCollection
import jp.co.soramitsu.nft.impl.data.Nft
import jp.co.soramitsu.nft.domain.models.NFTFilter
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList

class NftInteractor(
    private val nftInteractor: NFTInteractor,
    private val nftTransferInteractor: NFTTransferInteractor,
    private val chainsRepository: ChainsRepository,
    private val accountRepository: AccountRepository
) {

    private val loadedCollectionsByContractAddress = mutableMapOf<String, NftCollection>()

    private suspend fun userOwnedERC1155TokenTest(etherCollection: NFTCollection<NFTCollection.NFT.Light>) {
        for (lightToken in etherCollection.tokens) {
            val fullToken = nftInteractor.getNFTDetails(
                etherCollection.chainId,
                etherCollection.contractAddress!!,
                lightToken.tokenId!!
            )

            if (fullToken.tokenType != "ERC1155")
                continue

            println("This is checkpoint: fullToken, contractAddress - ${fullToken.contractAddress}, tokenId - ${fullToken.tokenId}")

            nftTransferInteractor.networkFeeFlow(
                token = fullToken,
                receiver = "0xA150ea05b1A515433a6426f309Ab1bC5Dc62A014",
                canReceiverAcceptToken = false
            ).onEach {
                println("This is checkpoint: erc1155.networkFee - $it")

                it.exceptionOrNull()?.let { exception -> throw exception }
            }.first()
        }
    }

    suspend fun getNfts(
        filters: List<NFTFilter>,
        selectedChainId: String?,
        metaAccountId: Long
    ): Map<Chain, Result<List<NftCollection>>> {
//        nftInteractor.setNFTFilter(NFTFilter.Airdrops, true)
//        nftInteractor.setNFTFilter(NFTFilter.Spam, true)
//        nftInteractor.setNFTFilter(NFTFilter.Airdrops, false)
//        nftInteractor.setNFTFilter(NFTFilter.Airdrops, true)
//        nftInteractor.setNFTFilter(NFTFilter.Airdrops, false)
//        nftInteractor.setNFTFilter(NFTFilter.Spam, false)
//        nftInteractor.setNFTFilter(NFTFilter.Spam, true)

        val result = nftInteractor.userOwnedNFTsFlow(
            paginationRequestFlow = flow {
                println("This is checkpoint: emitting Next.Page")
                emit(PaginationRequest.Next.Page)
                delay(20_000)
                println("This is checkpoint: emitting Prev.Page")
                emit(PaginationRequest.Prev.Page)
                delay(20_000)
                println("This is checkpoint: emitting Next.Page")
                emit(PaginationRequest.Next.Page)
                delay(20_000)
                println("This is checkpoint: emitting Next.Page")
                emit(PaginationRequest.Next.Page)
                delay(20_000)
                println("This is checkpoint: emitting Next.Page")
                emit(PaginationRequest.Next.Page)
                delay(20_000)
                println("This is checkpoint: emitting Next.Page")
                emit(PaginationRequest.Next.Page)
                delay(20_000)
                println("This is checkpoint: emitting Prev.Page")
                emit(PaginationRequest.Prev.Page)
            },
            chainSelectionFlow = flow { emit(selectedChainId) }
        ).onEach {
//            if (it.isNotEmpty())
//                it.forEach { (chain, result) ->
//                    result.onSuccess {
//                        println("This is checkpoint: chain - ${chain.name}, collectionName - ${it.collectionName}, firstToken - ${it.tokens.firstOrNull()?.tokenId}")
//                    }.onFailure {
//                        println("This is checkpoint: chain - ${chain.name}, error - ${it.message}")
//                    }.getOrNull()
//                }
//            else println("This is checkpoint: empty nft collections list")
        }.toList().first()

        println("This is checkpoint: light nfts loaded")

//        for(collection in result) {
//            if (collection.chainId != ethereumChainId)
//                continue
//
//            if (collection.contractAddress?.uppercase() != "0x495f947276749Ce646f68AC8c248420045cb7b5e".uppercase())
//                continue
//
//            userOwnedERC1155TokenTest(collection)
//
//            println("This is checkpoint: ---------------------------------------------")
//        }

        return result.groupBy { it.first.id }.mapKeys { (chainId, _) ->
            chainsRepository.getChain(chainId)
        }.mapValues { (_, collections) ->
            Result.success(
                collections.mapNotNull {
                    it.second.getOrNull()
                }.map { collection ->
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