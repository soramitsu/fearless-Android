package jp.co.soramitsu.feature_staking_impl.domain.scenarios

import java.math.BigInteger
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.model.accountId
import jp.co.soramitsu.feature_staking_api.domain.api.AccountIdMap
import jp.co.soramitsu.feature_staking_api.domain.api.EraTimeCalculator
import jp.co.soramitsu.feature_staking_api.domain.api.EraTimeCalculatorFactory
import jp.co.soramitsu.feature_staking_api.domain.api.StakingRepository
import jp.co.soramitsu.feature_staking_api.domain.api.erasPerDay
import jp.co.soramitsu.feature_staking_api.domain.api.getActiveElectedValidatorsExposures
import jp.co.soramitsu.feature_staking_api.domain.model.Exposure
import jp.co.soramitsu.feature_staking_api.domain.model.IndividualExposure
import jp.co.soramitsu.feature_staking_api.domain.model.StakingState
import jp.co.soramitsu.feature_staking_impl.data.StakingSharedState
import jp.co.soramitsu.feature_staking_impl.data.repository.StakingConstantsRepository
import jp.co.soramitsu.feature_staking_impl.data.repository.StakingRewardsRepository
import jp.co.soramitsu.feature_staking_impl.domain.ERA_OFFSET
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.domain.common.isWaiting
import jp.co.soramitsu.feature_staking_impl.domain.getSelectedChain
import jp.co.soramitsu.feature_staking_impl.domain.isNominationActive
import jp.co.soramitsu.feature_staking_impl.domain.minimumStake
import jp.co.soramitsu.feature_staking_impl.domain.model.NetworkInfo
import jp.co.soramitsu.feature_staking_impl.domain.model.NominatorStatus
import jp.co.soramitsu.feature_staking_impl.domain.model.StakeSummary
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.amountFromPlanks
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class StakingRelayChainScenarioInteractor(
    private val stakingInteractor: StakingInteractor,
    private val accountRepository: AccountRepository,
    private val walletRepository: WalletRepository,
    private val stakingConstantsRepository: StakingConstantsRepository,
    private val stakingRepository: StakingRepository,
    private val stakingRewardsRepository: StakingRewardsRepository,
    private val factory: EraTimeCalculatorFactory,
    private val stakingSharedState: StakingSharedState
) : StakingScenarioInteractor {

    override suspend fun observeNetworkInfoState(): Flow<NetworkInfo> {
        val chainId = stakingInteractor.getSelectedChain().id
        val lockupPeriod = getLockupPeriodInDays(chainId)

        return stakingRepository.electedExposuresInActiveEra(chainId).map { exposuresMap ->
            val exposures = exposuresMap.values

            val minimumNominatorBond = stakingRepository.minimumNominatorBond(chainId)

            NetworkInfo.RelayChain(
                lockupPeriodInDays = lockupPeriod,
                minimumStake = minimumStake(exposures, minimumNominatorBond),
                totalStake = totalStake(exposures),
                nominatorsCount = activeNominators(chainId, exposures),
            )
        }
    }

    private fun totalStake(exposures: Collection<Exposure>): BigInteger {
        return exposures.sumOf(Exposure::total)
    }

    private suspend fun activeNominators(chainId: ChainId, exposures: Collection<Exposure>): Int {
        val activeNominatorsPerValidator = stakingConstantsRepository.maxRewardedNominatorPerValidator(chainId)

        return exposures.fold(mutableSetOf<String>()) { acc, exposure ->
            acc += exposure.others.sortedByDescending(IndividualExposure::value)
                .take(activeNominatorsPerValidator)
                .map { it.who.toHexString() }

            acc
        }.size
    }

    private suspend fun getLockupPeriodInDays(chainId: ChainId): Int {
        return stakingConstantsRepository.lockupPeriodInEras(chainId).toInt() / stakingRepository.erasPerDay(chainId)
    }

    override suspend fun getStakingStateFlow(): Flow<StakingState> {
        return combine(
            stakingInteractor.selectedChainFlow(),
            stakingInteractor.currentAssetFlow()
        ) { chain, asset -> chain to asset }.flatMapConcat { (chain, asset) ->
            val accountId = accountRepository.getSelectedMetaAccount().accountId(chain) ?: error("cannot find accountId")
            stakingRepository.observeRelayChainState(chain, asset.token.configuration, accountId)
        }
    }

    private suspend fun <S> observeStakeSummary(
        state: StakingState.Stash,
        statusResolver: suspend (StatusResolutionContext) -> S,
    ): Flow<StakeSummary<S>> = withContext(Dispatchers.Default) {

        val (chain, chainAsset) = stakingSharedState.assetWithChain.first()
        val chainId = chainAsset.chainId
        val meta = accountRepository.getSelectedMetaAccount()

        combine(
            stakingRepository.observeActiveEraIndex(chainId),
            walletRepository.assetFlow(meta.id, state.accountId, chainAsset, chain.minSupportedVersion),
            stakingRewardsRepository.totalRewardFlow(state.stashAddress)
        ) { activeEraIndex, asset, totalReward ->
            val totalStaked = asset.bonded

            val eraStakers = stakingRepository.getActiveElectedValidatorsExposures(chainId)
            val rewardedNominatorsPerValidator = stakingConstantsRepository.maxRewardedNominatorPerValidator(chainId)

            val statusResolutionContext = StatusResolutionContext(eraStakers, activeEraIndex, asset, rewardedNominatorsPerValidator)

            val status = statusResolver(statusResolutionContext)

            StakeSummary(
                status = status,
                totalStaked = totalStaked,
                totalReward = asset.token.amountFromPlanks(totalReward),
                currentEra = activeEraIndex.toInt(),
            )
        }
    }

    suspend fun observeNominatorSummary(
        nominatorState: StakingState.Stash.Nominator,
    ): Flow<StakeSummary<NominatorStatus>> = observeStakeSummary(nominatorState) {
        val eraStakers = it.eraStakers.values
        val chainId = nominatorState.chain.id

        when {
            isNominationActive(nominatorState.stashId, it.eraStakers.values, it.rewardedNominatorsPerValidator) -> NominatorStatus.Active

            nominatorState.nominations.isWaiting(it.activeEraIndex) -> NominatorStatus.Waiting(
                timeLeft = getCalculator().calculate(nominatorState.nominations.submittedInEra + ERA_OFFSET).toLong()
            )

            else -> {
                val inactiveReason = when {
                    it.asset.bondedInPlanks.orZero() < minimumStake(eraStakers, stakingRepository.minimumNominatorBond(chainId)) -> {
                        NominatorStatus.Inactive.Reason.MIN_STAKE
                    }
                    else -> NominatorStatus.Inactive.Reason.NO_ACTIVE_VALIDATOR
                }

                NominatorStatus.Inactive(inactiveReason)
            }
        }
    }

    private suspend fun getCalculator(): EraTimeCalculator {
        return factory.create(stakingSharedState.chainId())
    }

    private class StatusResolutionContext(
        val eraStakers: AccountIdMap<Exposure>,
        val activeEraIndex: BigInteger,
        val asset: Asset,
        val rewardedNominatorsPerValidator: Int
    )
}
