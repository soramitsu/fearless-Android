package jp.co.soramitsu.nft.impl.data.domain

import jp.co.soramitsu.common.utils.concurrentRequestFlow
import jp.co.soramitsu.nft.data.pagination.PageBackStack
import jp.co.soramitsu.nft.data.pagination.PagedResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

class PagingRequestMediator @Inject constructor() {

    interface PageBackStackHandle {
        suspend fun span(vararg tagList: Any)

        suspend fun resetExisting()
    }

    abstract class RequestMetadata {
        abstract val requestsByTag: Map<Any, PageBackStack.Request>
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun <Metadata : RequestMetadata, ResponseItem> invoke(
        flowsFactory: (PageBackStackHandle) -> Iterable<Flow<*>>,
        argsConverter: (Array<*>) -> Metadata,
        request:
        suspend (tag: Any, page: String?, size: Int, Metadata) -> PageBackStack.PageResult.ValidPage<ResponseItem>
    ): Flow<List<PagedResponse<ResponseItem>>> {
        return flow {
            val mutex = Mutex()
            val pageStackMap = mutableMapOf<Any, PageBackStack>()

            val handle = object : PageBackStackHandle {
                override suspend fun span(vararg tagList: Any) {
                    mutex.withLock {
                        pageStackMap.clear()
                        tagList.forEach { pageStackMap[it] = PageBackStackImpl() }
                    }
                }

                override suspend fun resetExisting() {
                    mutex.withLock {
                        pageStackMap.keys.forEach {
                            pageStackMap.replace(it, PageBackStackImpl())
                        }
                    }
                }
            }

            combine(flowsFactory(handle)) { args: Array<*> ->
                argsConverter(args)
            }.transformLatest { holder ->
                // creating new list for reading with captured content
                val readOnlyChainToPageStackList = mutex.withLock {
                    pageStackMap.toList()
                }

                // running request for each pageStack concurrently
                val results = readOnlyChainToPageStackList
                    .concurrentRequestFlow { (tag, pageBackStack) ->
                        val backStackRequest = holder.requestsByTag[tag] ?: return@concurrentRequestFlow

                        val result = runCatching {
                            pageBackStack.runPagedRequest(
                                backStackRequest
                            ) { page, size -> request(tag, page, size, holder) }
                        }

                        PagedResponse(
                            tag = tag,
                            request = backStackRequest,
                            result = result
                        ).also { emit(it) }
                    }.toList()

                emit(results)
            }.collect(this)
        }.flowOn(Dispatchers.IO)
    }
}
