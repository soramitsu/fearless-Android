package jp.co.soramitsu.staking.impl.domain.alerts

import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.shared_utils.runtime.AccountId
import jp.co.soramitsu.staking.api.data.StakingSharedState
import jp.co.soramitsu.staking.api.domain.model.StakingState
import jp.co.soramitsu.staking.impl.data.repository.StakingConstantsRepository
import jp.co.soramitsu.staking.impl.domain.common.isWaiting
import jp.co.soramitsu.staking.impl.domain.isNominationActive
import jp.co.soramitsu.staking.impl.scenarios.relaychain.StakingRelayChainScenarioRepository
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.shared_utils.extensions.fromHex
import jp.co.soramitsu.shared_utils.extensions.toHexString
import jp.co.soramitsu.staking.api.domain.model.LegacyExposure
import kotlinx.coroutines.ExperimentalCoroutinesApi
import jp.co.soramitsu.core.models.Asset as CoreAsset

private const val NOMINATIONS_ACTIVE_MEMO = "NOMINATIONS_ACTIVE_MEMO"

class AlertsInteractor(
    private val stakingRepository: StakingRelayChainScenarioRepository,
    private val stakingConstantsRepository: StakingConstantsRepository,
    private val sharedState: StakingSharedState,
    private val walletRepository: WalletRepository,
    private val accountRepository: AccountRepository
) {

    class AlertContext(
        val exposures: Map<String, LegacyExposure>,
        val stakingState: StakingState,
        val maxRewardedNominatorsPerValidator: Int?,
        val minimumNominatorBond: BigInteger,
        val activeEra: BigInteger,
        val asset: Asset,
        val maxNominators: Int?,
        val isLegacyErasStakersSchema: Boolean
    ) {

        val memo = mutableMapOf<Any, Any?>()

        inline fun <reified T> useMemo(
            key: Any,
            lazyProducer: () -> T
        ): T {
            return memo.getOrPut(key, lazyProducer) as T
        }
    }

    private fun AlertContext.isStakingActive(stashId: AccountId) = useMemo(NOMINATIONS_ACTIVE_MEMO) {
        isNominationActive(stashId, exposures.values, maxRewardedNominatorsPerValidator)
    }

    private fun produceSetValidatorsAlert(context: AlertContext): Alert? {
        return requireState(context.stakingState) { _: StakingState.Stash.None ->
            Alert.SetValidators
        }
    }

    private suspend fun produceChangeValidatorsAlert(context: AlertContext): Alert? {
        return requireState(context.stakingState) { nominatorState: StakingState.Stash.Nominator ->
            if (context.isLegacyErasStakersSchema.not()) {
                val myValidators = stakingRepository.getRemoteAccountNominations(
                    context.stakingState.chain.id,
                    nominatorState.accountId
                )?.targets.orEmpty()

                return@requireState if (context.exposures.any { it.key.fromHex() in myValidators }) Alert.ChangeValidators else null
            }

            val allValidatorsAreOversubscribed = if (context.maxNominators == null) {
                false
            } else {
                nominatorState.nominations.targets.mapNotNull { context.exposures[it.toHexString()] }
                    .all { it.others.size > context.maxNominators }
            }
            val stakingIsNotActive = context.isStakingActive(nominatorState.stashId).not()

            if (stakingIsNotActive.not()) return null

            return@requireState when {
                stakingIsNotActive && allValidatorsAreOversubscribed -> Alert.AllValidatorsAreOversubscribed
                stakingIsNotActive && nominatorState.nominations.isWaiting(context.activeEra)
                    .not() -> Alert.ChangeValidators

                else -> null
            }
        }
    }

    private fun produceRedeemableAlert(context: AlertContext): Alert? = requireState(context.stakingState) { _: StakingState.Stash ->
        with(context.asset) {
            if (redeemable > BigDecimal.ZERO) Alert.RedeemTokens(redeemable, token) else null
        }
    }

    private suspend fun produceMinStakeAlert(context: AlertContext) =
        requireState(context.stakingState) { state: StakingState.Stash ->
            with(context) {
                val minActiveStake = stakingRepository.minimumActiveStake(stakingState.chain.id)
                    ?: context.exposures.values.minOf { exposure -> exposure.others.minOf { it.value } }

                val minimalStakeInPlanks =
                    minActiveStake.coerceAtLeast(stakingRepository.minimumNominatorBond(context.asset.token.configuration))

                if (
                // do not show alert for validators
                    state !is StakingState.Stash.Validator &&
                    asset.bondedInPlanks.orZero() < minimalStakeInPlanks &&
                    // prevent alert for situation where all tokens are being unbounded
                    asset.bondedInPlanks.orZero() > BigInteger.ZERO
                ) {
                    val minimalStake = asset.token.amountFromPlanks(minimalStakeInPlanks)

                    Alert.BondMoreTokens(minimalStake, asset.token)
                } else {
                    null
                }
            }
        }

    private fun produceWaitingNextEraAlert(context: AlertContext) = requireState(context.stakingState) { nominatorState: StakingState.Stash.Nominator ->
        Alert.WaitingForNextEra.takeIf {
            return@takeIf if(context.isLegacyErasStakersSchema) {
                // staking is inactive and there is pending change
                context.isStakingActive(nominatorState.stashId).not() && nominatorState.nominations.isWaiting(context.activeEra)
            } else {

                nominatorState.nominations.isWaiting(context.activeEra)
            }
        }
    }

    private val alertProducers = listOf(
        ::produceRedeemableAlert,
        ::produceWaitingNextEraAlert,
        ::produceSetValidatorsAlert
    )

    private val suspendableAlertProducers = listOf(::produceChangeValidatorsAlert,::produceMinStakeAlert)

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getAlertsFlow(stakingState: StakingState): Flow<List<Alert>> = sharedState.assetWithChain.flatMapLatest { (chain, chainAsset) ->
        if (chainAsset.staking != CoreAsset.StakingType.RELAYCHAIN) {
            return@flatMapLatest flowOf(emptyList())
        }

        val maxRewardedNominatorsPerValidator = stakingConstantsRepository.maxRewardedNominatorPerValidator(chain.id)
        val minimumNominatorBond = stakingRepository.minimumNominatorBond(chainAsset)
        val meta = accountRepository.getSelectedMetaAccount()

        val maxNominators = stakingConstantsRepository.maxRewardedNominatorPerValidator(chain.id)

        val alertsFlow = combine(
            stakingRepository.legacyElectedExposuresInActiveEra(chain.id),
            walletRepository.assetFlow(meta.id, stakingState.accountId, chainAsset, chain.minSupportedVersion),
            stakingRepository.observeActiveEraIndex(chain.id)
        ) { exposures, asset, activeEra ->
            val isLegacyErasStakersSchema = stakingRepository.isLegacyErasStakersSchema(chain.id)
            val context = AlertContext(
                exposures = exposures,
                stakingState = stakingState,
                maxRewardedNominatorsPerValidator = maxRewardedNominatorsPerValidator,
                minimumNominatorBond = minimumNominatorBond,
                asset = asset,
                activeEra = activeEra,
                maxNominators = maxNominators,
                isLegacyErasStakersSchema = isLegacyErasStakersSchema
            )
            val alerts = (alertProducers.mapNotNull { it.invoke(context) } + suspendableAlertProducers.mapNotNull { it.invoke(context) }).toMutableList()

            if(alerts.contains(Alert.WaitingForNextEra) && alerts.any { it is Alert.BondMoreTokens }) {
                alerts.remove(Alert.WaitingForNextEra)
            }

            alerts
        }

        alertsFlow
    }

    private inline fun <reified T : StakingState, R> requireState(
        state: StakingState,
        block: (T) -> R
    ): R? {
        return (state as? T)?.let(block)
    }
}
