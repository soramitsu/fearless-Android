package jp.co.soramitsu.feature_staking_impl.data.repository

import jp.co.soramitsu.common.data.network.rpc.BulkRetriever
import jp.co.soramitsu.common.utils.SuspendableProperty
import jp.co.soramitsu.common.utils.mapValuesNotNull
import jp.co.soramitsu.common.utils.networkType
import jp.co.soramitsu.common.utils.staking
import jp.co.soramitsu.common.utils.toAddress
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.feature_staking_api.domain.api.StakingRepository
import jp.co.soramitsu.feature_staking_api.domain.api.historicalEras
import jp.co.soramitsu.feature_staking_api.domain.model.Exposure
import jp.co.soramitsu.feature_staking_api.domain.model.ValidatorPrefs
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.StakingLedger
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.bindExposure
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.bindStakingLedger
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.bindValidatorPrefs
import jp.co.soramitsu.feature_staking_impl.data.network.subscan.SubscanValidatorSetFetcher
import java.math.BigInteger

class ValidatorHistoricalStats(
    val validatorAddress: String,
    val ledger: StakingLedger,
    val infoHistory: Map<BigInteger, ValidatorEraStats?>,
) {

    class ValidatorEraStats(
        val prefs: ValidatorPrefs,
        val exposure: Exposure,
    )
}

class PayoutRepository(
    private val stakingRepository: StakingRepository,
    private val bulkRetriever: BulkRetriever,
    private val runtimeProperty: SuspendableProperty<RuntimeSnapshot>,
    private val validatorSetFetcher: SubscanValidatorSetFetcher,
) {

    suspend fun calculateUnpaidPayouts(stashAddress: String): List<Pair<BigInteger, String>> {
        val networkType = stashAddress.networkType()

        val validatorAddresses = validatorSetFetcher.fetchAllValidators(stashAddress)

        val historicalRange = stakingRepository.historicalEras()
        val validatorStats = getValidatorHistoricalStats(historicalRange, validatorAddresses)
        val historicalRangeSet = historicalRange.toSet()

        return validatorStats.map { stats ->
            val claimedRewardsHoles = historicalRangeSet - stats.ledger.claimedRewards.toSet()

            val rewardHoles = claimedRewardsHoles.filter { holeEra ->
                stats.infoHistory[holeEra]?.let { eraStats ->
                    stashAddress in eraStats.exposure.others.map { it.who.toAddress(networkType) }
                } ?: false
            }

            rewardHoles.map { it to stats.validatorAddress }
        }.flatten()
    }

    private suspend fun getValidatorHistoricalStats(
        historicalRange: List<BigInteger>,
        validatorAddresses: List<String>
    ): List<ValidatorHistoricalStats> {
        val runtime = runtimeProperty.get()

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
}
