package jp.co.soramitsu.nft.impl.domain

import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.common.utils.concurrentRequestFlow
import jp.co.soramitsu.core.models.ChainId
import jp.co.soramitsu.nft.data.NFTRepository
import jp.co.soramitsu.nft.data.models.TokenInfo
import jp.co.soramitsu.nft.data.models.wrappers.NFTResponse
import jp.co.soramitsu.nft.data.pagination.PaginationEvent
import jp.co.soramitsu.nft.data.pagination.PaginationRequest
import jp.co.soramitsu.nft.domain.NFTInteractor
import jp.co.soramitsu.nft.domain.models.NFTCollection
import jp.co.soramitsu.nft.domain.models.NFTFilter
import jp.co.soramitsu.nft.domain.models.utils.toFullNFT
import jp.co.soramitsu.nft.domain.models.utils.toFullNFTCollection
import jp.co.soramitsu.nft.domain.models.utils.toLightNFTCollection
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.alchemyNftId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class NFTInteractorImpl(
    private val nftRepository: NFTRepository,
    private val accountRepository: AccountRepository,
    private val chainsRepository: ChainsRepository
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

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun userOwnedNFTsFlow(
        paginationRequestFlow: Flow<PaginationRequest>,
        chainSelectionFlow: Flow<String?>
    ): Flow<List<Pair<Chain, Result<NFTCollection<NFTCollection.NFT.Light>>>>> {
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

        return nftRepository.paginatedUserOwnedNFTsFlow(
            paginationRequestFlow = paginationRequestFlow,
            chainSelectionFlow = chainsHelperFlow,
            selectedMetaAccountFlow = accountRepository.selectedMetaAccountFlow(),
            exclusionFiltersFlow = exclusionFiltersHelperFlow
        ).transformLatest { responses ->
            responses.concurrentRequestFlow { pagedResponse ->
                val result = pagedResponse.result.mapCatching { paginationEvent ->
                    when (paginationEvent) {
                        is PaginationEvent.AllPreviousPagesLoaded ->
                            return@concurrentRequestFlow // skip operations for this chain; do nothing

                        is PaginationEvent.AllNextPagesLoaded ->
                            return@concurrentRequestFlow // skip operations for this chain; do nothing

                        is PaginationEvent.PageIsLoaded ->
                            return@mapCatching paginationEvent.toLightNFTCollection(
                                chain = pagedResponse.chain
                            )
                    }
                }

                emit(pagedResponse.chain to result)
            }.toList().flatMap { (chain, result) ->
                val dispatchedResultList =
                    result.map { collectionListPerSingleChain ->
                        collectionListPerSingleChain.map { collection ->
                            chain to Result.success(collection)
                        }
                    }

                dispatchedResultList.getOrElse { listOf(chain to Result.failure(it)) }
            }.also { collectionListOfAllChains ->
                if (collectionListOfAllChains.isNotEmpty()) {
                    emit(collectionListOfAllChains)
                }
            }
        }
    }

    private suspend fun PaginationEvent.PageIsLoaded<NFTResponse.UserOwnedTokens>.toLightNFTCollection(
        chain: Chain
    ): List<NFTCollection<NFTCollection.NFT.Light>> {
        val tokensByContractAddress = data.tokensInfoList
            .groupBy { it.contract?.address }
            .filter { it.key != null } as Map<String, List<TokenInfo.WithoutMetadata>>

        val contractsByAddresses = nftRepository.contractMetadataBatch(
            chain = chain,
            contractAddresses = tokensByContractAddress.keys
        ).associateBy { it.address }

        return tokensByContractAddress.mapNotNull { (contractAddress, rawTokens) ->
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
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun collectionNFTsFlow(
        paginationRequestFlow: Flow<PaginationRequest>,
        chainSelectionFlow: Flow<String>,
        contractAddressFlow: Flow<String>
    ): Flow<Pair<Result<NFTCollection<NFTCollection.NFT.Full>>, PaginationRequest>> {
        val chainSelectionHelperFlow =
            chainsRepository.chainsFlow().map { chains ->
                chains.filter { it.supportNft && !it.alchemyNftId.isNullOrEmpty() }
            }.combine(chainSelectionFlow) { chains, chainSelection ->
                chains.find { it.id == chainSelection } ?: error(
                    """
                        Chain with id - $chainSelection - is either not supported 
                        or does not support NFT operations.
                    """.trimIndent()
                )
            }

        val exclusionFiltersHelperFlow =
            nftRepository.nftFiltersFlow.map { filters ->
                filters.map { it.uppercase() }
            }

        return channelFlow {
            val mutableUserOwnedTokensRequestFlow = MutableSharedFlow<PaginationRequest>(
                replay = 1,
                onBufferOverflow = BufferOverflow.DROP_OLDEST
            )

            val mutableNFTCollectionRequestFlow = MutableSharedFlow<PaginationRequest>(
                replay = 1,
                onBufferOverflow = BufferOverflow.DROP_OLDEST
            )

            val atomicAreUserOwnedTokensLoading = AtomicBoolean(true)
            val atomicAreNFTCollectionsLoading = AtomicBoolean(false)

            launch {
                paginationRequestFlow.onEach {
                    if (!atomicAreNFTCollectionsLoading.get()) {
                        mutableUserOwnedTokensRequestFlow.tryEmit(it)
                    }

                    if (!atomicAreUserOwnedTokensLoading.get()) {
                        mutableNFTCollectionRequestFlow.tryEmit(it)
                    }
                }.collect()
            }

            launch {
                nftRepository.paginatedUserOwnedNFTsByContractAddressFlow(
                    paginationRequestFlow = mutableUserOwnedTokensRequestFlow,
                    chainSelectionFlow = chainSelectionHelperFlow,
                    contractAddressFlow = contractAddressFlow,
                    selectedMetaAccountFlow = accountRepository.selectedMetaAccountFlow(),
                    exclusionFiltersFlow = exclusionFiltersHelperFlow
                ).transformLatest { pagedResponse ->
                    val result = pagedResponse.result.mapCatching { paginationEvent ->
                        when (paginationEvent) {
                            is PaginationEvent.AllPreviousPagesLoaded -> {
                                // We are still in process of loading this request
                                atomicAreUserOwnedTokensLoading.set(true)
                                return@transformLatest // skip transformation of this emission; do nothing
                            }

                            is PaginationEvent.AllNextPagesLoaded -> {
                                // We are done loading this request
                                atomicAreUserOwnedTokensLoading.set(false)

                                mutableNFTCollectionRequestFlow.tryEmit(PaginationRequest.Next.Page)

                                return@transformLatest // skip transformation of this emission; do nothing
                            }

                            is PaginationEvent.PageIsLoaded -> {
                                // We are still in process of loading this request
                                atomicAreUserOwnedTokensLoading.set(true)

                                return@mapCatching paginationEvent.toFullNFTCollection(
                                    chain = pagedResponse.chain
                                )
                            }
                        }
                    }

                    emit(result to pagedResponse.paginationRequest)
                }.collect { this@channelFlow.send(it) }
            }

            launch {
                nftRepository.paginatedNFTCollectionByContractAddressFlow(
                    paginationRequestFlow = mutableNFTCollectionRequestFlow,
                    chainSelectionFlow = chainSelectionHelperFlow,
                    contractAddressFlow = contractAddressFlow
                ).transformLatest { pagedResponse ->
                    val result = pagedResponse.result.mapCatching { paginationEvent ->
                        when (paginationEvent) {
                            is PaginationEvent.AllPreviousPagesLoaded -> {
                                // We are done loading this request; this is inversion of userOwnedTokens request
                                atomicAreNFTCollectionsLoading.set(false)

                                mutableUserOwnedTokensRequestFlow.tryEmit(PaginationRequest.Prev.Page)

                                return@transformLatest // skip transformation of this emission; do nothing
                            }

                            is PaginationEvent.AllNextPagesLoaded -> {
                                // We are still in process of loading this request; this is inversion of userOwnedTokens request
                                atomicAreNFTCollectionsLoading.set(true)

                                return@transformLatest // skip transformation of this emission; do nothing
                            }

                            is PaginationEvent.PageIsLoaded -> {
                                // We are still in process of loading this request
                                atomicAreNFTCollectionsLoading.set(true)

                                return@mapCatching paginationEvent.toFullNFTCollection(
                                    chain = pagedResponse.chain
                                )
                            }
                        }
                    }

                    emit(result to pagedResponse.paginationRequest)
                }.collect { this@channelFlow.send(it) }
            }
        }.flowOn(Dispatchers.Default)
    }

    private fun PaginationEvent.PageIsLoaded<NFTResponse.TokensCollection>.toFullNFTCollection(
        chain: Chain
    ): NFTCollection<NFTCollection.NFT.Full> {
        val contractMetadata = data.tokenInfoList.firstOrNull {
            it.contractMetadata != null
        }?.contractMetadata

        val contractAddress = data.tokenInfoList.firstOrNull {
            it.contract?.address != null
        }?.contract?.address.orEmpty()

        return data.toFullNFTCollection(
            chain = chain,
            contractAddress = contractAddress,
            contractMetadata = contractMetadata
        )
    }

    override suspend fun getNFTDetails(
        chainId: ChainId,
        contractAddress: String,
        tokenId: String
    ): Result<NFTCollection.NFT.Full> {
        val chain = chainsRepository.getChain(chainId)

        return nftRepository.tokenMetadata(chain, contractAddress, tokenId).map { result ->
            result.toFullNFT(
                chain = chain,
                contractAddress = contractAddress
            )
        }
    }

    override suspend fun getOwnersForNFT(token: NFTCollection.NFT.Full): Result<List<String>> {
        val chain = chainsRepository.getChain(token.chainId)

        return runCatching {
            val contractAddress = token.contractAddress ?: error(
                """
                    TokenId supplied is null.
                """.trimIndent()
            )

            val tokenId = token.tokenId ?: error(
                """
                    TokenId supplied is null.
                """.trimIndent()
            )

            return@runCatching nftRepository.tokenOwners(
                chain = chain,
                contractAddress = contractAddress,
                tokenId = tokenId
            ).getOrThrow().ownersList
        }
    }
}
