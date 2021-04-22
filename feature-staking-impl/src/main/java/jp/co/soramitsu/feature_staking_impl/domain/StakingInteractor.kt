package jp.co.soramitsu.feature_staking_impl.domain

import jp.co.soramitsu.common.data.network.runtime.binding.MultiAddress
import jp.co.soramitsu.common.data.network.runtime.calls.SubstrateCalls
import jp.co.soramitsu.common.utils.networkType
import jp.co.soramitsu.common.utils.sumByBigDecimal
import jp.co.soramitsu.common.utils.sumByBigInteger
import jp.co.soramitsu.common.utils.toAddress
import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_staking_api.domain.api.AccountIdMap
import jp.co.soramitsu.feature_staking_api.domain.api.IdentityRepository
import jp.co.soramitsu.feature_staking_api.domain.api.StakingRepository
import jp.co.soramitsu.feature_staking_api.domain.model.Election
import jp.co.soramitsu.feature_staking_api.domain.model.Exposure
import jp.co.soramitsu.feature_staking_api.domain.model.IndividualExposure
import jp.co.soramitsu.feature_staking_api.domain.model.Nominations
import jp.co.soramitsu.feature_staking_api.domain.model.RewardDestination
import jp.co.soramitsu.feature_staking_api.domain.model.StakingAccount
import jp.co.soramitsu.feature_staking_api.domain.model.StakingState
import jp.co.soramitsu.feature_staking_api.domain.model.StakingStory
import jp.co.soramitsu.feature_staking_api.domain.model.isUnbondingIn
import jp.co.soramitsu.feature_staking_impl.data.mappers.mapAccountToStakingAccount
import jp.co.soramitsu.feature_staking_impl.data.model.Payout
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.calls.bond
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.calls.nominate
import jp.co.soramitsu.feature_staking_impl.data.repository.PayoutRepository
import jp.co.soramitsu.feature_staking_impl.data.repository.StakingConstantsRepository
import jp.co.soramitsu.feature_staking_impl.data.repository.StakingRewardsRepository
import jp.co.soramitsu.feature_staking_impl.domain.model.NetworkInfo
import jp.co.soramitsu.feature_staking_impl.domain.model.NominatorStatus
import jp.co.soramitsu.feature_staking_impl.domain.model.PendingPayout
import jp.co.soramitsu.feature_staking_impl.domain.model.PendingPayoutsStatistics
import jp.co.soramitsu.feature_staking_impl.domain.model.StakeSummary
import jp.co.soramitsu.feature_staking_impl.domain.model.StakingReward
import jp.co.soramitsu.feature_staking_impl.domain.model.StashSetup
import jp.co.soramitsu.feature_staking_impl.domain.model.Unbonding
import jp.co.soramitsu.feature_staking_impl.domain.model.ValidatorStatus
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletConstants
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import jp.co.soramitsu.feature_wallet_api.domain.model.planksFromAmount
import jp.co.soramitsu.runtime.extrinsic.ExtrinsicBuilderFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.math.BigDecimal
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
    private val substrateCalls: SubstrateCalls,
    private val identityRepository: IdentityRepository,
    private val walletConstants: WalletConstants,
    private val payoutRepository: PayoutRepository,
    private val extrinsicBuilderFactory: ExtrinsicBuilderFactory,
) {

    @OptIn(ExperimentalTime::class)
    suspend fun calculatePendingPayouts(): Result<PendingPayoutsStatistics> = withContext(Dispatchers.Default) {
        runCatching {
            val currentStakingState = selectedAccountStakingStateFlow().first()
            require(currentStakingState is StakingState.Stash)

            val erasPerDay = currentStakingState.stashAddress.networkType().runtimeConfiguration.erasPerDay
            val activeEraIndex = stakingRepository.getActiveEraIndex()
            val historyDepth = stakingRepository.getHistoryDepth()

            val payouts = payoutRepository.calculateUnpaidPayouts(currentStakingState.stashAddress)

            val allValidatorAddresses = payouts.map(Payout::validatorAddress).distinct()
            val identityMapping = identityRepository.getIdentitiesFromAddresses(allValidatorAddresses)

            val pendingPayouts = payouts.map {
                val relativeInfo = eraRelativeInfo(it.era, activeEraIndex, historyDepth, erasPerDay)

                val estimatedCreatedAt = System.currentTimeMillis().milliseconds - relativeInfo.daysPast.days

                val closeToExpire = relativeInfo.erasLeft < historyDepth / 2.toBigInteger()

                with(it) {
                    val validatorIdentity = identityMapping[validatorAddress]

                    val validatorInfo = PendingPayout.ValidatorInfo(validatorAddress, validatorIdentity?.display)

                    PendingPayout(validatorInfo, era, amount, estimatedCreatedAt.toLongMilliseconds(), relativeInfo.daysLeft, closeToExpire)
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
            stakingRewardsRepository.syncTotalRewards(accountAddress)
        }
    }

    suspend fun observeValidatorSummary(
        validatorState: StakingState.Stash.Validator,
    ): Flow<StakeSummary<ValidatorStatus>> = observeStakeSummary(validatorState) {
        when {
            it.electionStatus == Election.OPEN -> ValidatorStatus.Election
            isValidatorActive(validatorState.stashId, it.eraStakers) -> ValidatorStatus.Active
            else -> ValidatorStatus.Inactive
        }
    }

    suspend fun observeNominatorSummary(
        nominatorState: StakingState.Stash.Nominator,
    ): Flow<StakeSummary<NominatorStatus>> = observeStakeSummary(nominatorState) {
        val existentialDeposit = walletConstants.existentialDeposit()
        val eraStakers = it.eraStakers.values

        when {
            it.electionStatus == Election.OPEN -> NominatorStatus.Election
            isNominationActive(nominatorState.stashId, it.eraStakers.values) -> NominatorStatus.Active
            isNominationWaiting(nominatorState.nominations, it.activeEraIndex) -> NominatorStatus.Waiting
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
        val lockupPeriod = stakingRepository.getLockupPeriodInDays(networkType)

        return stakingRepository.observeActiveEraIndex(networkType).map { eraIndex ->
            val exposures = stakingRepository.getElectedValidatorsExposure(eraIndex).values

            NetworkInfo(
                lockupPeriodInDays = lockupPeriod,
                minimumStake = minimumStake(exposures, walletConstants.existentialDeposit()),
                totalStake = totalStake(exposures),
                nominatorsCount = activeNominators(exposures),
            )
        }
    }

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
        .flatMapLatest { walletRepository.assetsFlow(it.address) }
        .filter { it.isNotEmpty() }
        .map { it.first() }

    suspend fun getSelectedNetworkType(): Node.NetworkType {
        return accountRepository.getSelectedNode().networkType
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

    suspend fun setupStaking(
        amount: BigDecimal,
        tokenType: Token.Type,
        nominations: List<MultiAddress>,
        stashSetup: StashSetup,
    ) = withContext(Dispatchers.Default) {
        runCatching {
            val extrinsic = extrinsicBuilderFactory.create(stashSetup.controllerAddress).apply {
                if (stashSetup.alreadyHasStash.not()) {
                    bond(
                        controllerAddress = MultiAddress.Id(stashSetup.controllerAddress.toAccountId()),
                        amount = tokenType.planksFromAmount(amount),
                        payee = stashSetup.rewardDestination
                    )
                }

                nominate(nominations)
            }.build()

            substrateCalls.submitExtrinsic(extrinsic)
        }
    }

    suspend fun isAccountInApp(accountAddress: String): Boolean {
        return accountRepository.isAccountExists(accountAddress)
    }

    suspend fun getExistingStashSetup(accountStakingState: StakingState.Stash): StashSetup {
        val networkType = accountStakingState.accountAddress.networkType()
        val rewardDestination = stakingRepository.getRewardDestination(accountStakingState)

        return StashSetup(rewardDestination, accountStakingState.controllerId.toAddress(networkType), alreadyHasStash = true)
    }

    suspend fun getRewardDestination(accountStakingState: StakingState.Stash): RewardDestination {
        return stakingRepository.getRewardDestination(accountStakingState)
    }

    fun currentUnbondingsFlow(): Flow<List<Unbonding>> {
        return selectedAccountStakingStateFlow()
            .filterIsInstance<StakingState.Stash>()
            .flatMapLatest { stash ->
                val networkType = stash.stashAddress.networkType()

                stakingRepository.ledgerFlow(stash).map { ledger ->
                    val erasPerDay = networkType.runtimeConfiguration.erasPerDay
                    val activeEraIndex = stakingRepository.getActiveEraIndex()
                    val historyDepth = stakingRepository.getHistoryDepth()

                    ledger.unlocking
                        .filter { it.isUnbondingIn(activeEraIndex) }
                        .map {
                            val relativeInfo = eraRelativeInfo(it.era, activeEraIndex, historyDepth, erasPerDay)

                            Unbonding(it.amount, relativeInfo.daysLeft)
                        }
                }
            }
    }

    private fun eraRelativeInfo(
        referenceEra: BigInteger,
        activeEra: BigInteger,
        historyDepth: BigInteger,
        erasPerDay: Int,
    ): EraRelativeInfo {
        val erasPast = activeEra - referenceEra
        val erasLeft = historyDepth - erasPast

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
            stakingRepository.electionFlow(networkType),
            stakingRepository.observeActiveEraIndex(networkType),
            stakingRewardsRepository.stakingRewardsFlow(state.accountAddress),
            walletRepository.assetFlow(state.accountAddress, tokenType)
        ) { electionStatus, activeEraIndex, rewards, asset ->

            val totalStaked = asset.bonded

            val eraStakers = stakingRepository.getElectedValidatorsExposure(activeEraIndex)

            val statusResolutionContext = StatusResolutionContext(eraStakers, activeEraIndex, electionStatus, asset)

            val status = statusResolver(statusResolutionContext)

            StakeSummary(
                status = status,
                totalStaked = totalStaked,
                totalRewards = totalRewards(rewards),
                currentEra = activeEraIndex.toInt()
            )
        }
    }

    private fun totalRewards(rewards: List<StakingReward>) = rewards.sumByBigDecimal {
        it.amount * it.type.summingCoefficient.toBigDecimal()
    }

    private fun isNominationWaiting(nominations: Nominations, activeEraIndex: BigInteger): Boolean {
        return nominations.submittedInEra == activeEraIndex
    }

    private fun isNominationActive(stashId: ByteArray, exposures: Collection<Exposure>): Boolean {
        return exposures.any { exposure ->
            exposure.others.any {
                it.who.contentEquals(stashId)
            }
        }
    }

    private fun isValidatorActive(stashId: ByteArray, exposures: AccountIdMap<Exposure>): Boolean {
        val stashIdHex = stashId.toHexString()

        return stashIdHex in exposures.keys
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun activeNominators(exposures: Collection<Exposure>): Int {
        val activeNominatorsPerValidator = stakingConstantsRepository.maxRewardedNominatorPerValidatorPrefs()

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

    private fun minimumStake(
        exposures: Collection<Exposure>,
        existentialDeposit: BigInteger,
    ): BigInteger {

        val stakeByNominator = exposures
            .map(Exposure::others)
            .flatten()
            .fold(mutableMapOf<String, BigInteger>()) { acc, individualExposure ->
                val currentExposure = acc.getOrDefault(individualExposure.who.toHexString(), BigInteger.ZERO)

                acc[individualExposure.who.toHexString()] = currentExposure + individualExposure.value

                acc
            }

        return stakeByNominator.values.minOrNull()!!.coerceAtLeast(existentialDeposit)
    }

    private class StatusResolutionContext(
        val eraStakers: AccountIdMap<Exposure>,
        val activeEraIndex: BigInteger,
        val electionStatus: Election,
        val asset: Asset,
    )
}
