package jp.co.soramitsu.staking.impl.scenarios.relaychain

import com.google.gson.Gson
import com.google.gson.JsonObject
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import io.mockk.slot
import io.mockk.verify
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.common.validation.CompositeValidation
import jp.co.soramitsu.common.validation.ValidationStatus
import jp.co.soramitsu.coredb.dao.StakingTotalRewardDao
import jp.co.soramitsu.coredb.model.TotalRewardLocal
import jp.co.soramitsu.coredb.model.cache.FreshAssetCacheUpdate
import jp.co.soramitsu.core.runtime.ChainConnection
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.fearless_utils.runtime.metadata.ExtrinsicMetadata
import jp.co.soramitsu.fearless_utils.runtime.metadata.RuntimeMetadata
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.RuntimeRequest
import jp.co.soramitsu.fearless_utils.wsrpc.response.RpcResponse
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraMainChainId
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeProvider
import jp.co.soramitsu.runtime.state.SingleAssetSharedState
import jp.co.soramitsu.staking.api.data.StakingAssetSelection
import jp.co.soramitsu.staking.api.data.StakingSharedState
import jp.co.soramitsu.staking.api.domain.api.IdentityRepository
import jp.co.soramitsu.staking.api.domain.model.DelegationAction
import jp.co.soramitsu.staking.api.domain.model.Identity
import jp.co.soramitsu.staking.api.domain.model.IndividualExposure
import jp.co.soramitsu.staking.api.domain.model.LegacyExposure
import jp.co.soramitsu.staking.api.domain.model.Nominations
import jp.co.soramitsu.staking.api.domain.model.RewardDestination
import jp.co.soramitsu.staking.api.domain.model.RootIdentity
import jp.co.soramitsu.staking.api.domain.model.SlashingSpans
import jp.co.soramitsu.staking.api.domain.model.StakingLedger
import jp.co.soramitsu.staking.api.domain.model.StakingState
import jp.co.soramitsu.staking.api.domain.model.UnlockChunk
import jp.co.soramitsu.staking.api.domain.model.ValidatorPrefs
import jp.co.soramitsu.staking.impl.StakingLaneFixtures
import jp.co.soramitsu.staking.impl.data.model.Payout
import jp.co.soramitsu.staking.impl.data.repository.AssetHubMetadataContractTest
import jp.co.soramitsu.staking.impl.data.repository.PayoutRepository
import jp.co.soramitsu.staking.impl.data.repository.StakingConstantsRepository
import jp.co.soramitsu.staking.impl.data.repository.StakingRewardsRepository
import jp.co.soramitsu.staking.impl.domain.EraTimeCalculator
import jp.co.soramitsu.staking.impl.domain.EraTimeCalculatorFactory
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.domain.model.NetworkInfo
import jp.co.soramitsu.staking.impl.domain.model.NominatorStatus
import jp.co.soramitsu.staking.impl.domain.model.StakeSummarySource
import jp.co.soramitsu.staking.impl.domain.model.StashNoneStatus
import jp.co.soramitsu.staking.impl.domain.model.ValidatorStatus
import jp.co.soramitsu.staking.impl.domain.validations.balance.ManageStakingValidationFailure
import jp.co.soramitsu.staking.impl.domain.validations.balance.ManageStakingValidationPayload
import jp.co.soramitsu.staking.impl.domain.validations.bond.BondMoreValidationFailure
import jp.co.soramitsu.staking.impl.domain.validations.bond.BondMoreValidationPayload
import jp.co.soramitsu.staking.impl.domain.validations.rebond.RebondValidationFailure
import jp.co.soramitsu.staking.impl.domain.validations.rebond.RebondValidationPayload
import jp.co.soramitsu.staking.impl.domain.validations.reedeem.RedeemValidationFailure
import jp.co.soramitsu.staking.impl.domain.validations.reedeem.RedeemValidationPayload
import jp.co.soramitsu.staking.impl.domain.validations.unbond.UnbondValidationFailure
import jp.co.soramitsu.staking.impl.domain.validations.unbond.UnbondValidationPayload
import jp.co.soramitsu.staking.impl.presentation.staking.balance.rebond.RebondKind
import jp.co.soramitsu.wallet.api.data.cache.AssetCache
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletConstants
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real relay-chain scenario interactor over mocked repositories, storage and runtime collaborators. */
class StakingRelayChainScenarioInteractorCoverageTest {

    private val chainAsset = StakingLaneFixtures.chainAsset()
    private val chain = StakingLaneFixtures.chain(chainAsset)
    private val stashId = StakingLaneFixtures.accountId
    private val controllerId = ByteArray(32) { 8 }
    private val asset = StakingLaneFixtures.asset(chainAsset)
    private val activeEra = BigInteger.valueOf(100)

    private val stakingInteractor = mockk<StakingInteractor>(relaxed = true)
    private val accountRepository = mockk<AccountRepository>(relaxed = true)
    private val walletRepository = mockk<WalletRepository>(relaxed = true)
    private val constants = mockk<StakingConstantsRepository>(relaxed = true)
    private val repository = mockk<StakingRelayChainScenarioRepository>(relaxed = true)
    private val rewardsRepository = mockk<StakingRewardsRepository>(relaxed = true)
    private val factory = mockk<EraTimeCalculatorFactory>(relaxed = true)
    private val sharedState = mockk<StakingSharedState>(relaxed = true)
    private val identityRepository = mockk<IdentityRepository>(relaxed = true)
    private val payoutRepository = mockk<PayoutRepository>(relaxed = true)
    private val walletConstants = mockk<WalletConstants>(relaxed = true)
    private val chainRegistry = mockk<ChainRegistry>(relaxed = true)
    private val totalRewardDao = mockk<StakingTotalRewardDao>(relaxed = true)
    private val assetCache = mockk<AssetCache>(relaxed = true)
    private val calculator = mockk<EraTimeCalculator>()

    private val interactor by lazy {
        StakingRelayChainScenarioInteractor(
            stakingInteractor, accountRepository, walletRepository, constants, repository, rewardsRepository,
            factory, sharedState, identityRepository, payoutRepository, walletConstants, chainRegistry,
            totalRewardDao, assetCache
        )
    }

    private fun wallet(substrate: ByteArray? = stashId) = MetaAccount(
        id = 1L,
        chainAccounts = emptyMap(),
        favoriteChains = emptyMap(),
        substratePublicKey = null,
        substrateCryptoType = null,
        substrateAccountId = substrate,
        ethereumAddress = null,
        ethereumPublicKey = null,
        tonPublicKey = null,
        isSelected = true,
        isBackedUp = true,
        googleBackupAddress = null,
        name = "Main",
        initialized = true
    )

    private fun nominator(submittedInEra: Long = 90, controller: ByteArray = controllerId) = StakingState.Stash.Nominator(
        chain, stashId, controller, stashId, Nominations(listOf(ByteArray(32) { 3 }), BigInteger.valueOf(submittedInEra), false)
    )

    private fun plainBuilder(): ExtrinsicBuilder {
        val runtime = RuntimeSnapshot(mockk(relaxed = true), RuntimeMetadata(BigInteger.ONE, emptyMap(), ExtrinsicMetadata(BigInteger.valueOf(4), emptyList())))
        val builder = mockk<ExtrinsicBuilder>()
        every { builder.runtime } returns runtime
        every { builder.call(any<String>(), any<String>(), any()) } returns builder
        return builder
    }

    private fun selectState(state: StakingState, selectedChain: jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain = chain) {
        val selected = SingleAssetSharedState.AssetWithChain(selectedChain, selectedChain.assets.first())
        every { stakingInteractor.selectionStateFlow() } returns flowOf(wallet() to selected)
        every { repository.stakingStateFlow(any(), any(), any()) } returns flowOf(state)
    }

    private fun stubEraClock() {
        // 6 s blocks, 2400 blocks per session, 6 sessions: one era a day.
        coEvery { repository.blockCreationTime(any()) } returns BigInteger.valueOf(6000)
        coEvery { repository.eraLength(any()) } returns BigInteger.valueOf(6)
        coEvery { repository.sessionLength(any()) } returns BigInteger.valueOf(2400)
        coEvery { factory.create(any()) } returns calculator
        every { calculator.calculate(any()) } returns BigInteger.valueOf(3000)
        every { calculator.calculateTillEraSet(any()) } returns BigInteger.valueOf(1000)
    }

    private fun stubSummarySources(exposures: Map<String, LegacyExposure>) {
        every { repository.observeActiveEraIndex(any()) } returns flowOf(activeEra)
        every { sharedState.currentAssetFlow() } returns flowOf(asset)
        every { rewardsRepository.totalRewardFlow(any()) } returns emptyFlow()
        coEvery { repository.getLegacyActiveElectedValidatorsExposures(any()) } returns exposures
        coEvery { constants.maxRewardedNominatorPerValidator(any()) } returns null
        coEvery { repository.minimumNominatorBond(any()) } returns BigInteger.ZERO
        coEvery { repository.minimumActiveStake(any()) } returns BigInteger.ONE
        stubEraClock()
    }

    @Test
    fun `network info combines lockup, minimum stake, total stake and rewarded nominators`() = runTest {
        stubEraClock()
        every { sharedState.assetWithChain } returns flowOf(SingleAssetSharedState.AssetWithChain(chain, chainAsset))
        coEvery { constants.lockupPeriodInEras(chain.id) } returns BigInteger.valueOf(28)
        coEvery { constants.maxRewardedNominatorPerValidator(chain.id) } returns 1
        coEvery { repository.minimumNominatorBond(any()) } returns BigInteger.valueOf(5)
        coEvery { repository.minimumActiveStake(chain.id) } returns BigInteger.valueOf(3)
        val first = LegacyExposure(BigInteger.valueOf(100), BigInteger.TEN, listOf(
            IndividualExposure(ByteArray(32) { 1 }, BigInteger.valueOf(40)),
            IndividualExposure(ByteArray(32) { 2 }, BigInteger.valueOf(50))
        ))
        val second = LegacyExposure(BigInteger.valueOf(60), BigInteger.TEN, listOf(IndividualExposure(ByteArray(32) { 1 }, BigInteger.valueOf(50))))
        every { repository.legacyElectedExposuresInActiveEra(chain.id) } returns flowOf(mapOf("a" to first, "b" to second))

        val info = interactor.observeNetworkInfoState().first()

        assertEquals(NetworkInfo.RelayChain(672, BigInteger.valueOf(5), BigInteger.valueOf(160), 2, true), info)
    }

    @Test
    fun `staking state flows follow the selected wallet and chain`() = runTest {
        val state = nominator()
        every { sharedState.assetWithChain } returns flowOf(SingleAssetSharedState.AssetWithChain(chain, chainAsset))
        every { accountRepository.selectedMetaAccountFlow() } returns flowOf(wallet())
        every { repository.stakingStateFlow(chain, chainAsset, stashId) } returns flowOf(state)
        coEvery { stakingInteractor.getChain(chain.id) } returns chain

        assertSame(state, interactor.stakingStateFlow().first())
        assertSame(state, interactor.stakingStateFlow(chain.id).first())
        val selected = SingleAssetSharedState.AssetWithChain(chain, chainAsset)
        assertSame(state, interactor.selectedAccountStakingStateFlow(wallet(), selected).first())
    }

    @Test
    fun `wallet without an account on the chain has no staking state`() = runTest {
        every { sharedState.assetWithChain } returns flowOf(SingleAssetSharedState.AssetWithChain(chain, chainAsset))
        every { accountRepository.selectedMetaAccountFlow() } returns flowOf(wallet(substrate = null))

        assertTrue(interactor.stakingStateFlow().toList().isEmpty())
        verify(exactly = 0) { repository.stakingStateFlow(any(), any(), any()) }
    }

    @Test
    fun `nominator summary is active when the stash is in an exposure`() = runTest {
        val exposure = LegacyExposure(BigInteger.TEN, BigInteger.ONE, listOf(IndividualExposure(stashId, BigInteger.ONE)))
        stubSummarySources(mapOf("v" to exposure))

        val summary = interactor.observeNominatorSummary(nominator()).filterNotNull().first()

        assertSame(NominatorStatus.Active, summary.status)
        assertEquals(100, summary.currentEra)
        assertEquals(0, summary.totalStaked!!.compareTo(BigDecimal(50)))
        assertEquals(0, summary.totalReward.signum())
        assertEquals(StakeSummarySource.from(asset), summary.source)
    }

    @Test
    fun `nominator below the minimum stake is inactive for that reason`() = runTest {
        stubSummarySources(emptyMap())
        coEvery { repository.minimumActiveStake(any()) } returns StakingLaneFixtures.planks(100)

        val status = interactor.observeNominatorSummary(nominator()).filterNotNull().first().status

        assertEquals(NominatorStatus.Inactive.Reason.MIN_STAKE, (status as NominatorStatus.Inactive).reason)
    }

    @Test
    fun `fresh nomination waits for the next era`() = runTest {
        stubSummarySources(emptyMap())

        val status = interactor.observeNominatorSummary(nominator(submittedInEra = 100)).filterNotNull().first().status

        assertEquals(3000L, (status as NominatorStatus.Waiting).timeLeft)
        verify { calculator.calculate(BigInteger.valueOf(101)) }
    }

    @Test
    fun `old nomination without active validators is inactive`() = runTest {
        stubSummarySources(emptyMap())

        val status = interactor.observeNominatorSummary(nominator(submittedInEra = 90)).filterNotNull().first().status

        assertEquals(NominatorStatus.Inactive.Reason.NO_ACTIVE_VALIDATOR, (status as NominatorStatus.Inactive).reason)
    }

    @Test
    fun `validator and stash summaries resolve their own status`() = runTest {
        val validator = StakingState.Stash.Validator(chain, stashId, stashId, stashId, ValidatorPrefs(BigDecimal("0.05"), false))
        stubSummarySources(mapOf(stashId.toHexString() to LegacyExposure(BigInteger.TEN, BigInteger.TEN, emptyList())))
        assertEquals(ValidatorStatus.ACTIVE, interactor.observeValidatorSummary(validator).filterNotNull().first().status)

        stubSummarySources(emptyMap())
        assertEquals(ValidatorStatus.INACTIVE, interactor.observeValidatorSummary(validator).filterNotNull().first().status)

        val none = StakingState.Stash.None(chain, stashId, stashId, stashId)
        assertEquals(StashNoneStatus.INACTIVE, interactor.observeStashSummary(none).filterNotNull().first().status)
    }

    @Test
    fun `summary of another account's asset stays empty`() = runTest {
        stubSummarySources(emptyMap())
        every { sharedState.currentAssetFlow() } returns flowOf(StakingLaneFixtures.asset(StakingLaneFixtures.chainAsset(chainId = "other")))

        assertEquals(listOf(null), interactor.observeStashSummary(StakingState.Stash.None(chain, stashId, stashId, stashId)).toList())
    }

    @Test
    fun `minimum stake uses the larger of active stake and nominator bond`() = runTest {
        coEvery { repository.minimumActiveStake(chain.id) } returns null
        assertEquals(BigInteger.valueOf(7), interactor.minimumStake(chain.id, emptyList(), BigInteger.valueOf(7)))
        val exposure = LegacyExposure(BigInteger.TEN, BigInteger.ONE, listOf(IndividualExposure(stashId, BigInteger.valueOf(30))))
        assertEquals(BigInteger.valueOf(30), interactor.minimumStake(chain.id, listOf(exposure), BigInteger.valueOf(7)))
    }

    @Test
    fun `pending payouts are sorted, named and flagged when close to expiry`() = runTest {
        stubEraClock()
        selectState(nominator())
        coEvery { repository.getActiveEraIndex(chain.id) } returns activeEra
        coEvery { repository.getHistoryDepth(chain.id) } returns BigInteger.valueOf(84)
        coEvery { payoutRepository.calculateUnpaidPayouts(any()) } returns listOf(
            Payout("validator-b", BigInteger.valueOf(90), StakingLaneFixtures.planks(2)),
            Payout("validator-a", BigInteger.valueOf(30), StakingLaneFixtures.planks(1), page = BigInteger.ZERO)
        )
        coEvery { identityRepository.getIdentitiesFromAddresses(chain, any()) } returns
            mapOf<String, Identity?>("validator-a" to RootIdentity("Alice", null, null, null, null, null, null, null))

        val statistics = interactor.calculatePendingPayouts().getOrThrow()

        assertEquals(listOf(BigInteger.valueOf(30), BigInteger.valueOf(90)), statistics.payouts.map { it.era })
        assertEquals(listOf(true, false), statistics.payouts.map { it.closeToExpire })
        assertEquals("Alice", statistics.payouts.first().validatorInfo.identityName)
        assertNull(statistics.payouts.last().validatorInfo.identityName)
        assertEquals(1000L, statistics.payouts.first().timeLeft)
        assertEquals(BigInteger.ZERO, statistics.payouts.first().page)
        assertEquals(StakingLaneFixtures.planks(3), statistics.totalAmountInPlanks)
        coVerify(exactly = 0) { totalRewardDao.insert(any()) }
    }

    @Test
    fun `sora payouts are cached as the total reward`() = runTest {
        val soraChain = StakingLaneFixtures.chain(StakingLaneFixtures.chainAsset(chainId = soraMainChainId, symbol = "XOR", precision = 18))
        val state = StakingState.Stash.Nominator(soraChain, stashId, stashId, stashId, Nominations(emptyList(), BigInteger.ONE, false))
        stubEraClock()
        selectState(state, soraChain)
        coEvery { repository.getActiveEraIndex(any()) } returns activeEra
        coEvery { repository.getHistoryDepth(any()) } returns BigInteger.valueOf(84)
        coEvery { payoutRepository.calculateUnpaidPayouts(any()) } returns listOf(Payout("v", BigInteger.valueOf(99), BigInteger.valueOf(500)))
        coEvery { identityRepository.getIdentitiesFromAddresses(any(), any()) } returns emptyMap()

        assertTrue(interactor.calculatePendingPayouts().isSuccess)

        coVerify { totalRewardDao.insert(TotalRewardLocal(state.accountAddress, BigInteger.valueOf(500))) }
    }

    @Test
    fun `pending payouts of an account without a stash fail`() = runTest {
        stubEraClock()
        selectState(StakingState.NonStash(chain, stashId))

        assertTrue(interactor.calculatePendingPayouts().exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `reward sync listener recalculates payouts only for sora`() = runTest {
        val listener = slot<suspend (String) -> Unit>()
        every { stakingInteractor.syncStakingRewardListener = capture(listener) } just Runs
        every { stakingInteractor.selectionStateFlow() } returns emptyFlow()
        interactor.hashCode()

        listener.captured.invoke(chain.id)
        verify(exactly = 0) { stakingInteractor.selectionStateFlow() }
        listener.captured.invoke(soraMainChainId)
        verify { stakingInteractor.selectionStateFlow() }
    }

    @Test
    fun `stake more, rebond and unbond build staking calls`() = runTest {
        val builder = plainBuilder()
        coEvery { repository.minimumNominatorBond(any()) } returns StakingLaneFixtures.planks(10)

        interactor.stakeMore(builder, BigInteger.TEN, null)
        interactor.rebond(builder, BigInteger.ONE, null)
        interactor.stakeLess(builder, StakingLaneFixtures.planks(5), nominator(), StakingLaneFixtures.planks(50), null) { true }

        verify { builder.call("Staking", "bond_extra", mapOf("max_additional" to BigInteger.TEN)) }
        verify { builder.call("Staking", "rebond", mapOf("value" to BigInteger.ONE)) }
        verify { builder.call("Staking", "unbond", mapOf("value" to StakingLaneFixtures.planks(5))) }
        verify(exactly = 0) { builder.call("Staking", "chill", any()) }
    }

    @Test
    fun `nominator unbonding below the minimum bond chills first`() = runTest {
        val builder = plainBuilder()
        coEvery { repository.minimumNominatorBond(any()) } returns StakingLaneFixtures.planks(10)

        interactor.stakeLess(builder, StakingLaneFixtures.planks(45), nominator(), StakingLaneFixtures.planks(50), null) { true }
        interactor.stakeLess(builder, StakingLaneFixtures.planks(50), nominator(), StakingLaneFixtures.planks(50), null) { true }

        verify(exactly = 2) { builder.call("Staking", "chill", emptyMap()) }
    }

    @Test
    fun `non nominator full unbond does not chill`() = runTest {
        val builder = plainBuilder()
        coEvery { repository.minimumNominatorBond(any()) } returns StakingLaneFixtures.planks(10)
        val none = StakingState.Stash.None(chain, stashId, stashId, stashId)

        interactor.stakeLess(builder, StakingLaneFixtures.planks(50), none, StakingLaneFixtures.planks(50), null) { true }

        verify(exactly = 0) { builder.call("Staking", "chill", any()) }
        verify { builder.call("Staking", "unbond", any()) }
    }

    @Test
    fun `unbond refuses changed reviews and unknown balances`() = runTest {
        val builder = plainBuilder()
        coEvery { repository.minimumNominatorBond(any()) } returns BigInteger.ZERO
        val nonStash = StakingState.NonStash(chain, stashId)

        assertTrue(runCatching { interactor.stakeLess(builder, BigInteger.ONE, nonStash, BigInteger.TEN, null) { true } }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { interactor.stakeLess(builder, BigInteger.ONE, nominator(), BigInteger.TEN, null) { false } }.exceptionOrNull() is CancellationException)
        assertTrue(runCatching { interactor.stakeLess(builder, BigInteger.ONE, nominator(), null, null) { true } }.exceptionOrNull() is IllegalArgumentException)
        var checks = 0
        val lateChange = runCatching { interactor.stakeLess(builder, BigInteger.ONE, nominator(), BigInteger.TEN, null) { checks++ == 0 } }
        assertTrue(lateChange.exceptionOrNull() is CancellationException)
        verify(exactly = 0) { builder.call(any<String>(), any<String>(), any()) }
    }

    @Test
    fun `withdraw counts prior slashing spans`() = runTest {
        val builder = plainBuilder()
        coEvery { repository.getSlashingSpan(chain.id, stashId) } returns SlashingSpans(BigInteger.TEN, listOf(BigInteger.ONE, BigInteger.TWO))
        interactor.confirmRevoke(builder, null, nominator())
        verify { builder.call("Staking", "withdraw_unbonded", mapOf("num_slashing_spans" to BigInteger.valueOf(3))) }

        coEvery { repository.getSlashingSpan(chain.id, stashId) } returns null
        interactor.confirmRevoke(builder, null, nominator())
        verify { builder.call("Staking", "withdraw_unbonded", mapOf("num_slashing_spans" to BigInteger.ZERO)) }
    }

    @Test
    fun `current unbondings keep only chunks after the active era`() = runTest {
        stubEraClock()
        selectState(nominator())
        val ledger = StakingLedger(stashId, BigInteger.valueOf(20), BigInteger.TEN, listOf(
            UnlockChunk(BigInteger.valueOf(5), BigInteger.valueOf(105)),
            UnlockChunk(BigInteger.valueOf(7), BigInteger.valueOf(99))
        ), emptyList())
        every { repository.ledgerFlow(any()) } returns flowOf(ledger)
        every { repository.observeActiveEraIndex(chain.id) } returns flowOf(activeEra)

        val unbondings = interactor.currentUnbondingsFlow(null).first()

        assertEquals(listOf(BigInteger.valueOf(5)), unbondings.map { it.amount })
        assertEquals(3000L, unbondings.single().timeLeft)
        assertEquals(DelegationAction.UNSTAKE, unbondings.single().type)
        assertEquals(unbondings.map { it.amount }, interactor.getRebondingUnbondings(null).map { it.amount })
    }

    @Test
    fun `selected stash ledger and account checks read the controller`() = runTest {
        val state = nominator()
        selectState(state)
        val ledger = StakingLedger(stashId, BigInteger.TEN, BigInteger.TEN, emptyList(), emptyList())
        coEvery { repository.ledger(chain.id, state.controllerAddress) } returns ledger
        coEvery { repository.ledger(chain.id, "unknown") } returns null
        coEvery { accountRepository.isAccountExists(any()) } returns true

        assertSame(ledger, interactor.ledger())
        assertSame(state, interactor.getSelectedAccountStakingState())
        assertFalse(interactor.accountIsNotController(state.controllerAddress))
        assertTrue(interactor.accountIsNotController("unknown"))
        assertFalse(interactor.checkAccountRequiredValidation(null))
        assertTrue(interactor.checkAccountRequiredValidation(state.controllerAddress))
    }

    @Test
    fun `era and nominator limits come from the chain constants`() = runTest {
        stubEraClock()
        coEvery { sharedState.chainId() } returns chain.id
        coEvery { constants.maxValidatorsPerNominator(chain.id) } returns 16
        coEvery { constants.maxRewardedNominatorPerValidator(chain.id) } returns 512

        assertEquals(24, interactor.stakePeriodInHours())
        assertEquals(16, interactor.maxStakersPerBlockProducer())
        assertEquals(512, interactor.maxRewardedNominators())
    }

    @Test
    fun `nominator cap is reached only when both counters are known`() = runTest {
        coEvery { repository.nominatorsCount(chain.id) } returns null
        assertFalse(interactor.maxNumberOfStakesIsReached(chain.id))
        coEvery { repository.nominatorsCount(chain.id) } returns BigInteger.TEN
        coEvery { repository.maxNominators(chain.id) } returns null
        assertFalse(interactor.maxNumberOfStakesIsReached(chain.id))
        coEvery { repository.maxNominators(chain.id) } returns BigInteger.TEN
        assertTrue(interactor.maxNumberOfStakesIsReached(chain.id))
        coEvery { repository.maxNominators(chain.id) } returns BigInteger.valueOf(11)
        assertFalse(interactor.maxNumberOfStakesIsReached(chain.id))
    }

    @Test
    fun `reward destination is read for stashes only`() = runTest {
        coEvery { repository.getRewardDestination(any()) } returns RewardDestination.Restake
        assertSame(RewardDestination.Restake, interactor.getRewardDestination(nominator()))
        assertTrue(runCatching { interactor.getRewardDestination(StakingState.NonStash(chain, stashId)) }.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `static relay overrides and amounts`() = runTest {
        assertFalse(interactor.getSelectedAccountAddress().first().isPresent)
        assertFalse(interactor.getCollatorAddress("any").first().isPresent)
        assertEquals(RebondKind.values().toSet(), interactor.getRebondTypes())
        assertNull(interactor.overrideRedeemActionTitle())
        assertNull(interactor.overrideUnbondHint())
        assertEquals(R.string.staking_bonded_format, interactor.overrideUnbondAvailableLabel())
        assertEquals(asset.unbonding, interactor.getRebondAvailableAmount(asset, BigDecimal.ONE))
        assertEquals(asset.bonded, interactor.getUnstakeAvailableAmount(asset, null))
    }

    @Test
    fun `cross existential check allows leaving zero or at least the deposit`() = runTest {
        coEvery { walletConstants.existentialDeposit(any()) } returns StakingLaneFixtures.planks(1)
        val state = nominator()
        fun payload(amount: String) = UnbondValidationPayload(state, BigDecimal.ZERO, BigDecimal(amount), asset)

        assertTrue(interactor.checkCrossExistentialValidation(payload("10")))
        // Unbonding everything (the same bonded amount) leaves zero, which is allowed.
        assertTrue(interactor.checkCrossExistentialValidation(UnbondValidationPayload(state, BigDecimal.ZERO, asset.bonded!!, asset)))
        assertFalse(interactor.checkCrossExistentialValidation(payload("49.5")))
        assertTrue(interactor.checkEnoughToUnbondValidation(payload("50")))
        assertFalse(interactor.checkEnoughToUnbondValidation(payload("51")))
        assertTrue(interactor.checkEnoughToRebondValidation(RebondValidationPayload(asset, BigDecimal.ZERO, BigDecimal.TEN)))
        assertFalse(interactor.checkEnoughToRebondValidation(RebondValidationPayload(asset, BigDecimal.ZERO, BigDecimal(11))))
    }

    @Test
    fun `bond more balance comes from the stash when the controller differs`() = runTest {
        every { stakingInteractor.currentAssetFlow() } returns flowOf(asset)
        coEvery { stakingInteractor.getStashBalance(stashId, chainAsset) } returns BigDecimal(42)
        selectState(nominator())
        assertEquals(BigDecimal(42), interactor.getAvailableForBondMoreBalance())

        selectState(nominator(controller = stashId))
        assertEquals(asset.availableForStaking, interactor.getAvailableForBondMoreBalance())
    }

    @Test
    fun `bond more validation needs a non-zero amount that leaves the fee`() = runTest {
        every { stakingInteractor.currentAssetFlow() } returns flowOf(asset)
        selectState(StakingState.NonStash(chain, stashId))
        val system = interactor.provideBondMoreValidationSystem()

        val valid = system.validate(BondMoreValidationPayload("stash", BigDecimal("0.1"), BigDecimal.ONE, chainAsset)).getOrThrow()
        val zero = system.validate(BondMoreValidationPayload("stash", BigDecimal("0.1"), BigDecimal.ZERO, chainAsset)).getOrThrow()

        assertTrue(valid is ValidationStatus.Valid)
        assertEquals(BondMoreValidationFailure.ZERO_BOND, (zero as ValidationStatus.NotValid).reason)
    }

    @Test
    fun `rebond and redeem validations report their failures`() = runTest {
        val rebond = interactor.getRebondValidationSystem()
        assertTrue(rebond.validate(RebondValidationPayload(asset, BigDecimal("0.1"), BigDecimal.ONE)).getOrThrow() is ValidationStatus.Valid)
        val zero = rebond.validate(RebondValidationPayload(asset, BigDecimal("0.1"), BigDecimal.ZERO)).getOrThrow()
        assertEquals(RebondValidationFailure.ZERO_AMOUNT, (zero as ValidationStatus.NotValid).reason)

        val redeem = interactor.provideRedeemValidationSystem().validate(RedeemValidationPayload(BigDecimal(1_000), asset)).getOrThrow()
        assertEquals(RedeemValidationFailure.CANNOT_PAY_FEES, (redeem as ValidationStatus.NotValid).reason)
    }

    @Test
    fun `unbond validation checks the controller can pay the fee`() = runTest {
        val state = nominator()
        selectState(state)
        coEvery { repository.ledger(any(), any()) } returns null
        coEvery { walletConstants.existentialDeposit(any()) } returns StakingLaneFixtures.planks(1)
        val controllerWallet = wallet(controllerId)
        coEvery { accountRepository.findMetaAccount(controllerId) } returns controllerWallet
        coEvery { walletRepository.getAsset(1L, controllerId, chainAsset, null) } returns asset
        val payload = UnbondValidationPayload(state, BigDecimal("0.1"), BigDecimal.ONE, asset)

        assertTrue(interactor.getUnbondValidationSystem().validate(payload).getOrThrow() is ValidationStatus.Valid)

        coEvery { accountRepository.findMetaAccount(controllerId) } returns null
        val failure = interactor.getUnbondValidationSystem().validate(payload).getOrThrow()
        assertSame(UnbondValidationFailure.ControllerCantPayFees, (failure as ValidationStatus.NotValid).reason)
    }

    @Test
    fun `manage staking validations require the stash and controller accounts`() = runTest {
        selectState(nominator())
        coEvery { repository.ledger(any(), any()) } returns null
        val payload = ManageStakingValidationPayload(null)

        val redeem = interactor.getRedeemValidation().validate(payload).getOrThrow() as ValidationStatus.NotValid
        val bondMore = interactor.getBondMoreValidation().validate(payload).getOrThrow() as ValidationStatus.NotValid
        val rebond = interactor.getRebondValidation().validate(payload).getOrThrow() as ValidationStatus.NotValid
        val unbonding = interactor.getUnbondingValidation().validate(payload).getOrThrow() as ValidationStatus.NotValid

        assertTrue(redeem.reason is ManageStakingValidationFailure.ControllerRequired)
        assertTrue(bondMore.reason is ManageStakingValidationFailure.StashRequired)
        assertTrue(rebond.reason is ManageStakingValidationFailure.ControllerRequired)
        assertTrue(unbonding.reason is ManageStakingValidationFailure.ControllerRequired)
        val setup = interactor.getSetupStakingValidationSystem().value as CompositeValidation<*, *>
        assertEquals(3, setup.validations.size)
    }

    @Test
    fun `balance flow refreshes the cached ledger from the node first`() = runTest {
        val runtime = AssetHubMetadataContractTest.runtimes.getValue("polkadot")
        val vectors = javaClass.getResourceAsStream("/asset-hub/polkadot-vectors.json")!!.reader().use {
            Gson().fromJson(it, JsonObject::class.java)
        }
        val ledgerHex = vectors["ledger"].asString
        every { stakingInteractor.currentAssetFlow() } returns flowOf(asset)
        coEvery { accountRepository.getSelectedMetaAccount() } returns wallet()
        every { sharedState.selectionItem } returns flowOf(StakingAssetSelection.RelayChainStaking(chain.id, chainAsset.id))
        coEvery { chainRegistry.getChain(chain.id) } returns chain
        val provider = mockk<RuntimeProvider>()
        coEvery { provider.get() } returns runtime
        coEvery { chainRegistry.awaitRuntimeProvider(chain.id) } returns provider
        val socket = mockk<SocketService>()
        every { socket.jsonMapper } returns Gson()
        every { socket.executeRequest(any(), any(), any()) } answers {
            val request = firstArg<RuntimeRequest>()
            thirdArg<SocketService.ResponseListener<RpcResponse>>().onNext(RpcResponse("2.0", ledgerHex, request.id, null))
            mockk(relaxed = true)
        }
        val connection = mockk<ChainConnection>()
        every { connection.socketService } returns socket
        coEvery { chainRegistry.awaitConnection(chain.id) } returns connection
        coEvery { repository.getActiveEraIndex(chain.id) } returns BigInteger.valueOf(2310)
        val fresh = slot<FreshAssetCacheUpdate>()

        val balance = interactor.getStakingBalanceFlow(null).first()

        coVerify { assetCache.updateAsset(1L, any(), chainAsset, capture(fresh)) }
        assertEquals(FreshAssetCacheUpdate.Staking(BigInteger.ZERO, BigInteger.valueOf(345), BigInteger.valueOf(12_000)), fresh.captured)
        assertEquals(0, balance.staked.amount!!.compareTo(BigDecimal(50)))
    }
}
