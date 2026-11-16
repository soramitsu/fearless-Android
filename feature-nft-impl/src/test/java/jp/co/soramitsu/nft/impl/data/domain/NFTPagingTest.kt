package jp.co.soramitsu.nft.impl.data.domain

import jp.co.soramitsu.nft.data.pagination.PageBackStack
import jp.co.soramitsu.nft.data.pagination.PagedResponse
import jp.co.soramitsu.nft.data.pagination.PaginationRequest
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NFTPagingTest {
    @Test
    fun nextPageAndReplayPreserveCursorAndPageSize(): Unit = runBlocking {
        val stack = PageBackStackImpl()
        val calls = mutableListOf<Pair<String?, Int>>()
        val block: suspend (String?, Int) -> PageBackStack.PageResult.ValidPage<String> = { page, size ->
            calls += page to size
            Page(sequenceOf(page.orEmpty()), "next")
        }
        stack.runPagedRequest(PageBackStack.Request.FromStart(7), block)
        stack.runPagedRequest(PageBackStack.Request.Next("cursor", 9), block)
        val replay = stack.runPagedRequest(PageBackStack.Request.ReplayCurrent, block)
        assertEquals(listOf(null to 7, "cursor" to 9, "cursor" to 9), calls)
        assertEquals(listOf("cursor"), replay.items.toList())
    }

    @Test
    fun exhaustedRequestsDoNotInvokeLoaderAndCleanAllowsRestart(): Unit = runBlocking {
        val stack = PageBackStackImpl()
        var calls = 0
        val load: suspend (String?, Int) -> PageBackStack.PageResult.ValidPage<String> = { _, _ ->
            calls++
            Page(sequenceOf("only"), null)
        }
        stack.runPagedRequest(PageBackStack.Request.FromStart(4), load)
        val next = stack.runPagedRequest(PageBackStack.Request.Next(null, 4), load)
        val prev = stack.runPagedRequest(PageBackStack.Request.Prev(null), load)
        assertTrue(next is PageBackStack.PageResult.NoNextPages)
        assertTrue(prev is PageBackStack.PageResult.NoPrevPages)
        assertEquals(1, calls)
        stack.clean()
        assertEquals(listOf("only"), stack.runPagedRequest(PageBackStack.Request.FromStart(4), load).items.toList())
        assertEquals(2, calls)
    }

    @Test
    fun previousOnEmptyStackStartsAtFirstPageWithDefaultSize(): Unit = runBlocking {
        val stack = PageBackStackImpl()
        val result = stack.runPagedRequest(PageBackStack.Request.Prev("unused")) { page, size ->
            assertEquals(null, page)
            assertEquals(100, size)
            Page(sequenceOf("first"), "cursor")
        }
        assertEquals(listOf("first"), result.items.toList())
    }

    @Test
    fun previousRequestWalksBackThroughVisitedPages(): Unit = runBlocking {
        val stack = PageBackStackImpl()
        val load: suspend (String?, Int) -> PageBackStack.PageResult.ValidPage<String> = { page, _ ->
            Page(sequenceOf(page ?: "first"), page)
        }
        stack.runPagedRequest(PageBackStack.Request.FromStart(5), load)
        stack.runPagedRequest(PageBackStack.Request.Next("second", 5), load)
        stack.runPagedRequest(PageBackStack.Request.Next("third", 5), load)
        assertEquals(
            listOf("second"),
            stack.runPagedRequest(PageBackStack.Request.Prev("fourth"), load).items.toList()
        )
        assertEquals(
            listOf("first"),
            stack.runPagedRequest(PageBackStack.Request.Prev("third"), load).items.toList()
        )
        assertTrue(stack.runPagedRequest(PageBackStack.Request.Prev("second"), load) is PageBackStack.PageResult.NoPrevPages)
    }

    @Test
    fun failedLoadDoesNotMarkFirstPageAsVisited(): Unit = runBlocking {
        val stack = PageBackStackImpl()
        val failure = IllegalStateException("offline")
        val result = runCatching {
            stack.runPagedRequest<String>(PageBackStack.Request.FromStart(5)) { _, _ -> throw failure }
        }
        assertSame(failure, result.exceptionOrNull())
        val retry = stack.runPagedRequest(PageBackStack.Request.FromStart(5)) { page, size ->
            assertEquals(null, page)
            assertEquals(5, size)
            Page(sequenceOf("retried"), null)
        }
        assertEquals(listOf("retried"), retry.items.toList())
    }

    @Test
    fun cacheRetainsThreePagesAndSupportsBackwardNavigation(): Unit = runBlocking {
        withTimeout(5_000) {
            val acknowledged = Channel<Unit>()
            val snapshots = mutableListOf<List<String>>()
            PageCachingDecorator().invoke<String> { handle ->
                flow {
                    for (number in 1..4) {
                        emit(listOf(response(PageBackStack.Request.Next("p" + number, 1), number)))
                        acknowledged.receive()
                    }
                    val next = handle.swapRequests(PaginationRequest.Next(8)).getValue("chain") as PageBackStack.Request.Next
                    assertEquals("p4", next.page)
                    assertEquals(8, next.size)
                    val prev = handle.swapRequests(PaginationRequest.Prev).getValue("chain")
                    assertEquals("p2", prev.page)
                    assertSame(PageBackStack.Request.ReplayCurrent, handle.swapRequests(PaginationRequest.ProceedFromLastPage)["chain"])
                    val start = handle.swapRequests(PaginationRequest.Start(6)).getValue("chain") as PageBackStack.Request.Next
                    assertEquals(null, start.page)
                    assertEquals(6, start.size)
                    emit(listOf(response(PageBackStack.Request.Prev("p2"), 1)))
                    acknowledged.receive()
                    emit(listOf(response(PageBackStack.Request.Prev("p1"), 1)))
                    acknowledged.receive()
                    emit(listOf(response(PageBackStack.Request.Next("p3", 1), 3)))
                    acknowledged.receive()
                    emit(listOf(PagedResponse("chain", PageBackStack.Request.Next(null, 1),
                        Result.success(PageBackStack.PageResult.NoNextPages()))))
                    acknowledged.receive()
                    handle.wipeCache()
                    assertTrue(handle.swapRequests(PaginationRequest.Next(1)).isEmpty())
                    emit(listOf(response(PageBackStack.Request.Next(null, 1), 9)))
                    acknowledged.receive()
                }
            }.collect {
                snapshots += it.single().result.getOrThrow().items.toList()
                acknowledged.send(Unit)
            }
            assertEquals(
                listOf(listOf("1"), listOf("1", "2"), listOf("1", "2", "3"), listOf("2", "3", "4"),
                    listOf("1", "2", "3"), listOf("1", "2", "3"), listOf("1", "2", "3"),
                    listOf("1", "2", "3"), listOf("9")),
                snapshots
            )
        }
    }

    @Test
    fun cachePreservesFailuresWithoutInventingItems(): Unit = runBlocking {
        val failure = IllegalStateException("API failure")
        val response = PagedResponse<String>("chain", PageBackStack.Request.FromStart(5), Result.failure(failure))
        val result = PageCachingDecorator().invoke { flowOf(listOf(response)) }.single().single()
        assertEquals("chain", result.tag)
        assertSame(response.request, result.request)
        assertSame(failure, result.result.exceptionOrNull())
    }

    private fun response(request: PageBackStack.Request, number: Int) = PagedResponse<String>(
        "chain", request, Result.success(Page(sequenceOf(number.toString()), "p" + number))
    )

    private data class Page(
        override val items: Sequence<String>,
        override val nextPage: String?
    ) : PageBackStack.PageResult.ValidPage<String> {
        override fun updateItems(items: Sequence<String>) = copy(items = items)
    }
}
