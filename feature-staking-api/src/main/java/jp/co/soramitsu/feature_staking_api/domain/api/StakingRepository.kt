package jp.co.soramitsu.feature_staking_api.domain.api

import jp.co.soramitsu.common.domain.model.StoryGroup
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.feature_staking_api.domain.model.CandidateInfo
import jp.co.soramitsu.feature_staking_api.domain.model.DelegatorState
import jp.co.soramitsu.feature_staking_api.domain.model.EraIndex
import jp.co.soramitsu.feature_staking_api.domain.model.Exposure
import jp.co.soramitsu.feature_staking_api.domain.model.RewardDestination
import jp.co.soramitsu.feature_staking_api.domain.model.SlashingSpans
import jp.co.soramitsu.feature_staking_api.domain.model.StakingLedger
import jp.co.soramitsu.feature_staking_api.domain.model.StakingState
import jp.co.soramitsu.feature_staking_api.domain.model.ValidatorPrefs
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.math.BigInteger
import kotlin.math.floor
import kotlin.math.max
import kotlin.time.DurationUnit
import kotlin.time.toDuration

interface StakingRepository {

    suspend fun currentSessionIndex(chainId: ChainId): BigInteger

    suspend fun currentSlot(chainId: ChainId): BigInteger

    suspend fun genesisSlot(chainId: ChainId): BigInteger

    suspend fun eraStartSessionIndex(chainId: ChainId, currentEra: BigInteger): EraIndex

    suspend fun sessionLength(chainId: ChainId): BigInteger

    suspend fun eraLength(chainId: ChainId): BigInteger

    suspend fun blockCreationTime(chainId: ChainId): BigInteger

    fun stakingAvailableFlow(chainId: ChainId): Flow<Boolean>

    suspend fun getTotalIssuance(chainId: ChainId): BigInteger

    suspend fun getActiveEraIndex(chainId: ChainId): EraIndex

    suspend fun getCurrentEraIndex(chainId: ChainId): EraIndex

    suspend fun getHistoryDepth(chainId: ChainId): BigInteger

    fun observeActiveEraIndex(chainId: ChainId): Flow<EraIndex>

    suspend fun getElectedValidatorsExposure(chainId: ChainId, eraIndex: EraIndex): AccountIdMap<Exposure>

    suspend fun getValidatorPrefs(chainId: ChainId, accountIdsHex: List<String>): AccountIdMap<ValidatorPrefs?>

    suspend fun getSlashes(chainId: ChainId, accountIdsHex: List<String>): AccountIdMap<Boolean>

    suspend fun getDelegatorStates(chainId: ChainId, addresses20: List<ByteArray>): AccountIdMap<DelegatorState?>

    suspend fun getCandidateInfos(chainId: ChainId, addresses20: List<ByteArray>): AccountIdMap<CandidateInfo?>

    suspend fun getSlashingSpan(chainId: ChainId, accountId: AccountId): SlashingSpans?

    suspend fun stakingStateFlow(
        chain: Chain,
        chainAsset: Chain.Asset,
        accountId: AccountId
    ): Flow<StakingState>

    fun stakingStoriesFlow(): Flow<List<StoryGroup.Staking>>

    suspend fun ledgerFlow(stakingState: StakingState.Stash): Flow<StakingLedger>

    suspend fun ledger(chainId: ChainId, address: String): StakingLedger?

    suspend fun getRewardDestination(stakingState: StakingState.Stash): RewardDestination

    suspend fun minimumNominatorBond(chainId: ChainId): BigInteger

    suspend fun maxNominators(chainId: ChainId): BigInteger?

    suspend fun nominatorsCount(chainId: ChainId): BigInteger?

    fun electedExposuresInActiveEra(chainId: ChainId): Flow<Map<String, Exposure>>

    suspend fun getDelegatorState(chainId: ChainId, accountId: AccountId): Flow<DelegatorState?>
    suspend fun observeParachainState(
        chain: Chain,
        accountId: AccountId,
    ): Flow<StakingState.Parachain>

    fun observeRelayChainState(chain: Chain, chainAsset: Chain.Asset, accountId: AccountId): Flow<StakingState>
}

suspend fun StakingRepository.getActiveElectedValidatorsExposures(chainId: ChainId) = electedExposuresInActiveEra(chainId).first()

suspend fun StakingRepository.historicalEras(chainId: ChainId): List<BigInteger> {
    val activeEra = getActiveEraIndex(chainId).toInt()
    val currentEra = getCurrentEraIndex(chainId).toInt()
    val historyDepth = getHistoryDepth(chainId).toInt()

    val historicalRange = max((currentEra - historyDepth), 0) until activeEra

    return historicalRange.map(Int::toBigInteger)
}

suspend fun StakingRepository.erasPerDay(chainId: ChainId): Int {
    val blockCreationTime = blockCreationTime(chainId)
    val sessionPerEra = eraLength(chainId)
    val blocksPerSession = sessionLength(chainId)

    val eraDuration = (blockCreationTime * sessionPerEra * blocksPerSession).toDouble()

    val dayDuration = 1.toDuration(DurationUnit.DAYS).toDouble(DurationUnit.MILLISECONDS)

    return floor(dayDuration / eraDuration).toInt()
}
