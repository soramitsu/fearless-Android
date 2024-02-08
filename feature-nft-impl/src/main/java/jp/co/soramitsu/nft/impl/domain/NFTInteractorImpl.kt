package jp.co.soramitsu.nft.impl.domain

import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.common.utils.concurrentRequestFlow
import jp.co.soramitsu.common.utils.rememberAndZipAsPreviousIf
import jp.co.soramitsu.core.models.ChainId
import jp.co.soramitsu.nft.data.NFTRepository
import jp.co.soramitsu.nft.data.pagination.PaginationEvent
import jp.co.soramitsu.nft.data.pagination.PaginationRequest
import jp.co.soramitsu.nft.domain.NFTInteractor
import jp.co.soramitsu.nft.domain.models.NFT
import jp.co.soramitsu.nft.domain.models.NFTCollectionResult
import jp.co.soramitsu.nft.domain.models.NFTFilter
import jp.co.soramitsu.nft.domain.models.utils.toNFT
import jp.co.soramitsu.nft.domain.models.utils.toNFTCollection
import jp.co.soramitsu.nft.domain.models.utils.toNFTCollectionWithTokens
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.alchemyNftId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.transform
import java.math.BigInteger
import java.util.concurrent.atomic.AtomicInteger

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

        return nftRepository.paginatedUserOwnedContractsFlow(
            paginationRequestFlow = paginationRequestFlow,
            chainSelectionFlow = chainsHelperFlow,
            selectedMetaAccountFlow = accountRepository.selectedMetaAccountFlow(),
            exclusionFiltersFlow = exclusionFiltersHelperFlow
        ).transform { currentResponse ->
            currentResponse.concurrentRequestFlow { pagedResponse ->
                val (chainId, chainName) = pagedResponse.chain.run { id to name }

                val result = pagedResponse.result.mapCatching { paginationEvent ->
                    if (paginationEvent !is PaginationEvent.PageIsLoaded) {
                        return@mapCatching listOf(NFTCollectionResult.Empty(chainId, chainName))
                    }

                    if (paginationEvent.data.contracts.isEmpty()) {
                        return@mapCatching listOf(NFTCollectionResult.Empty(chainId, chainName))
                    }

                    paginationEvent.data.contracts.map { contract ->
                        contract.toNFTCollection(
                            chainId = chainId,
                            chainName = chainName
                        )
                    }
                }.getOrElse { throwable ->
                    val errorResult = NFTCollectionResult.Error(
                        chainId = chainId,
                        chainName = chainName,
                        throwable = throwable
                    )

                    listOf(errorResult)
                }

                emit(result)
            }.toList().flatten().also { collectionListOfAllChains ->
                emit(collectionListOfAllChains)
            }
        }.rememberAndZipAsPreviousIf { collectionsList ->
            collectionsList.any { it is NFTCollectionResult.Data }
        }.mapLatest { (prevDataCollections, currentCollections) ->
            val areCurrentDataCollectionsEmpty =
                currentCollections.count { it is NFTCollectionResult.Data } == 0

            if (areCurrentDataCollectionsEmpty && prevDataCollections != null) {
                prevDataCollections
            } else {
                currentCollections
            }
        }.flowOn(Dispatchers.Default)
    }

    @Suppress("VariableNaming")
    override fun collectionNFTsFlow(
        paginationRequestFlow: Flow<PaginationRequest>,
        chainSelectionFlow: Flow<String>,
        contractAddressFlow: Flow<String>
    ): Flow<Pair<NFTCollectionResult, PaginationRequest>> {
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
            val UserOwnedNFTsStartedLoading = 0
            val AvailableNFTsStartedLoading = 1

            val nonBlockingSemaphore = AtomicInteger(UserOwnedNFTsStartedLoading)

            val mutableSharedFlow = MutableSharedFlow<Unit>(
                replay = 1,
                onBufferOverflow = BufferOverflow.DROP_OLDEST
            )

            fun <T> Flow<T>.withNonBlockingLock(unlockOn: Int) = onStart {
                mutableSharedFlow.tryEmit(Unit)
            }.combine(mutableSharedFlow) { value, _ ->
                return@combine value
            }.filter {
                nonBlockingSemaphore.get() == unlockOn
            }

            val userOwnedNFTIds = mutableSetOf<String>()

            nftRepository.paginatedUserOwnedNFTsByContractAddressFlow(
                paginationRequestFlow = paginationRequestFlow.withNonBlockingLock(UserOwnedNFTsStartedLoading),
                chainSelectionFlow = chainSelectionHelperFlow,
                contractAddressFlow = contractAddressFlow,
                selectedMetaAccountFlow = accountRepository.selectedMetaAccountFlow(),
                exclusionFiltersFlow = exclusionFiltersHelperFlow
            ).onEach { pagedResponse ->
                val (chainId, chainName) = pagedResponse.chain.run { id to name }

                val result = pagedResponse.result.mapCatching { paginationEvent ->
                    if (paginationEvent !is PaginationEvent.PageIsLoaded) {
                        if (paginationEvent is PaginationEvent.AllNextPagesLoaded) {
                            nonBlockingSemaphore.set(AvailableNFTsStartedLoading)
                            mutableSharedFlow.tryEmit(Unit)
                        }

                        return@mapCatching NFTCollectionResult.Empty(chainId, chainName)
                    }

                    if (paginationEvent.data.tokenInfoList.isEmpty()) {
                        return@mapCatching NFTCollectionResult.Empty(chainId, chainName)
                    }

                    for (token in paginationEvent.data.tokenInfoList) {
                        token.id?.tokenId?.let { userOwnedNFTIds.add(it) }
                    }

                    paginationEvent.data.toNFTCollectionWithTokens(pagedResponse.chain)
                }.getOrElse { throwable ->
                    NFTCollectionResult.Error(
                        chainId = chainId,
                        chainName = chainName,
                        throwable = throwable
                    )
                }

                send(Pair(result, pagedResponse.paginationRequest))
            }.launchIn(this)

            nftRepository.paginatedNFTCollectionByContractAddressFlow(
                paginationRequestFlow = paginationRequestFlow.withNonBlockingLock(AvailableNFTsStartedLoading),
                chainSelectionFlow = chainSelectionHelperFlow,
                contractAddressFlow = contractAddressFlow
            ).onEach { pagedResponse ->
                val (chainId, chainName) = pagedResponse.chain.run { id to name }

                val result = pagedResponse.result.mapCatching { paginationEvent ->
                    if (paginationEvent !is PaginationEvent.PageIsLoaded) {
                        if (paginationEvent is PaginationEvent.AllPreviousPagesLoaded) {
                            nonBlockingSemaphore.set(UserOwnedNFTsStartedLoading)
                            mutableSharedFlow.tryEmit(Unit)
                        }

                        return@mapCatching NFTCollectionResult.Empty(chainId, chainName)
                    }

                    if (paginationEvent.data.tokenInfoList.isEmpty()) {
                        return@mapCatching NFTCollectionResult.Empty(chainId, chainName)
                    }

                    paginationEvent.data.toNFTCollectionWithTokens(
                        chain = pagedResponse.chain,
                        excludeTokensWithIds = userOwnedNFTIds
                    )
                }.getOrElse { throwable ->
                    NFTCollectionResult.Error(
                        chainId = chainId,
                        chainName = chainName,
                        throwable = throwable
                    )
                }

                send(Pair(result, pagedResponse.paginationRequest))
            }.launchIn(this)
        }.rememberAndZipAsPreviousIf { (collection, _) ->
            collection is NFTCollectionResult.Data
        }.transform { (prevDataCollectionPair, currentCollectionPair) ->
            val (currentCollection, _) = currentCollectionPair

            val collectionToSend = if (
                currentCollection !is NFTCollectionResult.Data &&
                prevDataCollectionPair != null
            ) {
                prevDataCollectionPair
            } else {
                currentCollectionPair
            }

            emit(collectionToSend)
        }.flowOn(Dispatchers.Default)
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
