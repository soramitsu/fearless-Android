package jp.co.soramitsu.feature_staking_impl.domain

import jp.co.soramitsu.common.utils.networkType
import jp.co.soramitsu.common.utils.sumByBigInteger
import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_staking_api.domain.api.AccountIdMap
import jp.co.soramitsu.feature_staking_api.domain.api.EraTimeCalculatorFactory
import jp.co.soramitsu.feature_staking_api.domain.api.IdentityRepository
import jp.co.soramitsu.feature_staking_api.domain.api.StakingRepository
import jp.co.soramitsu.feature_staking_api.domain.api.getActiveElectedValidatorsExposures
import jp.co.soramitsu.feature_staking_api.domain.model.Exposure
import jp.co.soramitsu.feature_staking_api.domain.model.IndividualExposure
import jp.co.soramitsu.feature_staking_api.domain.model.RewardDestination
import jp.co.soramitsu.feature_staking_api.domain.model.StakingAccount
import jp.co.soramitsu.feature_staking_api.domain.model.StakingState
import jp.co.soramitsu.feature_staking_api.domain.model.StakingStory
import jp.co.soramitsu.feature_staking_api.domain.model.isUnbondingIn
import jp.co.soramitsu.feature_staking_impl.data.mappers.mapAccountToStakingAccount
import jp.co.soramitsu.feature_staking_impl.data.model.Payout
import jp.co.soramitsu.feature_staking_impl.data.repository.PayoutRepository
import jp.co.soramitsu.feature_staking_impl.data.repository.StakingConstantsRepository
import jp.co.soramitsu.feature_staking_impl.data.repository.StakingRewardsRepository
import jp.co.soramitsu.feature_staking_impl.domain.common.isWaiting
import jp.co.soramitsu.feature_staking_impl.domain.model.NetworkInfo
import jp.co.soramitsu.feature_staking_impl.domain.model.NominatorStatus
import jp.co.soramitsu.feature_staking_impl.domain.model.PendingPayout
import jp.co.soramitsu.feature_staking_impl.domain.model.PendingPayoutsStatistics
import jp.co.soramitsu.feature_staking_impl.domain.model.StakeSummary
import jp.co.soramitsu.feature_staking_impl.domain.model.StashNoneStatus
import jp.co.soramitsu.feature_staking_impl.domain.model.Unbonding
import jp.co.soramitsu.feature_staking_impl.domain.model.ValidatorStatus
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletConstants
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.math.BigInteger
import kotlin.time.ExperimentalTime
import kotlin.time.days
import kotlin.time.milliseconds

class EraRelativeInfo(
    val daysLeft: Int,
    val daysPast: Int,
    val erasLeft: BigInteger,
    val erasPast: BigInteger,
)

class StakingInteractor(
    private val walletRepository: WalletRepository,
    private val accountRepository: AccountRepository,
    private val stakingRepository: StakingRepository,
    private val stakingRewardsRepository: StakingRewardsRepository,
    private val stakingConstantsRepository: StakingConstantsRepository,
    private val identityRepository: IdentityRepository,
    private val walletConstants: WalletConstants,
    private val payoutRepository: PayoutRepository,
) {
    val factory = EraTimeCalculatorFactory(stakingRepository)

    suspend fun getTimeLeft(): BigInteger {
        val calculator = factory.create()
        return calculator.calculate()
    }

    @OptIn(ExperimentalTime::class)
    suspend fun calculatePendingPayouts(): Result<PendingPayoutsStatistics> = withContext(Dispatchers.Default) {
        runCatching {
            val currentStakingState = selectedAccountStakingStateFlow().first()
            require(currentStakingState is StakingState.Stash)

            val erasPerDay = currentStakingState.stashAddress.networkType().runtimeConfiguration.erasPerDay
            val activeEraIndex = stakingRepository.getActiveEraIndex()
            val historyDepth = stakingRepository.getHistoryDepth()

            val payouts = payoutRepository.calculateUnpaidPayouts(currentStakingState)

            val allValidatorAddresses = payouts.map(Payout::validatorAddress).distinct()
            val identityMapping = identityRepository.getIdentitiesFromAddresses(allValidatorAddresses)

            val pendingPayouts = payouts.map {
                val relativeInfo = eraRelativeInfo(it.era, activeEraIndex, historyDepth, erasPerDay)

                val estimatedCreatedAt = System.currentTimeMillis().milliseconds - relativeInfo.daysPast.days

                val closeToExpire = relativeInfo.erasLeft < historyDepth / 2.toBigInteger()

                val leftTime = factory.create().calculate(destinationEra = it.era + historyDepth).toLong()

                with(it) {
                    val validatorIdentity = identityMapping[validatorAddress]

                    val validatorInfo = PendingPayout.ValidatorInfo(validatorAddress, validatorIdentity?.display)

                    PendingPayout(validatorInfo, era, amount, estimatedCreatedAt.toLongMilliseconds(), leftTime, closeToExpire)
                }
            }

            PendingPayoutsStatistics(
                payouts = pendingPayouts,
                totalAmountInPlanks = pendingPayouts.sumByBigInteger(PendingPayout::amountInPlanks)
            )
        }
    }

    suspend fun syncStakingRewards(accountAddress: String) = withContext(Dispatchers.IO) {
        runCatching {
            stakingRewardsRepository.sync(accountAddress)
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
        val existentialDeposit = walletConstants.existentialDeposit()
        val eraStakers = it.eraStakers.values

        when {
            isNominationActive(nominatorState.stashId, it.eraStakers.values, it.rewardedNominatorsPerValidator) -> NominatorStatus.Active

            nominatorState.nominations.isWaiting(it.activeEraIndex) -> NominatorStatus.Waiting(timeLeft = getTimeLeft().toLong())

            else -> {
                val inactiveReason = when {
                    it.asset.bondedInPlanks < minimumStake(eraStakers, existentialDeposit) -> NominatorStatus.Inactive.Reason.MIN_STAKE
                    else -> NominatorStatus.Inactive.Reason.NO_ACTIVE_VALIDATOR
                }

                NominatorStatus.Inactive(inactiveReason)
            }
        }
    }

    suspend fun observeNetworkInfoState(networkType: Node.NetworkType): Flow<NetworkInfo> {
        val lockupPeriod = getLockupPeriodInDays(networkType)

        return stakingRepository.electedExposuresInActiveEra.map { exposuresMap ->
            val exposures = exposuresMap.values

            NetworkInfo(
                lockupPeriodInDays = lockupPeriod,
                minimumStake = minimumStake(exposures, walletConstants.existentialDeposit()),
                totalStake = totalStake(exposures),
                nominatorsCount = activeNominators(exposures),
            )
        }
    }

    suspend fun getLockupPeriodInDays() = getLockupPeriodInDays(getSelectedNetworkType())

    fun stakingStoriesFlow(): Flow<List<StakingStory>> {
        return stakingRepository.stakingStoriesFlow()
    }

    fun selectedAccountStakingStateFlow() = accountRepository.selectedAccountFlow()
        .flatMapLatest { stakingRepository.stakingStateFlow(it.address) }

    suspend fun getAccountsInCurrentNetwork() = withContext(Dispatchers.Default) {
        val account = accountRepository.getSelectedAccount()

        accountRepository.getAccountsByNetworkType(account.network.type)
            .map(::mapAccountToStakingAccount)
    }

    fun currentAssetFlow() = accountRepository.selectedAccountFlow()
        .flatMapLatest { assetFlow(it.address) }

    fun assetFlow(accountAddress: String): Flow<Asset> {
        return walletRepository.assetsFlow(accountAddress)
            .filter { it.isNotEmpty() }
            .map { it.first() }
    }

    suspend fun getSelectedNetworkType(): Node.NetworkType {
        return accountRepository.getSelectedNodeOrDefault().networkType
    }

    fun selectedAccountFlow(): Flow<StakingAccount> {
        return accountRepository.selectedAccountFlow()
            .map { mapAccountToStakingAccount(it) }
    }

    fun selectedNetworkTypeFLow() = accountRepository.selectedNetworkTypeFlow()

    suspend fun getAccount(address: String) = mapAccountToStakingAccount(accountRepository.getAccount(address))

    suspend fun getSelectedAccount(): StakingAccount = withContext(Dispatchers.Default) {
        val account = accountRepository.getSelectedAccount()

        mapAccountToStakingAccount(account)
    }

    suspend fun getRewardDestination(accountStakingState: StakingState.Stash): RewardDestination {
        return stakingRepository.getRewardDestination(accountStakingState)
    }

    fun maxValidatorsPerNominator(): Int = stakingConstantsRepository.maxValidatorsPerNominator()

    fun currentUnbondingsFlow(): Flow<List<Unbonding>> {
        return selectedAccountStakingStateFlow()
            .filterIsInstance<StakingState.Stash>()
            .flatMapLatest { stash ->
                val networkType = stash.stashAddress.networkType()

                combine(
                    stakingRepository.ledgerFlow(stash),
                    stakingRepository.observeActiveEraIndex(networkType)
                ) { ledger, activeEraIndex ->
                    ledger.unlocking
                        .filter { it.isUnbondingIn(activeEraIndex) }
                        .map {
                            val leftTime = factory.create().calculate(destinationEra = it.era)
                            Unbonding(it.amount, leftTime.toLong())
                        }
                }
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

    private suspend fun <S> observeStakeSummary(
        state: StakingState.Stash,
        statusResolver: suspend (StatusResolutionContext) -> S,
    ): Flow<StakeSummary<S>> = withContext(Dispatchers.Default) {
        val networkType = state.accountAddress.networkType()
        val tokenType = Token.Type.fromNetworkType(networkType)

        combine(
            stakingRepository.observeActiveEraIndex(networkType),
            walletRepository.assetFlow(state.accountAddress, tokenType),
            stakingRewardsRepository.totalRewardFlow(state.stashAddress)
        ) { activeEraIndex, asset, totalReward ->
            val totalStaked = asset.bonded

            val eraStakers = stakingRepository.getActiveElectedValidatorsExposures()
            val rewardedNominatorsPerValidator = stakingConstantsRepository.maxRewardedNominatorPerValidator()

            val statusResolutionContext = StatusResolutionContext(eraStakers, activeEraIndex, asset, rewardedNominatorsPerValidator)

            val status = statusResolver(statusResolutionContext)

            StakeSummary(
                status = status,
                totalStaked = totalStaked,
                totalRewards = totalReward,
                currentEra = activeEraIndex.toInt(),
            )
        }
    }

    private fun isValidatorActive(stashId: ByteArray, exposures: AccountIdMap<Exposure>): Boolean {
        val stashIdHex = stashId.toHexString()

        return stashIdHex in exposures.keys
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun activeNominators(exposures: Collection<Exposure>): Int {
        val activeNominatorsPerValidator = stakingConstantsRepository.maxRewardedNominatorPerValidator()

        return exposures.fold(mutableSetOf<String>()) { acc, exposure ->
            acc += exposure.others.sortedByDescending(IndividualExposure::value)
                .take(activeNominatorsPerValidator)
                .map { it.who.toHexString() }

            acc
        }.size
    }

    private fun totalStake(exposures: Collection<Exposure>): BigInteger {
        return exposures.sumOf(Exposure::total)
    }

    private suspend fun getLockupPeriodInDays(networkType: Node.NetworkType): Int {
        return stakingConstantsRepository.lockupPeriodInEras().toInt() / networkType.runtimeConfiguration.erasPerDay
    }

    private class StatusResolutionContext(
        val eraStakers: AccountIdMap<Exposure>,
        val activeEraIndex: BigInteger,
        val asset: Asset,
        val rewardedNominatorsPerValidator: Int
    )
}
