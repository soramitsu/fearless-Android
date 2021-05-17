package jp.co.soramitsu.feature_staking_impl.data.network.subscan

import jp.co.soramitsu.common.data.network.subscan.SubscanResponse
import jp.co.soramitsu.feature_staking_impl.data.network.subscan.request.ExtrinsicHistoryRequest
import jp.co.soramitsu.feature_staking_impl.data.network.subscan.request.StakingRewardRequest
import jp.co.soramitsu.feature_staking_impl.data.network.subscan.response.ExtrinsicHistory
import jp.co.soramitsu.feature_staking_impl.data.network.subscan.response.StakingRewardHistory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

interface StakingApi {

    @POST("//{subDomain}.api.subscan.io/api/scan/extrinsics")
    suspend fun getExtrinsicHistory(
        @Path("subDomain") subDomain: String,
        @Body body: ExtrinsicHistoryRequest,
    ): SubscanResponse<ExtrinsicHistory>

    @POST("//{subDomain}.api.subscan.io/api/scan/account/reward_slash")
    suspend fun getRewardsHistory(
        @Path("subDomain") subDomain: String,
        @Body body: StakingRewardRequest,
    ): SubscanResponse<StakingRewardHistory>
}
