package jp.co.soramitsu.feature_staking_impl.domain.validators.current

import jp.co.soramitsu.common.list.GroupedList
import jp.co.soramitsu.common.utils.mapValuesNotNull
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.feature_staking_api.domain.api.StakingRepository
import jp.co.soramitsu.feature_staking_api.domain.model.NominatedValidator
import jp.co.soramitsu.feature_staking_api.domain.model.NominatedValidatorStatus
import jp.co.soramitsu.feature_staking_api.domain.model.StakingState
import jp.co.soramitsu.feature_staking_impl.data.repository.StakingConstantsRepository
import jp.co.soramitsu.feature_staking_impl.domain.common.isWaiting
import jp.co.soramitsu.feature_staking_impl.domain.validators.ValidatorProvider
import jp.co.soramitsu.feature_staking_impl.domain.validators.ValidatorSource

class CurrentValidatorsInteractor(
    private val stakingRepository: StakingRepository,
    private val stakingConstantsRepository: StakingConstantsRepository,
    private val validatorProvider: ValidatorProvider,
) {

    suspend fun getNominatedValidators(
        nominatorState: StakingState.Stash.Nominator,
    ): GroupedList<NominatedValidatorStatus, NominatedValidator> {
        val stashId = nominatorState.stashId

        val activeEra = stakingRepository.getActiveEraIndex()

        val exposures = stakingRepository.getElectedValidatorsExposure(activeEra)
        val activeNominations = exposures.mapValuesNotNull { (_, exposure) ->
            exposure.others.firstOrNull { it.who.contentEquals(stashId) }
        }

        val nominatedValidatorIds = nominatorState.nominations.targets.mapTo(mutableSetOf(), ByteArray::toHexString)

        val allValidators = activeNominations.keys + nominatedValidatorIds

        val isWaitingForNextEra = nominatorState.nominations.isWaiting(activeEra)
        val waitingForNextEraStatus = NominatedValidatorStatus.WaitingForNextEra(stakingConstantsRepository.maxValidatorsPerNominator())

        return validatorProvider.getValidators(
            ValidatorSource.Custom(allValidators.toList()),
            cachedExposures = exposures
        ).map {
            NominatedValidator(it, activeNominations[it.accountIdHex]?.value)
        }.groupBy {
            when {
                it.nominationInPlanks != null -> NominatedValidatorStatus.Active
                isWaitingForNextEra -> waitingForNextEraStatus
                exposures[it.validator.accountIdHex] != null -> NominatedValidatorStatus.Elected
                else -> NominatedValidatorStatus.Inactive
            }
        }.toSortedMap(NominatedValidatorStatus.COMPARATOR)
    }
}
