package jp.co.soramitsu.feature_staking_impl.data.network.subscan

import jp.co.soramitsu.common.data.network.subquery.SubQueryResponse
import jp.co.soramitsu.common.data.network.subquery.SumRewardResponse
import jp.co.soramitsu.common.data.network.subscan.SubscanResponse
import jp.co.soramitsu.feature_staking_impl.BuildConfig
import jp.co.soramitsu.feature_staking_impl.data.network.subscan.request.ExtrinsicHistoryRequest
import jp.co.soramitsu.feature_staking_impl.data.network.subscan.request.StakingRewardRequest
import jp.co.soramitsu.feature_staking_impl.data.network.subscan.request.StakingSumRewardRequest
import jp.co.soramitsu.feature_staking_impl.data.network.subscan.response.ExtrinsicHistory
import jp.co.soramitsu.feature_staking_impl.data.network.subscan.response.StakingRewardHistory
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Path

interface StakingApi {

    @Headers("x-api-key: ${BuildConfig.SUBSCAN_API_KEY}")
    @POST("//{subDomain}.api.subscan.io/api/scan/extrinsics")
    suspend fun getExtrinsicHistory(
        @Path("subDomain") subDomain: String,
        @Body body: ExtrinsicHistoryRequest,
    ): SubscanResponse<ExtrinsicHistory>

    @Headers("x-api-key: ${BuildConfig.SUBSCAN_API_KEY}")
    @POST("//{subDomain}.api.subscan.io/api/scan/account/reward_slash")
    suspend fun getRewardsHistory(
        @Path("subDomain") subDomain: String,
        @Body body: StakingRewardRequest,
    ): SubscanResponse<StakingRewardHistory>

    @POST("//api.subquery.network/sq/OnFinality-io/{path}")
    suspend fun getSumReward(
        @Path("path") path: String,
        @Body body: StakingSumRewardRequest
    ): SubQueryResponse<SumRewardResponse>
}
