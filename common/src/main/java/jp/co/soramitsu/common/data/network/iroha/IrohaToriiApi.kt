package jp.co.soramitsu.common.data.network.iroha

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Url

interface IrohaToriiApi {
    @GET
    suspend fun health(@Url url: String): String

    @GET
    suspend fun accounts(@Url url: String): Response<IrohaAccountListResponse>

    @GET
    suspend fun account(@Url url: String): Response<IrohaAccountListItem>

    @GET
    suspend fun accountAssets(@Url url: String): Response<IrohaAccountAssetListResponse>

    @GET
    suspend fun assetDefinitions(@Url url: String): Response<IrohaAssetDefinitionListResponse>

    @GET
    suspend fun transactionStatus(@Url url: String): Response<IrohaPipelineTransactionStatusResponse>

    @GET
    suspend fun mcpCapabilities(@Url url: String): Response<Map<String, Any?>>

    @POST
    suspend fun mcpJsonRpc(
        @Url url: String,
        @Body body: IrohaMcpJsonRpcRequest
    ): Response<IrohaMcpJsonRpcResponse>
}
