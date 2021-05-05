package jp.co.soramitsu.feature_staking_impl.data.repository

import jp.co.soramitsu.common.data.network.rpc.BulkRetriever
import jp.co.soramitsu.common.data.network.runtime.binding.BinderWithType
import jp.co.soramitsu.common.data.network.runtime.binding.returnType
import jp.co.soramitsu.common.utils.SuspendableProperty
import jp.co.soramitsu.common.utils.mapValuesNotNull
import jp.co.soramitsu.common.utils.staking
import jp.co.soramitsu.core.storage.StorageCache
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.metadata.StorageEntry
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.feature_staking_api.domain.api.StakingRepository
import jp.co.soramitsu.feature_staking_api.domain.api.historicalEras
import jp.co.soramitsu.feature_staking_api.domain.model.Exposure
import jp.co.soramitsu.feature_staking_api.domain.model.StakingLedger
import jp.co.soramitsu.feature_staking_api.domain.model.StakingState
import jp.co.soramitsu.feature_staking_api.domain.model.ValidatorPrefs
import jp.co.soramitsu.feature_staking_impl.data.model.Payout
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.EraRewardPoints
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.bindEraRewardPoints
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.bindExposure
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.bindStakingLedger
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.bindTotalValidatorEraReward
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.bindValidatorPrefs
import jp.co.soramitsu.feature_staking_impl.data.network.subscan.SubscanValidatorSetFetcher
import java.math.BigInteger

typealias HistoricalMapping<T> = Map<BigInteger, T> // EraIndex -> T

class ValidatorHistoricalStats(
    val validatorAddress: String,
    val ledger: StakingLedger,
    val infoHistory: HistoricalMapping<ValidatorEraStats?>,
) {

    class ValidatorEraStats(
        val prefs: ValidatorPrefs,
        val exposure: Exposure,
    )
}

private class RewardCalculationContext(
    val validatorEraStats: ValidatorHistoricalStats.ValidatorEraStats,
    val eraValidatorPointsDistribution: EraRewardPoints,
    val validatorAccountId: AccountId,
    val totalEraReward: BigInteger
)

class PayoutRepository(
    private val stakingRepository: StakingRepository,
    private val bulkRetriever: BulkRetriever,
    private val runtimeProperty: SuspendableProperty<RuntimeSnapshot>,
    private val validatorSetFetcher: SubscanValidatorSetFetcher,
    private val storageCache: StorageCache,
) {

    suspend fun calculateUnpaidPayouts(stakingState: StakingState.Stash): List<Payout> {
        return when(stakingState) {
            is StakingState.Stash.Nominator -> calculateUnpaidPayouts(
                retrieveValidatorAddresses = {
                    validatorSetFetcher.fetchAllValidators(stakingState.stashAddress)
                },
                calculatePayoutReward = {
                    calculateNominatorReward(stakingState.stashId, it)
                }
            )
            is StakingState.Stash.Validator -> calculateUnpaidPayouts(
                retrieveValidatorAddresses =  { listOf(stakingState.stashAddress) },
                calculatePayoutReward = ::calculateValidatorReward
            )
            else -> throw IllegalStateException("Cannot calculate payouts for ${stakingState::class.simpleName} state")
        }
    }

    private suspend fun calculateUnpaidPayouts(
        retrieveValidatorAddresses: suspend () -> List<String>,
        calculatePayoutReward: (RewardCalculationContext) -> BigInteger?
    ): List<Payout> {
        val runtime = runtimeProperty.get()

        val validatorAddresses = retrieveValidatorAddresses()

        val historicalRange = stakingRepository.historicalEras()
        val validatorStats = getValidatorHistoricalStats(runtime, historicalRange, validatorAddresses)
        val historicalRangeSet = historicalRange.toSet()

        val historicalTotalEraRewards = retrieveTotalEraReward(runtime, historicalRange)
        val historicalRewardDistribution = retrieveEraPointsDistribution(runtime, historicalRange)

        return validatorStats.map { stats ->
            val claimedRewardsHoles = historicalRangeSet - stats.ledger.claimedRewards.toSet()
            val validatorAddress = stats.validatorAddress

            claimedRewardsHoles.mapNotNull { holeEra ->
                val rewardCalculationContext = RewardCalculationContext(
                    validatorEraStats = stats.infoHistory[holeEra] ?: return@mapNotNull null,
                    validatorAccountId = validatorAddress.toAccountId(),
                    totalEraReward = historicalTotalEraRewards[holeEra]!!,
                    eraValidatorPointsDistribution = historicalRewardDistribution[holeEra]!!
                )

                val reward = calculatePayoutReward(rewardCalculationContext)

                reward?.let { Payout(validatorAddress, holeEra, it) }
            }
        }.flatten()
    }

    private fun calculateNominatorReward(
        nominatorAccountId: AccountId,
        rewardCalculationContext: RewardCalculationContext
    ): BigInteger? = with(rewardCalculationContext) {
        val nominatorIdHex = nominatorAccountId.toHexString()

        val nominatorStakeInEra = validatorEraStats.exposure.others.firstOrNull {
            it.who.toHexString() == nominatorIdHex
        }?.value?.toDouble() ?: return null

        val validatorTotalStake = validatorEraStats.exposure.total.toDouble()
        val validatorCommission = validatorEraStats.prefs.commission.toDouble()

        val validatorTotalReward = calculateValidatorTotalReward(totalEraReward, eraValidatorPointsDistribution, validatorAccountId) ?: return null

        val nominatorReward = validatorTotalReward * (1 - validatorCommission) * (nominatorStakeInEra / validatorTotalStake)

        return nominatorReward.toBigDecimal().toBigInteger()
    }

    private fun calculateValidatorReward(
        rewardCalculationContext: RewardCalculationContext
    ): BigInteger? = with(rewardCalculationContext) {
        val validatorTotalStake = validatorEraStats.exposure.total.toDouble()
        val validatorOwnStake = validatorEraStats.exposure.own.toDouble()
        val validatorCommission = validatorEraStats.prefs.commission.toDouble()

        val validatorTotalReward = calculateValidatorTotalReward(totalEraReward, eraValidatorPointsDistribution, validatorAccountId) ?: return null

        val validatorRewardFromCommission = validatorTotalReward * validatorCommission
        val validatorRewardFromStake = validatorTotalReward * (1 - validatorCommission) * (validatorOwnStake / validatorTotalStake)

        val validatorOwnReward = validatorRewardFromStake + validatorRewardFromCommission

        validatorOwnReward.toBigDecimal().toBigInteger()
    }

    private fun calculateValidatorTotalReward(
        totalEraReward: BigInteger,
        eraValidatorPointsDistribution: EraRewardPoints,
        validatorAccountId: AccountId,
    ): Double? {
        val validatorIdHex = validatorAccountId.toHexString()

        val totalMinted = totalEraReward.toDouble()

        val totalPoints = eraValidatorPointsDistribution.totalPoints.toDouble()
        val validatorPoints = eraValidatorPointsDistribution.individual.firstOrNull {
            it.accountId.toHexString() == validatorIdHex
        }?.rewardPoints?.toDouble() ?: return null

        return totalMinted * validatorPoints / totalPoints
    }

    private suspend fun getValidatorHistoricalStats(
        runtime: RuntimeSnapshot,
        historicalRange: List<BigInteger>,
        validatorAddresses: List<String>,
    ): List<ValidatorHistoricalStats> {

        val stakingModule = runtime.metadata.staking()

        val exposureClippedStorage = stakingModule.storage("ErasStakersClipped")
        val exposureKeyMapping = validatorAddresses.associateWith { validatorAddress ->
            historicalRange.associateWith { era ->
                exposureClippedStorage.storageKey(runtime, era, validatorAddress.toAccountId())
            }
        }

        val validatorPrefsStorage = stakingModule.storage("ErasValidatorPrefs")
        val prefsKeyMapping = validatorAddresses.associateWith { validatorAddress ->
            historicalRange.associateWith { era ->
                validatorPrefsStorage.storageKey(runtime, era, validatorAddress.toAccountId())
            }
        }

        val controllerStorage = stakingModule.storage("Bonded")
        val controllerMapping = validatorAddresses.associateWith { validatorAddress ->
            controllerStorage.storageKey(runtime, validatorAddress.toAccountId())
        }

        val exposureClippedKeys = exposureKeyMapping.values.map(Map<BigInteger, String>::values).flatten()
        val prefsKeys = prefsKeyMapping.values.map(Map<BigInteger, String>::values).flatten()
        val ledgerKeys = controllerMapping.values.toList()

        val allResults = bulkRetriever.queryKeys(exposureClippedKeys + prefsKeys + ledgerKeys)

        val ledgerStorage = stakingModule.storage("Ledger")
        val ledgerKeysMapping = controllerMapping.mapValuesNotNull { (_, key) ->
            allResults[key]?.fromHex()?.let { ledgerStorage.storageKey(runtime, it) }
        }

        val ledgerResults = bulkRetriever.queryKeys(ledgerKeysMapping.values.toList())

        return validatorAddresses.mapNotNull { validatorAddress ->
            val ledger = ledgerResults[ledgerKeysMapping[validatorAddress]]?.let { bindStakingLedger(it, runtime) } ?: return@mapNotNull null

            val historicalStats = historicalRange.associateWith { era ->
                val exposureKey = exposureKeyMapping[validatorAddress]!![era]!!
                val prefsKey = prefsKeyMapping[validatorAddress]!![era]!!

                val exposure = allResults[exposureKey]?.let { bindExposure(it, runtime) }
                val prefs = allResults[prefsKey]?.let { bindValidatorPrefs(it, runtime) }

                if (exposure != null && prefs != null) {
                    ValidatorHistoricalStats.ValidatorEraStats(prefs, exposure)
                } else {
                    null
                }
            }

            ValidatorHistoricalStats(validatorAddress, ledger, historicalStats)
        }
    }

    private suspend fun retrieveEraPointsDistribution(
        runtime: RuntimeSnapshot,
        historicalRange: List<BigInteger>,
    ): HistoricalMapping<EraRewardPoints> {
        val storage = runtime.metadata.staking().storage("ErasRewardPoints")

        return retrieveHistoricalInfo(runtime, historicalRange, storage, ::bindEraRewardPoints)
    }

    private suspend fun retrieveTotalEraReward(
        runtime: RuntimeSnapshot,
        historicalRange: List<BigInteger>,
    ): HistoricalMapping<BigInteger> {
        val storage = runtime.metadata.staking().storage("ErasValidatorReward")

        return retrieveHistoricalInfo(runtime, historicalRange, storage, ::bindTotalValidatorEraReward)
    }

    private suspend fun <T> retrieveHistoricalInfo(
        runtime: RuntimeSnapshot,
        historicalRange: List<BigInteger>,
        storage: StorageEntry,
        binder: BinderWithType<T>,
    ): HistoricalMapping<T> {
        val historicalKeysMapping = historicalRange.associateBy { storage.storageKey(runtime, it) }
        val storageReturnType = storage.returnType()

        return storageCache.getEntries(historicalKeysMapping.keys.toList())
            .associate {
                historicalKeysMapping[it.storageKey]!! to binder(it.content, runtime, storageReturnType)
            }
    }
}
