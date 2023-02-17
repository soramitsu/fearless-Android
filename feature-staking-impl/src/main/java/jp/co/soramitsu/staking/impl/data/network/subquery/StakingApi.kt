package jp.co.soramitsu.staking.impl.data.network.subquery

import jp.co.soramitsu.common.data.network.subquery.EraValidatorInfoQueryResponse
import jp.co.soramitsu.common.data.network.subquery.StakingCollatorsApyResponse
import jp.co.soramitsu.common.data.network.subquery.StakingHistoryRemote
import jp.co.soramitsu.common.data.network.subquery.StakingLastRoundId
import jp.co.soramitsu.common.data.network.subquery.SubQueryResponse
import jp.co.soramitsu.common.data.network.subquery.SubsquidCollatorsApyResponse
import jp.co.soramitsu.common.data.network.subquery.SubsquidLastRoundId
import jp.co.soramitsu.common.data.network.subquery.SubsquidResponse
import jp.co.soramitsu.common.data.network.subquery.SubsquidRewardAmountResponse
import jp.co.soramitsu.common.data.network.subquery.SubsquidRewardResponse
import jp.co.soramitsu.common.data.network.subquery.TransactionHistoryRemote
import jp.co.soramitsu.staking.impl.data.network.subquery.request.StakingAllCollatorsApyRequest
import jp.co.soramitsu.staking.impl.data.network.subquery.request.StakingCollatorsApyRequest
import jp.co.soramitsu.staking.impl.data.network.subquery.request.StakingDelegatorHistoryRequest
import jp.co.soramitsu.staking.impl.data.network.subquery.request.StakingEraValidatorInfosRequest
import jp.co.soramitsu.staking.impl.data.network.subquery.request.StakingLastRoundIdRequest
import jp.co.soramitsu.staking.impl.data.network.subquery.request.StakingSumRewardRequest
import jp.co.soramitsu.staking.impl.data.network.subquery.request.SubsquidCollatorsApyRequest
import jp.co.soramitsu.staking.impl.data.network.subquery.request.SubsquidDelegatorHistoryRequest
import jp.co.soramitsu.staking.impl.data.network.subquery.request.SubsquidLastRoundIdRequest
import jp.co.soramitsu.staking.impl.data.network.subquery.request.SubsquidRewardAmountRequest
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Url

interface StakingApi {

    @POST
    suspend fun getSumReward(
        @Url url: String,
        @Body body: StakingSumRewardRequest
    ): SubQueryResponse<TransactionHistoryRemote>

    @POST
    suspend fun getValidatorsInfo(
        @Url url: String,
        @Body body: StakingEraValidatorInfosRequest
    ): SubQueryResponse<EraValidatorInfoQueryResponse>

    @POST
    suspend fun getDelegatorHistory(
        @Url url: String,
        @Body body: StakingDelegatorHistoryRequest
    ): SubQueryResponse<StakingHistoryRemote>

    @POST
    suspend fun getLastRoundId(
        @Url url: String,
        @Body body: StakingLastRoundIdRequest
    ): SubQueryResponse<StakingLastRoundId>

    @POST
    suspend fun getCollatorsApy(
        @Url url: String,
        @Body body: StakingCollatorsApyRequest
    ): SubQueryResponse<StakingCollatorsApyResponse>

    @POST
    suspend fun getAllCollatorsApy(
        @Url url: String,
        @Body body: StakingAllCollatorsApyRequest
    ): SubQueryResponse<StakingCollatorsApyResponse>

    @POST
    suspend fun getDelegatorHistory(
        @Url url: String,
        @Body body: SubsquidDelegatorHistoryRequest
    ): SubsquidResponse<SubsquidRewardResponse>

    @POST
    suspend fun getRewardAmounts(
        @Url url: String,
        @Body body: SubsquidRewardAmountRequest
    ): SubsquidResponse<SubsquidRewardAmountResponse>

    @POST
    suspend fun getCollatorsApy(
        @Url url: String,
        @Body body: SubsquidCollatorsApyRequest
    ): SubsquidResponse<SubsquidCollatorsApyResponse>

    @POST
    suspend fun getLastRoundId(
        @Url url: String,
        @Body body: SubsquidLastRoundIdRequest
    ): SubsquidResponse<SubsquidLastRoundId>
}
