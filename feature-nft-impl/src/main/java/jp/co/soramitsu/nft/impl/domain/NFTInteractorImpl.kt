package jp.co.soramitsu.nft.impl.domain

import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.core.models.ChainId
import jp.co.soramitsu.nft.data.NFTRepository
import jp.co.soramitsu.nft.data.pagination.PageBackStack
import jp.co.soramitsu.nft.data.pagination.PaginationRequest
import jp.co.soramitsu.nft.domain.NFTInteractor
import jp.co.soramitsu.nft.domain.models.NFT
import jp.co.soramitsu.nft.domain.models.NFTCollectionResult
import jp.co.soramitsu.nft.domain.models.NFTFilter
import jp.co.soramitsu.nft.impl.domain.models.nft.NFTImpl
import jp.co.soramitsu.nft.impl.domain.usecase.collections.CollectionsMappingAdapter
import jp.co.soramitsu.nft.impl.domain.usecase.tokensbycontract.RequestSwitchingMediator
import jp.co.soramitsu.nft.impl.domain.usecase.tokensbycontract.TokensMappingAdapter
import jp.co.soramitsu.nft.impl.domain.usecase.tokensbycontract.TokensTrimmingMediator
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.alchemyNftId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import java.math.BigInteger

class NFTInteractorImpl(
    private val nftRepository: NFTRepository,
    private val accountRepository: AccountRepository,
    private val chainsRepository: ChainsRepository,
    private val collectionMappingAdapter: CollectionsMappingAdapter,
    private val requestSwitchingMediator: RequestSwitchingMediator,
    private val tokensMappingAdapter: TokensMappingAdapter,
    private val tokensTrimmingMediator: TokensTrimmingMediator
) : NFTInteractor {

    override fun setNFTFilter(filter: NFTFilter, isApplied: Boolean) {
        nftRepository.setNFTFilter(filter.name, !isApplied)
    }

    override fun nftFiltersFlow(): Flow<Map<NFTFilter, Boolean>> {
        return nftRepository.nftFiltersFlow.map { allCurrentlyAppliedFilters ->
            NFTFilter.values().associateWith { filter ->
                filter.name !in allCurrentlyAppliedFilters
            }
        }
    }

    override fun collectionsFlow(
        paginationRequestFlow: Flow<PaginationRequest>,
        chainSelectionFlow: Flow<String?>
    ): Flow<Sequence<NFTCollectionResult>> {
        val chainsHelperFlow = chainsRepository.chainsFlow().map { chains ->
            chains.filter { it.supportNft && !it.alchemyNftId.isNullOrEmpty() }
        }.combine(chainSelectionFlow) { chains, chainSelection ->
            if (chainSelection.isNullOrEmpty()) {
                chains
            } else {
                chains.filter { it.id == chainSelection }
            }
        }

        val exclusionFiltersHelperFlow =
            nftRepository.nftFiltersFlow.map { filters ->
                filters.map { it.uppercase() }
            }

        return collectionMappingAdapter {
            nftRepository.paginatedUserOwnedContractsFlow(
                paginationRequestFlow = paginationRequestFlow,
                chainSelectionFlow = chainsHelperFlow,
                selectedMetaAccountFlow = accountRepository.selectedMetaAccountFlow(),
                exclusionFiltersFlow = exclusionFiltersHelperFlow
            )
        }.flowOn(Dispatchers.Default)
    }

    @Suppress("VariableNaming", "CyclomaticComplexMethod")
    override fun tokensFlow(
        paginationRequestFlow: Flow<PaginationRequest>,
        chainSelectionFlow: Flow<String>,
        contractAddressFlow: Flow<String>
    ): Flow<NFTCollectionResult> {
        val chainSelectionHelperFlow = chainsRepository.chainsFlow()
            .map { chains -> chains.filter { it.supportNft && !it.alchemyNftId.isNullOrEmpty() } }
            .combine(chainSelectionFlow) { chains, chainSelection ->
                chains.find { it.id == chainSelection } ?: error(
                    """
                        Chain with id - $chainSelection - is either not supported 
                        or does not support NFT operations.
                    """.trimIndent()
                )
            }

        val exclusionFiltersHelperFlow = nftRepository.nftFiltersFlow
            .map { filters -> filters.map { it.uppercase() } }

        return tokensTrimmingMediator {
            requestSwitchingMediator(
                requestFlow = paginationRequestFlow,
                RequestSwitchingMediator.Holder(
                    flag = FLAG_USER_OWNED_TOKENS_FLOW
                ) { alteredPaginationRequestFlow, switchFlowHandle ->
                    tokensMappingAdapter {
                        nftRepository.paginatedUserOwnedNFTsByContractAddressFlow(
                            paginationRequestFlow = alteredPaginationRequestFlow,
                            chainSelectionFlow = chainSelectionHelperFlow,
                            contractAddressFlow = contractAddressFlow,
                            selectedMetaAccountFlow = accountRepository.selectedMetaAccountFlow(),
                            exclusionFiltersFlow = exclusionFiltersHelperFlow
                        ).mapNotNull { pagedResponse ->
                            if (pagedResponse.result.getOrNull() is PageBackStack.PageResult.NoNextPages) {
                                switchFlowHandle.switchToFlowWithFlag(
                                    flag = FLAG_ALL_CONTRACT_TOKENS_FLOW
                                )
                                return@mapNotNull null
                            }

                            return@mapNotNull pagedResponse
                        }
                    }
                },
                RequestSwitchingMediator.Holder(
                    flag = FLAG_ALL_CONTRACT_TOKENS_FLOW
                ) { alteredPaginationRequestFlow, switchFlowHandle ->
                    tokensMappingAdapter {
                        nftRepository.paginatedNFTCollectionByContractAddressFlow(
                            paginationRequestFlow = alteredPaginationRequestFlow,
                            chainSelectionFlow = chainSelectionHelperFlow,
                            contractAddressFlow = contractAddressFlow
                        ).mapNotNull { pagedResponse ->
                            if (pagedResponse.result.getOrNull() is PageBackStack.PageResult.NoPrevPages) {
                                switchFlowHandle.switchToFlowWithFlag(
                                    flag = FLAG_USER_OWNED_TOKENS_FLOW
                                )
                                return@mapNotNull null
                            }

                            return@mapNotNull pagedResponse
                        }
                    }
                }
            )
        }
    }

    override suspend fun getNFTDetails(
        chainId: ChainId,
        contractAddress: String,
        tokenId: String
    ): Result<NFT> {
        val chain = chainsRepository.getChain(chainId)

        return nftRepository.tokenMetadata(chain, contractAddress, tokenId).map { result ->
            NFTImpl(result, chain.id, chain.name)
        }
    }

    override suspend fun getOwnersForNFT(token: NFT): Result<List<String>> {
        val chain = chainsRepository.getChain(token.chainId)

        return runCatching {
            if (token.contractAddress.isBlank()) {
                error(
                    """
                        TokenId supplied is null.
                    """.trimIndent()
                )
            }

            if (token.tokenId < BigInteger.ZERO) {
                error(
                    """
                        TokenId supplied is null.
                    """.trimIndent()
                )
            }

            return@runCatching nftRepository.tokenOwners(
                chain = chain,
                contractAddress = token.contractAddress,
                tokenId = token.tokenId.toString()
            ).getOrThrow().ownersList
        }
    }

    private companion object {
        const val FLAG_USER_OWNED_TOKENS_FLOW = 0
        const val FLAG_ALL_CONTRACT_TOKENS_FLOW = 1
    }
}
