package jp.co.soramitsu.feature_staking_api.domain.api

import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.feature_staking_api.domain.model.ElectionStatus
import jp.co.soramitsu.feature_staking_api.domain.model.Exposure
import jp.co.soramitsu.feature_staking_api.domain.model.StakingState
import jp.co.soramitsu.feature_staking_api.domain.model.ValidatorPrefs
import kotlinx.coroutines.flow.Flow
import java.math.BigInteger

interface StakingRepository {

    suspend fun electionStatusFlow(networkType: Node.NetworkType): Flow<ElectionStatus>

    suspend fun getLockupPeriodInDays(networkType: Node.NetworkType): Int

    suspend fun getTotalIssuance(): BigInteger

    suspend fun getActiveEraIndex(): BigInteger

    suspend fun observeActiveEraIndex(networkType: Node.NetworkType): Flow<BigInteger>

    suspend fun getElectedValidatorsExposure(eraIndex: BigInteger): AccountIdMap<Exposure>

    suspend fun getElectedValidatorsPrefs(eraIndex: BigInteger): AccountIdMap<ValidatorPrefs>

    suspend fun getSlashes(accountIdsHex: List<String>): AccountIdMap<Boolean>

    fun stakingStateFlow(accountAddress: String): Flow<StakingState>
}
