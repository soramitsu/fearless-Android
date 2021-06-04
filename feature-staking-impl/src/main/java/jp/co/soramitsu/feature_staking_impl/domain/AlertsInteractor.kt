package jp.co.soramitsu.feature_staking_impl.domain

import jp.co.soramitsu.common.utils.networkType
import jp.co.soramitsu.feature_staking_api.domain.api.StakingRepository
import jp.co.soramitsu.feature_staking_api.domain.model.Election
import jp.co.soramitsu.feature_staking_api.domain.model.Exposure
import jp.co.soramitsu.feature_staking_api.domain.model.StakingState
import jp.co.soramitsu.feature_staking_impl.data.repository.StakingConstantsRepository
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

class AlertsInteractor(
    private val stakingRepository: StakingRepository,
    private val stakingConstantsRepository: StakingConstantsRepository,
    private val walletRepository: WalletRepository,
) {

    class AlertContext(
        val election: Election,
        val exposures: Map<String, Exposure>,
        val stakingState: StakingState,
        val maxRewardedNominatorsPerValidator: Int,
        val asset: Asset
    )

    private fun produceElectionAlert(context: AlertContext): Alert? {
        return if (context.election == Election.OPEN) Alert.Election else null
    }

    private fun produceChangeValidatorsAlert(context: AlertContext): Alert? = requireState(context.stakingState) { stashState: StakingState.Stash.Nominator ->
        with(context) {
            Alert.ChangeValidators.takeUnless {
                isNominationActive(stashState.stashId, exposures.values, maxRewardedNominatorsPerValidator)
            }
        }
    }

    private fun produceRedeemableAlert(context: AlertContext): Alert? = with(context.asset) {
        if (true/*redeemable > BigDecimal.ZERO*/) Alert.RedeemTokens(redeemable, token) else null
    }

    private val alertProducers = listOf(
        ::produceElectionAlert,
        ::produceChangeValidatorsAlert,
        ::produceRedeemableAlert
    )

    fun getAlertsFlow(stakingState: StakingState): Flow<List<Alert>> = flow {
        val networkType = stakingState.accountAddress.networkType()
        val token = Token.Type.fromNetworkType(networkType)
        val maxRewardedNominatorsPerValidator = stakingConstantsRepository.maxRewardedNominatorPerValidator()

        val alertsFlow = combine(
            stakingRepository.electionFlow(networkType),
            stakingRepository.electedExposuresInActiveEra,
            walletRepository.assetFlow(stakingState.accountAddress, token)
        ) { electionStatus, changeValidators, asset ->

            val context = AlertContext(electionStatus, changeValidators, stakingState, maxRewardedNominatorsPerValidator, asset)

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
