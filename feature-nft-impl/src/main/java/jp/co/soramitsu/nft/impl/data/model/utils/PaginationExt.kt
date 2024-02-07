package jp.co.soramitsu.nft.impl.data.model.utils

import jp.co.soramitsu.nft.data.pagination.Page
import jp.co.soramitsu.nft.data.pagination.PaginationRequest
import java.util.Stack

@Suppress("NOTHING_TO_INLINE")
inline fun PaginationRequest.getPageSize(defaultPageSize: Int): Int {
    return when (this) {
        is PaginationRequest.Next.Page -> defaultPageSize

        is PaginationRequest.Next.WithSize -> pageLimit

        is PaginationRequest.Next.Specific -> pageLimit ?: defaultPageSize

        is PaginationRequest.Prev.Page -> defaultPageSize

        is PaginationRequest.Prev.WithSize -> pageLimit

        is PaginationRequest.Prev.TwoBeforeSpecific -> pageLimit ?: defaultPageSize
    }
}

@Suppress("NOTHING_TO_INLINE", "ReturnCount", "CyclomaticComplexMethod", "NestedBlockDepth", "SwallowedException")
inline fun PaginationRequest.nextOrPrevPage(pageStack: Stack<String?>): Page {
    val exception = IllegalStateException(
        """
            Could not request page.
        """.trimIndent()
    )

    when (this) {
        is PaginationRequest.Next.Page, is PaginationRequest.Next.WithSize ->
            return if (pageStack.size == 1 && pageStack.peek() == null) {
                Page.ValidPage(pageStack.peek())
            } else if (pageStack.size > 1 && pageStack.peek() != null) {
                Page.ValidPage(pageStack.peek())
            } else {
                Page.NoNextPages
            }

        is PaginationRequest.Next.Specific -> {
            for (i in pageStack.indices.reversed()) {
                if (i == 0 || pageStack.peek() == page) {
                    break
                }

                pageStack.pop()
            }

            if (pageStack.size > 1 && pageStack.peek() == null) {
                return Page.NoNextPages
            }

            return Page.ValidPage(pageStack.peek())
        }

        is PaginationRequest.Prev -> {
            if (pageStack.size < 2) {
                return Page.NoPrevPages
            }

            val nextPageToLoad = pageStack.pop()
            val currentPage = pageStack.pop()

            try {
                if (
                    this is PaginationRequest.Prev.Page ||
                    this is PaginationRequest.Prev.WithSize
                ) {
                    if (currentPage == null && nextPageToLoad == null) {
                        return Page.ValidPage(null)
                    }

                    if (pageStack.size < 1) {
                        // after 2 popping there must at least 1 element
                        return Page.NoPrevPages
                    }

                    return Page.ValidPage(pageStack.peek())
                }

                if (this !is PaginationRequest.Prev.TwoBeforeSpecific) {
                    throw exception
                }

                // Important(!): page can be null only if it is either first or last
                when {
                    /*
                        if we are looking for null page and next page to load is not the last one then
                        the page that we are actually looking for is the one before first
                        which does not exist
                     */
                    page == null && nextPageToLoad != null ->
                        return Page.NoPrevPages

                    /*
                        if we are looking for null page and next page to load is the last one then
                        the page that we are looking for is the one before last
                     */
                    page == null && nextPageToLoad == null ->
                        return Page.ValidPage(currentPage)

                    /*
                        if there are only two pages in stack (first and next one to be loaded) and
                        if specific page equals to the next page to be loaded then
                        we want to skip reloading of the current page
                     */
                    nextPageToLoad == page ->
                        return if (pageStack.isNotEmpty()) {
                            Page.ValidPage(pageStack.peek())
                        } else {
                            Page.NoPrevPages
                        }

                    /*
                        if there are only two pages in stack (first and next one to be loaded) and
                        if specific page equals to the current page then
                        there two pages before current is needed
                     */
                    currentPage == page ->
                        return if (pageStack.size > 1) {
                            val newCurrentPage = pageStack.pop()
                            Page.ValidPage(pageStack.peek()).also {
                                pageStack.push(newCurrentPage)
                            }
                        } else {
                            Page.NoPrevPages
                        }

                    /*
                        Otherwise, we need to traverse entire stack and find page
                        that is one before specific
                     */
                    else -> {
                        val stackIterator = pageStack.listIterator(0)

                        while (stackIterator.hasNext()) {
                            if (stackIterator.next() == page) {
                                break
                            }
                        }

                        return try {
                            val previousPage = stackIterator.run {
                                previous() // next page to load
                                previous() // current page
                                previous() // previous page
                            }
                            Page.ValidPage(previousPage)
                        } catch (noSuchElementException: NoSuchElementException) {
                            Page.NoPrevPages
                        }
                    }
                }
            } finally {
                pageStack.push(currentPage)
                pageStack.push(nextPageToLoad)
            }
        }
    }
}

@Suppress("NOTHING_TO_INLINE")
inline fun PaginationRequest.transformToSpecificOrDoNothing(page: String?): PaginationRequest {
    return when (this) {
        is PaginationRequest.Next.Specific,
        is PaginationRequest.Prev.TwoBeforeSpecific -> this

        is PaginationRequest.Next.Page,
        is PaginationRequest.Next.WithSize ->
            PaginationRequest.Next.Specific(page)

        is PaginationRequest.Prev.Page,
        is PaginationRequest.Prev.WithSize ->
            PaginationRequest.Prev.TwoBeforeSpecific(page)
    }
}
