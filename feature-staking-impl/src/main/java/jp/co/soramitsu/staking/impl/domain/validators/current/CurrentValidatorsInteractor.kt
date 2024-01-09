package jp.co.soramitsu.staking.impl.domain.validators.current

import jp.co.soramitsu.common.list.GroupedList
import jp.co.soramitsu.common.list.emptyGroupedList
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.shared_utils.extensions.toHexString
import jp.co.soramitsu.shared_utils.runtime.AccountId
import jp.co.soramitsu.staking.api.domain.model.IndividualExposure
import jp.co.soramitsu.staking.api.domain.model.NominatedValidator
import jp.co.soramitsu.staking.api.domain.model.NominatedValidator.Status
import jp.co.soramitsu.staking.api.domain.model.Nominations
import jp.co.soramitsu.staking.api.domain.model.StakingState
import jp.co.soramitsu.staking.impl.data.repository.StakingConstantsRepository
import jp.co.soramitsu.staking.impl.domain.common.isWaiting
import jp.co.soramitsu.staking.impl.domain.validators.ValidatorProvider
import jp.co.soramitsu.staking.impl.domain.validators.ValidatorSource
import jp.co.soramitsu.staking.impl.scenarios.relaychain.StakingRelayChainScenarioRepository
import jp.co.soramitsu.staking.impl.scenarios.relaychain.getActiveElectedValidatorsExposures
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlin.reflect.KClass

class CurrentValidatorsInteractor(
    private val stakingRepository: StakingRelayChainScenarioRepository,
    private val stakingConstantsRepository: StakingConstantsRepository,
    private val validatorProvider: ValidatorProvider
) {

    suspend fun nominatedValidatorsFlow(
        nominatorState: StakingState.Stash
    ): Flow<GroupedList<Status.Group, NominatedValidator>> {
        if (nominatorState !is StakingState.Stash.Nominator) {
            return flowOf(emptyGroupedList())
        }
        return nominatedValidatorsFlow(nominatorState.chain, nominatorState.stashId, nominatorState.nominations)
    }

    suspend fun nominatedValidatorsFlow(
        chain: Chain,
        stashId: AccountId,
        nominations: Nominations
    ): Flow<GroupedList<Status.Group, NominatedValidator>> {
        val chainId = chain.id

        return stakingRepository.observeActiveEraIndex(chainId).map { activeEra ->

            val exposures = stakingRepository.getActiveElectedValidatorsExposures(chainId)

            val activeNominations = exposures.mapValues { (_, exposure) ->
                exposure.others.firstOrNull { it.who.contentEquals(stashId) }
            }

            val nominatedValidatorIds = nominations.targets.mapTo(mutableSetOf(), ByteArray::toHexString)

            val isWaitingForNextEra = nominations.isWaiting(activeEra)

            val maxRewardedNominators = stakingConstantsRepository.maxRewardedNominatorPerValidator(chainId)

            val groupedByStatusClass = validatorProvider.getValidators(
                chain = chain,
                source = ValidatorSource.Custom(nominatedValidatorIds.toList()),
                cachedExposures = exposures
            )
                .map { validator ->
                    val userIndividualExposure = activeNominations[validator.accountIdHex]

                    val status = when {
                        userIndividualExposure != null -> {
                            // safe to !! here since non null nomination means that validator is elected
                            val userNominationIndex = validator.electedInfo!!.nominatorStakes
                                .sortedByDescending(IndividualExposure::value)
                                .indexOfFirst { it.who.contentEquals(stashId) }

                            val userNominationRank = userNominationIndex + 1

                            val willBeRewarded = userNominationRank < maxRewardedNominators

                            Status.Active(nomination = userIndividualExposure.value, willUserBeRewarded = willBeRewarded)
                        }
                        isWaitingForNextEra -> Status.WaitingForNextEra
                        exposures[validator.accountIdHex] != null -> Status.Elected
                        else -> Status.Inactive
                    }

                    NominatedValidator(validator, status)
                }
                .groupBy { it.status::class }

            val totalElectiveCount = with(groupedByStatusClass) { groupSize(Status.Active::class) + groupSize(Status.Elected::class) }
            val electedGroup = Status.Group.Active(totalElectiveCount)

            val waitingForNextEraGroup = Status.Group.WaitingForNextEra(
                maxValidatorsPerNominator = stakingConstantsRepository.maxValidatorsPerNominator(chainId),
                numberOfValidators = groupedByStatusClass.groupSize(Status.WaitingForNextEra::class)
            )

            groupedByStatusClass.mapKeys { (statusClass, validators) ->
                when (statusClass) {
                    Status.Active::class -> electedGroup
                    Status.Elected::class -> Status.Group.Elected(validators.size)
                    Status.Inactive::class -> Status.Group.Inactive(validators.size)
                    Status.WaitingForNextEra::class -> waitingForNextEraGroup
                    else -> throw IllegalArgumentException("Unknown status class: $statusClass")
                }
            }
                .toSortedMap(Status.Group.COMPARATOR)
        }
    }

    private fun Map<KClass<out Status>, List<NominatedValidator>>.groupSize(statusClass: KClass<out Status>): Int {
        return get(statusClass)?.size ?: 0
    }
}
