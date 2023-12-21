package jp.co.soramitsu.nft.impl.data.remote

import jp.co.soramitsu.nft.impl.data.model.AlchemyNftCollectionResponse
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
        @Query("pageSize") pageSize: Int = 100,
        @Query("excludeFilters[]") excludeFilters: List<String> = listOf("SPAM")
    ): AlchemyNftResponse

    @GET
    suspend fun getNFTCollectionByCollectionSlug(
        @Url requestUrl: String,
        @Query("collectionSlug") collectionSlug: String,
        @Query("withMetadata") withMetadata: Boolean,
        @Query("startToken") startTokenId: String,
        @Query("limit") limit: Int
    ): AlchemyNftCollectionResponse

    @GET
    suspend fun getNFTCollectionByContactAddress(
        @Url requestUrl: String,
        @Query("contractAddress") contractAddress: String,
        @Query("withMetadata") withMetadata: Boolean,
        @Query("startToken") startTokenId: String,
        @Query("limit") limit: Int
    ): AlchemyNftCollectionResponse
}