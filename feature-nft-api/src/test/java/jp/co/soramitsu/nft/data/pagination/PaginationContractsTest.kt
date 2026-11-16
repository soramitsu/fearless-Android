package jp.co.soramitsu.nft.data.pagination

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PaginationContractsTest {
    @Test
    fun nextRequestUpdatesCursorWithoutChangingPageSize() {
        val initial = PageBackStack.Request.FromStart(25)
        val next = initial.updatePage("page-two") as PageBackStack.Request.Next
        assertEquals(null, initial.page)
        assertEquals("page-two", next.page)
        assertEquals(25, next.size)
    }

    @Test
    fun previousRequestUpdatesCursorAndReplayRemainsTheSameRequest() {
        val previous = PageBackStack.Request.Prev("old").updatePage("new")
        assertEquals("new", previous.page)
        assertSame(PageBackStack.Request.ReplayCurrent, PageBackStack.Request.ReplayCurrent.updatePage("ignored"))
        assertEquals("Proceed", PageBackStack.Request.ReplayCurrent.page)
    }

    @Test
    fun boundaryPagesPreserveTheirKindWhenReplacingItems() {
        val next = PageBackStack.PageResult.NoNextPages<Int>().updateItems(sequenceOf(1, 2))
        val previous = PageBackStack.PageResult.NoPrevPages<Int>().updateItems(sequenceOf(3))
        assertTrue(next is PageBackStack.PageResult.NoNextPages)
        assertTrue(previous is PageBackStack.PageResult.NoPrevPages)
        assertEquals(listOf(1, 2), next.items.toList())
        assertEquals(listOf(3), previous.items.toList())
    }

    @Test
    fun replacingPageResultKeepsRequestAndChainTag() {
        val request = PageBackStack.Request.Next(null, 10)
        val original = PagedResponse("chain", request, Result.success<PageBackStack.PageResult<Int>>(PageBackStack.PageResult.NoPrevPages()))
        val failure = IllegalStateException("offline")
        val updated = original.updateResult(Result.failure(failure))
        assertEquals("chain", updated.tag)
        assertSame(request, updated.request)
        assertSame(failure, updated.result.exceptionOrNull())
        assertTrue(original.result.isSuccess)
    }
}
