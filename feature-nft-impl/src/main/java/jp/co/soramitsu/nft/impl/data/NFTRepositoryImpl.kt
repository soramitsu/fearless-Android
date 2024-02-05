package jp.co.soramitsu.nft.impl.data

import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.utils.castOrNull
import jp.co.soramitsu.common.utils.concurrentRequestFlow
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
        onBufferOverflow = BufferOverflow.DROP_LATEST
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    override val nftFiltersFlow: Flow<Set<String>> = flow {
        var filtersSnapshot: MutableSet<String> = preferences.getStringSet(
            NFT_FILTERS_KEY,
            emptySet()
        ).toMutableSet()

        mutableFiltersFlow.buffer().transformLatest { (filter, isApplied) ->
            val cache = filtersSnapshot.apply {
                if (isApplied) {
                    add(filter)
                } else remove(filter)
            }

            emit(cache)

            /*
                Wait for 10 seconds till user finishes all filters selection
                to compare saved selection

                1) Delay will be cancelled by use of collectLatest except for the last one
            */
            kotlinx.coroutines.delay(10_000)

            val dbCache = preferences.getStringSet(NFT_FILTERS_KEY, emptySet())

            if (dbCache.containsAll(cache))
                return@transformLatest

            filtersSnapshot = dbCache.toMutableSet()

            emit(filtersSnapshot)
        }.onStart { emit(filtersSnapshot) }.collect(this)
    }.shareIn(scope = localScope, started = SharingStarted.Eagerly, replay = 1)

    override fun setNFTFilter(value: String, excludeFromSearchQuery: Boolean) {
        /* non-suspended call to avoid UI delays or inconsistencies */
        mutableFiltersFlow.tryEmit(value to excludeFromSearchQuery)

        localScope.launch {
            with(preferences) {
                val mutableFilters = getStringSet(NFT_FILTERS_KEY, emptySet()).toMutableSet()

                if (excludeFromSearchQuery) mutableFilters.add(value)
                else mutableFilters.remove(value)

                putStringSet(NFT_FILTERS_KEY, mutableFilters)
            }
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
                    // first page in alchemy is accessed by passing null pageKey
                    push(null)
                }
            }

            // Resetting page stack on all chains to start over because request args changed
            fun <T> Flow<T>.withRefreshPagesStackSideEffect() =
                this.distinctUntilChanged().onEach {
                    mutex.withLock {
                        val possibleChainsList = it.castOrNull<List<Chain>>()

                        if (possibleChainsList == null) {
                            pageStackMap.keys.forEach { pageStackMap.replace(it, newPageStackBuilder()) }
                            return@withLock
                        }

                        pageStackMap.clear()
                        possibleChainsList.forEach { pageStackMap[it] = newPageStackBuilder() }
                    }
                }

            combine(
                paginationRequestFlow,
                selectedMetaAccountFlow.withRefreshPagesStackSideEffect(),
                chainSelectionFlow.withRefreshPagesStackSideEffect(),
                exclusionFiltersFlow.withRefreshPagesStackSideEffect(),
            ) { request, metaAccount, _, filters ->
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

                emit(result)
            }.onEach { result ->
                /*
                    If request has been executed without cancellation,
                    add pageKeys to existing page stacks for use in next pagination requests
                */
                for (pagedResponse in result) {
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
            }.collect(this)
        }.flowOn(Dispatchers.IO)
    }

    private fun concurrentNFTs(
        paginationRequest: PaginationRequest,
        chainToPageStackList: List<Pair<Chain, Stack<String?>>>,
        pageSize: Int,
        metaAccount: MetaAccount,
        exclusionFilters: List<String>
    ): Flow<UserOwnedTokensPagedResponse> =
        chainToPageStackList.run {
            // running request for each chain concurrently
            concurrentRequestFlow { (chain, pageStack) ->
                runCatching {
                    paginationRequest.nextOrPrevPage(pageStack).mapToPaginationEvent { pageKey ->
                        val ownerAddress = metaAccount.address(chain) ?: error(
                            """
                                Owner is not supported for chain with id: ${chain.id}.
                            """.trimIndent()
                        )

                        alchemyNftApi.getUserOwnedContracts(
                            url = NFTRequest.UserOwnedContracts.requestUrl(chain.alchemyNftId),
                            owner = ownerAddress,
                            withMetadata = true,
                            pageKey = pageKey,
                            pageSize = pageSize,
                            excludeFilters = exclusionFilters
                        )
                    }
                }.also { result ->
                    emit(
                        UserOwnedTokensPagedResponse(
                            chain = chain,
                            result = result,
                            paginationRequest = paginationRequest
                        )
                    )
                }
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

            // Resetting page iterator to start over because request args changed
            fun <T> Flow<T>.withRefreshPageStackSideEffect() =
                this.distinctUntilChanged().onEach {
                    mutex.withLock {
                        userOwnedNFTsPageStack.clear()
                        userOwnedNFTsPageStack.push(null)
                    }
                }

            combine(
                paginationRequestFlow,
                selectedMetaAccountFlow.withRefreshPageStackSideEffect(),
                chainSelectionFlow.withRefreshPageStackSideEffect(),
                contractAddressFlow.withRefreshPageStackSideEffect(),
                exclusionFiltersFlow.withRefreshPageStackSideEffect()
            ) { request, metaAccount, chain, contractAddress, exclusionFilters ->
                return@combine LocalHolder(
                    paginationRequest = request,
                    chain = chain,
                    contractAddress = contractAddress,
                    metaAccount = metaAccount,
                    exclusionFilters = exclusionFilters
                )
            }.transformLatest { holder ->
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
                            url = NFTRequest.UserOwnedNFTs.requestUrl(holder.chain.alchemyNftId),
                            owner = ownerAddress,
                            contractAddress = holder.contractAddress,
                            withMetadata = true,
                            pageKey = pageKey,
                            pageSize = holder.paginationRequest.getPageSize(DEFAULT_PAGE_SIZE),
                            excludeFilters = holder.exclusionFilters
                        )
                    }
                }.also { result ->
                    emit(
                        UserOwnedTokensByContractAddressPagedResponse(
                            chain = holder.chain,
                            result = result,
                            paginationRequest = holder.paginationRequest
                        )
                    )
                }
            }.onEach { pagedResponseWrapper ->

                /*
                    If request has been executed without cancellation,
                    add pageKey to page stack for use in next pagination request
                */

                val result = pagedResponseWrapper.result.getOrNull()
                if (
                    result == null ||
                    result !is PaginationEvent.PageIsLoaded ||
                    pagedResponseWrapper.paginationRequest !is PaginationRequest.Next
                ) return@onEach

                mutex.withLock {
                    userOwnedNFTsPageStack.push(
                        result.data.nextPage
                    )
                }
            }.collect(this)
        }.flowOn(Dispatchers.IO)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun paginatedNFTCollectionByContractAddressFlow(
        paginationRequestFlow: Flow<PaginationRequest>,
        chainSelectionFlow: Flow<Chain>,
        contractAddressFlow: Flow<String>
    ): Flow<NFTCollectionByContractAddressPagedResponse> {
        return flow {
            val mutex = Mutex()
            val tokenCollectionsPageStack = Stack<String?>().apply { push(null) }

            // Resetting page iterator to start over because request args changed
            fun <T> Flow<T>.withRefreshPageStackSideEffect() =
                this.distinctUntilChanged().onEach {
                    mutex.withLock {
                        tokenCollectionsPageStack.clear()
                        tokenCollectionsPageStack.push(null)
                    }
                }

            combine(
                paginationRequestFlow,
                chainSelectionFlow.withRefreshPageStackSideEffect(),
                contractAddressFlow.withRefreshPageStackSideEffect()
            ) { request, chain, contractAddress ->
                return@combine Triple(request, chain, contractAddress,)
            }.transformLatest { (request, chain, contractAddress) ->
                runCatching {
                    val page = mutex.withLock {
                        request.nextOrPrevPage(
                            pageStack = tokenCollectionsPageStack
                        )
                    }

                    page.mapToPaginationEvent { pageKey ->
                        alchemyNftApi.getNFTCollectionByContactAddress(
                            requestUrl = NFTRequest.TokensCollection.requestUrl(chain.alchemyNftId),
                            contractAddress = contractAddress,
                            withMetadata = true,
                            startTokenId = pageKey,
                            limit = request.getPageSize(DEFAULT_PAGE_SIZE)
                        )
                    }
                }.also { result ->
                    emit(
                        NFTCollectionByContractAddressPagedResponse(
                            chain = chain,
                            result = result,
                            paginationRequest = request
                        )
                    )
                }
            }.onEach { pagedResponseWrapper ->

                /*
                    If request has been executed without cancellation,
                    add pageKey to page stack for use in next pagination request
                */

                val result = pagedResponseWrapper.result.getOrNull()
                if (
                    result == null ||
                    result !is PaginationEvent.PageIsLoaded ||
                    pagedResponseWrapper.paginationRequest !is PaginationRequest.Next
                ) return@onEach

                mutex.withLock {
                    tokenCollectionsPageStack.push(
                        result.data.nextPage
                    )
                }
            }.collect(this)
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun tokenMetadata(
        chain: Chain,
        contractAddress: String,
        tokenId: String
    ): Result<TokenInfo> {
        return runCatching {
            withContext(Dispatchers.IO) {
                alchemyNftApi.getNFTMetadata(
                    requestUrl = NFTRequest.TokenMetadata.requestUrl(chain.alchemyNftId),
                    contractAddress = contractAddress,
                    tokenId = tokenId
                )
            }
        }
    }

    override suspend fun tokenOwners(
        chain: Chain,
        contractAddress: String,
        tokenId: String
    ): Result<NFTResponse.TokenOwners> {
        return runCatching {
            withContext(Dispatchers.IO) {
                alchemyNftApi.getNFTOwners(
                    requestUrl = NFTRequest.TokenOwners.requestUrl(chain.alchemyNftId),
                    contractAddress = contractAddress,
                    tokenId = tokenId
                )
            }
        }
    }

}