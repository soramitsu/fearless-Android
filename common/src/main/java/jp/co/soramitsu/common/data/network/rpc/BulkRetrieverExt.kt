package jp.co.soramitsu.common.data.network.rpc

suspend fun BulkRetriever.retrieveAllValues(keyPrefix: String): Map<String, String?> {
    val allKeys = retrieveAllKeys(keyPrefix)

    return queryKeys(allKeys)
}