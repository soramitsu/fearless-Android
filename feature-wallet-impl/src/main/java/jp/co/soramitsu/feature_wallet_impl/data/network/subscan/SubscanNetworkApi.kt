package jp.co.soramitsu.feature_wallet_impl.data.network.subscan

import io.reactivex.Single
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
    fun getAssetPrice(
        @Path("subDomain") subDomain: String,
        @Body body: AssetPriceRequest
    ): Single<SubscanResponse<AssetPriceStatistics>>

    @POST("//{subDomain}.subscan.io/api/scan/transfers")
    fun getTransactionHistory(
        @Path("subDomain") subDomain: String,
        @Body body: TransactionHistoryRequest
    ): Single<SubscanResponse<TransactionHistory>>
}