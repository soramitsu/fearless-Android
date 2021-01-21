package jp.co.soramitsu.feature_wallet_impl.data.network.subscan

import jp.co.soramitsu.feature_wallet_impl.data.network.model.request.AssetPriceRequest
import jp.co.soramitsu.feature_wallet_impl.data.network.model.request.TransactionHistoryRequest
import jp.co.soramitsu.feature_wallet_impl.data.network.model.response.AssetPriceStatistics
import jp.co.soramitsu.feature_wallet_impl.data.network.model.response.SubscanResponse
import jp.co.soramitsu.feature_wallet_impl.data.network.model.response.TransactionHistory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

class SubscanError(message: String) : Throwable(message)

interface SubscanNetworkApi {

    @POST("//{subDomain}.subscan.io/api/open/price")
    suspend fun getAssetPrice(
        @Path("subDomain") subDomain: String,
        @Body body: AssetPriceRequest
    ): SubscanResponse<AssetPriceStatistics>

    @POST("//{subDomain}.subscan.io/api/scan/transfers")
    suspend fun getTransactionHistory(
        @Path("subDomain") subDomain: String,
        @Body body: TransactionHistoryRequest
    ): SubscanResponse<TransactionHistory>
}