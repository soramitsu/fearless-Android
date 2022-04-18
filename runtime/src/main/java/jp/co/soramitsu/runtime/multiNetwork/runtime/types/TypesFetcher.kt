package jp.co.soramitsu.runtime.multiNetwork.runtime.types

import jp.co.soramitsu.commonnetworking.networkclient.SoraNetworkClient

private const val DEFAULT_TYPES_URL = "https://raw.githubusercontent.com/polkascan/py-scale-codec/master/scalecodec/type_registry/default.json"

class TypesFetcher(
    private val networkClient: SoraNetworkClient,
) {

    suspend fun getTypes(url: String): String {
        return networkClient.get(url)
    }

    suspend fun getBaseTypes() = getTypes(DEFAULT_TYPES_URL)
}
