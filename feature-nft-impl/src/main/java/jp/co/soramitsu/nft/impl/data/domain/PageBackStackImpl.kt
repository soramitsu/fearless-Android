package jp.co.soramitsu.nft.impl.data.domain

import jp.co.soramitsu.nft.data.pagination.PageBackStack
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Stack

class PageBackStackImpl : PageBackStack {
    private class VisitedPage(
        val page: String?,
        val size: Int
    )

    private val localMutex = Mutex()
    private val historyStack = Stack<VisitedPage>()

    @Suppress("CollapsibleIfStatements", "NestedBlockDepth")
    override suspend fun <ResponseItem> runPagedRequest(
        request: PageBackStack.Request,
        block: suspend (page: String?, size: Int) -> PageBackStack.PageResult.ValidPage<ResponseItem>
    ): PageBackStack.PageResult<ResponseItem> {
        if (request.page == null) {
            when (request) {
                is PageBackStack.Request.Prev ->
                    return PageBackStack.PageResult.NoPrevPages()

                is PageBackStack.Request.Next ->
                    if (!historyStack.isEmpty()) {
                        return PageBackStack.PageResult.NoNextPages()
                    }

                else -> Unit
            }
        }

        val page = when (request) {
            is PageBackStack.Request.Prev ->
                localMutex.withLock { getPrevPageAndTrimStack(request) }
                    ?: return PageBackStack.PageResult.NoPrevPages()

            is PageBackStack.Request.Next ->
                localMutex.withLock { getNextPageAndTrimStack(request) }
                    ?: VisitedPage(request.page, request.size)

            is PageBackStack.Request.ReplayCurrent ->
                localMutex.withLock { historyStack.peek() }
        }

        val pagedRequestResult = block(page.page, page.size)

        // if request has been executed successfully, save request as visitedPage
        if (page.page == request.page) {
            localMutex.withLock { historyStack.push(page) }
        }

        return pagedRequestResult
    }

    @Suppress("NestedBlockDepth")
    private fun getPrevPageAndTrimStack(request: PageBackStack.Request.Prev): VisitedPage? {
        return with(historyStack) {
            if (count { it.page == request.page } > 0) {
                while (!isEmpty()) {
                    if (pop().page == request.page) {
                        break
                    }
                }
            }

            if (size < 2) {
                return null
            }

            val currentPage = pop()
            return@with peek().also { push(currentPage) }
        }
    }

    @Suppress("NestedBlockDepth")
    private fun getNextPageAndTrimStack(request: PageBackStack.Request.Next): VisitedPage? {
        return with(historyStack) {
            if (count { it.page == request.page } == 0) {
                return@with null
            }

            while (!isEmpty()) {
                if (pop().page == request.page) {
                    break
                }
            }

            return@with if (!isEmpty()) peek() else null
        }
    }

    override suspend fun clean() {
        localMutex.withLock { historyStack.clear() }
    }
}
