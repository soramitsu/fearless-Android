package jp.co.soramitsu.feature_staking_impl.domain

import jp.co.soramitsu.common.utils.networkType
import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.feature_staking_api.domain.api.StakingRepository
import jp.co.soramitsu.feature_staking_api.domain.model.Election
import jp.co.soramitsu.feature_staking_api.domain.model.Exposure
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
        val election: Election,
        val exposures: Map<String, Exposure>,
        val stakingState: StakingState.Stash
    )

    private fun produceElectionAlert(context: AlertContext): Alert? {
        return if (context.election == Election.OPEN) Alert.Election else null
    }

    private fun produceValidatorsAlert(context: AlertContext): Alert? {
        val result = context.exposures.values.flatMap { it.others }.any { it.who.contentEquals(context.stakingState.stashId) }

        return if (!result) Alert.ChangeValidators else null
    }

    private val alertProducers = listOf(
        ::produceElectionAlert,
        ::produceValidatorsAlert
    )

    fun getAlertsFlow(stakingState: StakingState): Flow<List<Alert>> = flow {
        val networkType = stakingState.accountAddress.networkType()
        val alertsFlow = combine(
            stakingRepository.electionFlow(networkType),
            stakingRepository.electedExposuresInActiveEra
        ) { electionStatus, changeValidators ->
            val context = AlertContext(electionStatus, changeValidators, stakingState as StakingState.Stash)

            alertProducers.mapNotNull { it.invoke(context) }
        }
        emitAll(alertsFlow)
    }
}
