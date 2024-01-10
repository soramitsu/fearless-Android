package jp.co.soramitsu.nft.impl.data.remote

import jp.co.soramitsu.nft.data.models.TokenInfo
import jp.co.soramitsu.nft.impl.data.model.request.NFTRequest
import jp.co.soramitsu.nft.data.models.response.NFTResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Url

interface AlchemyNftApi {
    @GET
    suspend fun getUserOwnedNFTs(
        @Url url: String,
        @Query("owner") owner: String,
        @Query("withMetadata") withMetadata: Boolean = true,
        @Query("pageKey") pageKey: String? = null,
        @Query("pageSize") pageSize: Int = 1000,
        @Query("excludeFilters[]") excludeFilters: List<String> = listOf("SPAM")
    ): NFTResponse.UserOwnedTokens

    @GET
    suspend fun getNFTCollectionByCollectionSlug(
        @Url requestUrl: String,
        @Query("collectionSlug") collectionSlug: String,
        @Query("withMetadata") withMetadata: Boolean,
        @Query("startToken") startTokenId: String,
        @Query("limit") limit: Int
    ): NFTResponse.TokensCollection

    @GET
    suspend fun getNFTCollectionByContactAddress(
        @Url requestUrl: String,
        @Query("contractAddress") contractAddress: String,
        @Query("withMetadata") withMetadata: Boolean,
        @Query("startToken") startTokenId: String,
        @Query("limit") limit: Int
    ): NFTResponse.TokensCollection

    @POST
    suspend fun getNFTContractMetadataBatch(
        @Url requestUrl: String,
        @Body body: NFTRequest.ContractMetadata.Body
    ): List<NFTResponse.ContractMetadata>

    @GET
    suspend fun getNFTMetadata(
        @Url requestUrl: String,
        @Query("contractAddress") contractAddress: String,
        @Query("tokenId") tokenId: String
    ): TokenInfo.WithMetadata
}