package jp.co.soramitsu.feature_wallet_impl.data.network.subscan

import jp.co.soramitsu.common.data.network.subquery.SubQueryResponse
import jp.co.soramitsu.common.data.network.subquery.SumRewardResponse
import jp.co.soramitsu.common.data.network.subscan.SubscanResponse
import jp.co.soramitsu.feature_wallet_impl.BuildConfig
import jp.co.soramitsu.feature_wallet_impl.data.network.model.request.AssetPriceRequest
import jp.co.soramitsu.feature_wallet_impl.data.network.model.request.SubqueryHistoryElementByAddressRequest
import jp.co.soramitsu.feature_wallet_impl.data.network.model.request.TransactionHistoryRequest
import jp.co.soramitsu.feature_wallet_impl.data.network.model.response.AssetPriceStatistics
import jp.co.soramitsu.feature_wallet_impl.data.network.model.response.SubqueryHistoryElementResponse
import jp.co.soramitsu.feature_wallet_impl.data.network.model.response.TransactionHistory
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Path

class SubscanError(message: String) : Throwable(message)

interface SubscanNetworkApi {

    @Headers("x-api-key: ${BuildConfig.SUBSCAN_API_KEY}")
    @POST("//{subDomain}.api.subscan.io/api/open/price")
    suspend fun getAssetPrice(
        @Path("subDomain") subDomain: String,
        @Body body: AssetPriceRequest
    ): SubscanResponse<AssetPriceStatistics>

    @POST("http://10.0.2.2:3000/")
    suspend fun getSubscanHistory(
        @Body body: SubqueryHistoryElementByAddressRequest
    ): SubQueryResponse<SubqueryHistoryElementResponse>
}
