package jp.co.soramitsu.feature_staking_impl.domain

import jp.co.soramitsu.common.utils.networkType
import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_staking_api.domain.api.StakingRepository
import jp.co.soramitsu.feature_staking_api.domain.model.Election
import jp.co.soramitsu.feature_staking_api.domain.model.ElectionStatus
import jp.co.soramitsu.feature_staking_api.domain.model.StakingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class AlertsInteractor(
    private val stakingRepository: StakingRepository,
) {
    class AlertContext(
        val isElection: Boolean,
        val isAllValidatorsBad: Boolean
    )

    private fun produceElectionAlert(context: AlertContext) : Alert? {
        return if(context.isElection) Alert.Warning.Election else null
    }

    private fun produceValidatorsAlert(context: AlertContext) : Alert? {
        return if(context.isAllValidatorsBad) Alert.CallToAction.ChangeValidators else null
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
        val result = stakingRepository.electedExposuresInActiveEra.first().values.flatMap { it.others }.contains(stakingState.stashId)
        emit(!result)
    }


}
