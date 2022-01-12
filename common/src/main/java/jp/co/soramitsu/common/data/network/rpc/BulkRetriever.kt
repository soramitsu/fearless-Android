package jp.co.soramitsu.common.data.network.rpc

import jp.co.soramitsu.common.data.network.runtime.binding.BlockHash
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.executeAsync
import jp.co.soramitsu.fearless_utils.wsrpc.mappers.nonNull
import jp.co.soramitsu.fearless_utils.wsrpc.mappers.pojoList
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.RuntimeRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class GetKeysPagedRequest(
    keyPrefix: String,
    pageSize: Int,
    fullKeyOffset: String?,
    at: BlockHash?
) : RuntimeRequest(
    method = "state_getKeysPaged",
    params = listOfNotNull(
        keyPrefix,
        pageSize,
        fullKeyOffset,
        at
    )
)

class QueryStorageAtRequest(
    keys: List<String>,
    at: String?
) : RuntimeRequest(
    method = "state_queryStorageAt",
    params = listOfNotNull(
        keys,
        at
    )
)

class QueryStorageAtResponse(
    val block: String,
    val changes: List<List<String?>>
) {
    fun changesAsMap(): Map<String, String?> {
        return changes.map { it[0]!! to it[1] }.toMap()
    }
}

private const val DEFAULT_PAGE_SIZE = 1000

class BulkRetriever(
    private val pageSize: Int = DEFAULT_PAGE_SIZE
) {

    suspend fun retrieveAllKeys(
        socketService: SocketService,
        keyPrefix: String,
        at: BlockHash? = null
    ): List<String> = withContext(Dispatchers.IO) {
        val result = mutableListOf<String>()

        var currentOffset: String? = null

        while (true) {
            ensureActive()

            val request = GetKeysPagedRequest(keyPrefix, DEFAULT_PAGE_SIZE, currentOffset, at)

            val page = socketService.executeAsync(request, mapper = pojoList<String>().nonNull())

            result += page

            if (isLastPage(page)) break

            currentOffset = page.last()
        }

        result
    }

    suspend fun queryKeys(
        socketService: SocketService,
        keys: List<String>,
        at: BlockHash? = null
    ): Map<String, String?> = withContext(Dispatchers.IO) {
        val chunks = keys.chunked(pageSize)

        chunks.fold(mutableMapOf()) { acc, chunk ->
            ensureActive()

            val request = QueryStorageAtRequest(chunk, at)

            val chunkValues = socketService.executeAsync(request, mapper = pojoList<QueryStorageAtResponse>().nonNull())
                .first().changesAsMap()

            acc.putAll(chunkValues)

            acc
        }
    }

    private fun isLastPage(page: List<String>) = page.size < pageSize
}

suspend fun BulkRetriever.queryKey(
    socketService: SocketService,
    key: String,
    at: BlockHash? = null
): String? = queryKeys(socketService, listOf(key), at).values.first()
