package jp.co.soramitsu.nft.impl.data

import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.utils.concurrentRequestFlow
import jp.co.soramitsu.common.utils.refreshOnNewDistinct
import jp.co.soramitsu.nft.data.NFTCollectionByContractAddressPagedResponse
import jp.co.soramitsu.nft.data.NFTRepository
import jp.co.soramitsu.nft.data.UserOwnedTokensByContractAddressPagedResponse
import jp.co.soramitsu.nft.data.UserOwnedTokensPagedResponse
import jp.co.soramitsu.nft.data.models.TokenInfo
import jp.co.soramitsu.nft.data.pagination.PaginationEvent
import jp.co.soramitsu.nft.data.pagination.PaginationRequest
import jp.co.soramitsu.nft.data.models.wrappers.NFTResponse
import jp.co.soramitsu.nft.data.pagination.mapToPaginationEvent
import jp.co.soramitsu.nft.impl.data.model.request.NFTRequest
import jp.co.soramitsu.nft.impl.data.model.utils.getPageSize
import jp.co.soramitsu.nft.impl.data.model.utils.nextOrPrevPage
import jp.co.soramitsu.nft.impl.data.remote.AlchemyNftApi
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.alchemyNftId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Stack

internal const val DEFAULT_PAGE_SIZE = 100
internal const val NFT_FILTERS_KEY = "NFT_FILTERS_KEY"

class NFTRepositoryImpl(
    private val alchemyNftApi: AlchemyNftApi,
    private val preferences: Preferences
): NFTRepository {

    private val localScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val mutableFiltersFlow = MutableSharedFlow<Pair<String, Boolean>>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    override val nftFiltersFlow: Flow<Set<Pair<String, Boolean>>> = flow {
        var filtersSnapshot: Set<Pair<String, Boolean>> =
            preferences.getStringSet(NFT_FILTERS_KEY, emptySet()).map { it to true }.toSet()

        mutableFiltersFlow.buffer().transformLatest { newFilterToIsApplied ->
            val cache = filtersSnapshot.toMutableSet().apply {
                removeIf { it.first == newFilterToIsApplied.first }
                add(newFilterToIsApplied)
            }

            emit(cache)

            /*
                Wait for 10 seconds till user finishes all filters selection
                to compare saved selection

                1) Delay will be cancelled by use of collectLatest except for the last one
            */
            kotlinx.coroutines.delay(10_000)

            val dbCache = preferences.getStringSet(NFT_FILTERS_KEY, emptySet())

            if (
                dbCache.containsAll(
                    cache.filter { it.second } // filter out non selected filters
                        .map { it.first } // get only selected filters
                )
            ) return@transformLatest

            filtersSnapshot = dbCache.map { filter ->
                val isApplied = true

                return@map filter to isApplied
            }.toMutableSet()

            emit(filtersSnapshot.toSet())
        }.onStart { emit(filtersSnapshot) }.collect(this)
    }.shareIn(scope = localScope, started = SharingStarted.Eagerly, replay = 1)

    override fun setNFTFilter(value: String, isApplied: Boolean) {
        localScope.launch {
            with(preferences) {
                val mutableFilters = getStringSet(NFT_FILTERS_KEY, emptySet()).toMutableSet()

                when {
                    value in mutableFilters && !isApplied ->
                        mutableFilters.remove(value)

                    value !in mutableFilters && isApplied ->
                        mutableFilters.add(value)

                    else -> Unit /* DO NOTHING */
                }

                putStringSet(NFT_FILTERS_KEY, mutableFilters)
            }
        }.invokeOnCompletion {
            if (it != null)
                return@invokeOnCompletion

            /* non-suspended call to avoid UI delays or inconsistencies */
            mutableFiltersFlow.tryEmit(value to isApplied)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun paginatedUserOwnedNFTsFlow(
        paginationRequestFlow: Flow<PaginationRequest>,
        chainSelectionFlow: Flow<List<Chain>>,
        selectedMetaAccountFlow: Flow<MetaAccount>,
        exclusionFiltersFlow: Flow<List<String>>
    ): Flow<List<UserOwnedTokensPagedResponse>> {
        return flow {
            val mutex = Mutex()
            val pageStackMap = mutableMapOf<Chain, Stack<String?>>()

            val newPageStackBuilder = {
                Stack<String?>().apply {
                    push(null)
                }
            }

            fun <T> Flow<T>.withRefreshPagesStackSideEffect() = this.refreshOnNewDistinct {
                runBlocking {
                    mutex.withLock {
                        // Resetting page iterators on all chains to start over because request args changed
                        pageStackMap.keys.forEach {
                            pageStackMap.replace(it, newPageStackBuilder())
                        }
                    }
                }
            }

            combine(
                paginationRequestFlow,
                selectedMetaAccountFlow.withRefreshPagesStackSideEffect(),
                exclusionFiltersFlow.withRefreshPagesStackSideEffect(),
                chainSelectionFlow.withRefreshPagesStackSideEffect().onEach { chains ->
                    mutex.withLock {
                        for(chain in chains) {
                            // perform refresh pageIteratorsMap sideEffect on new chains
                            pageStackMap.putIfAbsent(chain, newPageStackBuilder())
                        }
                    }
                },
            ) { request, metaAccount, filters, _ ->
                return@combine Triple(request, metaAccount, filters)
            }.transformLatest { (paginationRequest, metaAccount, filters) ->
                val readOnlyChainToPageStackList = mutex.withLock {
                    // creating new list for reading with captured content
                    pageStackMap.toList()
                }

                val result = concurrentNFTs(
                    paginationRequest = paginationRequest,
                    chainToPageStackList = readOnlyChainToPageStackList,
                    pageSize = paginationRequest.getPageSize(DEFAULT_PAGE_SIZE),
                    metaAccount = metaAccount,
                    exclusionFilters = filters
                ).toList()

                /* Add pageKeys to page iterators for next pagination request */
                for(pagedResponse in result) {
                    val pageDataWrapper = pagedResponse.result.getOrNull()

                    if (
                        pageDataWrapper == null ||
                        pageDataWrapper !is PaginationEvent.PageIsLoaded
                    ) continue

                    mutex.withLock {
                        val pageStack = pageStackMap.getOrPut(
                            pagedResponse.chain,
                            newPageStackBuilder
                        )

                        if (pagedResponse.paginationRequest is PaginationRequest.Next)
                            pageStack.push(pageDataWrapper.data.nextPage)
                    }
                }

                emit(result)
            }.collect(this)
        }
    }

    private fun concurrentNFTs(
        paginationRequest: PaginationRequest,
        chainToPageStackList: List<Pair<Chain, Stack<String?>>>,
        pageSize: Int,
        metaAccount: MetaAccount,
        exclusionFilters: List<String>
    ): Flow<UserOwnedTokensPagedResponse> {
        return chainToPageStackList.concurrentRequestFlow { (chain, pageStack) ->
            val result =
                runCatching {
                    paginationRequest.nextOrPrevPage(pageStack).mapToPaginationEvent { pageKey ->
                        val ownerAddress = metaAccount.address(chain) ?: error(
                            """
                                Owner is not supported for chain with id: ${chain.id}.
                            """.trimIndent()
                        )

                        alchemyNftApi.getUserOwnedNFTs(
                            url = NFTRequest.UserOwnedTokens.requestUrl(chain.alchemyNftId),
                            owner = ownerAddress,
                            withMetadata = false,
                            pageKey = pageKey,
                            pageSize = pageSize,
                            excludeFilters = exclusionFilters
                        )
                    }
                }

            emit(
                UserOwnedTokensPagedResponse(
                    chain = chain,
                    result = result,
                    paginationRequest = paginationRequest
                )
            )
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun paginatedUserOwnedNFTsByContractAddressFlow(
        paginationRequestFlow: Flow<PaginationRequest>,
        chainSelectionFlow: Flow<Chain>,
        contractAddressFlow: Flow<String>,
        selectedMetaAccountFlow: Flow<MetaAccount>,
        exclusionFiltersFlow: Flow<List<String>>
    ): Flow<UserOwnedTokensByContractAddressPagedResponse> {
        class LocalHolder(
            val paginationRequest: PaginationRequest,
            val chain: Chain,
            val contractAddress: String,
            val metaAccount: MetaAccount,
            val exclusionFilters: List<String>
        )

        return flow {
            val mutex = Mutex()
            val userOwnedNFTsPageStack = Stack<String?>().apply { push(null) }

            fun <T> Flow<T>.withRefreshPageStackSideEffect() = this.refreshOnNewDistinct {
                runBlocking {
                    mutex.withLock {
                        // Resetting page iterator to start over because request args changed
                        userOwnedNFTsPageStack.clear()
                        userOwnedNFTsPageStack.push(null)
                    }
                }
            }

            combine(
                paginationRequestFlow,
                chainSelectionFlow.withRefreshPageStackSideEffect(),
                contractAddressFlow.withRefreshPageStackSideEffect(),
                selectedMetaAccountFlow.withRefreshPageStackSideEffect(),
                exclusionFiltersFlow.withRefreshPageStackSideEffect()
            ) { request, chain, contractAddress, selectedMetaAccount, exclusionFilters ->
                return@combine LocalHolder(
                    paginationRequest = request,
                    chain = chain,
                    contractAddress = contractAddress,
                    metaAccount = selectedMetaAccount,
                    exclusionFilters = exclusionFilters
                )
            }.transformLatest { holder ->
                val result =
                    runCatching {
                        val page = mutex.withLock {
                            holder.paginationRequest.nextOrPrevPage(
                                pageStack = userOwnedNFTsPageStack
                            )
                        }

                        page.mapToPaginationEvent { pageKey ->
                            val ownerAddress = holder.metaAccount.address(holder.chain) ?: error(
                                """
                                    Owner is not supported for chain with id: ${holder.chain.id}.
                                """.trimIndent()
                            )

                            alchemyNftApi.getUserOwnedNFTsByContractAddress(
                                url = NFTRequest.UserOwnedTokens.requestUrl(holder.chain.alchemyNftId),
                                owner = ownerAddress,
                                contractAddress = holder.contractAddress,
                                withMetadata = true,
                                pageKey = pageKey,
                                pageSize = holder.paginationRequest.getPageSize(DEFAULT_PAGE_SIZE),
                                excludeFilters = holder.exclusionFilters
                            )
                        }
                    }

                with(result.getOrNull()) {
                    if (
                        this == null ||
                        this !is PaginationEvent.PageIsLoaded
                    ) return@with

                    mutex.withLock {
                        if (holder.paginationRequest is PaginationRequest.Next)
                            userOwnedNFTsPageStack.push(data.nextPage)
                    }
                }

                emit(
                    UserOwnedTokensByContractAddressPagedResponse(
                        chain = holder.chain,
                        result = result,
                        paginationRequest = holder.paginationRequest
                    )
                )
            }.collect(this)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun paginatedNFTCollectionByContractAddressFlow(
        paginationRequestFlow: Flow<PaginationRequest>,
        chainSelectionFlow: Flow<Chain>,
        contractAddressFlow: Flow<String>
    ): Flow<NFTCollectionByContractAddressPagedResponse> {
        class LocalHolder(
            val paginationRequest: PaginationRequest,
            val chain: Chain,
            val contractAddress: String
        )

        return flow {
            val mutex = Mutex()
            val tokenCollectionsPageStack = Stack<String?>().apply { push(null) }

            fun <T> Flow<T>.withRefreshPageStackSideEffect() = this.refreshOnNewDistinct {
                runBlocking {
                    mutex.withLock {
                        // Resetting page iterator to start over because request args changed
                        tokenCollectionsPageStack.clear()
                        tokenCollectionsPageStack.push(null)
                    }
                }
            }

            combine(
                paginationRequestFlow,
                chainSelectionFlow.withRefreshPageStackSideEffect(),
                contractAddressFlow.withRefreshPageStackSideEffect()
            ) { request, chain, contractAddress ->
                return@combine LocalHolder(
                    paginationRequest = request,
                    chain = chain,
                    contractAddress = contractAddress,
                )
            }.transformLatest { holder ->
                val result =
                    runCatching {
                        val page = mutex.withLock {
                            holder.paginationRequest.nextOrPrevPage(
                                pageStack = tokenCollectionsPageStack
                            )
                        }


                        page.mapToPaginationEvent { pageKey ->
                            alchemyNftApi.getNFTCollectionByContactAddress(
                                requestUrl = NFTRequest.TokensCollection.requestUrl(holder.chain.alchemyNftId),
                                contractAddress = holder.contractAddress,
                                withMetadata = true,
                                startTokenId = pageKey,
                                limit = holder.paginationRequest.getPageSize(DEFAULT_PAGE_SIZE)
                            )
                        }
                    }

                with(result.getOrNull()) {
                    if (
                        this == null ||
                        this !is PaginationEvent.PageIsLoaded
                    ) return@with

                    mutex.withLock {
                        if (holder.paginationRequest is PaginationRequest.Next)
                            tokenCollectionsPageStack.push(data.nextPage)
                    }
                }

                emit(
                    NFTCollectionByContractAddressPagedResponse(
                        chain = holder.chain,
                        result = result,
                        paginationRequest = holder.paginationRequest
                    )
                )
            }.collect(this)
        }
    }

    override suspend fun contractMetadataBatch(
        chain: Chain,
        contractAddresses: Set<String>
    ): List<NFTResponse.ContractMetadata> {
        return alchemyNftApi.getNFTContractMetadataBatch(
            requestUrl = NFTRequest.ContractMetadataBatch.requestUrl(chain.alchemyNftId),
            body = NFTRequest.ContractMetadataBatch.Body(contractAddresses = contractAddresses.toList())
        )
    }

    override suspend fun tokenMetadata(
        chain: Chain,
        contractAddress: String,
        tokenId: String
    ): TokenInfo.WithMetadata {
        return alchemyNftApi.getNFTMetadata(
            requestUrl = NFTRequest.TokenMetadata.requestUrl(chain.alchemyNftId),
            contractAddress = contractAddress,
            tokenId = tokenId
        )
    }

}