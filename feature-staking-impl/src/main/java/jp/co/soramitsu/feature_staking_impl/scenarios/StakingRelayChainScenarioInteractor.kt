package jp.co.soramitsu.feature_staking_impl.scenarios

import java.math.BigInteger
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.utils.sumByBigInteger
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.model.MetaAccount
import jp.co.soramitsu.feature_account_api.domain.model.accountId
import jp.co.soramitsu.feature_staking_api.domain.api.AccountIdMap
import jp.co.soramitsu.feature_staking_impl.domain.EraTimeCalculator
import jp.co.soramitsu.feature_staking_impl.domain.EraTimeCalculatorFactory
import jp.co.soramitsu.feature_staking_api.domain.api.IdentityRepository
import jp.co.soramitsu.feature_staking_api.domain.model.Exposure
import jp.co.soramitsu.feature_staking_api.domain.model.IndividualExposure
import jp.co.soramitsu.feature_staking_api.domain.model.RewardDestination
import jp.co.soramitsu.feature_staking_api.domain.model.StakingState
import jp.co.soramitsu.feature_staking_api.domain.model.isUnbondingIn
import jp.co.soramitsu.feature_staking_impl.data.StakingSharedState
import jp.co.soramitsu.feature_staking_impl.data.model.Payout
import jp.co.soramitsu.feature_staking_impl.data.repository.PayoutRepository
import jp.co.soramitsu.feature_staking_impl.data.repository.StakingConstantsRepository
import jp.co.soramitsu.feature_staking_impl.data.repository.StakingRewardsRepository
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.domain.common.isWaiting
import jp.co.soramitsu.feature_staking_impl.domain.getSelectedChain
import jp.co.soramitsu.feature_staking_impl.domain.isNominationActive
import jp.co.soramitsu.feature_staking_impl.domain.minimumStake
import jp.co.soramitsu.feature_staking_impl.domain.model.NetworkInfo
import jp.co.soramitsu.feature_staking_impl.domain.model.NominatorStatus
import jp.co.soramitsu.feature_staking_impl.domain.model.PendingPayout
import jp.co.soramitsu.feature_staking_impl.domain.model.PendingPayoutsStatistics
import jp.co.soramitsu.feature_staking_impl.domain.model.StakeSummary
import jp.co.soramitsu.feature_staking_impl.domain.model.StashNoneStatus
import jp.co.soramitsu.feature_staking_impl.domain.model.Unbonding
import jp.co.soramitsu.feature_staking_impl.domain.model.ValidatorStatus
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.amountFromPlanks
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.state.SingleAssetSharedState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext


val ERA_OFFSET = 1.toBigInteger()
const val HOURS_IN_DAY = 24

class StakingRelayChainScenarioInteractor(
    private val stakingInteractor: StakingInteractor,
    private val accountRepository: AccountRepository,
    private val walletRepository: WalletRepository,
    private val stakingConstantsRepository: StakingConstantsRepository,
    private val stakingRelayChainScenarioRepository: StakingRelayChainScenarioRepository,
    private val stakingRewardsRepository: StakingRewardsRepository,
    private val factory: EraTimeCalculatorFactory,
    private val stakingSharedState: StakingSharedState,
    private val identityRepository: IdentityRepository,
    private val payoutRepository: PayoutRepository,
) : StakingScenarioInteractor {

    override suspend fun observeNetworkInfoState(): Flow<NetworkInfo> {
        val chainId = stakingInteractor.getSelectedChain().id
        val lockupPeriod = getLockupPeriodInDays(chainId)

        return stakingRelayChainScenarioRepository.electedExposuresInActiveEra(chainId).map { exposuresMap ->
            val exposures = exposuresMap.values

            val minimumNominatorBond = stakingRelayChainScenarioRepository.minimumNominatorBond(chainId)

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
        return stakingConstantsRepository.lockupPeriodInEras(chainId).toInt() / stakingRelayChainScenarioRepository.erasPerDay(chainId)
    }

    override suspend fun getStakingStateFlow(): Flow<StakingState> {
        return combine(
            stakingInteractor.selectedChainFlow(),
            stakingInteractor.currentAssetFlow()
        ) { chain, asset -> chain to asset }.flatMapConcat { (chain, asset) ->
            val accountId = accountRepository.getSelectedMetaAccount().accountId(chain) ?: error("cannot find accountId")
            stakingRelayChainScenarioRepository.stakingStateFlow(chain, asset.token.configuration, accountId)
        }
    }

    suspend fun observeStashSummary(
        stashState: StakingState.Stash.None
    ): Flow<StakeSummary<StashNoneStatus>> = observeStakeSummary(stashState) {
        StashNoneStatus.INACTIVE
    }

    suspend fun observeValidatorSummary(
        validatorState: StakingState.Stash.Validator,
    ): Flow<StakeSummary<ValidatorStatus>> = observeStakeSummary(validatorState) {
        when {
            isValidatorActive(validatorState.stashId, it.eraStakers) -> ValidatorStatus.ACTIVE
            else -> ValidatorStatus.INACTIVE
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
                    it.asset.bondedInPlanks.orZero() < minimumStake(eraStakers, stakingRelayChainScenarioRepository.minimumNominatorBond(chainId)) -> {
                        NominatorStatus.Inactive.Reason.MIN_STAKE
                    }
                    else -> NominatorStatus.Inactive.Reason.NO_ACTIVE_VALIDATOR
                }

                NominatorStatus.Inactive(inactiveReason)
            }
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
            stakingRelayChainScenarioRepository.observeActiveEraIndex(chainId),
            walletRepository.assetFlow(meta.id, state.accountId, chainAsset, chain.minSupportedVersion),
            stakingRewardsRepository.totalRewardFlow(state.stashAddress)
        ) { activeEraIndex, asset, totalReward ->
            val totalStaked = asset.bonded

            val eraStakers = stakingRelayChainScenarioRepository.getActiveElectedValidatorsExposures(chainId)
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

    private fun isValidatorActive(stashId: ByteArray, exposures: AccountIdMap<Exposure>): Boolean {
        val stashIdHex = stashId.toHexString()

        return stashIdHex in exposures.keys
    }

    private suspend fun getCalculator(): EraTimeCalculator {
        return factory.create(stakingSharedState.chainId())
    }

    fun selectedAccountStakingStateFlow(
        metaAccount: MetaAccount,
        assetWithChain: SingleAssetSharedState.AssetWithChain
    ) = flow {
        val (chain, chainAsset) = assetWithChain
        val accountId = metaAccount.accountId(chain)!! // TODO may be null for ethereum chains

        emitAll(stakingRelayChainScenarioRepository.stakingStateFlow(chain, chainAsset, accountId))
    }

    fun selectedAccountStakingStateFlow() = stakingInteractor.selectionStateFlow().flatMapLatest { (selectedAccount, assetWithChain) ->
        selectedAccountStakingStateFlow(selectedAccount, assetWithChain)
    }

    suspend fun calculatePendingPayouts(): Result<PendingPayoutsStatistics> = withContext(Dispatchers.Default) {
        runCatching {
            val currentStakingState = selectedAccountStakingStateFlow().first()
            val chainId = currentStakingState.chain.id

            require(currentStakingState is StakingState.Stash)

            val erasPerDay = stakingRelayChainScenarioRepository.erasPerDay(chainId)
            val activeEraIndex = stakingRelayChainScenarioRepository.getActiveEraIndex(chainId)
            val historyDepth = stakingRelayChainScenarioRepository.getHistoryDepth(chainId)

            val payouts = payoutRepository.calculateUnpaidPayouts(currentStakingState)

            val allValidatorAddresses = payouts.map(Payout::validatorAddress).distinct()
            val identityMapping = identityRepository.getIdentitiesFromAddresses(currentStakingState.chain, allValidatorAddresses)

            val calculator = getCalculator()
            val pendingPayouts = payouts.map {
                val relativeInfo = eraRelativeInfo(it.era, activeEraIndex, historyDepth, erasPerDay)

                val closeToExpire = relativeInfo.erasLeft < historyDepth / 2.toBigInteger()

                val leftTime = calculator.calculateTillEraSet(destinationEra = it.era + historyDepth + ERA_OFFSET).toLong()
                val currentTimestamp = System.currentTimeMillis()
                with(it) {
                    val validatorIdentity = identityMapping[validatorAddress]

                    val validatorInfo =
                        PendingPayout.ValidatorInfo(validatorAddress, validatorIdentity?.display)

                    PendingPayout(
                        validatorInfo = validatorInfo,
                        era = era,
                        amountInPlanks = amount,
                        timeLeft = leftTime,
                        createdAt = currentTimestamp,
                        closeToExpire = closeToExpire
                    )
                }
            }.sortedBy { it.era }

            PendingPayoutsStatistics(
                payouts = pendingPayouts,
                totalAmountInPlanks = pendingPayouts.sumByBigInteger(PendingPayout::amountInPlanks)
            )
        }
    }

    private fun eraRelativeInfo(
        createdAtEra: BigInteger,
        activeEra: BigInteger,
        lifespanInEras: BigInteger,
        erasPerDay: Int,
    ): EraRelativeInfo {
        val erasPast = activeEra - createdAtEra
        val erasLeft = lifespanInEras - erasPast

        val daysPast = erasPast.toInt() / erasPerDay
        val daysLeft = erasLeft.toInt() / erasPerDay

        return EraRelativeInfo(daysLeft, daysPast, erasLeft, erasPast)
    }

    suspend fun getEraHoursLength(): Int = withContext(Dispatchers.Default) {
        val chainId = stakingSharedState.chainId()

        HOURS_IN_DAY / stakingRelayChainScenarioRepository.erasPerDay(chainId)
    }

    suspend fun getMinimumStake(chainId: ChainId): BigInteger {
        val exposures = stakingRelayChainScenarioRepository.electedExposuresInActiveEra(chainId).first().values
        val minimumNominatorBond = stakingRelayChainScenarioRepository.minimumNominatorBond(chainId)
        return minimumStake(exposures, minimumNominatorBond)
    }

    suspend fun getLockupPeriodInDays() = withContext(Dispatchers.Default) {
        getLockupPeriodInDays(stakingSharedState.chainId())
    }

    suspend fun getRewardDestination(accountStakingState: StakingState.Stash): RewardDestination = withContext(Dispatchers.Default) {
        stakingRelayChainScenarioRepository.getRewardDestination(accountStakingState)
    }

    fun currentUnbondingsFlow(): Flow<List<Unbonding>> {
        return selectedAccountStakingStateFlow()
            .filterIsInstance<StakingState.Stash>()
            .flatMapLatest { stash ->
                val calculator = getCalculator()

                combine(
                    stakingRelayChainScenarioRepository.ledgerFlow(stash),
                    stakingRelayChainScenarioRepository.observeActiveEraIndex(stash.chain.id)
                ) { ledger, activeEraIndex ->
                    ledger.unlocking
                        .filter { it.isUnbondingIn(activeEraIndex) }
                        .map {
                            val leftTime = calculator.calculate(destinationEra = it.era)
                            Unbonding(
                                amount = it.amount,
                                timeLeft = leftTime.toLong(),
                                calculatedAt = System.currentTimeMillis()
                            )
                        }
                }
            }
    }

    suspend fun maxValidatorsPerNominator(): Int = withContext(Dispatchers.Default) {
        stakingConstantsRepository.maxValidatorsPerNominator(stakingSharedState.chainId())
    }

    suspend fun maxRewardedNominators(): Int = withContext(Dispatchers.Default) {
        stakingConstantsRepository.maxRewardedNominatorPerValidator(stakingSharedState.chainId())
    }

    private class StatusResolutionContext(
        val eraStakers: AccountIdMap<Exposure>,
        val activeEraIndex: BigInteger,
        val asset: Asset,
        val rewardedNominatorsPerValidator: Int
    )

}

class EraRelativeInfo(
    val daysLeft: Int,
    val daysPast: Int,
    val erasLeft: BigInteger,
    val erasPast: BigInteger,
)
