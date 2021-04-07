package jp.co.soramitsu.feature_staking_impl.data.repository

import jp.co.soramitsu.common.data.network.HttpExceptionHandler
import jp.co.soramitsu.common.data.network.subscan.CollectionContent
import jp.co.soramitsu.common.data.network.subscan.SubscanResponse
import jp.co.soramitsu.common.utils.generateLinearSequence
import kotlin.math.ceil

typealias PageFetcher<T> = suspend (page: Int, row: Int) -> SubscanPagedSynchronizer.PageResult<T>?

class SubscanPagedSynchronizer(
    private val httpExceptionHandler: HttpExceptionHandler,
) {

    companion object {
        const val ROW_MAX = 100
    }

    class PageResult<T>(val totalCount: Int, val items: List<T>)

    suspend fun <T> sync(
        alreadySavedItems: Int,
        pageFetcher: PageFetcher<T>,
        pageCacher: suspend (List<T>) -> Unit,
    ) {
        val metaPage = httpExceptionHandler.wrap { pageFetcher(0, 1) } ?: return

        val totalCount = metaPage.totalCount

        if (totalCount == alreadySavedItems) return

        val totalPages = ceil(totalCount.toDouble() / ROW_MAX).toInt()

        val completelySavedPages = alreadySavedItems / ROW_MAX

        val startingPage = totalPages - completelySavedPages
        val startingPageIndex = startingPage - 1

        retrievePagesFromTail(startingPageIndex, pageFetcher, pageCacher)
    }

    suspend fun <T> retrievePagesFromTail(
        startingPageIndex: Int,
        pageFetcher: PageFetcher<T>,
        pageProcessor: suspend (List<T>) -> Unit,
    ) {
        (startingPageIndex downTo 0).forEach {
            val pageResult = httpExceptionHandler.wrap { pageFetcher(it, ROW_MAX) } ?: return

            pageProcessor(pageResult.items)
        }
    }

    suspend fun <T> retrievePagesFromHead(
        pageFetcher: PageFetcher<T>,
        pageProcessor: suspend (List<T>) -> Unit,
    ) {
        generateLinearSequence(initial = 0, step = 1).forEach {
            val pageResult = httpExceptionHandler.wrap { pageFetcher(it, ROW_MAX) } ?: return

            pageProcessor(pageResult.items)

            if (pageResult.items.size < ROW_MAX) return
        }
    }
}

suspend fun <T> SubscanPagedSynchronizer.fetchAll(
    pageFetcher: suspend (page: Int, row: Int) -> SubscanPagedSynchronizer.PageResult<T>?
): List<T> {
    val result = mutableListOf<T>()

    retrievePagesFromHead(pageFetcher, pageProcessor = result::addAll)

    return result
}

suspend inline fun <T> subscanCollectionFetcher(
    crossinline fetcher: suspend (page: Int, row: Int) -> SubscanResponse<out CollectionContent<T>>
): PageFetcher<T> = { page, row ->
    val content = fetcher(page, row).content
    val items = content?.items

    items?.let {
        SubscanPagedSynchronizer.PageResult(content.count, it)
    }
}
