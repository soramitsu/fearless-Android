package jp.co.soramitsu.runtime.multiNetwork.runtime.types

import retrofit2.http.GET
import retrofit2.http.Url

interface TypesFetcher {
    @GET
    suspend fun getTypes(@Url url: String): String
}
