package jp.co.soramitsu.feature_staking_impl.data.repository

import jp.co.soramitsu.common.data.network.HttpExceptionHandler
import kotlin.math.ceil

class SubscanPagedSynchronizer(
    private val httpExceptionHandler: HttpExceptionHandler,
) {

    companion object {
        const val ROW_MAX = 100
    }

    class PageResult<T>(val totalCount: Int, val items: List<T>)

    suspend fun <T> sync(
        alreadySavedItems: Int,
        pageFetcher: suspend (page: Int, row: Int) -> PageResult<T>?,
        pageCacher: suspend (List<T>) -> Unit,
    ) {
        val metaPage = httpExceptionHandler.wrap { pageFetcher(0, 1) } ?: return

        val totalCount = metaPage.totalCount

        if (totalCount == alreadySavedItems) return

        val totalPages = ceil(totalCount.toDouble() / ROW_MAX).toInt()

        val completelySavedPages = alreadySavedItems / ROW_MAX

        val startingPage = totalPages - completelySavedPages
        val startingPageIndex = startingPage - 1

        (startingPageIndex downTo 0).forEach {
            val pageResult = httpExceptionHandler.wrap { pageFetcher(it, ROW_MAX) } ?: return

            pageCacher(pageResult.items)
        }
    }
}
