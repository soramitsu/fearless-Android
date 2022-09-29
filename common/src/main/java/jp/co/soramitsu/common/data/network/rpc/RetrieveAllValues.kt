package jp.co.soramitsu.common.data.network.rpc

import jp.co.soramitsu.fearless_utils.wsrpc.SocketService

suspend fun BulkRetriever.retrieveAllValues(socketService: SocketService, keyPrefix: String): Map<String, String?> {
    val allKeys = retrieveAllKeys(socketService, keyPrefix)

    return queryKeys(socketService, allKeys)
}
