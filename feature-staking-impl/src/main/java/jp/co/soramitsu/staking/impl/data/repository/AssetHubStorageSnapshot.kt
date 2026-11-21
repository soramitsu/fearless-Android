package jp.co.soramitsu.staking.impl.data.repository

import jp.co.soramitsu.common.data.network.rpc.GetKeysPagedRequest
import jp.co.soramitsu.common.data.network.rpc.QueryStorageAtRequest
import jp.co.soramitsu.common.data.network.rpc.QueryStorageAtResponse
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.executeAsync
import jp.co.soramitsu.fearless_utils.wsrpc.mappers.nonNull
import jp.co.soramitsu.fearless_utils.wsrpc.mappers.pojoList
import jp.co.soramitsu.fearless_utils.wsrpc.mappers.string
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.RuntimeRequest
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Bounded reads at one finalized block; partial RPC results never imply empty staking state. */
internal class AssetHubStorageSnapshot(val runtime: RuntimeSnapshot, val socket: SocketService, val block: String) {
    suspend fun query(keys: List<String>): Map<String, String?> {
        val result = mutableMapOf<String, String?>()
        for (chunk in keys.distinct().chunked(PAGE_BATCH)) {
            currentCoroutineContext().ensureActive()
            val response = socket.executeAsync(
                QueryStorageAtRequest(chunk, block), mapper = pojoList<QueryStorageAtResponse>().nonNull()
            ).single()
            require(response.block == block) { "Staking response is from a different block" }
            require(
                response.changes.all { it.size == 2 && it[0] in chunk } &&
                response.changes.map { it[0] }.toSet().size == chunk.size && response.changes.size == chunk.size
            ) {
                "Incomplete finalized staking response"
            }
            result.putAll(response.changesAsMap())
        }
        return result
    }

    suspend fun discoverKeys(prefix: String): List<String> {
        val result = mutableListOf<String>()
        while (true) {
            currentCoroutineContext().ensureActive()
            val previous = result.lastOrNull()
            val page = socket.executeAsync(
                GetKeysPagedRequest(prefix, KEY_PAGE_SIZE, previous, block), mapper = pojoList<String>().nonNull()
            )
            require(
                page.size <= KEY_PAGE_SIZE && page.all { it.startsWith(prefix) } &&
                page.zipWithNext().all { (left, right) -> left < right } &&
                (previous == null || page.firstOrNull()?.let { it > previous } != false)
            ) {
                "Invalid staking storage page sequence"
            }
            require(result.size + page.size <= MAX_EXPOSURE_KEYS) { "Staking history exceeds supported scan size" }
            result += page
            if (page.size < KEY_PAGE_SIZE) return result
        }
    }

    companion object {
        private const val PAGE_BATCH = 128
        private const val KEY_PAGE_SIZE = 1000
        private const val MAX_EXPOSURE_KEYS = 250_000

        suspend fun capture(registry: ChainRegistry, chainId: String): AssetHubStorageSnapshot {
            val (provider, connection) = registry.getService(chainId)
            val runtime = provider.get()
            val socket = connection.socketService
            val block = socket.executeAsync(RuntimeRequest("chain_getFinalizedHead", emptyList()), mapper = string().nonNull())
            return AssetHubStorageSnapshot(runtime, socket, block)
        }
    }
}
