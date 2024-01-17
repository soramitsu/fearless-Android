package jp.co.soramitsu.nft.impl.domain

import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.core.models.ChainId
import jp.co.soramitsu.nft.domain.NFTInteractor
import jp.co.soramitsu.nft.impl.data.NftCollection
import jp.co.soramitsu.nft.data.models.requests.PaginationRequest
import jp.co.soramitsu.nft.domain.NFTTransferInteractor
import jp.co.soramitsu.nft.impl.data.Nft
import jp.co.soramitsu.nft.domain.models.NFTFilter
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ethereumChainId
import jp.co.soramitsu.shared_utils.extensions.toHexString
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take

class NftInteractor(
    private val nftInteractor: NFTInteractor,
    private val nftTransferInteractor: NFTTransferInteractor,
    private val chainsRepository: ChainsRepository,
    private val accountRepository: AccountRepository
) {

    private val loadedCollectionsByContractAddress = mutableMapOf<String, NftCollection>()

    suspend fun getNfts(
        filters: List<NFTFilter>,
        selectedChainId: String?,
        metaAccountId: Long
    ): Map<Chain, Result<List<NftCollection>>> {
        val result = nftInteractor.userOwnedNFTsFlow(
            paginationRequestFlow = flow { emit(PaginationRequest.NextPageSized(10_000)) },
            chainSelectionFlow = flow { emit(selectedChainId) }
        ).first()

//        val accountAddress = accountRepository.getSelectedMetaAccount().address(
//            chain = chainsRepository.getChain(ethereumChainId)
//        )
//        println("This is checkpoint: accountAddress - $accountAddress")

        println("This is checkpoint: light nfts loaded")

//        for(collection in result) {
//            if (collection.chainId != ethereumChainId)
//                continue
//
//            try {
//                val erc721TestResult = erc721TokenTest(
//                    chainId = ethereumChainId,
//                    contractAddress = collection.contractAddress!!,
//                    receiver = "0x3DCf140bA86310e5F5a96c511ea97D1e217E049D"
//                )
//
////                if (erc721TestResult)
////                    break
//            } catch (t: Throwable) {
//                throw t
//            }
//
//            println("This is checkpoint: ---------------------------------------------")
//        }


        for(collection in result) {
            if (collection.chainId != ethereumChainId)
                continue

            println("This is checkpoint: contractAddress - ${collection.contractAddress}")

            if (collection.contractAddress?.uppercase() != "0x495f947276749Ce646f68AC8c248420045cb7b5e".uppercase())
                continue

            try {
                val erc1155TestResult = erc1155TokenTest(
                    chainId = ethereumChainId,
                    contractAddress = collection.contractAddress!!,
                    receiver = "0x3DCf140bA86310e5F5a96c511ea97D1e217E049D"
                )

//                if (erc1155TestResult)
//                    break
            } catch (t: Throwable) {
                t.printStackTrace()
                continue
            }

            println("This is checkpoint: ---------------------------------------------")
        }

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

    private suspend fun erc721TokenTest(
        chainId: ChainId,
        receiver: String,
        contractAddress: String
    ): Boolean {
        val result = nftInteractor.collectionNFTsFlow(
            paginationRequestFlow = flow { emit(PaginationRequest.NextPage) },
            chainSelectionFlow = flow { emit(chainId) },
            contractAddressFlow = flow { emit(contractAddress) }
        ).first().getOrThrow()

        for (token in result.tokens) {
            try {
                if (token.tokenType != "ERC721")
                    continue
//
//                nftTransferInteractor.networkFeeFlow(
//                    token = token,
//                    receiver = receiver,
//                    canReceiverAcceptToken = false
//                ).onEach {
//                    println("This is checkpoint: erc721.networkFee - $it")
//                }.take(1).collect()

                return nftTransferInteractor.send(
                    token = token,
                    receiver = receiver,
                    canReceiverAcceptToken = false
                ).onSuccess {
                    println("This is checkpoint: erc721.send.txHash - $it")
                }.onFailure {
                    println("This is checkpoint: erc721.send.error - ${it.message}")
                    throw it
                }.getOrNull() != null
            } catch (t: Throwable) {
                throw t
            }
        }

        return true
    }

    private suspend fun erc1155TokenTest(
        chainId: ChainId,
        receiver: String,
        contractAddress: String
    ): Boolean {
        val result = nftInteractor.collectionNFTsFlow(
            paginationRequestFlow = flow { emit(PaginationRequest.NextPageSized(10_000)) },
            chainSelectionFlow = flow { emit(chainId) },
            contractAddressFlow = flow { emit(contractAddress) }
        ).first().getOrThrow()

        for (token in result.tokens) {
            try {
                if (token.tokenType != "ERC1155")
                    continue

                nftTransferInteractor.networkFeeFlow(
                    token = token,
                    receiver = receiver,
                    canReceiverAcceptToken = false
                ).onEach {
                    println("This is checkpoint: erc1155.networkFee - $it")
                }.take(1).collect()

                break

//                return nftTransferInteractor.send(
//                    token = token,
//                    receiver = receiver,
//                    canReceiverAcceptToken = false
//                ).onSuccess {
//                    println("This is checkpoint: erc1155.send.txHash - $it")
//                }.onFailure {
//                    println("This is checkpoint: erc1155.send.error - ${it.message}")
//                    throw it
//                }.getOrNull() != null
            } catch (t: Throwable) {
                t.printStackTrace()
                continue
            }
        }

        return true
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