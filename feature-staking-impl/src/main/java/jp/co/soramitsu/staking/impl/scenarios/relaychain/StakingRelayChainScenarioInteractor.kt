package jp.co.soramitsu.staking.impl.scenarios.relaychain

import java.math.BigDecimal
import java.math.BigInteger
import java.util.Optional
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.common.address.AddressModel
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.utils.sumByBigInteger
import jp.co.soramitsu.common.validation.CompositeValidation
import jp.co.soramitsu.common.validation.ValidationSystem
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.runtime.ext.accountIdOf
import jp.co.soramitsu.runtime.ext.utilityAsset
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.state.SingleAssetSharedState
import jp.co.soramitsu.staking.api.data.StakingSharedState
import jp.co.soramitsu.staking.api.domain.api.AccountIdMap
import jp.co.soramitsu.staking.api.domain.api.IdentityRepository
import jp.co.soramitsu.staking.api.domain.model.DelegationAction
import jp.co.soramitsu.staking.api.domain.model.Exposure
import jp.co.soramitsu.staking.api.domain.model.IndividualExposure
import jp.co.soramitsu.staking.api.domain.model.RewardDestination
import jp.co.soramitsu.staking.api.domain.model.StakingLedger
import jp.co.soramitsu.staking.api.domain.model.StakingState
import jp.co.soramitsu.staking.api.domain.model.isUnbondingIn
import jp.co.soramitsu.staking.impl.data.model.Payout
import jp.co.soramitsu.staking.impl.data.network.blockhain.calls.bondMore
import jp.co.soramitsu.staking.impl.data.network.blockhain.calls.chill
import jp.co.soramitsu.staking.impl.data.network.blockhain.calls.rebond
import jp.co.soramitsu.staking.impl.data.network.blockhain.calls.unbond
import jp.co.soramitsu.staking.impl.data.network.blockhain.calls.withdrawUnbonded
import jp.co.soramitsu.staking.impl.data.repository.PayoutRepository
import jp.co.soramitsu.staking.impl.data.repository.StakingConstantsRepository
import jp.co.soramitsu.staking.impl.data.repository.StakingRewardsRepository
import jp.co.soramitsu.staking.impl.domain.EraTimeCalculator
import jp.co.soramitsu.staking.impl.domain.EraTimeCalculatorFactory
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.domain.common.isWaiting
import jp.co.soramitsu.staking.impl.domain.getSelectedChain
import jp.co.soramitsu.staking.impl.domain.isNominationActive
import jp.co.soramitsu.staking.impl.domain.minimumStake
import jp.co.soramitsu.staking.impl.domain.model.NetworkInfo
import jp.co.soramitsu.staking.impl.domain.model.NominatorStatus
import jp.co.soramitsu.staking.impl.domain.model.PendingPayout
import jp.co.soramitsu.staking.impl.domain.model.PendingPayoutsStatistics
import jp.co.soramitsu.staking.impl.domain.model.StakeSummary
import jp.co.soramitsu.staking.impl.domain.model.StashNoneStatus
import jp.co.soramitsu.staking.impl.domain.model.Unbonding
import jp.co.soramitsu.staking.impl.domain.model.ValidatorStatus
import jp.co.soramitsu.staking.impl.domain.validations.balance.BalanceAccountRequiredValidation
import jp.co.soramitsu.staking.impl.domain.validations.balance.BalanceUnlockingLimitValidation
import jp.co.soramitsu.staking.impl.domain.validations.balance.ManageStakingValidationFailure
import jp.co.soramitsu.staking.impl.domain.validations.balance.ManageStakingValidationPayload
import jp.co.soramitsu.staking.impl.domain.validations.bond.BondMoreValidationFailure
import jp.co.soramitsu.staking.impl.domain.validations.bond.BondMoreValidationPayload
import jp.co.soramitsu.staking.impl.domain.validations.bond.BondMoreValidationSystem
import jp.co.soramitsu.staking.impl.domain.validations.bond.NotZeroBondValidation
import jp.co.soramitsu.staking.impl.domain.validations.rebond.EnoughToRebondValidation
import jp.co.soramitsu.staking.impl.domain.validations.rebond.NotZeroRebondValidation
import jp.co.soramitsu.staking.impl.domain.validations.rebond.RebondFeeValidation
import jp.co.soramitsu.staking.impl.domain.validations.rebond.RebondValidationFailure
import jp.co.soramitsu.staking.impl.domain.validations.rebond.RebondValidationPayload
import jp.co.soramitsu.staking.impl.domain.validations.rebond.RebondValidationSystem
import jp.co.soramitsu.staking.impl.domain.validations.reedeem.RedeemFeeValidation
import jp.co.soramitsu.staking.impl.domain.validations.reedeem.RedeemValidationFailure
import jp.co.soramitsu.staking.impl.domain.validations.reedeem.RedeemValidationSystem
import jp.co.soramitsu.staking.impl.domain.validations.setup.MinimumAmountValidation
import jp.co.soramitsu.staking.impl.domain.validations.setup.SetupStakingMaximumNominatorsValidation
import jp.co.soramitsu.staking.impl.domain.validations.setup.SetupStakingPayload
import jp.co.soramitsu.staking.impl.domain.validations.setup.SetupStakingValidationFailure
import jp.co.soramitsu.staking.impl.domain.validations.unbond.CrossExistentialValidation
import jp.co.soramitsu.staking.impl.domain.validations.unbond.EnoughToUnbondValidation
import jp.co.soramitsu.staking.impl.domain.validations.unbond.NotZeroUnbondValidation
import jp.co.soramitsu.staking.impl.domain.validations.unbond.UnbondFeeValidation
import jp.co.soramitsu.staking.impl.domain.validations.unbond.UnbondLimitValidation
import jp.co.soramitsu.staking.impl.domain.validations.unbond.UnbondValidationFailure
import jp.co.soramitsu.staking.impl.domain.validations.unbond.UnbondValidationPayload
import jp.co.soramitsu.staking.impl.domain.validations.unbond.UnbondValidationSystem
import jp.co.soramitsu.staking.impl.presentation.staking.balance.model.StakingBalanceModel
import jp.co.soramitsu.staking.impl.presentation.staking.balance.rebond.RebondKind
import jp.co.soramitsu.staking.impl.scenarios.StakingScenarioInteractor
import jp.co.soramitsu.wallet.api.presentation.model.mapAmountToAmountModel
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletConstants
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks
import jp.co.soramitsu.wallet.impl.domain.validation.EnoughToPayFeesValidation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import jp.co.soramitsu.core.models.Asset as CoreAsset

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
    private val walletConstants: WalletConstants
) : StakingScenarioInteractor {

    override suspend fun observeNetworkInfoState(): Flow<NetworkInfo> {
        val chain = stakingInteractor.getSelectedChain()
        val lockupPeriod = runCatching { getLockupPeriodInHours(chain.id) }.getOrDefault(0)

        return stakingRelayChainScenarioRepository.electedExposuresInActiveEra(chain.id).map { exposuresMap ->
            val exposures = exposuresMap.values

            val minimumNominatorBond = stakingRelayChainScenarioRepository.minimumNominatorBond(chain.utilityAsset)

            NetworkInfo.RelayChain(
                lockupPeriodInHours = lockupPeriod,
                minimumStake = minimumStake(exposures, minimumNominatorBond),
                totalStake = totalStake(exposures),
                nominatorsCount = activeNominators(chain.id, exposures)
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

    private suspend fun getLockupPeriodInHours(chainId: ChainId): Int {
        return stakingConstantsRepository.lockupPeriodInEras(chainId).toInt() * stakingRelayChainScenarioRepository.hoursInEra(chainId)
    }

    override val stakingStateFlow = stakingSharedState.assetWithChain.distinctUntilChanged().flatMapConcat { (chain, asset) ->
        val accountId = accountRepository.getSelectedMetaAccount().accountId(chain) ?: error("cannot find accountId")
        stakingRelayChainScenarioRepository.stakingStateFlow(chain, asset, accountId)
    }

    suspend fun observeStashSummary(
        stashState: StakingState.Stash.None
    ): Flow<StakeSummary<StashNoneStatus>> = observeStakeSummary(stashState) {
        StashNoneStatus.INACTIVE
    }

    suspend fun observeValidatorSummary(
        validatorState: StakingState.Stash.Validator
    ): Flow<StakeSummary<ValidatorStatus>> = observeStakeSummary(validatorState) {
        when {
            isValidatorActive(validatorState.stashId, it.eraStakers) -> ValidatorStatus.ACTIVE
            else -> ValidatorStatus.INACTIVE
        }
    }

    suspend fun observeNominatorSummary(
        nominatorState: StakingState.Stash.Nominator
    ): Flow<StakeSummary<NominatorStatus>> = observeStakeSummary(nominatorState) {
        val eraStakers = it.eraStakers.values
        val chainId = nominatorState.chain.id

        when {
            isNominationActive(nominatorState.stashId, it.eraStakers.values, it.rewardedNominatorsPerValidator) -> NominatorStatus.Active

            nominatorState.nominations.isWaiting(it.activeEraIndex) -> NominatorStatus.Waiting(
                timeLeft = getCalculator(chainId).calculate(nominatorState.nominations.submittedInEra + ERA_OFFSET).toLong()
            )

            else -> {
                val inactiveReason = when {
                    it.asset.bondedInPlanks.orZero() < minimumStake(
                        eraStakers,
                        stakingRelayChainScenarioRepository.minimumNominatorBond(nominatorState.chain.utilityAsset)
                    ) -> {
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
        statusResolver: suspend (StatusResolutionContext) -> S
    ): Flow<StakeSummary<S>> = withContext(Dispatchers.Default) {
        val (chain, chainAsset) = stakingSharedState.assetWithChain.first()
        val chainId = chainAsset.chainId
        val meta = accountRepository.getSelectedMetaAccount()

        combine(
            stakingRelayChainScenarioRepository.observeActiveEraIndex(chainId),
            walletRepository.assetFlow(meta.id, state.accountId, chainAsset, chain.minSupportedVersion),
            stakingRewardsRepository.totalRewardFlow(state.stashAddress).onStart { emit(BigInteger.ZERO) }
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
                currentEra = activeEraIndex.toInt()
            )
        }
    }

    private fun isValidatorActive(stashId: ByteArray, exposures: AccountIdMap<Exposure>): Boolean {
        val stashIdHex = stashId.toHexString()

        return stashIdHex in exposures.keys
    }

    private suspend fun getCalculator(chainId: String): EraTimeCalculator {
        return factory.create(chainId)
    }

    fun selectedAccountStakingStateFlow(
        metaAccount: MetaAccount,
        assetWithChain: SingleAssetSharedState.AssetWithChain
    ) = flow {
        val (chain, chainAsset) = assetWithChain
        val accountId = metaAccount.accountId(chain)!! // TODO may be null for ethereum chains

        emitAll(stakingRelayChainScenarioRepository.stakingStateFlow(chain, chainAsset, accountId))
    }

    override fun selectedAccountStakingStateFlow() = stakingInteractor.selectionStateFlow().flatMapLatest { (selectedAccount, assetWithChain) ->
        selectedAccountStakingStateFlow(selectedAccount, assetWithChain)
    }

    override fun getSelectedAccountAddress(): Flow<Optional<AddressModel>> = flowOf(Optional.empty())

    override suspend fun getRebondingUnbondings(collatorAddress: String?): List<Unbonding> = currentUnbondingsFlow(null).first()

    override fun getRebondTypes(): Set<RebondKind> = RebondKind.values().toSet()

    override fun rebond(extrinsicBuilder: ExtrinsicBuilder, amount: BigInteger, candidate: String?) = extrinsicBuilder.rebond(amount)

    override fun getCollatorAddress(collatorAddress: String?): Flow<Optional<AddressModel>> = flowOf(Optional.empty())

    override suspend fun stakeMore(extrinsicBuilder: ExtrinsicBuilder, amountInPlanks: BigInteger, candidate: String?) =
        extrinsicBuilder.bondMore(amountInPlanks)

    override suspend fun stakeLess(
        extrinsicBuilder: ExtrinsicBuilder,
        amountInPlanks: BigInteger,
        stashState: StakingState,
        currentBondedBalance: BigInteger,
        candidate: String?
    ) {
        require(stashState is StakingState.Stash)
        extrinsicBuilder.constructUnbondExtrinsic(stashState, currentBondedBalance, amountInPlanks)
    }

    override suspend fun confirmRevoke(
        extrinsicBuilder: ExtrinsicBuilder,
        candidate: String?,
        stashState: StakingState
    ) {
        require(stashState is StakingState.Stash)

        extrinsicBuilder.withdrawUnbonded(getSlashingSpansNumber(stashState.chain.id, stashState.stashId))
    }

    override suspend fun getSelectedAccountStakingState() = selectedAccountStakingStateFlow().first()

    override suspend fun getStakingBalanceFlow(collatorId: AccountId?): Flow<StakingBalanceModel> {
        return stakingInteractor.currentAssetFlow().map { asset ->
            StakingBalanceModel(
                staked = mapAmountToAmountModel(asset.bonded, asset, R.string.wallet_balance_bonded),
                unstaking = mapAmountToAmountModel(asset.unbonding, asset, R.string.wallet_balance_unbonding_v1_9_0),
                redeemable = mapAmountToAmountModel(asset.redeemable, asset, R.string.wallet_balance_redeemable)
            )
        }
    }

    override fun overrideRedeemActionTitle(): Int? = null
    override suspend fun overrideUnbondHint(): String? = null
    override fun overrideUnbondAvailableLabel(): Int = R.string.staking_bonded_format
    override suspend fun getUnstakeAvailableAmount(asset: Asset, collatorId: AccountId?) = asset.bonded
    override fun getRebondAvailableAmount(asset: Asset, amount: BigDecimal) = asset.unbonding
    override suspend fun checkEnoughToUnbondValidation(payload: UnbondValidationPayload) = payload.amount <= payload.asset.bonded
    override suspend fun checkEnoughToRebondValidation(payload: RebondValidationPayload) = payload.rebondAmount <= payload.controllerAsset.unbonding

    override suspend fun checkCrossExistentialValidation(payload: UnbondValidationPayload): Boolean {
        val tokenConfiguration = payload.asset.token.configuration

        val existentialDepositInPlanks = walletConstants.existentialDeposit(tokenConfiguration).orZero()
        val existentialDeposit = tokenConfiguration.amountFromPlanks(existentialDepositInPlanks)

        val bonded = payload.asset.bonded
        val resultGreaterThanExistential = bonded - payload.amount >= existentialDeposit
        val resultIsZero = bonded == payload.amount
        return resultGreaterThanExistential || resultIsZero
    }

    override suspend fun accountIsNotController(controllerAddress: String): Boolean {
        val currentStakingState = selectedAccountStakingStateFlow().first()
        val chainId = currentStakingState.chain.id

        val ledger = stakingRelayChainScenarioRepository.ledger(chainId, controllerAddress)
        return ledger == null
    }

    override suspend fun ledger(): StakingLedger? {
        val currentStakingState = selectedAccountStakingStateFlow().first()
        require(currentStakingState is StakingState.Stash)
        val chainId = currentStakingState.chain.id
        val controllerAddress = currentStakingState.controllerAddress

        return stakingRelayChainScenarioRepository.ledger(chainId, controllerAddress)
    }

    override suspend fun checkAccountRequiredValidation(accountAddress: String?): Boolean {
        accountAddress ?: return false
        val currentStakingState = selectedAccountStakingStateFlow().first()
        val chain = currentStakingState.chain

        return accountRepository.isAccountExists(chain.accountIdOf(accountAddress))
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

            val calculator = getCalculator(chainId)
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

    private suspend fun getSlashingSpansNumber(chainId: ChainId, stashId: AccountId): BigInteger {
        val slashingSpans = stakingRelayChainScenarioRepository.getSlashingSpan(chainId, stashId)

        return slashingSpans?.let {
            val totalSpans = it.prior.size + 1 //  all from prior + one for lastNonZeroSlash

            totalSpans.toBigInteger()
        } ?: BigInteger.ZERO
    }

    private fun eraRelativeInfo(
        createdAtEra: BigInteger,
        activeEra: BigInteger,
        lifespanInEras: BigInteger,
        erasPerDay: Int
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

    override suspend fun getMinimumStake(chainAsset: CoreAsset): BigInteger {
        val exposures = stakingRelayChainScenarioRepository.electedExposuresInActiveEra(chainAsset.chainId).firstOrNull()?.values ?: emptyList()
        val minimumNominatorBond = stakingRelayChainScenarioRepository.minimumNominatorBond(chainAsset)
        return minimumStake(exposures, minimumNominatorBond)
    }

    suspend fun getLockupPeriodInHours() = withContext(Dispatchers.Default) {
        getLockupPeriodInHours(stakingSharedState.chainId())
    }

    override suspend fun getRewardDestination(accountStakingState: StakingState): RewardDestination = withContext(Dispatchers.Default) {
        require(accountStakingState is StakingState.Stash)
        stakingRelayChainScenarioRepository.getRewardDestination(accountStakingState)
    }

    override suspend fun currentUnbondingsFlow(collatorAddress: String?): Flow<List<Unbonding>> {
        return selectedAccountStakingStateFlow()
            .filterIsInstance<StakingState.Stash>()
            .flatMapLatest { stash ->
                val calculator = getCalculator(stash.chain.id)

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
                                calculatedAt = System.currentTimeMillis(),
                                type = DelegationAction.UNSTAKE
                            )
                        }
                }
            }
    }

    suspend fun maxValidatorsPerNominator(): Int {
        return withContext(Dispatchers.Default) {
            stakingConstantsRepository.maxValidatorsPerNominator(stakingSharedState.chainId())
        }
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

    override suspend fun maxNumberOfStakesIsReached(chainId: ChainId): Boolean {
        val nominatorCount = stakingRelayChainScenarioRepository.nominatorsCount(chainId) ?: return false
        val maxNominatorsAllowed = stakingRelayChainScenarioRepository.maxNominators(chainId) ?: return false
        return nominatorCount >= maxNominatorsAllowed
    }

    override suspend fun maxStakersPerBlockProducer(): Int {
        return maxValidatorsPerNominator()
    }

    override suspend fun unstakingPeriod(): Int {
        return getLockupPeriodInHours()
    }

    override suspend fun stakePeriodInHours(): Int {
        return getEraHoursLength()
    }

    override suspend fun getSetupStakingValidationSystem(): ValidationSystem<SetupStakingPayload, SetupStakingValidationFailure> {
        return ValidationSystem(
            CompositeValidation(
                listOf(
                    stakingInteractor.feeValidation(),
                    MinimumAmountValidation(this),
                    SetupStakingMaximumNominatorsValidation(
                        stakingScenarioInteractor = this,
                        errorProducer = { SetupStakingValidationFailure.MaxNominatorsReached },
                        isAlreadyNominating = SetupStakingPayload::isAlreadyNominating,
                        sharedState = stakingSharedState
                    )
                )
            )
        )
    }

    override fun getRedeemValidation(): ValidationSystem<ManageStakingValidationPayload, ManageStakingValidationFailure> {
        return ValidationSystem(
            CompositeValidation(
                listOf(
                    BalanceAccountRequiredValidation(
                        this,
                        accountAddressExtractor = { it.stashState?.controllerAddress },
                        errorProducer = ManageStakingValidationFailure::ControllerRequired
                    )
                )
            )
        )
    }

    override fun getBondMoreValidation(): ValidationSystem<ManageStakingValidationPayload, ManageStakingValidationFailure> {
        return ValidationSystem(
            CompositeValidation(
                listOf(
                    BalanceAccountRequiredValidation(
                        this,
                        accountAddressExtractor = { it.stashState?.stashAddress },
                        errorProducer = ManageStakingValidationFailure::StashRequired
                    )
                )
            )
        )
    }

    override fun getUnbondingValidation(): ValidationSystem<ManageStakingValidationPayload, ManageStakingValidationFailure> {
        return ValidationSystem(
            CompositeValidation(
                validations = listOf(
                    BalanceAccountRequiredValidation(
                        this,
                        accountAddressExtractor = { it.stashState?.controllerAddress },
                        errorProducer = ManageStakingValidationFailure::ControllerRequired
                    ),
                    BalanceUnlockingLimitValidation(
                        this,
                        errorProducer = ManageStakingValidationFailure::UnbondingRequestLimitReached
                    )
                )
            )
        )
    }

    override fun getRebondValidation(): ValidationSystem<ManageStakingValidationPayload, ManageStakingValidationFailure> {
        return ValidationSystem(
            CompositeValidation(
                validations = listOf(
                    BalanceAccountRequiredValidation(
                        this,
                        accountAddressExtractor = { it.stashState?.controllerAddress },
                        errorProducer = ManageStakingValidationFailure::ControllerRequired
                    )
                )
            )
        )
    }

    private suspend fun ExtrinsicBuilder.constructUnbondExtrinsic(
        stashState: StakingState.Stash,
        currentBondedBalance: BigInteger,
        unbondAmount: BigInteger
    ): ExtrinsicBuilder {
        // see https://github.com/paritytech/substrate/blob/master/frame/staking/src/lib.rs#L1614
        // if account is nominating
        val resultedBalance = currentBondedBalance.minus(unbondAmount)
        val minBond = stakingRelayChainScenarioRepository.minimumNominatorBond(stashState.chain.utilityAsset)
        val needChill = stashState is StakingState.Stash.Nominator &&
            // and resulting bonded balance is less than min bond
            resultedBalance.compareTo(minBond) == -1

        if (needChill) {
            chill()
        }
        unbond(unbondAmount)
        return this
    }

    override fun getUnbondValidationSystem() = UnbondValidationSystem(
        CompositeValidation(
            validations = listOf(
                UnbondFeeValidation(
                    feeExtractor = { it.fee },
                    availableBalanceProducer = { it.asset.availableForStaking },
                    errorProducer = { UnbondValidationFailure.CannotPayFees }
                ),
                NotZeroUnbondValidation(
                    amountExtractor = { it.amount },
                    errorProvider = { UnbondValidationFailure.ZeroUnbond }
                ),
                UnbondLimitValidation(
                    stakingScenarioInteractor = this,
                    errorProducer = UnbondValidationFailure::UnbondLimitReached
                ),
                EnoughToUnbondValidation(this),
                CrossExistentialValidation(this)
            )
        )
    )

    override fun getRebondValidationSystem() = RebondValidationSystem(
        CompositeValidation(
            validations = listOf(
                RebondFeeValidation(
                    feeExtractor = { it.fee },
                    availableBalanceProducer = { it.controllerAsset.availableForStaking },
                    errorProducer = { RebondValidationFailure.CANNOT_PAY_FEE }
                ),
                NotZeroRebondValidation(
                    amountExtractor = { it.rebondAmount },
                    errorProvider = { RebondValidationFailure.ZERO_AMOUNT }
                ),
                EnoughToRebondValidation(this)
            )
        )
    )

    override fun provideRedeemValidationSystem() = RedeemValidationSystem(
        CompositeValidation(
            validations = listOf(
                RedeemFeeValidation(
                    feeExtractor = { it.fee },
                    availableBalanceProducer = { it.asset.availableForStaking },
                    errorProducer = { RedeemValidationFailure.CANNOT_PAY_FEES }
                )
            )
        )
    )

    override suspend fun provideBondMoreValidationSystem(): BondMoreValidationSystem {
        val availableForStaking = getAvailableForBondMoreBalance()
        return BondMoreValidationSystem(
            validation = CompositeValidation(
                validations = listOf(
                    EnoughToPayFeesValidation(
                        feeExtractor = { it.fee },
                        availableBalanceProducer = { availableForStaking },
                        errorProducer = { BondMoreValidationFailure.NOT_ENOUGH_TO_PAY_FEES },
                        extraAmountExtractor = { it.amount }
                    ),
                    NotZeroBondValidation(
                        amountExtractor = BondMoreValidationPayload::amount,
                        errorProvider = { BondMoreValidationFailure.ZERO_BOND }
                    )
                )
            )
        )
    }

    override suspend fun getAvailableForBondMoreBalance(): BigDecimal {
        return withContext(Dispatchers.Default) {
            val state = selectedAccountStakingStateFlow().first()
            val asset = stakingInteractor.currentAssetFlow().first()
            val availableForBondMore = if (state is StakingState.Stash.Nominator && state.stashId.contentEquals(state.controllerId).not()) {
                stakingInteractor.getStashBalance(state.stashId, asset.token.configuration)
            } else {
                asset.availableForStaking
            }
            availableForBondMore
        }
    }
}

class EraRelativeInfo(
    val daysLeft: Int,
    val daysPast: Int,
    val erasLeft: BigInteger,
    val erasPast: BigInteger
)
