package jp.co.soramitsu.staking.impl.data.network.subquery

import jp.co.soramitsu.common.data.network.subquery.EraValidatorInfoQueryResponse
import jp.co.soramitsu.common.data.network.subquery.GiantsquidRewardAmountResponse
import jp.co.soramitsu.common.data.network.subquery.ReefStakingRewardsResponse
import jp.co.soramitsu.common.data.network.subquery.SoraEraInfoValidatorResponse
import jp.co.soramitsu.common.data.network.subquery.StakingCollatorsApyResponse
import jp.co.soramitsu.common.data.network.subquery.StakingHistoryRemote
import jp.co.soramitsu.common.data.network.subquery.StakingLastRoundId
import jp.co.soramitsu.common.data.network.subquery.SubQueryResponse
import jp.co.soramitsu.common.data.network.subquery.SubsquidCollatorsApyResponse
import jp.co.soramitsu.common.data.network.subquery.SubsquidEthRewardAmountResponse
import jp.co.soramitsu.common.data.network.subquery.SubsquidLastRoundId
import jp.co.soramitsu.common.data.network.subquery.SubsquidRelayRewardAmountResponse
import jp.co.soramitsu.common.data.network.subquery.SubsquidResponse
import jp.co.soramitsu.common.data.network.subquery.SubsquidRewardResponse
import jp.co.soramitsu.common.data.network.subquery.SubsquidSoraStakingRewards
import jp.co.soramitsu.common.data.network.subquery.TransactionHistoryRemote
import jp.co.soramitsu.staking.impl.data.network.subquery.request.GiantsquidRewardAmountRequest
import jp.co.soramitsu.staking.impl.data.network.subquery.request.ReefStakingRewardsRequest
import jp.co.soramitsu.staking.impl.data.network.subquery.request.StakingAllCollatorsApyRequest
import jp.co.soramitsu.staking.impl.data.network.subquery.request.StakingCollatorsApyRequest
import jp.co.soramitsu.staking.impl.data.network.subquery.request.StakingDelegatorHistoryRequest
import jp.co.soramitsu.staking.impl.data.network.subquery.request.StakingEraValidatorInfosRequest
import jp.co.soramitsu.staking.impl.data.network.subquery.request.StakingLastRoundIdRequest
import jp.co.soramitsu.staking.impl.data.network.subquery.request.StakingSoraEraValidatorsRequest
import jp.co.soramitsu.staking.impl.data.network.subquery.request.StakingSumRewardRequest
import jp.co.soramitsu.staking.impl.data.network.subquery.request.SubsquidCollatorsApyRequest
import jp.co.soramitsu.staking.impl.data.network.subquery.request.SubsquidDelegatorHistoryRequest
import jp.co.soramitsu.staking.impl.data.network.subquery.request.SubsquidEthRewardAmountRequest
import jp.co.soramitsu.staking.impl.data.network.subquery.request.SubsquidLastRoundIdRequest
import jp.co.soramitsu.staking.impl.data.network.subquery.request.SubsquidRelayRewardAmountRequest
import jp.co.soramitsu.staking.impl.data.network.subquery.request.SubsquidSoraStakingRewardsRequest
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
    suspend fun getSoraValidatorsInfo(
        @Url url: String,
        @Body body: StakingSoraEraValidatorsRequest
    ): SubsquidResponse<SoraEraInfoValidatorResponse>

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
    suspend fun getEthRewardAmounts(
        @Url url: String,
        @Body body: SubsquidEthRewardAmountRequest
    ): SubsquidResponse<SubsquidEthRewardAmountResponse>

    @POST
    suspend fun getRelayRewardAmounts(
        @Url url: String,
        @Body body: SubsquidRelayRewardAmountRequest
    ): SubsquidResponse<SubsquidRelayRewardAmountResponse>

    @POST
    suspend fun getRelayRewardAmounts(
        @Url url: String,
        @Body body: GiantsquidRewardAmountRequest
    ): SubsquidResponse<GiantsquidRewardAmountResponse>

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

    @POST
    suspend fun getSoraRewards(
        @Url url: String,
        @Body body: SubsquidSoraStakingRewardsRequest
    ): SubsquidResponse<SubsquidSoraStakingRewards>

    @POST
    suspend fun getReefRewards(
        @Url url: String,
        @Body body: ReefStakingRewardsRequest
    ): SubsquidResponse<ReefStakingRewardsResponse>
}
