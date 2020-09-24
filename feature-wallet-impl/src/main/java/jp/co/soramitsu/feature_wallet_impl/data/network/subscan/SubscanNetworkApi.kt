package jp.co.soramitsu.feature_wallet_impl.data.network.subscan

import io.reactivex.Single
import jp.co.soramitsu.feature_wallet_impl.data.network.model.request.AssetPriceRequest
import jp.co.soramitsu.feature_wallet_impl.data.network.model.response.AssetPriceStatistics
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

interface SubscanNetworkApi {

    @POST("//{subDomain}.subscan.io/api/open/price")
    fun getAssetPrice(
        @Path("subDomain") subDomain: String,
        @Body body: AssetPriceRequest
    ): Single<AssetPriceStatistics>
}