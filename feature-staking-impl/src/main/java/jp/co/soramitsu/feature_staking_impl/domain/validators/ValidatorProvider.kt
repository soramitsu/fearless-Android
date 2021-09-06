package jp.co.soramitsu.feature_staking_impl.domain.validators

import jp.co.soramitsu.common.utils.toAddress
import jp.co.soramitsu.common.utils.toHexAccountId
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.currentNetworkType
import jp.co.soramitsu.feature_staking_api.domain.api.AccountIdMap
import jp.co.soramitsu.feature_staking_api.domain.api.IdentityRepository
import jp.co.soramitsu.feature_staking_api.domain.api.StakingRepository
import jp.co.soramitsu.feature_staking_api.domain.api.getActiveElectedValidatorsExposures
import jp.co.soramitsu.feature_staking_api.domain.model.Exposure
import jp.co.soramitsu.feature_staking_api.domain.model.Validator
import jp.co.soramitsu.feature_staking_impl.data.repository.StakingConstantsRepository
import jp.co.soramitsu.feature_staking_impl.domain.rewards.RewardCalculatorFactory

sealed class ValidatorSource {

    object Elected : ValidatorSource()

    class Custom(val validatorIds: List<String>) : ValidatorSource()
}

class ValidatorProvider(
    private val stakingRepository: StakingRepository,
    private val identityRepository: IdentityRepository,
    private val accountRepository: AccountRepository,
    private val rewardCalculatorFactory: RewardCalculatorFactory,
    private val stakingConstantsRepository: StakingConstantsRepository,
) {

    suspend fun getValidators(
        source: ValidatorSource,
        cachedExposures: AccountIdMap<Exposure>? = null,
    ): List<Validator> {
        val networkType = accountRepository.currentNetworkType()
        val electedValidatorExposures = cachedExposures ?: stakingRepository.getActiveElectedValidatorsExposures()

        val requestedValidatorIds = when (source) {
            ValidatorSource.Elected -> electedValidatorExposures.keys.toList()
            is ValidatorSource.Custom -> source.validatorIds
        }

        val validatorIdsToQueryPrefs = electedValidatorExposures.keys + requestedValidatorIds

        val validatorPrefs = stakingRepository.getValidatorPrefs(validatorIdsToQueryPrefs.toList())

        val identities = identityRepository.getIdentitiesFromIds(requestedValidatorIds)
        val slashes = stakingRepository.getSlashes(requestedValidatorIds)

        val rewardCalculator = rewardCalculatorFactory.create(electedValidatorExposures, validatorPrefs)
        val maxNominators = stakingConstantsRepository.maxRewardedNominatorPerValidator()

        return requestedValidatorIds.map { accountIdHex ->
            val prefs = validatorPrefs[accountIdHex]

            val electedInfo = electedValidatorExposures[accountIdHex]?.let {
                Validator.ElectedInfo(
                    totalStake = it.total,
                    ownStake = it.own,
                    nominatorStakes = it.others,
                    apy = rewardCalculator.getApyFor(accountIdHex),
                    isOversubscribed = it.others.size > maxNominators
                )
            }

            Validator(
                slashed = slashes.getOrDefault(accountIdHex, false),
                accountIdHex = accountIdHex,
                electedInfo = electedInfo,
                prefs = prefs,
                identity = identities[accountIdHex],
                address = accountIdHex.fromHex().toAddress(networkType)
            )
        }
    }

    suspend fun getValidatorWithoutElectedInfo(address: String): Validator {
        val accountId = address.toHexAccountId()

        val accountIdBridged = listOf(accountId)

        val prefs = stakingRepository.getValidatorPrefs(accountIdBridged)[accountId]
        val identity = identityRepository.getIdentitiesFromIds(accountIdBridged)[accountId]

        val slashes = stakingRepository.getSlashes(accountIdBridged)

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
