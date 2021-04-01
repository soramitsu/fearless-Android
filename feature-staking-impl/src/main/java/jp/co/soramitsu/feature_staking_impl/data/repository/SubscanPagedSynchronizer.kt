package jp.co.soramitsu.feature_staking_impl.data.repository

import android.util.Log
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
        Log.d("RX", "Already Saved: $alreadySavedItems")

        val metaPage = httpExceptionHandler.wrap { pageFetcher(0, 1) } ?: return

        val totalCount = metaPage.totalCount
        Log.d("RX", "totalCount: $totalCount")

        if (totalCount == alreadySavedItems) return

        val totalPages = ceil(totalCount.toDouble() / ROW_MAX).toInt()
        Log.d("RX", "totalPages: $totalPages")

        val completelySavedPages = alreadySavedItems / ROW_MAX
        Log.d("RX", "completelySavedPages: $completelySavedPages")

        val startingPage = totalPages - completelySavedPages
        Log.d("RX", "startingPage: $startingPage")
        val startingPageIndex = startingPage - 1
        Log.d("RX", "startingPageIndex: $startingPageIndex")

        var currentPage = startingPageIndex

        do {
            val pageResult = httpExceptionHandler.wrap { pageFetcher(currentPage, ROW_MAX) } ?: return

            pageCacher(pageResult.items)

            Log.d("RX", "Fetched and cached page $currentPage with ${pageResult.items.size} items")

            currentPage--
        } while (currentPage >= 0)
    }
}
