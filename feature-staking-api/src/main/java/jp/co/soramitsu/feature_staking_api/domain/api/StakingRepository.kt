package jp.co.soramitsu.feature_staking_api.domain.api

import jp.co.soramitsu.common.data.network.runtime.binding.AccountInfo
import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.feature_staking_api.domain.model.Exposure
import jp.co.soramitsu.feature_staking_api.domain.model.RewardDestination
import jp.co.soramitsu.feature_staking_api.domain.model.SlashingSpans
import jp.co.soramitsu.feature_staking_api.domain.model.StakingLedger
import jp.co.soramitsu.feature_staking_api.domain.model.StakingState
import jp.co.soramitsu.feature_staking_api.domain.model.StakingStory
import jp.co.soramitsu.feature_staking_api.domain.model.ValidatorPrefs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.math.BigInteger

interface StakingRepository {

    fun stakingAvailableFlow(): Flow<Boolean>

    suspend fun eraLeftTime(destinationEra: BigInteger) : BigInteger

    suspend fun getTotalIssuance(): BigInteger

    suspend fun getActiveEraIndex(): BigInteger

    suspend fun getCurrentEraIndex(): BigInteger

    suspend fun getHistoryDepth(): BigInteger

    suspend fun observeActiveEraIndex(networkType: Node.NetworkType): Flow<BigInteger>

    suspend fun getElectedValidatorsExposure(eraIndex: BigInteger): AccountIdMap<Exposure>

    suspend fun getValidatorPrefs(accountIdsHex: List<String>): AccountIdMap<ValidatorPrefs?>

    suspend fun getSlashes(accountIdsHex: List<String>): AccountIdMap<Boolean>

    suspend fun getSlashingSpan(accountId: AccountId): SlashingSpans?

    fun stakingStateFlow(accountAddress: String): Flow<StakingState>

    fun stakingStoriesFlow(): Flow<List<StakingStory>>

    suspend fun ledgerFlow(stakingState: StakingState.Stash): Flow<StakingLedger>

    suspend fun ledger(address: String): StakingLedger?

    suspend fun getRewardDestination(stakingState: StakingState.Stash): RewardDestination

    suspend fun getControllerAccountInfo(stakingState: StakingState.Stash): AccountInfo

    suspend fun minimumNominatorBond(): BigInteger

    suspend fun maxNominators(): BigInteger?

    suspend fun nominatorsCount(): BigInteger?

    val electedExposuresInActiveEra: Flow<Map<String, Exposure>>
}

suspend fun StakingRepository.getActiveElectedValidatorsExposures() = electedExposuresInActiveEra.first()

suspend fun StakingRepository.historicalEras(): List<BigInteger> {
    val activeEra = getActiveEraIndex().toInt()
    val currentEra = getCurrentEraIndex().toInt()
    val historyDepth = getHistoryDepth().toInt()

    val historicalRange = (currentEra - historyDepth) until activeEra

    return historicalRange.map(Int::toBigInteger)
}
