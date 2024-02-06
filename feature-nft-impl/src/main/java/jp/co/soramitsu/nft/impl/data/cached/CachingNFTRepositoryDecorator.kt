package jp.co.soramitsu.nft.impl.data.cached

import androidx.collection.LruCache
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.coredb.dao.NFTContractMetadataResponseDao
import jp.co.soramitsu.nft.data.NFTCollectionByContractAddressPagedResponse
import jp.co.soramitsu.nft.data.NFTRepository
import jp.co.soramitsu.nft.data.UserOwnedTokensByContractAddressPagedResponse
import jp.co.soramitsu.nft.data.UserOwnedTokensPagedResponse
import jp.co.soramitsu.nft.data.models.TokenInfo
import jp.co.soramitsu.nft.data.pagination.PaginationEvent
import jp.co.soramitsu.nft.data.pagination.PaginationRequest
import jp.co.soramitsu.nft.data.models.wrappers.NFTResponse
import jp.co.soramitsu.nft.impl.data.DEFAULT_PAGE_SIZE
import jp.co.soramitsu.nft.impl.data.model.utils.transformToSpecificOrDoNothing
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class CachingNFTRepositoryDecorator(
    private val nftRepository: NFTRepository,
    private val nftContractMetadataResponseDao: NFTContractMetadataResponseDao
): NFTRepository by nftRepository {

    private val lruCacheMutex = Mutex()
    private val tokensWithMetadataLRUCache: LruCache<Int, TokenInfo> = LruCache(3 * DEFAULT_PAGE_SIZE)

    override fun paginatedUserOwnedContractsFlow(
        paginationRequestFlow: Flow<PaginationRequest>,
        chainSelectionFlow: Flow<List<Chain>>,
        selectedMetaAccountFlow: Flow<MetaAccount>,
        exclusionFiltersFlow: Flow<List<String>>
    ): Flow<List<UserOwnedTokensPagedResponse>> {
        // No caching because it is harder to control due to multiplicity of chains
        return nftRepository.paginatedUserOwnedContractsFlow(
            paginationRequestFlow = paginationRequestFlow,
            chainSelectionFlow = chainSelectionFlow,
            selectedMetaAccountFlow = selectedMetaAccountFlow,
            exclusionFiltersFlow = exclusionFiltersFlow
        )
    }

    override fun paginatedUserOwnedNFTsByContractAddressFlow(
        paginationRequestFlow: Flow<PaginationRequest>,
        chainSelectionFlow: Flow<Chain>,
        contractAddressFlow: Flow<String>,
        selectedMetaAccountFlow: Flow<MetaAccount>,
        exclusionFiltersFlow: Flow<List<String>>
    ): Flow<UserOwnedTokensByContractAddressPagedResponse> {
        return flow {
            val mutex = Mutex()
            val savedTokensCollections = ArrayDeque<NFTResponse.TokensCollection>()

            val paginationRequestHelperFlow = paginationRequestFlow.map { request ->
                request.switchRequestsAndWipeCache(
                    savedTokensCollections,
                    mutex
                )
            }

            fun <T> Flow<T>.withRefreshCacheSideEffect() =
                this.distinctUntilChanged().onEach {
                    mutex.withLock {
                        savedTokensCollections.clear()
                    }
                }

            nftRepository.paginatedUserOwnedNFTsByContractAddressFlow(
                paginationRequestFlow = paginationRequestHelperFlow,
                chainSelectionFlow = chainSelectionFlow.withRefreshCacheSideEffect(),
                contractAddressFlow = contractAddressFlow.withRefreshCacheSideEffect(),
                selectedMetaAccountFlow = selectedMetaAccountFlow.withRefreshCacheSideEffect(),
                exclusionFiltersFlow = exclusionFiltersFlow.withRefreshCacheSideEffect()
            ).transform { pagedResponse ->
                val updatedPagedResponse = pagedResponse.result.map { paginationEvent ->
                    if (paginationEvent !is PaginationEvent.PageIsLoaded)
                        return@map paginationEvent

                    saveTokensToCache(
                        chain = pagedResponse.chain,
                        tokens = paginationEvent.data.tokenInfoList
                    )

                    val tokenInfoListFromCache = updateAndFlattenCacheLocked(
                        paginationRequest = pagedResponse.paginationRequest,
                        collectionsCache = savedTokensCollections,
                        collection = paginationEvent.data,
                        mutex = mutex
                    )

                    // pretend nothing happened, and return loaded page but with all cached tokens
                    return@map PaginationEvent.PageIsLoaded(
                        data = NFTResponse.TokensCollection(
                            tokenInfoList = tokenInfoListFromCache,
                            nextPage = paginationEvent.data.nextPage
                        )
                    )
                }.let { updatedPaginationEvent ->
                    pagedResponse.copy(result = updatedPaginationEvent)
                }

                emit(updatedPagedResponse)
            }.collect(this)
        }.flowOn(Dispatchers.Default)
    }
    
    override fun paginatedNFTCollectionByContractAddressFlow(
        paginationRequestFlow: Flow<PaginationRequest>,
        chainSelectionFlow: Flow<Chain>,
        contractAddressFlow: Flow<String>
    ): Flow<NFTCollectionByContractAddressPagedResponse> {
        return flow {
            val mutex = Mutex()
            val savedTokensCollections = ArrayDeque<NFTResponse.TokensCollection>()

            val paginationRequestHelperFlow = paginationRequestFlow.map { request ->
                request.switchRequestsAndWipeCache(
                    savedTokensCollections,
                    mutex
                )
            }

            fun <T> Flow<T>.withRefreshCacheSideEffect() =
                this.distinctUntilChanged().onEach {
                    mutex.withLock {
                        savedTokensCollections.clear()
                    }
                }

            nftRepository.paginatedNFTCollectionByContractAddressFlow(
                paginationRequestFlow = paginationRequestHelperFlow,
                chainSelectionFlow = chainSelectionFlow.withRefreshCacheSideEffect(),
                contractAddressFlow = contractAddressFlow.withRefreshCacheSideEffect()
            ).transform { pagedResponse ->
                val updatedPagedResponse = pagedResponse.result.map { paginationEvent ->
                    if (paginationEvent !is PaginationEvent.PageIsLoaded)
                        return@map paginationEvent

                    saveTokensToCache(
                        chain = pagedResponse.chain,
                        tokens = paginationEvent.data.tokenInfoList
                    )

                    val tokenInfoListFromCache = updateAndFlattenCacheLocked(
                        paginationRequest = pagedResponse.paginationRequest,
                        collectionsCache = savedTokensCollections,
                        collection = paginationEvent.data,
                        mutex = mutex
                    )

                    // pretend nothing happened, and return loaded page but with all cached tokens
                    return@map PaginationEvent.PageIsLoaded(
                        data = NFTResponse.TokensCollection(
                            tokenInfoList = tokenInfoListFromCache,
                            nextPage = paginationEvent.data.nextPage
                        )
                    )
                }.let { updatedPaginationEvent ->
                    pagedResponse.copy(result = updatedPaginationEvent)
                }

                emit(updatedPagedResponse)
            }.collect(this)
        }.flowOn(Dispatchers.Default)
    }

    private suspend fun PaginationRequest.switchRequestsAndWipeCache(
        collectionsCache: ArrayDeque<NFTResponse.TokensCollection>,
        mutex: Mutex
    ): PaginationRequest {
        val page = mutex.withLock {
            when (this) {

                is PaginationRequest.Prev.Page,
                is PaginationRequest.Prev.WithSize ->
                    return@withLock collectionsCache.firstOrNull()?.nextPage

                is PaginationRequest.Next.Page,
                is PaginationRequest.Next.WithSize ->
                    return@withLock collectionsCache.lastOrNull()?.nextPage

                is PaginationRequest.Next.Specific -> {
                    val iteratorReversed = collectionsCache.listIterator(collectionsCache.size)

                    while (iteratorReversed.hasPrevious()) {
                        if (iteratorReversed.previous().nextPage != page) {
                            iteratorReversed.remove()
                            break // cache is purged, we can continue and return page
                        } else {
                            /*
                                We removed everything after the specified page;
                                now we need to remove the specific page for it to be cached again
                            */
                            iteratorReversed.remove()
                        }
                    }

                    return@withLock page
                }

                is PaginationRequest.Prev.TwoBeforeSpecific -> {
                    val iterator = collectionsCache.listIterator()

                    while (iterator.hasNext()) {
                        if (iterator.next().nextPage != page)
                            iterator.remove()
                        else break  // cache is purged, we can continue and return page
                    }

                    return@withLock page
                }

            }
        }

        return transformToSpecificOrDoNothing(page)
    }

    private suspend fun updateAndFlattenCacheLocked(
        paginationRequest: PaginationRequest,
        collectionsCache: ArrayDeque<NFTResponse.TokensCollection>,
        collection: NFTResponse.TokensCollection,
        mutex: Mutex
    ): List<TokenInfo> {
        return mutex.withLock {
            // insert new token collection into cache
            when(paginationRequest) {
                /*
                    Collection can be duplicates if there is only one collection at all;
                    thus, first page has nextPage == null
                */
                is PaginationRequest.Prev -> {
                    if (collectionsCache.isNotEmpty()) {
                        val isNewCollectionDuplicationOfFirstCached =
                            collectionsCache.firstOrNull()?.nextPage == collection.nextPage

                        if (!isNewCollectionDuplicationOfFirstCached)
                            collectionsCache.addFirst(collection)
                    } else collectionsCache.addFirst(collection)
                }

                is PaginationRequest.Next -> {
                    /*
                        Collection can be duplicates if there is only one collection at all;
                        thus, first page has nextPage == null
                    */
                    if (collectionsCache.isNotEmpty()) {
                        val isNewCollectionDuplicationOfLastCached =
                            collectionsCache.lastOrNull()?.nextPage == collection.nextPage

                        if (!isNewCollectionDuplicationOfLastCached)
                            collectionsCache.addLast(collection)
                    } else collectionsCache.addLast(collection)
                }
            }

            var totalTokensCount = collectionsCache.sumOf { it.tokenInfoList.size }

            // shrink cache to allowed size
            while (totalTokensCount > 3 * DEFAULT_PAGE_SIZE) {
                val removedCollection = when(paginationRequest) {
                    is PaginationRequest.Prev ->
                        collectionsCache.removeLast()

                    is PaginationRequest.Next ->
                        collectionsCache.removeFirst()
                }

                totalTokensCount -= removedCollection.tokenInfoList.size
            }

            return@withLock collectionsCache.flatMap { it.tokenInfoList }
        }
    }

    override suspend fun tokenMetadata(
        chain: Chain,
        contractAddress: String,
        tokenId: String
    ): Result<TokenInfo> {
        val tokenFromCache =
            getTokenFromCache(
                chainId = chain.id,
                contractAddress = contractAddress,
                tokenId = tokenId
            )

        if (tokenFromCache != null)
            return Result.success(tokenFromCache)

        return runCatching {
            val result = nftRepository.tokenMetadata(
                chain = chain,
                contractAddress = contractAddress,
                tokenId = tokenId
            ).getOrThrow()

            saveTokensToCache(
                chain = chain,
                tokens = listOf(result)
            )

            return@runCatching result
        }
    }

    private suspend fun saveTokensToCache(chain: Chain, tokens: List<TokenInfo>) {
        lruCacheMutex.withLock {
            for(token in tokens) {
                Triple(
                    first = chain.id,
                    second = token.contract?.address,
                    third = token.id?.tokenId
                ).also { uniqueTokenTriple ->
                    tokensWithMetadataLRUCache.put(
                        uniqueTokenTriple.hashCode(),
                        token
                    )
                }
            }
        }
    }

    private suspend fun getTokenFromCache(
        chainId: ChainId,
        contractAddress: String,
        tokenId: String
    ): TokenInfo? {
        return lruCacheMutex.withLock {
            Triple(
                first = chainId,
                second = contractAddress,
                third = tokenId
            ).run {
                tokensWithMetadataLRUCache[this.hashCode()]
            }
        }
    }

}