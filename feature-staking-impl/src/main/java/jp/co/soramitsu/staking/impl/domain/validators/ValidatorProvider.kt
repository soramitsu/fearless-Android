package jp.co.soramitsu.staking.impl.domain.validators

import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.utils.toHexAccountId
import jp.co.soramitsu.core.utils.utilityAsset
import jp.co.soramitsu.runtime.ext.addressOf
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.reefChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraMainChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ternoaChainId
import jp.co.soramitsu.shared_utils.extensions.fromHex
import jp.co.soramitsu.staking.api.domain.api.AccountIdMap
import jp.co.soramitsu.staking.api.domain.api.IdentityRepository
import jp.co.soramitsu.staking.api.domain.model.Exposure
import jp.co.soramitsu.staking.api.domain.model.Validator
import jp.co.soramitsu.staking.impl.data.repository.StakingConstantsRepository
import jp.co.soramitsu.staking.impl.domain.rewards.RewardCalculationTarget
import jp.co.soramitsu.staking.impl.domain.rewards.RewardCalculatorFactory
import jp.co.soramitsu.staking.impl.scenarios.relaychain.StakingRelayChainScenarioRepository
import jp.co.soramitsu.staking.impl.scenarios.relaychain.getActiveElectedValidatorsExposures

sealed class ValidatorSource {

    object Elected : ValidatorSource()

    class Custom(val validatorIds: List<String>) : ValidatorSource()
}

class ValidatorProvider(
    private val stakingRepository: StakingRelayChainScenarioRepository,
    private val identityRepository: IdentityRepository,
    private val rewardCalculatorFactory: RewardCalculatorFactory,
    private val stakingConstantsRepository: StakingConstantsRepository
) {

    suspend fun getValidators(
        chain: Chain,
        source: ValidatorSource,
        cachedExposures: AccountIdMap<Exposure>? = null
    ): List<Validator> {
        val chainId = chain.id

        val electedValidatorExposures = cachedExposures ?: stakingRepository.getActiveElectedValidatorsExposures(chainId)
        val allValidatorPrefs = stakingRepository.getAllValidatorPrefs(chainId)

        val requestedValidatorIds = when (source) {
            ValidatorSource.Elected -> allValidatorPrefs.keys.toList()
            is ValidatorSource.Custom -> source.validatorIds
        }

        val identities = identityRepository.getIdentitiesFromIds(chain, requestedValidatorIds)
        val slashes = stakingRepository.getSlashes(chainId, requestedValidatorIds)

        val calculationTargets = electedValidatorExposures.keys.mapNotNull { accountIdHex ->
            val exposure = electedValidatorExposures[accountIdHex] ?: return@mapNotNull null
            val prefs = allValidatorPrefs[accountIdHex] ?: return@mapNotNull null

            RewardCalculationTarget(
                accountIdHex = accountIdHex,
                totalStake = exposure.total,
                nominatorStakes = exposure.others,
                ownStake = exposure.own,
                commission = prefs.commission
            )
        }

        val rewardCalculator = when (chainId) {
            soraMainChainId -> {
                val utilityAsset = chain.utilityAsset ?: error("Utility asset not specified for chain ${chain.name} - ${chain.id}")
                rewardCalculatorFactory.createSora(utilityAsset, calculationTargets)
            }
            reefChainId -> {
                val utilityAsset = chain.utilityAsset ?: error("Utility asset not specified for chain ${chain.name} - ${chain.id}")
                rewardCalculatorFactory.createReef(utilityAsset, calculationTargets)
            }

            ternoaChainId -> {
                val utilityAsset = chain.utilityAsset ?: error("Utility asset not specified for chain ${chain.name} - ${chain.id}")
                rewardCalculatorFactory.createTernoa(utilityAsset, calculationTargets)
            }

            else -> {
                rewardCalculatorFactory.createManual(chainId, calculationTargets)
            }
        }

        val maxNominators = stakingConstantsRepository.maxRewardedNominatorPerValidator(chainId)

        return requestedValidatorIds.map { accountIdHex ->
            val prefs = allValidatorPrefs[accountIdHex]

            val electedInfo = electedValidatorExposures[accountIdHex]?.let {
                Validator.ElectedInfo(
                    totalStake = it.total,
                    ownStake = it.own,
                    nominatorStakes = it.others,
                    apy = runCatching { rewardCalculator.getApyFor(accountIdHex.fromHex()) }
                        .getOrNull().orZero(),
                    isOversubscribed = it.others.size > maxNominators
                )
            }

            Validator(
                slashed = slashes.getOrDefault(accountIdHex, false),
                accountIdHex = accountIdHex,
                electedInfo = electedInfo,
                prefs = prefs,
                identity = identities[accountIdHex],
                address = chain.addressOf(accountIdHex.fromHex())
            )
        }
    }

    suspend fun getValidatorWithoutElectedInfo(chain: Chain, address: String): Validator {
        val accountId = address.toHexAccountId()

        val accountIdBridged = listOf(accountId)

        val prefs = stakingRepository.getValidatorPrefs(chain.id, accountIdBridged)[accountId]
        val identity = identityRepository.getIdentitiesFromIds(chain, accountIdBridged)[accountId]

        val slashes = stakingRepository.getSlashes(chain.id, accountIdBridged)

        return Validator(
            slashed = slashes.getOrDefault(accountId, false),
            accountIdHex = accountId,
            address = address,
            prefs = prefs,
            identity = identity,
            electedInfo = null
        )
    }
}
