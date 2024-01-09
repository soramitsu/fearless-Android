package jp.co.soramitsu.nft.impl.domain

import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.common.data.network.runtime.binding.cast
import jp.co.soramitsu.common.utils.concurrentRequestFlow
import jp.co.soramitsu.nft.data.NFTRepository
import jp.co.soramitsu.nft.domain.NFTInteractor
import jp.co.soramitsu.nft.domain.models.NFTCollection
import jp.co.soramitsu.nft.data.models.requests.PaginationRequest
import jp.co.soramitsu.nft.data.models.TokenInfo
import jp.co.soramitsu.nft.domain.models.utils.toFullNFT
import jp.co.soramitsu.nft.domain.models.utils.toFullNFTCollection
import jp.co.soramitsu.nft.domain.models.utils.toLightNFTCollection
import jp.co.soramitsu.nft.impl.domain.models.NFTTransferParams
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.alchemyNftId
import jp.co.soramitsu.shared_utils.extensions.requireHexPrefix
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.transformLatest
import java.math.BigDecimal
import java.math.BigInteger

class NFTInteractorImpl(
    private val sendNFTUseCase: SendNFTUseCase,
    private val nftRepository: NFTRepository,
    private val accountRepository: AccountRepository,
    private val chainsRepository: ChainsRepository
): NFTInteractor {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun userOwnedNFTsFlow(
        paginationRequestFlow: Flow<PaginationRequest>,
        chainSelectionFlow: Flow<String?>,
        exclusionFiltersFlow: Flow<List<String>>
    ): Flow<List<NFTCollection<NFTCollection.NFT.Light>>> {
        val chainsHelperFlow = chainsRepository.chainsFlow().map { chains ->
            chains.filter { it.supportNft && !it.alchemyNftId.isNullOrEmpty() }
        }.combine(chainSelectionFlow) { chains, chainSelection ->
            if (chainSelection.isNullOrEmpty()) chains
            else chains.filter { it.id == chainSelection }
        }

        return nftRepository.paginatedUserOwnedNFTsFlow(
            paginationRequestFlow = paginationRequestFlow,
            chainSelectionFlow = chainsHelperFlow,
            selectedMetaAddressFlow = accountRepository.selectedMetaAccountFlow(),
            exclusionFiltersFlow = exclusionFiltersFlow
        ).transformLatest { responses ->
            val result =
                responses.concurrentRequestFlow { (chain, userOwnedTokens) ->
                    val tokensByContractAddress = userOwnedTokens.ownedNfts
                        .groupBy { it.contract?.address }
                        .filter { it.key != null }
                        .cast<Map<String, List<TokenInfo.WithoutMetadata>>>()

                    val contractsByAddresses = nftRepository.contractMetadataBatch(
                        chain = chain,
                        contractAddresses = tokensByContractAddress.keys
                    ).associateBy { it.address }

                    val result = tokensByContractAddress.mapNotNull { (contractAddress, rawTokens) ->
                        contractsByAddresses[contractAddress]?.toLightNFTCollection(
                            chain = chain,
                            contractAddress = contractAddress,
                            tokens = rawTokens.map {
                                NFTCollection.NFT.Light(
                                    tokenId = it.id?.tokenId,
                                    balance = it.balance
                                )
                            }
                        )
                    }

                    emit(result)
                }.toList().flatten()

            emit(result)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun collectionNFTsFlow(
        paginationRequestFlow: Flow<PaginationRequest>,
        chainSelectionFlow: Flow<String>,
        contractAddressFlow: Flow<String>
    ): Flow<Result<NFTCollection<NFTCollection.NFT.Full>>> {
        return flow {
            val chainSelectionHelperFlow =
                chainsRepository.chainsFlow().map { chains ->
                    chains.filter { it.supportNft && !it.alchemyNftId.isNullOrEmpty() }
                }.combine(chainSelectionFlow) { chains, chainSelection ->
                    chains.find { it.id == chainSelection } ?: error(
                        """
                            Chain with provided chainId of $chainSelection, is either not supported or does not support NFT operations
                        """.trimIndent()
                    )
                }

            nftRepository.paginatedNFTCollectionByContractAddressFlow(
                paginationRequestFlow = paginationRequestFlow,
                chainSelectionFlow = chainSelectionHelperFlow,
                contractAddressFlow = contractAddressFlow
            ).mapLatest { result ->
                result.mapCatching { (chain, response) ->
                    val contractMetadata = response.nfts.firstOrNull {
                        it.contractMetadata != null
                    }?.contractMetadata

                    val contractAddress = response.nfts.firstOrNull {
                        it.contract?.address != null
                    }?.contract?.address.orEmpty()

                    response.toFullNFTCollection(
                        chain = chain,
                        contractAddress = contractAddress,
                        contractMetadata = contractMetadata
                    )
                }
            }.collect(this)
        }
    }

    override suspend fun getNFTDetails(
        chainId: ChainId,
        contractAddress: String,
        tokenId: String
    ): NFTCollection.NFT.Full {
        val chain = chainsRepository.getChain(chainId)

        return nftRepository.tokenMetadata(chain, contractAddress, tokenId).toFullNFT(
            chain = chain,
            contractAddress = contractAddress
        )
    }

    override suspend fun send(token: NFTCollection.NFT.Full, receiver: String): Result<String> {
        val chain = chainsRepository.getChain(token.chainId)
        val sender = accountRepository.getSelectedMetaAccount().address(chain)!!

        val tokenId = token.tokenId?.requireHexPrefix()?.drop(2) ?: error(
            """
                TokenId supplied is null.
            """.trimIndent()
        )

        val params = when(token.tokenType) {
            "ERC721" -> NFTTransferParams.ERC721(
                sender = sender,
                receiver = receiver,
                tokenId = BigInteger(tokenId, 16),
                data = ByteArray(0)
            )
            "ERC1155" -> NFTTransferParams.ERC1155(
                sender = sender,
                receiver = receiver,
                tokenId = BigInteger(tokenId, 16),
                amount = BigDecimal.ONE,
                data = ByteArray(0)
            )
            else -> return Result.failure(
                IllegalArgumentException(
                    """
                        Token provided is not supported.
                    """.trimIndent()
                )
            )
        }

        return sendNFTUseCase.invoke(
            chain = chain,
            params = params
        )
    }
}