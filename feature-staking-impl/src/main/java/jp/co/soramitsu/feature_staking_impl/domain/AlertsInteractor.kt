package jp.co.soramitsu.feature_staking_impl.domain

import jp.co.soramitsu.common.utils.networkType
import jp.co.soramitsu.feature_staking_api.domain.api.StakingRepository
import jp.co.soramitsu.feature_staking_api.domain.model.Election
import jp.co.soramitsu.feature_staking_api.domain.model.Exposure
import jp.co.soramitsu.feature_staking_api.domain.model.StakingState
import jp.co.soramitsu.feature_staking_impl.data.repository.StakingConstantsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

class AlertsInteractor(
    private val stakingRepository: StakingRepository,
    private val stakingConstantsRepository: StakingConstantsRepository
) {

    class AlertContext(
        val election: Election,
        val exposures: Map<String, Exposure>,
        val stakingState: StakingState,
        val maxRewardedNominatorsPerValidator: Int,
    )

    private fun produceElectionAlert(context: AlertContext): Alert? {
        return if (context.election == Election.OPEN) Alert.Election else null
    }

    private fun produceValidatorsAlert(context: AlertContext): Alert? = requireState(context.stakingState) { stashState: StakingState.Stash ->
        with(context) {
            Alert.ChangeValidators.takeUnless {
                isNominationActive(stashState.stashId, exposures.values, maxRewardedNominatorsPerValidator)
            }
        }
    }

    private val alertProducers = listOf(
        ::produceElectionAlert,
        ::produceValidatorsAlert
    )

    fun getAlertsFlow(stakingState: StakingState): Flow<List<Alert>> = flow {
        val networkType = stakingState.accountAddress.networkType()
        val maxRewardedNominatorsPerValidator = stakingConstantsRepository.maxRewardedNominatorPerValidator()

        val alertsFlow = combine(
            stakingRepository.electionFlow(networkType),
            stakingRepository.electedExposuresInActiveEra
        ) { electionStatus, changeValidators ->

            val context = AlertContext(electionStatus, changeValidators, stakingState, maxRewardedNominatorsPerValidator)

            alertProducers.mapNotNull { it.invoke(context) }
        }
        emitAll(alertsFlow)
    }

    private inline fun <reified T : StakingState, R> requireState(
        state: StakingState,
        block: (T) -> R
    ): R? {
        return (state as? T)?.let(block)
    }
}
