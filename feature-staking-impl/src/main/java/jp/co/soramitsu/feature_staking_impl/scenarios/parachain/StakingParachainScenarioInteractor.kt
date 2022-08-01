package jp.co.soramitsu.feature_staking_impl.scenarios.parachain

import java.math.BigDecimal
import java.math.BigInteger
import java.util.Optional
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.AddressModel
import jp.co.soramitsu.common.address.createAddressModel
import jp.co.soramitsu.common.address.createEthereumAddressModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.utils.sumByBigInteger
import jp.co.soramitsu.common.validation.CompositeValidation
import jp.co.soramitsu.common.validation.ValidationSystem
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.requireHexPrefix
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.model.MetaAccount
import jp.co.soramitsu.feature_account_api.domain.model.accountId
import jp.co.soramitsu.feature_staking_api.data.StakingSharedState
import jp.co.soramitsu.feature_staking_api.domain.api.AccountIdMap
import jp.co.soramitsu.feature_staking_api.domain.api.IdentityRepository
import jp.co.soramitsu.feature_staking_api.domain.model.AtStake
import jp.co.soramitsu.feature_staking_api.domain.model.BlockNumber
import jp.co.soramitsu.feature_staking_api.domain.model.CandidateInfo
import jp.co.soramitsu.feature_staking_api.domain.model.Delegation
import jp.co.soramitsu.feature_staking_api.domain.model.DelegationScheduledRequest
import jp.co.soramitsu.feature_staking_api.domain.model.Identity
import jp.co.soramitsu.feature_staking_api.domain.model.RewardDestination
import jp.co.soramitsu.feature_staking_api.domain.model.Round
import jp.co.soramitsu.feature_staking_api.domain.model.StakingLedger
import jp.co.soramitsu.feature_staking_api.domain.model.StakingState
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.calls.parachainCancelDelegationRequest
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.calls.parachainCandidateBondMore
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.calls.parachainDelegatorBondMore
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.calls.parachainExecuteDelegationRequest
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.calls.parachainScheduleCandidateBondLess
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.calls.parachainScheduleDelegatorBondLess
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.calls.parachainScheduleRevokeDelegation
import jp.co.soramitsu.feature_staking_impl.data.network.subquery.SubQueryDelegationHistoryFetcher
import jp.co.soramitsu.feature_staking_impl.data.repository.StakingConstantsRepository
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.domain.getSelectedChain
import jp.co.soramitsu.feature_staking_impl.domain.model.NetworkInfo
import jp.co.soramitsu.feature_staking_impl.domain.model.Unbonding
import jp.co.soramitsu.feature_staking_impl.domain.model.toUnbonding
import jp.co.soramitsu.feature_staking_impl.domain.validations.balance.BalanceUnlockingLimitValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.balance.ManageStakingValidationFailure
import jp.co.soramitsu.feature_staking_impl.domain.validations.balance.ManageStakingValidationPayload
import jp.co.soramitsu.feature_staking_impl.domain.validations.rebond.RebondValidationPayload
import jp.co.soramitsu.feature_staking_impl.domain.validations.setup.MinimumAmountValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.setup.SetupStakingMaximumNominatorsValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.setup.SetupStakingPayload
import jp.co.soramitsu.feature_staking_impl.domain.validations.setup.SetupStakingValidationFailure
import jp.co.soramitsu.feature_staking_impl.domain.validations.unbond.UnbondValidationPayload
import jp.co.soramitsu.feature_staking_impl.presentation.staking.balance.model.StakingBalanceModel
import jp.co.soramitsu.feature_staking_impl.presentation.staking.balance.rebond.RebondKind
import jp.co.soramitsu.feature_staking_impl.scenarios.StakingScenarioInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.amountFromPlanks
import jp.co.soramitsu.feature_wallet_api.domain.model.planksFromAmount
import jp.co.soramitsu.feature_wallet_api.presentation.model.mapAmountToAmountModel
import jp.co.soramitsu.runtime.ext.accountIdOf
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.state.SingleAssetSharedState
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

class StakingParachainScenarioInteractor(
    private val stakingInteractor: StakingInteractor,
    private val accountRepository: AccountRepository,
    private val stakingConstantsRepository: StakingConstantsRepository,
    private val stakingParachainScenarioRepository: StakingParachainScenarioRepository,
    private val identityRepositoryImpl: IdentityRepository,
    private val stakingSharedState: StakingSharedState,
    private val iconGenerator: AddressIconGenerator,
    private val resourceManager: ResourceManager,
    private val delegationHistoryFetcher: SubQueryDelegationHistoryFetcher,
) : StakingScenarioInteractor {

    override suspend fun observeNetworkInfoState(): Flow<NetworkInfo> {
        val chainId = stakingInteractor.getSelectedChain().id
        val lockupPeriod = getParachainLockupPeriodInDays(chainId)
        val minimumStakeInPlanks = getMinimumStake(chainId)

        return flowOf(
            NetworkInfo.Parachain(
                lockupPeriodInDays = lockupPeriod,
                minimumStake = minimumStakeInPlanks
            )
        )
    }

    private suspend fun getParachainLockupPeriodInDays(chainId: ChainId): Int {
        val hoursInRound = hoursInRound[chainId] ?: return 0
        val lockupPeriodInRounds = stakingConstantsRepository.parachainLockupPeriodInRounds(chainId).toInt()
        val lockupPeriodInHours = lockupPeriodInRounds * hoursInRound
        return lockupPeriodInHours.toDuration(DurationUnit.HOURS).toInt(DurationUnit.DAYS)
    }

    // todo move to overrides parameter of chain_type.json
    val hoursInRound = mapOf(
        "fe58ea77779b7abda7da4ec526d14db9b1e9cd40a217c34892af80a9b332b76d" to 6, // moonbeam
        "401a1f9dca3da46f5c4091016c8a2f26dcea05865116b286f60f668207d1474b" to 2, // moonriver
        "91bc6e169807aaa54802737e1c504b2577d4fafedd5a02c10293b1cd60e39527" to 2 // moonbase
    )

    override val stakingStateFlow = combine(
        stakingInteractor.selectedChainFlow(),
        stakingInteractor.currentAssetFlow()
    ) { chain, asset -> chain to asset }.flatMapConcat { (chain, asset) ->
        val accountId = accountRepository.getSelectedMetaAccount().accountId(chain) ?: error("cannot find accountId")
        stakingParachainScenarioRepository.stakingStateFlow(chain, accountId)
    }

    suspend fun getIdentities(collatorsIds: List<AccountId>): Map<String, Identity?> {
        if (collatorsIds.isEmpty()) return emptyMap()
        val chain = stakingInteractor.getSelectedChain()
        return identityRepositoryImpl.getIdentitiesFromIds(chain, collatorsIds.map { it.toHexString(false) })
    }

    suspend fun getCurrentRound(chainId: ChainId): Result<Round> {
        return kotlin.runCatching { stakingParachainScenarioRepository.getCurrentRound(chainId) }
    }

    suspend fun getAtStake(chainId: ChainId, collatorId: AccountId): Result<AtStake> {
        return runCatching { stakingParachainScenarioRepository.getAtStakeOfCollator(chainId, collatorId, getCurrentRound(chainId).getOrThrow().current) }
    }

    suspend fun getStaked(chainId: ChainId): Result<BigInteger> {
        return runCatching { stakingParachainScenarioRepository.getStaked(chainId, getCurrentRound(chainId).getOrThrow().current) }
    }

    fun selectedAccountStakingStateFlow(
        metaAccount: MetaAccount,
        assetWithChain: SingleAssetSharedState.AssetWithChain
    ) = flow {
        val chain = assetWithChain.chain
        metaAccount.accountId(chain)?.let { accountId ->
            emitAll(stakingParachainScenarioRepository.stakingStateFlow(chain, accountId))
        }
    }

    override fun selectedAccountStakingStateFlow(): Flow<StakingState> {
        return stakingInteractor.selectionStateFlow().flatMapLatest { (selectedAccount, assetWithChain) ->
            selectedAccountStakingStateFlow(selectedAccount, assetWithChain)
        }
    }

    override suspend fun checkEnoughToUnbondValidation(payload: UnbondValidationPayload): Boolean {
        return payload.amount <= getUnstakeAvailableAmount(payload.asset, payload.collatorAddress?.fromHex())
    }

    override suspend fun checkEnoughToRebondValidation(payload: RebondValidationPayload): Boolean {
        return true
    }

    override suspend fun checkCrossExistentialValidation(payload: UnbondValidationPayload): Boolean {
        val tokenConfiguration = payload.asset.token.configuration

        val minimumStakeInPlanks = getMinimumStake(tokenConfiguration.chainId)
        val minimumStake = tokenConfiguration.amountFromPlanks(minimumStakeInPlanks)
        val unstakeAvailable = getUnstakeAvailableAmount(payload.asset, payload.collatorAddress?.fromHex())
        val resultGreaterThanMinimalStake = unstakeAvailable - payload.amount >= minimumStake
        val resultIsZero = unstakeAvailable == payload.amount
        return resultGreaterThanMinimalStake || resultIsZero
    }

    suspend fun maxDelegationsPerDelegator(): Int {
        return stakingConstantsRepository.maxDelegationsPerDelegator(stakingInteractor.getSelectedChain().id)
    }

    override suspend fun getMinimumStake(chainId: ChainId): BigInteger {
        return stakingConstantsRepository.parachainMinimumStaking(chainId)
    }

    override suspend fun maxNumberOfStakesIsReached(chainId: ChainId): Boolean {
        val chain = stakingInteractor.getChain(chainId)
        val accountId = accountRepository.getSelectedMetaAccount().accountId(chain) ?: error("cannot find accountId")
        val maxDelegations = maxDelegationsPerDelegator()
        val delegatorState = stakingParachainScenarioRepository.getDelegatorState(chainId, accountId)
        val currentDelegationsCount = delegatorState?.delegations?.size ?: return false
        return currentDelegationsCount >= maxDelegations
    }

    override suspend fun currentUnbondingsFlow(collatorAddress: String?): Flow<List<Unbonding>> {
        collatorAddress ?: throw IllegalArgumentException("No collator address provided")
        val chain = stakingInteractor.getSelectedChain()
        val accountId = accountRepository.getSelectedMetaAccount().accountId(chain) ?: error("cannot find accountId")
        val delegationHistoryFlow: Flow<List<Unbonding>> = flowOf(
            delegationHistoryFetcher.fetchDelegationHistory(chain.id, accountId.toHexString(true), collatorAddress)
        ).onStart { emit(emptyList()) }

        val unbondingRequestsFlow = getUnbondingRequestsFlow(chain.id, collatorAddress.fromHex())

        return combine(unbondingRequestsFlow, delegationHistoryFlow) { currentUnbondings: List<Unbonding>, subQueryHistory: List<Unbonding> ->
            currentUnbondings + subQueryHistory
        }
    }

    suspend fun List<DelegationScheduledRequest>.toUnbondings(): List<Unbonding> {
        val chain = stakingInteractor.getSelectedChain()
        val accountId = accountRepository.getSelectedMetaAccount().accountId(chain) ?: error("cannot find accountId")

        val userRequests = filter {
            it.delegator.contentEquals(accountId)
        }

        val currentRound = getCurrentRound(chain.id).getOrThrow()
        val currentBlock = stakingInteractor.currentBlockNumber()
        val hoursInRound = hoursInRound[chain.id] ?: 0
        val unbondings = userRequests.map {
            val timeLeft = calculateTimeTillTheRoundStart(currentRound, currentBlock, it.whenExecutable, hoursInRound)
            it.toUnbonding(timeLeft)
        }
        return unbondings
    }

    override suspend fun getSelectedAccountStakingState(): StakingState {
        return selectedAccountStakingStateFlow().first()
    }

    override fun getSelectedAccountAddress(): Flow<Optional<AddressModel>> {
        return stakingInteractor.selectedAccountProjectionFlow().map {
            if (it.isEthereumBased) {
                Optional.of(iconGenerator.createEthereumAddressModel(it.address, AddressIconGenerator.SIZE_MEDIUM, it.name))
            } else {
                Optional.of(iconGenerator.createAddressModel(it.address, AddressIconGenerator.SIZE_SMALL, it.name))
            }
        }
    }

    override fun getCollatorAddress(collatorAddress: String?): Flow<Optional<AddressModel>> {
        collatorAddress ?: throw IllegalArgumentException("No collator address provided")
        return channelFlow {
            val identities = getIdentities(listOf(collatorAddress.fromHex()))
            val collatorWoPrefix = collatorAddress.fromHex().toHexString()
            val name = identities[collatorWoPrefix]?.display

            val model = iconGenerator.createEthereumAddressModel(collatorAddress, AddressIconGenerator.SIZE_MEDIUM, name)
            send(Optional.of(model))
        }
    }

    override suspend fun stakeMore(extrinsicBuilder: ExtrinsicBuilder, amountInPlanks: BigInteger, candidate: String?): ExtrinsicBuilder {
        require(candidate != null) {
            "Candidate address not specified for stake more"
        }

        val chain = stakingInteractor.getSelectedChain()
        val accountId = accountRepository.getSelectedMetaAccount().accountId(chain) ?: error("cannot find accountId")

        val userIsCollator = candidate.fromHex().contentEquals(accountId)
        return if (userIsCollator) {
            extrinsicBuilder.parachainCandidateBondMore(amountInPlanks)
        } else {
            extrinsicBuilder.parachainDelegatorBondMore(candidate, amountInPlanks)
        }
    }

    override suspend fun stakeLess(
        extrinsicBuilder: ExtrinsicBuilder,
        amountInPlanks: BigInteger,
        stashState: StakingState,
        currentBondedBalance: BigInteger,
        candidate: String?
    ) {
        require(stashState is StakingState.Parachain)
        require(candidate != null) {
            "Candidate address not specified for stake less"
        }
        val chain = stakingInteractor.getSelectedChain()
        val accountId = accountRepository.getSelectedMetaAccount().accountId(chain) ?: error("cannot find accountId")
        val asset = stakingInteractor.currentAssetFlow().first()
        val maxUnstakeAmount = getUnstakeAvailableAmount(asset, candidate.fromHex())
        val maxInPlanks = asset.token.planksFromAmount(maxUnstakeAmount)
        val userIsCollator = candidate.fromHex().contentEquals(accountId)
        val performRevoke = maxInPlanks == amountInPlanks
        when {
            userIsCollator -> extrinsicBuilder.parachainScheduleCandidateBondLess(amountInPlanks)
            performRevoke -> extrinsicBuilder.parachainScheduleRevokeDelegation(candidate)
            else -> extrinsicBuilder.parachainScheduleDelegatorBondLess(candidate, amountInPlanks)
        }
    }

    override suspend fun confirmRevoke(
        extrinsicBuilder: ExtrinsicBuilder,
        candidate: String?,
        stashState: StakingState
    ) {
        require(stashState is StakingState.Parachain)
        require(candidate != null) {
            "Candidate address not specified for stake less"
        }
        val chain = stakingInteractor.getSelectedChain()
        val accountId = accountRepository.getSelectedMetaAccount().accountId(chain) ?: error("cannot find accountId")

        extrinsicBuilder.parachainExecuteDelegationRequest(candidateId = candidate.fromHex(), delegatorId = accountId)
    }

    private fun getUnbondingRequestsFlow(chainId: ChainId, collatorId: AccountId) =
        stakingParachainScenarioRepository.getDelegationScheduledRequestsFlow(chainId, collatorId).map {
            it.toUnbondings()
        }

    override suspend fun getStakingBalanceFlow(collatorId: AccountId?): Flow<StakingBalanceModel> {
        collatorId ?: error("cannot find collatorId")
        val chain = stakingInteractor.getSelectedChain()
        val accountId = accountRepository.getSelectedMetaAccount().accountId(chain) ?: error("cannot find accountId")
        val delegatorStateFlow = stakingParachainScenarioRepository.stakingStateFlow(chain, accountId).filterIsInstance<StakingState.Parachain.Delegator>()
        val delegationScheduledRequestsFlow = stakingParachainScenarioRepository.getDelegationScheduledRequestsFlow(chain.id, collatorId)

        return combine(
            stakingInteractor.currentAssetFlow(),
            delegatorStateFlow,
            delegationScheduledRequestsFlow,
        ) { asset, delegatorState, delegationScheduledRequests ->
            val staked = delegatorState.delegations.firstOrNull {
                it.collatorId.contentEquals(collatorId)
            }?.delegatedAmountInPlanks.orZero()

            val currentRound = getCurrentRound(chain.id).getOrNull()?.current ?: BigInteger.ZERO
            val userRequests = delegationScheduledRequests.filter {
                it.delegator.contentEquals(accountId)
            }

            val unstaking = userRequests.filter {
                it.whenExecutable > currentRound
            }.sumByBigInteger { it.actionValue }

            val readyForUnlocking = userRequests.filter {
                it.whenExecutable <= currentRound
            }.sumByBigInteger { it.actionValue }

            StakingBalanceModel(
                staked = mapAmountToAmountModel(staked, asset, R.string.staking_main_stake_balance_staked),
                unstaking = mapAmountToAmountModel(unstaking, asset, R.string.wallet_balance_unbonding_v1_9_0),
                redeemable = mapAmountToAmountModel(readyForUnlocking, asset, R.string.staking_balance_ready_for_unlocking)
            )
        }
    }

    override suspend fun getRebondingUnbondings(collatorAddress: String?): List<Unbonding> = flowOf(
        stakingInteractor.getSelectedChain()
    ).flatMapLatest { chain ->
        collatorAddress ?: error("cannot find collatorAddress")
        getUnbondingRequestsFlow(chain.id, collatorAddress.fromHex())
    }
        .map { it.filter { it.timeLeft > 0 } }
        .first()

    override fun rebond(extrinsicBuilder: ExtrinsicBuilder, amount: BigInteger, candidate: String?): ExtrinsicBuilder {
        candidate ?: error("cannot find collatorAddress")

        return extrinsicBuilder.parachainCancelDelegationRequest(candidate)
    }

    override fun getRebondTypes(): Set<RebondKind> = setOf(RebondKind.ALL)

    override suspend fun overrideUnbondHint(): String {
        val chain = stakingInteractor.getSelectedChain()
        val bondLessDelayInRounds = stakingConstantsRepository.candidateBondLessDelay(chain.id)
        val hoursInRound = hoursInRound[chain.id] ?: 0
        val delayInHours = bondLessDelayInRounds * hoursInRound
        val timePart = if (delayInHours < 24) {
            resourceManager.getQuantityString(R.plurals.common_hours_format, delayInHours, delayInHours)
        } else {
            val delayInDays = delayInHours / 24
            resourceManager.getQuantityString(R.plurals.days_format, delayInDays, delayInDays)
        }
        val roundsPart = resourceManager.getQuantityString(R.plurals.rounds_format, bondLessDelayInRounds, bondLessDelayInRounds)
        val unbondDurationHint = resourceManager.getString(R.string.parachain_staking_unbonding_period_template, roundsPart, chain.name, timePart)
        return resourceManager.getString(R.string.parachain_staking_unbonding_hint_template, unbondDurationHint)
    }

    override fun overrideRedeemActionTitle(): Int = R.string.parachain_staking_unlock
    override fun overrideUnbondAvailableLabel(): Int? = null
    override fun getRebondAvailableAmount(asset: Asset, amount: BigDecimal) = amount

    override suspend fun getUnstakeAvailableAmount(asset: Asset, collatorId: AccountId?): BigDecimal {
        collatorId ?: error("cannot find collatorId")

        val chainToAccountIdFlow = jp.co.soramitsu.common.utils.flowOf {
            val chain = stakingInteractor.getSelectedChain()
            val accountId = accountRepository.getSelectedMetaAccount().accountId(chain) ?: error("cannot find accountId")
            chain to accountId
        }

        val availableToStakeLessFlow = chainToAccountIdFlow.flatMapLatest { (chain, accountId) ->
            combine(
                stakingParachainScenarioRepository.stakingStateFlow(chain, accountId).filterIsInstance<StakingState.Parachain.Delegator>(),
                stakingParachainScenarioRepository.getDelegationScheduledRequestsFlow(chain.id, collatorId),
            ) { delegatorState, delegationScheduledRequests ->
                val staked = delegatorState.delegations.firstOrNull {
                    it.collatorId.contentEquals(collatorId)
                }?.delegatedAmountInPlanks.orZero()

                val currentRound = getCurrentRound(chain.id).getOrNull()?.current ?: BigInteger.ZERO

                val userRequests = delegationScheduledRequests.filter {
                    it.delegator.contentEquals(accountId)
                }
                val unstaking = userRequests.filter {
                    it.whenExecutable >= currentRound
                }.sumByBigInteger { it.actionValue }

                val readyForUnlocking = userRequests.filter {
                    it.whenExecutable < currentRound
                }.sumByBigInteger { it.actionValue }

                val availableToStakeLess = staked - unstaking - readyForUnlocking
                val amount = asset.token.amountFromPlanks(availableToStakeLess)
                amount
            }
        }

        return availableToStakeLessFlow.first()
    }

    override suspend fun accountIsNotController(controllerAddress: String): Boolean {
        return true
    }

    override suspend fun ledger(): StakingLedger? {
        return null
    }

    override suspend fun checkAccountRequiredValidation(accountAddress: String?): Boolean {
        accountAddress ?: return true
        val currentStakingState = selectedAccountStakingStateFlow().first()
        val chain = currentStakingState.chain

        return accountRepository.isAccountExists(chain.accountIdOf(accountAddress))
    }

    override suspend fun maxStakersPerBlockProducer(): Int {
        return maxDelegationsPerDelegator()
    }

    override suspend fun unstakingPeriod(): Int {
        val chainId = stakingInteractor.getSelectedChain().id
        return getParachainLockupPeriodInDays(chainId)
    }

    override suspend fun stakePeriodInHours(): Int {
        val chainId = stakingInteractor.getSelectedChain().id
        return hoursInRound[chainId] ?: error("Chain id is not found in round duration map")
    }

    override suspend fun getRewardDestination(accountStakingState: StakingState): RewardDestination {
        require(accountStakingState is StakingState.Parachain)
        return RewardDestination.Payout(accountStakingState.accountId)
    }

    suspend fun getCollator(collatorId: AccountId): CandidateInfo {
        val chainId = stakingInteractor.getSelectedChain().id
        return stakingParachainScenarioRepository.getCandidateInfo(chainId, collatorId)
    }

    suspend fun getIdentity(collatorId: AccountId): Identity? {
        val chain = stakingInteractor.getSelectedChain()
        val identities = identityRepositoryImpl.getIdentitiesFromIdsBytes(chain, listOf(collatorId))
        return identities[collatorId.toHexString()]
    }

    suspend fun getCandidateInfos(chainId: ChainId, addresses20: List<ByteArray>): AccountIdMap<CandidateInfo> {
        return stakingParachainScenarioRepository.getCandidateInfos(chainId, addresses20)
    }

    suspend fun getBottomDelegations(chainId: ChainId, addresses20: List<ByteArray>): AccountIdMap<List<Delegation>> {
        return stakingParachainScenarioRepository.getBottomDelegations(chainId, addresses20)
    }

    override fun getSetupStakingValidationSystem(): ValidationSystem<SetupStakingPayload, SetupStakingValidationFailure> {
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
                emptyList()
            )
        )
    }

    override fun getBondMoreValidation(): ValidationSystem<ManageStakingValidationPayload, ManageStakingValidationFailure> {
        return ValidationSystem(
            CompositeValidation(
                emptyList()
            )
        )
    }

    override fun getUnbondingValidation(): ValidationSystem<ManageStakingValidationPayload, ManageStakingValidationFailure> {
        return ValidationSystem(
            CompositeValidation(
                validations = listOf(
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
                validations = listOf()
            )
        )
    }

    suspend fun getLeaveCandidatesDelay(): Result<Int> {
        val chainId = stakingInteractor.getSelectedChain().id
        return kotlin.runCatching { stakingConstantsRepository.parachainLeaveCandidatesDelay(chainId).toInt() }
    }

    private fun calculateTimeTillTheRoundStart(currentRound: Round, currentBlock: BlockNumber, roundNumber: BigInteger, hoursInRound: Int): Long {
        if (roundNumber <= currentRound.current) return 0

        val currentRoundFinishAtBlock = currentRound.first + currentRound.length
        val blocksTillTheEndOfRound = currentRoundFinishAtBlock - currentBlock
        val secondsInRound = (hoursInRound * 60 * 60).toBigDecimal()
        val secondsInBlock = secondsInRound / currentRound.length.toBigDecimal()
        val secondsTillTheEndOfRound = blocksTillTheEndOfRound.toBigDecimal() * secondsInBlock

        val wholeRoundsMore = roundNumber - currentRound.current - BigInteger.ONE
        val secondsInWholeRounds = wholeRoundsMore.toBigDecimal() * secondsInRound

        val secondTillRound = secondsTillTheEndOfRound + secondsInWholeRounds
        val millisecondTillRound = secondTillRound * BigDecimal(1000)
        return millisecondTillRound.toLong()
    }

    suspend fun getCollatorIdsWithReadyToUnlockingTokens(collatorIds: List<AccountId>, accountId: AccountId): List<AccountId> {
        val chainId = stakingInteractor.getSelectedChain().id
        val currentRound = getCurrentRound(chainId).getOrNull()?.current ?: BigInteger.ZERO
        val delegationScheduledRequests = runCatching {
            stakingParachainScenarioRepository.getScheduledRequests(chainId, collatorIds).mapValues {
                it.value?.filter { request -> request.delegator.contentEquals(accountId) }
            }.filter {
                it.value?.any { scheduledRequest -> scheduledRequest.whenExecutable <= currentRound } == true
            }
        }.getOrNull()
        return delegationScheduledRequests?.keys?.map { it.requireHexPrefix().fromHex() } ?: emptyList()
    }
}
