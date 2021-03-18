package jp.co.soramitsu.feature_staking_impl.data.network.subscan

import jp.co.soramitsu.common.data.network.subscan.SubscanResponse
import jp.co.soramitsu.feature_staking_impl.data.network.subscan.request.StakingRewardRequest
import jp.co.soramitsu.feature_staking_impl.data.network.subscan.response.StakingRewardHistory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

interface StakingRewardsApi {

    @POST("//{subDomain}.subscan.io/api/scan/account/reward_slash")
    suspend fun getTransactionHistory(
        @Path("subDomain") subDomain: String,
        @Body body: StakingRewardRequest,
    ): SubscanResponse<StakingRewardHistory>
}
