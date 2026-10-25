package jp.co.soramitsu.common.data.network.iroha

import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Url

interface IrohaToriiApi {
    @GET
    suspend fun health(@Url url: String): String

    @GET
    suspend fun accounts(@Url url: String): IrohaAccountListResponse

    @GET
    suspend fun account(@Url url: String): IrohaAccountListItem

    @GET
    suspend fun accountAssets(@Url url: String): IrohaAccountAssetListResponse

    @GET
    suspend fun assetDefinitions(@Url url: String): IrohaAssetDefinitionListResponse

    @GET
    suspend fun transactionStatus(@Url url: String): IrohaPipelineTransactionStatusResponse

    @Headers("Content-Type: application/x-norito", "Accept: application/json")
    @POST
    suspend fun submitTransaction(
        @Url url: String,
        @Body body: RequestBody
    ): IrohaTransactionSubmissionReceipt

    @GET
    suspend fun mcpCapabilities(@Url url: String): Map<String, Any?>

    @POST
    suspend fun mcpJsonRpc(
        @Url url: String,
        @Body body: IrohaMcpJsonRpcRequest
    ): IrohaMcpJsonRpcResponse
}
