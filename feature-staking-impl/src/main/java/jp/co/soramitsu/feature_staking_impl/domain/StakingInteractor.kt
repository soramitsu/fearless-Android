package jp.co.soramitsu.feature_staking_impl.domain

import java.math.BigInteger
import jp.co.soramitsu.common.data.network.runtime.binding.BlockNumber
import jp.co.soramitsu.common.domain.model.StoryGroup
import jp.co.soramitsu.common.utils.combineToPair
import jp.co.soramitsu.common.utils.sumByBigInteger
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.model.MetaAccount
import jp.co.soramitsu.feature_account_api.domain.model.accountId
import jp.co.soramitsu.feature_staking_api.domain.api.AccountIdMap
import jp.co.soramitsu.feature_staking_api.domain.api.EraTimeCalculator
import jp.co.soramitsu.feature_staking_api.domain.api.EraTimeCalculatorFactory
import jp.co.soramitsu.feature_staking_api.domain.api.IdentityRepository
import jp.co.soramitsu.feature_staking_api.domain.api.StakingRepository
import jp.co.soramitsu.feature_staking_api.domain.api.erasPerDay
import jp.co.soramitsu.feature_staking_api.domain.model.Exposure
import jp.co.soramitsu.feature_staking_api.domain.model.IndividualExposure
import jp.co.soramitsu.feature_staking_api.domain.model.RewardDestination
import jp.co.soramitsu.feature_staking_api.domain.model.StakingAccount
import jp.co.soramitsu.feature_staking_api.domain.model.StakingState
import jp.co.soramitsu.feature_staking_api.domain.model.isUnbondingIn
import jp.co.soramitsu.feature_staking_impl.data.StakingSharedState
import jp.co.soramitsu.feature_staking_impl.data.mappers.mapAccountToStakingAccount
import jp.co.soramitsu.feature_staking_impl.data.model.Payout
import jp.co.soramitsu.feature_staking_impl.data.repository.PayoutRepository
import jp.co.soramitsu.feature_staking_impl.data.repository.StakingConstantsRepository
import jp.co.soramitsu.feature_staking_impl.data.repository.StakingRewardsRepository
import jp.co.soramitsu.feature_staking_impl.domain.model.PendingPayout
import jp.co.soramitsu.feature_staking_impl.domain.model.PendingPayoutsStatistics
import jp.co.soramitsu.feature_staking_impl.domain.model.Unbonding
import jp.co.soramitsu.feature_wallet_api.domain.AssetUseCase
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.runtime.ext.accountIdOf
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.repository.ChainStateRepository
import jp.co.soramitsu.runtime.state.SingleAssetSharedState
import jp.co.soramitsu.runtime.state.chain
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

const val HOURS_IN_DAY = 24

class EraRelativeInfo(
    val daysLeft: Int,
    val daysPast: Int,
    val erasLeft: BigInteger,
    val erasPast: BigInteger,
)

val ERA_OFFSET = 1.toBigInteger()

class StakingInteractor(
    private val walletRepository: WalletRepository,
    private val accountRepository: AccountRepository,
    private val stakingRepository: StakingRepository,
    private val stakingRewardsRepository: StakingRewardsRepository,
    private val stakingConstantsRepository: StakingConstantsRepository,
    private val identityRepository: IdentityRepository,
    private val stakingSharedState: StakingSharedState,
    private val payoutRepository: PayoutRepository,
    private val assetUseCase: AssetUseCase,
    private val factory: EraTimeCalculatorFactory,
    private val chainStateRepository: ChainStateRepository,
) {

    suspend fun calculatePendingPayouts(): Result<PendingPayoutsStatistics> = withContext(Dispatchers.Default) {
        runCatching {
            val currentStakingState = selectedAccountStakingStateFlow().first()
            val chainId = currentStakingState.chain.id

            require(currentStakingState is StakingState.Stash)

            val erasPerDay = stakingRepository.erasPerDay(chainId)
            val activeEraIndex = stakingRepository.getActiveEraIndex(chainId)
            val historyDepth = stakingRepository.getHistoryDepth(chainId)

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

                    val validatorInfo = PendingPayout.ValidatorInfo(validatorAddress, validatorIdentity?.display)

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

    suspend fun syncStakingRewards(chainId: ChainId, accountAddress: String) = withContext(Dispatchers.IO) {
        runCatching {
            stakingRewardsRepository.sync(chainId, accountAddress)
        }
    }

    suspend fun getMinimumStake(chainId: ChainId): BigInteger {
        val exposures = stakingRepository.electedExposuresInActiveEra(chainId).first().values
        val minimumNominatorBond = stakingRepository.minimumNominatorBond(chainId)
        return minimumStake(exposures, minimumNominatorBond)
    }

    suspend fun getLockupPeriodInDays() = withContext(Dispatchers.Default) {
        getLockupPeriodInDays(stakingSharedState.chainId())
    }

    fun selectedChainFlow() = stakingSharedState.assetWithChain.map { it.chain }

    fun selectedChainAssetFlow() = stakingSharedState.assetWithChain

    suspend fun getEraHoursLength(): Int = withContext(Dispatchers.Default) {
        val chainId = stakingSharedState.chainId()

        HOURS_IN_DAY / stakingRepository.erasPerDay(chainId)
    }

    fun stakingStoriesFlow(): Flow<List<StoryGroup.Staking>> {
        return stakingRepository.stakingStoriesFlow()
    }

    fun selectionStateFlow() = combineToPair(
        accountRepository.selectedMetaAccountFlow(),
        stakingSharedState.assetWithChain
    )

    fun selectedAccountStakingStateFlow(
        metaAccount: MetaAccount,
        assetWithChain: SingleAssetSharedState.AssetWithChain
    ) = flow {
        val (chain, chainAsset) = assetWithChain
        val accountId = metaAccount.accountId(chain)!! // TODO may be null for ethereum chains

        emitAll(stakingRepository.stakingStateFlow(chain, chainAsset, accountId))
    }

    fun selectedAccountStakingStateFlow() = selectionStateFlow().flatMapLatest { (selectedAccount, assetWithChain) ->
        selectedAccountStakingStateFlow(selectedAccount, assetWithChain)
    }

    suspend fun getAccountProjectionsInSelectedChains() = withContext(Dispatchers.Default) {
        val chain = stakingSharedState.chain()

        accountRepository.allMetaAccounts().map {
            mapAccountToStakingAccount(chain, it)
        }
    }

    fun currentAssetFlow() = assetUseCase.currentAssetFlow()

    fun assetFlow(accountAddress: String): Flow<Asset> {
        return flow {
            val (chain, chainAsset) = stakingSharedState.assetWithChain.first()
            val meta = accountRepository.getSelectedMetaAccount()

            emitAll(
                walletRepository.assetFlow(
                    metaId = meta.id,
                    accountId = chain.accountIdOf(accountAddress),
                    chainAsset = chainAsset,
                    minSupportedVersion = chain.minSupportedVersion
                )
            )
        }
    }

    fun selectedAccountProjectionFlow(): Flow<StakingAccount> {
        return combine(
            stakingSharedState.assetWithChain,
            accountRepository.selectedMetaAccountFlow()
        ) { (chain, _), account ->
            mapAccountToStakingAccount(chain, account)
        }
    }

    suspend fun getProjectedAccount(address: String): StakingAccount = withContext(Dispatchers.Default) {
        val chain = stakingSharedState.chain()
        val accountId = chain.accountIdOf(address)

        val metaAccount = accountRepository.findMetaAccount(accountId)!!

        mapAccountToStakingAccount(chain, metaAccount)
    }

    suspend fun getSelectedAccountProjection(): StakingAccount = withContext(Dispatchers.Default) {
        val account = accountRepository.getSelectedAccount(stakingSharedState.chainId())

        mapAccountToStakingAccount(account)
    }

    suspend fun getRewardDestination(accountStakingState: StakingState.Stash): RewardDestination = withContext(Dispatchers.Default) {
        stakingRepository.getRewardDestination(accountStakingState)
    }

    suspend fun maxValidatorsPerNominator(): Int = withContext(Dispatchers.Default) {
        stakingConstantsRepository.maxValidatorsPerNominator(stakingSharedState.chainId())
    }

    suspend fun maxRewardedNominators(): Int = withContext(Dispatchers.Default) {
        stakingConstantsRepository.maxRewardedNominatorPerValidator(stakingSharedState.chainId())
    }

    fun currentUnbondingsFlow(): Flow<List<Unbonding>> {
        return selectedAccountStakingStateFlow()
            .filterIsInstance<StakingState.Stash>()
            .flatMapLatest { stash ->
                val calculator = getCalculator()

                combine(
                    stakingRepository.ledgerFlow(stash),
                    stakingRepository.observeActiveEraIndex(stash.chain.id)
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

    private suspend fun getCalculator(): EraTimeCalculator {
        return factory.create(stakingSharedState.chainId())
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

    private fun isValidatorActive(stashId: ByteArray, exposures: AccountIdMap<Exposure>): Boolean {
        val stashIdHex = stashId.toHexString()

        return stashIdHex in exposures.keys
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun activeNominators(chainId: ChainId, exposures: Collection<Exposure>): Int {
        val activeNominatorsPerValidator = stakingConstantsRepository.maxRewardedNominatorPerValidator(chainId)

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

    private suspend fun getLockupPeriodInDays(chainId: ChainId): Int {
        return stakingConstantsRepository.lockupPeriodInEras(chainId).toInt() / stakingRepository.erasPerDay(chainId)
    }

    suspend fun currentBlockNumber(): BlockNumber {
        return chainStateRepository.currentBlock(getSelectedChain().id)
    }
}
