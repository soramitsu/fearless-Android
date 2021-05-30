package jp.co.soramitsu.feature_staking_impl.domain

import jp.co.soramitsu.common.utils.networkType
import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.feature_staking_api.domain.api.StakingRepository
import jp.co.soramitsu.feature_staking_api.domain.model.Election
import jp.co.soramitsu.feature_staking_api.domain.model.StakingState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

class AlertsInteractor(
    private val stakingRepository: StakingRepository,
) {
    class AlertContext(
        val isElection: Boolean,
        val isAllValidatorsNotElected: Boolean
    )

    private fun produceElectionAlert(context: AlertContext): Alert? {
        return if (context.isElection) Alert.Election else null
    }

    private fun produceValidatorsAlert(context: AlertContext): Alert? {
        return if (context.isAllValidatorsNotElected) Alert.ChangeValidators else null
    }

    private val alertProducers = listOf(
        ::produceElectionAlert,
        ::produceValidatorsAlert
    )

    fun getAlertsFlow(stakingState: StakingState): Flow<List<Alert>> = flow {
        val alertsFlow = combine(
            getElectionStatus(stakingState.accountAddress.networkType()),
            getChangeValidators(stakingState as StakingState.Stash)
        ) { electionStatus, changeValidators ->
            val context = AlertContext(electionStatus, changeValidators)

            alertProducers.mapNotNull { it.invoke(context) }
        }
        emitAll(alertsFlow)
    }

    private suspend fun getElectionStatus(networkType: Node.NetworkType): Flow<Boolean> {
        return stakingRepository.electionFlow(networkType).map {
            it != Election.OPEN
        }
    }

    private suspend fun getChangeValidators(stakingState: StakingState.Stash) = flow {
        val exposures = stakingRepository.electedExposuresInActiveEra.first().values
        val allNominators = exposures.flatMap { it.others }.map { it.who }
        val result = allNominators.find { it.contentEquals(stakingState.stashId) } != null
        emit(!result)
    }
}
