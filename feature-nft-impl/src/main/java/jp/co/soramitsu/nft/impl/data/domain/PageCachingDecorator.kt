package jp.co.soramitsu.nft.impl.data.domain

import jp.co.soramitsu.nft.data.pagination.PageBackStack
import jp.co.soramitsu.nft.data.pagination.PagedResponse
import jp.co.soramitsu.nft.data.pagination.PaginationRequest
import jp.co.soramitsu.nft.impl.data.DEFAULT_CACHED_PAGES_AMOUNT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

class PageCachingDecorator @Inject constructor() {

    interface UpdateCacheHandle {
        suspend fun swapRequests(request: PaginationRequest): Map<Any, PageBackStack.Request>

        suspend fun wipeCache()
    }

    private suspend fun <T> PaginationRequest.switchRequestsAndWipeCache(
        responseCache: ArrayDeque<PageBackStack.PageResult.ValidPage<T>>,
        mutex: Mutex
    ): PageBackStack.Request = mutex.withLock {
        when (this) {
            is PaginationRequest.Prev -> PageBackStack.Request.Prev(
                page = responseCache.firstOrNull()?.nextPage
            )

            is PaginationRequest.Next -> PageBackStack.Request.Next(
                page = responseCache.lastOrNull()?.nextPage,
                size = pageLimit
            )

            is PaginationRequest.Start -> PageBackStack.Request.Next(
                page = null,
                size = pageLimit
            )

            is PaginationRequest.ProceedFromLastPage ->
                PageBackStack.Request.ReplayCurrent
        }
    }

    operator fun <T> invoke(
        factory: (UpdateCacheHandle) -> Flow<List<PagedResponse<T>>>
    ): Flow<List<PagedResponse<T>>> {
        return flow {
            val mutexMap = mutableMapOf<Any, Mutex>()
            val savedTokensCollections = mutableMapOf<Any, ArrayDeque<PageBackStack.PageResult.ValidPage<T>>>()

            val handle = object : UpdateCacheHandle {
                @Suppress("FunctionSignature")
                override suspend fun swapRequests(request: PaginationRequest) =
                    mutexMap.mapValues { (tag, mutex) ->
                        request.switchRequestsAndWipeCache(
                            responseCache = savedTokensCollections.getOrPut(tag, ::ArrayDeque),
                            mutex = mutex
                        )
                    }

                override suspend fun wipeCache() {
                    mutexMap.forEach { (tag, mutex) ->
                        mutex.withLock {
                            savedTokensCollections[tag]?.clear()
                        }
                    }
                    mutexMap.clear()
                }
            }

            factory(handle).map { pagedResponseList ->
                pagedResponseList.map { pagedResponse ->
                    handlePagedResponse(
                        pagedResponse = pagedResponse,
                        responseCache = savedTokensCollections.getOrPut(pagedResponse.tag, ::ArrayDeque),
                        mutex = mutexMap.getOrPut(pagedResponse.tag, ::Mutex)
                    )
                }
            }.collect(this)
        }.flowOn(Dispatchers.Default)
    }

    private suspend fun <T> handlePagedResponse(
        pagedResponse: PagedResponse<T>,
        responseCache: ArrayDeque<PageBackStack.PageResult.ValidPage<T>>,
        mutex: Mutex
    ): PagedResponse<T> {
        val pageResult = pagedResponse.result.map { pageResult ->
            if (pageResult !is PageBackStack.PageResult.ValidPage) {
                return@map pageResult.updateItems(responseCache.asSequence().flatMap { it.items })
            }

            val tokenInfoListFromCache = updateAndFlattenCacheLocked(
                request = pagedResponse.request,
                responseCache = responseCache,
                response = pageResult,
                mutex = mutex
            )

            // pretend nothing happened, and return loaded page but with all cached tokens
            return@map pageResult.updateItems(tokenInfoListFromCache)
        }

        return pagedResponse.updateResult(pageResult)
    }

    private suspend fun <T> updateAndFlattenCacheLocked(
        request: PageBackStack.Request,
        responseCache: ArrayDeque<PageBackStack.PageResult.ValidPage<T>>,
        response: PageBackStack.PageResult.ValidPage<T>,
        mutex: Mutex
    ): Sequence<T> {
        return mutex.withLock {
            // insert new token collection into cache
            when (request) {
                /*
                    Collection can be duplicates if there is only one collection at all;
                    thus, first page has nextPage == null
                 */
                is PageBackStack.Request.Prev -> {
                    if (responseCache.isNotEmpty()) {
                        val isNewCollectionDuplicationOfFirstCached =
                            responseCache.firstOrNull()?.nextPage == response.nextPage

                        if (!isNewCollectionDuplicationOfFirstCached) {
                            responseCache.addFirst(response)
                        }
                    } else {
                        responseCache.addFirst(response)
                    }
                }

                is PageBackStack.Request.Next -> {
                    /*
                        Collection can be duplicates if there is only one collection at all;
                        thus, first page has nextPage == null
                     */
                    if (responseCache.isNotEmpty()) {
                        val isNewCollectionDuplicationOfLastCached =
                            responseCache.lastOrNull()?.nextPage == response.nextPage

                        if (!isNewCollectionDuplicationOfLastCached) {
                            responseCache.addLast(response)
                        }
                    } else {
                        responseCache.addLast(response)
                    }
                }

                else -> Unit
            }

            // shrink cache to allowed size
            while (responseCache.size > DEFAULT_CACHED_PAGES_AMOUNT) {
                when (request) {
                    is PageBackStack.Request.Prev ->
                        responseCache.removeLast()

                    is PageBackStack.Request.Next ->
                        responseCache.removeFirst()

                    else -> Unit
                }
            }

            return@withLock responseCache.asSequence().flatMap { it.items }
        }
    }
}
