package jp.co.soramitsu.feature_staking_impl.domain

import jp.co.soramitsu.common.utils.networkType
import jp.co.soramitsu.feature_staking_api.domain.api.StakingRepository
import jp.co.soramitsu.feature_staking_api.domain.model.Election
import jp.co.soramitsu.feature_staking_api.domain.model.Exposure
import jp.co.soramitsu.feature_staking_api.domain.model.StakingState
import jp.co.soramitsu.feature_staking_impl.data.repository.StakingConstantsRepository
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletConstants
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import jp.co.soramitsu.feature_wallet_api.domain.model.amountFromPlanks
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import java.math.BigDecimal
import java.math.BigInteger

class AlertsInteractor(
    private val stakingRepository: StakingRepository,
    private val stakingConstantsRepository: StakingConstantsRepository,
    private val walletRepository: WalletRepository,
    private val walletConstants: WalletConstants,
) {

    class AlertContext(
        val election: Election,
        val exposures: Map<String, Exposure>,
        val stakingState: StakingState,
        val maxRewardedNominatorsPerValidator: Int,
        val existentialDeposit: BigInteger,
        val asset: Asset,
    )

    private fun produceElectionAlert(context: AlertContext): Alert? {
        return if (context.election == Election.OPEN) Alert.Election else null
    }

    private fun produceChangeValidatorsAlert(context: AlertContext): Alert? {
        return requireState(context.stakingState) { nominatorState: StakingState.Stash.Nominator ->
            with(context) {
                Alert.ChangeValidators.takeUnless {
                    isNominationActive(nominatorState.stashId, exposures.values, maxRewardedNominatorsPerValidator)
                }
            }
        }
    }

    private fun produceRedeemableAlert(context: AlertContext): Alert? = requireState(context.stakingState) { _: StakingState.Stash ->
        with(context.asset) {
            if (redeemable > BigDecimal.ZERO) Alert.RedeemTokens(redeemable, token) else null
        }
    }

    private fun produceMinStakeAlert(context: AlertContext) = requireState(context.stakingState) { _: StakingState.Stash ->
        with(context) {
            val minimalStakeInPlanks = minimumStake(exposures.values, existentialDeposit)

            if (asset.bondedInPlanks < minimalStakeInPlanks) {
                val minimalStake = asset.token.amountFromPlanks(minimalStakeInPlanks)

                Alert.BondMoreTokens(minimalStake, asset.token)
            } else {
                null
            }
        }
    }

    private val alertProducers = listOf(
        ::produceElectionAlert,
        ::produceChangeValidatorsAlert,
        ::produceRedeemableAlert,
        ::produceMinStakeAlert
    )

    fun getAlertsFlow(stakingState: StakingState): Flow<List<Alert>> = flow {
        val networkType = stakingState.accountAddress.networkType()
        val token = Token.Type.fromNetworkType(networkType)
        val maxRewardedNominatorsPerValidator = stakingConstantsRepository.maxRewardedNominatorPerValidator()
        val existentialDeposit = walletConstants.existentialDeposit()

        val alertsFlow = combine(
            stakingRepository.electionFlow(networkType),
            stakingRepository.electedExposuresInActiveEra,
            walletRepository.assetFlow(stakingState.accountAddress, token)
        ) { electionStatus, exposures, asset ->

            val context = AlertContext(
                election = electionStatus,
                exposures = exposures,
                stakingState = stakingState,
                maxRewardedNominatorsPerValidator = maxRewardedNominatorsPerValidator,
                existentialDeposit = existentialDeposit,
                asset = asset
            )

            alertProducers.mapNotNull { it.invoke(context) }
        }

        emitAll(alertsFlow)
    }

    private inline fun <reified T : StakingState, R> requireState(
        state: StakingState,
        block: (T) -> R,
    ): R? {
        return (state as? T)?.let(block)
    }
}
