package jp.co.soramitsu.nft.impl.domain

import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.core.models.ChainId
import jp.co.soramitsu.nft.data.NFTRepository
import jp.co.soramitsu.nft.data.pagination.PaginationEvent
import jp.co.soramitsu.nft.data.pagination.PaginationRequest
import jp.co.soramitsu.nft.domain.NFTInteractor
import jp.co.soramitsu.nft.domain.models.NFT
import jp.co.soramitsu.nft.domain.models.NFTCollectionResult
import jp.co.soramitsu.nft.domain.models.NFTFilter
import jp.co.soramitsu.nft.domain.models.utils.toNFT
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.alchemyNftId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import java.math.BigInteger

class NFTInteractorImpl(
    private val nftRepository: NFTRepository,
    private val accountRepository: AccountRepository,
    private val chainsRepository: ChainsRepository,
    private val collectionsByContractBufferingMediator: NFTCollectionsByContractBufferingMediator,
    private val paginationRequestAlteringMediator: PaginationRequestAlteringMediator,
    private val collectionsByContractMappingAdapter: NFTCollectionsByContractMappingAdapter
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

    override fun userOwnedCollectionsFlow(
        paginationRequestFlow: Flow<PaginationRequest>,
        chainSelectionFlow: Flow<String?>
    ): Flow<List<NFTCollectionResult>> {
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

        return AlterNFTCollectionsResultListUseCase {
            MapUserOwnedNFTCollectionsListUseCase {
                nftRepository.paginatedUserOwnedContractsFlow(
                    paginationRequestFlow = paginationRequestFlow,
                    chainSelectionFlow = chainsHelperFlow,
                    selectedMetaAccountFlow = accountRepository.selectedMetaAccountFlow(),
                    exclusionFiltersFlow = exclusionFiltersHelperFlow
                )
            }.invoke()
        }.invoke().flowOn(Dispatchers.Default)
    }

    @Suppress("VariableNaming", "CyclomaticComplexMethod")
    override fun collectionNFTsFlow(
        paginationRequestFlow: Flow<PaginationRequest>,
        chainSelectionFlow: Flow<String>,
        contractAddressFlow: Flow<String>
    ): Flow<Pair<NFTCollectionResult, PaginationRequest>> {
        val UserOwnedNFTsStartedLoading = 0
        val AvailableNFTsStartedLoading = 1

        val mutableTriggerFlow = MutableStateFlow(0)

        val paginationRequestHelperFlow =
            paginationRequestFlow.combine(mutableTriggerFlow) { request, capacity ->
                if (capacity == 0)
                    return@combine request

                return@combine when(request) {
                    is PaginationRequest.Prev -> {
                        PaginationRequest.Prev.WithSize(capacity)
                    }

                    is PaginationRequest.Next -> {
                        PaginationRequest.Next.WithSize(capacity)
                    }
                }
            }

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

        return collectionsByContractBufferingMediator(
            callback = {
                println("This is checkpoint: callback, value - $it")
                mutableTriggerFlow.tryEmit(it)
            }
        ) {
            paginationRequestAlteringMediator(
                paginationRequestFlow = paginationRequestHelperFlow,
                PaginationRequestAlteringMediator.Holder(
                    UserOwnedNFTsStartedLoading
                ) { alteredPaginationRequestFlow, alterRequestFlowCallback ->
                    collectionsByContractMappingAdapter {
                        nftRepository.paginatedUserOwnedNFTsByContractAddressFlow(
                            paginationRequestFlow = alteredPaginationRequestFlow,
                            chainSelectionFlow = chainSelectionHelperFlow,
                            contractAddressFlow = contractAddressFlow,
                            selectedMetaAccountFlow = accountRepository.selectedMetaAccountFlow(),
                            exclusionFiltersFlow = exclusionFiltersHelperFlow
                        ).onEach {
                            if (it.result.getOrNull() is PaginationEvent.AllNextPagesLoaded) {
                                alterRequestFlowCallback.alterTo(AvailableNFTsStartedLoading)
                            }
                        }
                    }
                },
                PaginationRequestAlteringMediator.Holder(
                    AvailableNFTsStartedLoading
                ) { alteredPaginationRequestFlow, alterLoadingRequestFlow ->
                    collectionsByContractMappingAdapter {
                        nftRepository.paginatedNFTCollectionByContractAddressFlow(
                            paginationRequestFlow = alteredPaginationRequestFlow,
                            chainSelectionFlow = chainSelectionHelperFlow,
                            contractAddressFlow = contractAddressFlow
                        ).onEach {
                            if (it.result.getOrNull() is PaginationEvent.AllPreviousPagesLoaded) {
                                alterLoadingRequestFlow.alterTo(UserOwnedNFTsStartedLoading)
                            }
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
            result.toNFT(
                chain = chain
            )
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
}
