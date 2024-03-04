package jp.co.soramitsu.nft.impl.data

import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.nft.data.NFTRepository
import jp.co.soramitsu.nft.data.models.ContractInfo
import jp.co.soramitsu.nft.data.models.TokenInfo
import jp.co.soramitsu.nft.data.models.wrappers.NFTResponse
import jp.co.soramitsu.nft.data.pagination.PageBackStack
import jp.co.soramitsu.nft.data.pagination.PagedResponse
import jp.co.soramitsu.nft.data.pagination.PaginationRequest
import jp.co.soramitsu.nft.impl.data.domain.PageCachingDecorator
import jp.co.soramitsu.nft.impl.data.domain.PagingRequestMediator
import jp.co.soramitsu.nft.impl.data.model.request.NFTRequest
import jp.co.soramitsu.nft.impl.data.remote.AlchemyNftApi
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal const val DEFAULT_CACHED_PAGES_AMOUNT = 3
internal const val DEFAULT_PAGE_SIZE = 100
internal const val NFT_FILTERS_KEY = "NFT_FILTERS_KEY"

// if not set, some NFTs can return up to 94000 of owners (might end up in out of memory exception)
internal const val DEFAULT_OWNERS_PAGE_SIZE = 10

class NFTRepositoryImpl(
    private val alchemyNftApi: AlchemyNftApi,
    private val preferences: Preferences,
    private val pagingRequestMediator: PagingRequestMediator,
    private val pageCachingDecorator: PageCachingDecorator
) : NFTRepository {

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
                } else {
                    remove(filter)
                }
            }

            emit(cache)

            /*
                Wait for 10 seconds till user finishes all filters selection
                to compare saved selection

                1) Delay will be cancelled by use of collectLatest except for the last one
             */
            kotlinx.coroutines.delay(10_000)

            val dbCache = preferences.getStringSet(NFT_FILTERS_KEY, emptySet())

            if (dbCache.containsAll(cache)) {
                return@transformLatest
            }

            filtersSnapshot = dbCache.toMutableSet()

            emit(filtersSnapshot)
        }.onStart { emit(filtersSnapshot) }.collect(this)
    }.shareIn(scope = localScope, started = SharingStarted.Eagerly, replay = 1)

    override fun setNFTFilter(value: String, excludeFromSearchQuery: Boolean) {
        // non-suspended call to avoid UI delays or inconsistencies
        mutableFiltersFlow.tryEmit(value to excludeFromSearchQuery)

        localScope.launch {
            with(preferences) {
                val mutableFilters = getStringSet(NFT_FILTERS_KEY, emptySet()).toMutableSet()

                if (excludeFromSearchQuery) {
                    mutableFilters.add(value)
                } else {
                    mutableFilters.remove(value)
                }

                putStringSet(NFT_FILTERS_KEY, mutableFilters)
            }
        }
    }

    @Suppress("SpreadOperator")
    override fun paginatedUserOwnedContractsFlow(
        paginationRequestFlow: Flow<PaginationRequest>,
        chainSelectionFlow: Flow<List<Chain>>,
        selectedMetaAccountFlow: Flow<MetaAccount>,
        exclusionFiltersFlow: Flow<List<String>>
    ): Flow<List<PagedResponse<ContractInfo>>> {
        class LocalMetadataHolder(args: Array<*>) : PagingRequestMediator.RequestMetadata() {
            val chains: Map<ChainId, Chain> = (args[1] as List<Chain>).associateBy { it.id }

            val metaAccount: MetaAccount = args[2] as MetaAccount

            val exclusionFilters: List<String> = args[3] as List<String>

            override val requestsByTag: Map<Any, PageBackStack.Request> =
                (args[0] as Map<Any, PageBackStack.Request>)
                    .ifEmpty { chains.mapValues { PageBackStack.Request.FromStart(DEFAULT_PAGE_SIZE) } }
        }

        return pageCachingDecorator { updateCacheHandle ->
            pagingRequestMediator(
                flowsFactory = { pageBackStackHandle ->
                    listOf(
                        paginationRequestFlow.map {
                            if (it is PaginationRequest.Start) {
                                pageBackStackHandle.resetExisting()
                                updateCacheHandle.wipeCache()
                                return@map emptyMap()
                            }

                            updateCacheHandle.swapRequests(it)
                        },
                        chainSelectionFlow.distinctUntilChanged().onEach { chains ->
                            println()
                            pageBackStackHandle.span(*chains.map { it.id }.toTypedArray())
                            updateCacheHandle.wipeCache()
                        },
                        selectedMetaAccountFlow.distinctUntilChanged().onEach {
                            pageBackStackHandle.resetExisting()
                            updateCacheHandle.wipeCache()
                        },
                        exclusionFiltersFlow.distinctUntilChanged().onEach {
                            pageBackStackHandle.resetExisting()
                            updateCacheHandle.wipeCache()
                        }
                    )
                },
                argsConverter = ::LocalMetadataHolder
            ) { tag, page, size, requestMetadata ->
                val requestUrl =
                    NFTRequest.UserOwnedContracts.requestUrl(requestMetadata.chains[tag]!!.alchemyNftId)

                val ownerAddress = requestMetadata.metaAccount.address(requestMetadata.chains[tag]!!)
                    ?: error(
                        """
                            Owner is not supported for chain with id: ${requestMetadata.chains[tag]!!.id}.
                        """.trimIndent()
                    )

                alchemyNftApi.getUserOwnedContracts(
                    url = requestUrl,
                    owner = ownerAddress,
                    withMetadata = true,
                    pageKey = page,
                    pageSize = size,
                    excludeFilters = requestMetadata.exclusionFilters
                )
            }
        }
    }

    override fun paginatedUserOwnedNFTsByContractAddressFlow(
        paginationRequestFlow: Flow<PaginationRequest>,
        chainSelectionFlow: Flow<Chain>,
        contractAddressFlow: Flow<String>,
        selectedMetaAccountFlow: Flow<MetaAccount>,
        exclusionFiltersFlow: Flow<List<String>>
    ): Flow<PagedResponse<TokenInfo>> {
        class LocalMetadataHolder(args: Array<*>) : PagingRequestMediator.RequestMetadata() {
            val chain: Chain = args[1] as Chain

            val contractAddress: String = args[2] as String

            val metaAccount: MetaAccount = args[3] as MetaAccount

            val exclusionFilters: List<String> = args[4] as List<String>

            override val requestsByTag: Map<Any, PageBackStack.Request> =
                (args[0] as Map<Any, PageBackStack.Request>)
                    .ifEmpty { mapOf(chain.id to PageBackStack.Request.FromStart(DEFAULT_PAGE_SIZE)) }
        }

        return pageCachingDecorator { updateCacheHandle ->

            pagingRequestMediator(
                flowsFactory = { pageBackStackHandle ->
                    listOf(
                        paginationRequestFlow.map {
                            if (it is PaginationRequest.Start) {
                                pageBackStackHandle.resetExisting()
                                updateCacheHandle.wipeCache()
                                return@map emptyMap()
                            }

                            updateCacheHandle.swapRequests(it)
                        },
                        chainSelectionFlow.distinctUntilChanged().onEach {
                            pageBackStackHandle.span(it.id)
                            updateCacheHandle.wipeCache()
                        },
                        contractAddressFlow.distinctUntilChanged().onEach {
                            pageBackStackHandle.resetExisting()
                            updateCacheHandle.wipeCache()
                        },
                        selectedMetaAccountFlow.distinctUntilChanged().onEach {
                            pageBackStackHandle.resetExisting()
                            updateCacheHandle.wipeCache()
                        },
                        exclusionFiltersFlow.distinctUntilChanged().onEach {
                            pageBackStackHandle.resetExisting()
                            updateCacheHandle.wipeCache()
                        }
                    )
                },
                argsConverter = ::LocalMetadataHolder
            ) { _, page, size, requestMetadata ->
                val requestUrl =
                    NFTRequest.UserOwnedNFTs.requestUrl(requestMetadata.chain.alchemyNftId)

                val ownerAddress = requestMetadata.metaAccount.address(requestMetadata.chain)
                    ?: error(
                        """
                            Owner is not supported for chain with id: ${requestMetadata.chain.id}.
                        """.trimIndent()
                    )

                alchemyNftApi.getUserOwnedNFTsByContractAddress(
                    url = requestUrl,
                    owner = ownerAddress,
                    contractAddress = requestMetadata.contractAddress,
                    withMetadata = true,
                    pageKey = page,
                    pageSize = size,
                    excludeFilters = requestMetadata.exclusionFilters
                )
            }
        }.map { it.first() }
    }

    override fun paginatedNFTCollectionByContractAddressFlow(
        paginationRequestFlow: Flow<PaginationRequest>,
        chainSelectionFlow: Flow<Chain>,
        contractAddressFlow: Flow<String>
    ): Flow<PagedResponse<TokenInfo>> {
        class LocalMetadataHolder(args: Array<*>) : PagingRequestMediator.RequestMetadata() {
            val chain: Chain = args[1] as Chain

            val contractAddress: String = args[2] as String

            override val requestsByTag: Map<Any, PageBackStack.Request> =
                (args[0] as Map<Any, PageBackStack.Request>)
                    .ifEmpty { mapOf(chain.id to PageBackStack.Request.FromStart(DEFAULT_PAGE_SIZE)) }
        }

        return pageCachingDecorator { updateCacheHandle ->
            pagingRequestMediator(
                flowsFactory = { pageBackStackHandle ->
                    listOf(
                        paginationRequestFlow.map {
                            if (it is PaginationRequest.Start) {
                                pageBackStackHandle.resetExisting()
                                updateCacheHandle.wipeCache()
                                return@map emptyMap()
                            }

                            updateCacheHandle.swapRequests(it)
                        },
                        chainSelectionFlow.distinctUntilChanged().onEach {
                            pageBackStackHandle.span(it.id)
                            updateCacheHandle.wipeCache()
                        },
                        contractAddressFlow.distinctUntilChanged().onEach {
                            pageBackStackHandle.resetExisting()
                            updateCacheHandle.wipeCache()
                        }
                    )
                },
                argsConverter = ::LocalMetadataHolder
            ) { _, page, size, requestMetadata ->
                val requestUrl = NFTRequest.TokensCollection.requestUrl(
                    alchemyChainId = requestMetadata.chain.alchemyNftId
                )

                alchemyNftApi.getNFTCollectionByContactAddress(
                    requestUrl = requestUrl,
                    contractAddress = requestMetadata.contractAddress,
                    withMetadata = true,
                    startTokenId = page,
                    limit = size
                )
            }
        }.map { it.first() }
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
                    tokenId = tokenId,
                    pageSize = DEFAULT_OWNERS_PAGE_SIZE
                )
            }
        }
    }
}
