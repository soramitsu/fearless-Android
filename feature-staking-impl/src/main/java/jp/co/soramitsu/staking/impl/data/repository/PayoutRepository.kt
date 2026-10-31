package jp.co.soramitsu.staking.impl.data.repository

import java.math.BigInteger
import jp.co.soramitsu.common.data.network.rpc.BulkRetriever
import jp.co.soramitsu.common.data.network.runtime.binding.BinderWithType
import jp.co.soramitsu.common.utils.mapValuesNotNull
import jp.co.soramitsu.common.utils.staking
import jp.co.soramitsu.core.runtime.storage.returnType
import jp.co.soramitsu.core.storage.StorageCache
import jp.co.soramitsu.runtime.ext.accountIdOf
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.StorageEntry
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.staking.api.domain.model.Commission
import jp.co.soramitsu.staking.api.domain.model.LegacyExposure
import jp.co.soramitsu.staking.api.domain.model.StakingLedger
import jp.co.soramitsu.staking.api.domain.model.StakingState
import jp.co.soramitsu.staking.api.domain.model.ValidatorPrefs
import jp.co.soramitsu.staking.impl.data.model.Payout
import jp.co.soramitsu.staking.impl.data.network.blockhain.bindings.EraRewardPoints
import jp.co.soramitsu.staking.impl.data.network.blockhain.bindings.bindEraRewardPoints
import jp.co.soramitsu.staking.impl.data.network.blockhain.bindings.bindExposure
import jp.co.soramitsu.staking.impl.data.network.blockhain.bindings.bindLegacyExposure
import jp.co.soramitsu.staking.impl.data.network.blockhain.bindings.bindStakingLedger
import jp.co.soramitsu.staking.impl.data.network.blockhain.bindings.bindTotalValidatorEraReward
import jp.co.soramitsu.staking.impl.data.network.blockhain.bindings.bindValidatorPrefs
import jp.co.soramitsu.staking.impl.data.network.subquery.SubQueryValidatorSetFetcher
import jp.co.soramitsu.staking.impl.scenarios.relaychain.StakingRelayChainScenarioRepository
import jp.co.soramitsu.staking.impl.scenarios.relaychain.historicalEras

typealias HistoricalMapping<T> = Map<BigInteger, T> // EraIndex -> T

class ValidatorHistoricalStats(
    val validatorAddress: String,
    val ledger: StakingLedger,
    val infoHistory: HistoricalMapping<ValidatorEraStats?>
) {

    class ValidatorEraStats(
        val prefs: ValidatorPrefs,
        val exposure: LegacyExposure
    )
}

private class RewardCalculationContext(
    val validatorEraStats: ValidatorHistoricalStats.ValidatorEraStats,
    val eraValidatorPointsDistribution: EraRewardPoints,
    val validatorAccountId: AccountId,
    val totalEraReward: BigInteger
)

class PayoutRepository(
    private val stakingRepository: StakingRelayChainScenarioRepository,
    private val bulkRetriever: BulkRetriever,
    private val validatorSetFetcher: SubQueryValidatorSetFetcher,
    private val storageCache: StorageCache,
    private val chainRegistry: ChainRegistry
) {

    suspend fun calculateUnpaidPayouts(stakingState: StakingState.Stash): List<Payout> {
        return when (stakingState) {
            is StakingState.Stash.Nominator -> calculateUnpaidPayouts(
                chain = stakingState.chain,
                retrieveValidatorAddresses = {
                    validatorSetFetcher.fetchAllValidators(
                        stakingState.chain.id,
                        stakingState.stashAddress
                    )
                },
                calculatePayoutReward = {
                    calculateNominatorReward(stakingState.stashId, it)
                }
            )

            is StakingState.Stash.Validator -> calculateUnpaidPayouts(
                chain = stakingState.chain,
                retrieveValidatorAddresses = { listOf(stakingState.stashAddress) },
                calculatePayoutReward = ::calculateValidatorReward
            )

            else -> throw IllegalStateException("Cannot calculate payouts for ${stakingState::class.simpleName} state")
        }
    }

    private suspend fun calculateUnpaidPayouts(
        chain: Chain,
        retrieveValidatorAddresses: suspend () -> List<String>,
        calculatePayoutReward: (RewardCalculationContext) -> BigInteger?
    ): List<Payout> {
        val chainId = chain.id

        val (runtimeProvider, connection) = chainRegistry.getService(chainId)
        val runtime = runtimeProvider.get()

        val validatorAddresses = retrieveValidatorAddresses()

        val historicalRange = stakingRepository.historicalEras(chainId)
        val validatorStats = getValidatorHistoricalStats(
            runtime,
            connection.socketService,
            historicalRange,
            validatorAddresses
        )
        val historicalRangeSet = historicalRange.toSet()

        val historicalTotalEraRewards = retrieveTotalEraReward(chainId, runtime, historicalRange)
        val historicalRewardDistribution =
            retrieveEraPointsDistribution(chainId, runtime, historicalRange)

        return validatorStats.map { stats ->
            val claimedRewardsHoles = historicalRangeSet - stats.ledger.claimedRewards.toSet()
            val validatorAddress = stats.validatorAddress

            claimedRewardsHoles.mapNotNull { holeEra ->
                val rewardCalculationContext = RewardCalculationContext(
                    validatorEraStats = stats.infoHistory[holeEra] ?: return@mapNotNull null,
                    validatorAccountId = chain.accountIdOf(validatorAddress),
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
        }?.value ?: return null

        val validatorTotalReward = calculateValidatorTotalReward(
            totalEraReward,
            eraValidatorPointsDistribution,
            validatorAccountId
        ) ?: return null

        val validatorCommission = validatorEraStats.prefs.commission.toPerbillParts()
        val validatorLeftoverReward = validatorTotalReward - perbillMultiply(validatorCommission, validatorTotalReward)
        val nominatorShare = perbillFromRational(nominatorStakeInEra, validatorEraStats.exposure.total)

        return perbillMultiply(nominatorShare, validatorLeftoverReward)
    }

    private fun calculateValidatorReward(
        rewardCalculationContext: RewardCalculationContext
    ): BigInteger? = with(rewardCalculationContext) {
        val validatorTotalReward = calculateValidatorTotalReward(
            totalEraReward,
            eraValidatorPointsDistribution,
            validatorAccountId
        ) ?: return null

        val validatorCommission = validatorEraStats.prefs.commission.toPerbillParts()
        val validatorRewardFromCommission = perbillMultiply(validatorCommission, validatorTotalReward)
        val validatorLeftoverReward = validatorTotalReward - validatorRewardFromCommission
        val validatorOwnShare = perbillFromRational(validatorEraStats.exposure.own, validatorEraStats.exposure.total)
        val validatorRewardFromStake = perbillMultiply(validatorOwnShare, validatorLeftoverReward)

        validatorRewardFromStake + validatorRewardFromCommission
    }

    private fun calculateValidatorTotalReward(
        totalEraReward: BigInteger,
        eraValidatorPointsDistribution: EraRewardPoints,
        validatorAccountId: AccountId
    ): BigInteger? {
        val validatorIdHex = validatorAccountId.toHexString()

        val validatorPoints = eraValidatorPointsDistribution.individual.firstOrNull {
            it.accountId.toHexString() == validatorIdHex
        }?.rewardPoints ?: return null

        // The runtime pays nothing for an era in which the validator earned no points.
        if (validatorPoints.signum() == 0) return BigInteger.ZERO

        val validatorShare = perbillFromRational(validatorPoints, eraValidatorPointsDistribution.totalPoints)

        return perbillMultiply(validatorShare, totalEraReward)
    }

    private suspend fun getValidatorHistoricalStats(
        runtime: RuntimeSnapshot,
        socketService: SocketService,
        historicalRange: List<BigInteger>,
        validatorAddresses: List<String>
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

        val exposureClippedKeys =
            exposureKeyMapping.values.map(Map<BigInteger, String>::values).flatten()
        val prefsKeys = prefsKeyMapping.values.map(Map<BigInteger, String>::values).flatten()
        val ledgerKeys = controllerMapping.values.toList()

        val allResults =
            bulkRetriever.queryKeys(socketService, exposureClippedKeys + prefsKeys + ledgerKeys)

        val ledgerStorage = stakingModule.storage("Ledger")
        val ledgerKeysMapping = controllerMapping.mapValuesNotNull { (_, key) ->
            allResults[key]?.fromHex()?.let { ledgerStorage.storageKey(runtime, it) }
        }

        val ledgerResults =
            bulkRetriever.queryKeys(socketService, ledgerKeysMapping.values.toList())

        return validatorAddresses.mapNotNull { validatorAddress ->
            val ledger = ledgerResults[ledgerKeysMapping[validatorAddress]]?.let {
                bindStakingLedger(
                    it,
                    runtime
                )
            } ?: return@mapNotNull null

            val historicalStats = historicalRange.associateWith { era ->
                val exposureKey = exposureKeyMapping[validatorAddress]!![era]!!
                val prefsKey = prefsKeyMapping[validatorAddress]!![era]!!

                val exposure = allResults[exposureKey]?.let {
                    bindLegacyExposure(
                        it,
                        runtime,
                        exposureClippedStorage.returnType()
                    )
                }
                val prefs = allResults[prefsKey]?.let {
                    bindValidatorPrefs(
                        it,
                        runtime,
                        validatorPrefsStorage.returnType()
                    )
                }

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
        chainId: ChainId,
        runtime: RuntimeSnapshot,
        historicalRange: List<BigInteger>
    ): HistoricalMapping<EraRewardPoints> {
        val storage = runtime.metadata.staking().storage("ErasRewardPoints")

        return retrieveHistoricalInfo(
            chainId,
            runtime,
            historicalRange,
            storage,
            ::bindEraRewardPoints
        )
    }

    private suspend fun retrieveTotalEraReward(
        chainId: ChainId,
        runtime: RuntimeSnapshot,
        historicalRange: List<BigInteger>
    ): HistoricalMapping<BigInteger> {
        val storage = runtime.metadata.staking().storage("ErasValidatorReward")

        return retrieveHistoricalInfo(
            chainId,
            runtime,
            historicalRange,
            storage,
            ::bindTotalValidatorEraReward
        )
    }

    private suspend fun <T> retrieveHistoricalInfo(
        chainId: ChainId,
        runtime: RuntimeSnapshot,
        historicalRange: List<BigInteger>,
        storage: StorageEntry,
        binder: BinderWithType<T>
    ): HistoricalMapping<T> {
        val historicalKeysMapping = historicalRange.associateBy { storage.storageKey(runtime, it) }
        val storageReturnType = storage.returnType()

        return storageCache.getEntries(historicalKeysMapping.keys.toList(), chainId)
            .associate {
                historicalKeysMapping[it.storageKey]!! to binder(
                    it.content,
                    runtime,
                    storageReturnType
                )
            }
    }
}

private const val PERBILL_DECIMALS = 9
private val PERBILL_ACCURACY: BigInteger = BigInteger.TEN.pow(PERBILL_DECIMALS)

/**
 * `Perbill::from_rational`, which the staking pallet uses for every reward share: parts per billion,
 * rounded down. Like the runtime (`unwrap_or(Perbill::one())`), a zero denominator or a share above one is 100%.
 */
private fun perbillFromRational(numerator: BigInteger, denominator: BigInteger): BigInteger =
    if (denominator.signum() <= 0 || numerator > denominator) {
        PERBILL_ACCURACY
    } else {
        numerator * PERBILL_ACCURACY / denominator
    }

/** `Perbill * Balance`: [amount] times [parts] per billion, to the nearest plank with an exact half rounded down. */
private fun perbillMultiply(parts: BigInteger, amount: BigInteger): BigInteger {
    val (whole, remainder) = (amount * parts).divideAndRemainder(PERBILL_ACCURACY)
    return if (remainder.shiftLeft(1) > PERBILL_ACCURACY) whole + BigInteger.ONE else whole
}

/** The on-chain commission is a Perbill, which cannot exceed 100% (`Perbill::from_parts` saturates). */
private fun Commission.toPerbillParts(): BigInteger =
    movePointRight(PERBILL_DECIMALS).toBigInteger().coerceIn(BigInteger.ZERO, PERBILL_ACCURACY)
