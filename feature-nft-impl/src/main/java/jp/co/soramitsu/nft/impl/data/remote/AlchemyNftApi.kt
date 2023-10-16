package jp.co.soramitsu.nft.impl.data.remote

import jp.co.soramitsu.nft.impl.data.model.AlchemyNftResponse
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url

interface AlchemyNftApi {
    @GET
    suspend fun getNfts(
        @Url url: String,
        @Query("owner") owner: String,
        @Query("withMetadata") withMetadata: Boolean = true,
        @Query("pageSize") pageSize: Int = 100
    ): AlchemyNftResponse
}