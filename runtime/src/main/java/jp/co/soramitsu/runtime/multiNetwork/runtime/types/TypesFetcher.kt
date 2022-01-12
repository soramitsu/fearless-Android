package jp.co.soramitsu.runtime.multiNetwork.runtime.types

import retrofit2.http.GET
import retrofit2.http.Url

private const val DEFAULT_TYPES_URL = "https://raw.githubusercontent.com/polkascan/py-scale-codec/master/scalecodec/type_registry/default.json"

interface TypesFetcher {

    @GET
    suspend fun getTypes(@Url url: String): String
}

suspend fun TypesFetcher.getBaseTypes() = getTypes(DEFAULT_TYPES_URL)
