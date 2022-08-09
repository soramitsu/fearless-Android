package jp.co.soramitsu.featurestakingimpl.domain.validators

import jp.co.soramitsu.common.utils.toHexAccountId
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.featurestakingapi.domain.api.AccountIdMap
import jp.co.soramitsu.featurestakingapi.domain.api.IdentityRepository
import jp.co.soramitsu.featurestakingapi.domain.model.Exposure
import jp.co.soramitsu.featurestakingapi.domain.model.Validator
import jp.co.soramitsu.featurestakingimpl.data.repository.StakingConstantsRepository
import jp.co.soramitsu.featurestakingimpl.domain.rewards.RewardCalculatorFactory
import jp.co.soramitsu.featurestakingimpl.scenarios.relaychain.StakingRelayChainScenarioRepository
import jp.co.soramitsu.featurestakingimpl.scenarios.relaychain.getActiveElectedValidatorsExposures
import jp.co.soramitsu.runtime.ext.addressOf
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain

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

        val requestedValidatorIds = when (source) {
            ValidatorSource.Elected -> electedValidatorExposures.keys.toList()
            is ValidatorSource.Custom -> source.validatorIds
        }

        val validatorIdsToQueryPrefs = electedValidatorExposures.keys + requestedValidatorIds

        val validatorPrefs = stakingRepository.getValidatorPrefs(chainId, validatorIdsToQueryPrefs.toList())

        val identities = identityRepository.getIdentitiesFromIds(chain, requestedValidatorIds)
        val slashes = stakingRepository.getSlashes(chainId, requestedValidatorIds)

        val rewardCalculator = rewardCalculatorFactory.createManual(electedValidatorExposures, validatorPrefs, chainId)
        val maxNominators = stakingConstantsRepository.maxRewardedNominatorPerValidator(chainId)

        return requestedValidatorIds.map { accountIdHex ->
            val prefs = validatorPrefs[accountIdHex]

            val electedInfo = electedValidatorExposures[accountIdHex]?.let {
                Validator.ElectedInfo(
                    totalStake = it.total,
                    ownStake = it.own,
                    nominatorStakes = it.others,
                    apy = rewardCalculator.getApyFor(accountIdHex.fromHex()),
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
