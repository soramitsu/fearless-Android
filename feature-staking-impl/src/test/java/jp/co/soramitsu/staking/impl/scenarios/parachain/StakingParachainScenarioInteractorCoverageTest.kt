package jp.co.soramitsu.staking.impl.scenarios.parachain

import android.graphics.drawable.PictureDrawable
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.validation.CompositeValidation
import jp.co.soramitsu.common.validation.ValidationStatus
import jp.co.soramitsu.core.models.Asset.StakingType
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.runtime.ext.addressOf
import jp.co.soramitsu.runtime.multiNetwork.chain.model.polkadotChainId
import jp.co.soramitsu.runtime.state.SingleAssetSharedState
import jp.co.soramitsu.staking.api.data.StakingAssetSelection
import jp.co.soramitsu.staking.api.data.StakingSharedState
import jp.co.soramitsu.staking.api.domain.api.IdentityRepository
import jp.co.soramitsu.staking.api.domain.model.AtStake
import jp.co.soramitsu.staking.api.domain.model.CandidateCapacity
import jp.co.soramitsu.staking.api.domain.model.CandidateInfo
import jp.co.soramitsu.staking.api.domain.model.CandidateInfoStatus
import jp.co.soramitsu.staking.api.domain.model.CollatorDelegation
import jp.co.soramitsu.staking.api.domain.model.Delegation
import jp.co.soramitsu.staking.api.domain.model.DelegationAction
import jp.co.soramitsu.staking.api.domain.model.DelegationScheduledRequest
import jp.co.soramitsu.staking.api.domain.model.DelegatorState
import jp.co.soramitsu.staking.api.domain.model.DelegatorStateStatus
import jp.co.soramitsu.staking.api.domain.model.Identity
import jp.co.soramitsu.staking.api.domain.model.RewardDestination
import jp.co.soramitsu.staking.api.domain.model.RootIdentity
import jp.co.soramitsu.staking.api.domain.model.Round
import jp.co.soramitsu.staking.api.domain.model.StakingAccount
import jp.co.soramitsu.staking.api.domain.model.StakingState
import jp.co.soramitsu.staking.impl.StakingLaneFixtures
import jp.co.soramitsu.staking.impl.data.network.subquery.SubQueryDelegationHistoryFetcher
import jp.co.soramitsu.staking.impl.data.repository.StakingConstantsRepository
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.domain.model.NetworkInfo
import jp.co.soramitsu.staking.impl.domain.model.Unbonding
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
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
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

/** Real parachain (collator) scenario interactor over mocked repositories and constants. */
class StakingParachainScenarioInteractorCoverageTest {

    private val moonbeamId = "fe58ea77779b7abda7da4ec526d14db9b1e9cd40a217c34892af80a9b332b76d"
    private val chainAsset = StakingLaneFixtures.chainAsset(chainId = moonbeamId, symbol = "GLMR", precision = 18, staking = StakingType.PARACHAIN)
    private val chain = StakingLaneFixtures.chain(chainAsset)
    private val asset = StakingLaneFixtures.asset(chainAsset)
    private val accountId = StakingLaneFixtures.accountId
    private val collatorId = ByteArray(32) { 2 }
    private val collatorHex = collatorId.toHexString(true)
    private val otherDelegator = ByteArray(32) { 9 }
    private val round = Round(current = BigInteger.valueOf(5), first = BigInteger.valueOf(100), length = BigInteger.TEN)

    private val stakingInteractor = mockk<StakingInteractor>(relaxed = true)
    private val accountRepository = mockk<AccountRepository>(relaxed = true)
    private val constants = mockk<StakingConstantsRepository>(relaxed = true)
    private val repository = mockk<StakingParachainScenarioRepository>(relaxed = true)
    private val identityRepository = mockk<IdentityRepository>(relaxed = true)
    private val sharedState = mockk<StakingSharedState>(relaxed = true)
    private val iconGenerator = mockk<AddressIconGenerator>(relaxed = true)
    private val resourceManager = mockk<ResourceManager>(relaxed = true)
    private val historyFetcher = mockk<SubQueryDelegationHistoryFetcher>(relaxed = true)
    private val walletRepository = mockk<WalletRepository>(relaxed = true)

    private val interactor = StakingParachainScenarioInteractor(
        stakingInteractor, accountRepository, constants, repository, identityRepository, sharedState,
        iconGenerator, resourceManager, historyFetcher, walletRepository
    )

    private fun wallet(substrate: ByteArray? = accountId) = MetaAccount(
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

    private fun delegator(amount: Long = 30) = StakingState.Parachain.Delegator(
        chain, accountId,
        listOf(CollatorDelegation(collatorId, StakingLaneFixtures.planks(amount, 18), BigInteger.ZERO, DelegatorStateStatus.ACTIVE)),
        BigDecimal(amount)
    )

    private fun request(delegator: ByteArray, executable: Long, amount: Long) = DelegationScheduledRequest(
        delegator, BigInteger.valueOf(executable), DelegationAction.UNSTAKE, StakingLaneFixtures.planks(amount, 18)
    )

    private fun candidate() = CandidateInfo(
        BigInteger.TEN, BigInteger.ONE, BigInteger.TEN, BigInteger.ONE, BigInteger.ONE, BigInteger.ONE,
        CandidateCapacity.Partial, CandidateCapacity.Empty, null, CandidateInfoStatus.ACTIVE
    )

    private fun builder(): ExtrinsicBuilder {
        val builder = mockk<ExtrinsicBuilder>()
        every { builder.call(any<String>(), any<String>(), any()) } returns builder
        return builder
    }

    private fun selectChain() {
        every { stakingInteractor.selectedChainFlow() } returns flowOf(chain)
        coEvery { accountRepository.getSelectedMetaAccount() } returns wallet()
        coEvery { repository.getCurrentRound(moonbeamId) } returns round
    }

    @Test
    fun `network info uses the round length and minimum delegation`() = runTest {
        every { sharedState.assetWithChain } returns flowOf(SingleAssetSharedState.AssetWithChain(chain, chainAsset))
        coEvery { constants.parachainLockupPeriodInRounds(moonbeamId) } returns BigInteger.valueOf(4)
        coEvery { constants.parachainMinimumStaking(moonbeamId) } returns StakingLaneFixtures.planks(5, 18)

        val info = interactor.observeNetworkInfoState().first()

        assertEquals(NetworkInfo.Parachain(24, StakingLaneFixtures.planks(5, 18)), info)
    }

    @Test
    fun `network info of a chain with an unknown round length has no lockup period`() = runTest {
        val unknownAsset = StakingLaneFixtures.chainAsset(staking = StakingType.PARACHAIN)
        every { sharedState.assetWithChain } returns flowOf(SingleAssetSharedState.AssetWithChain(StakingLaneFixtures.chain(unknownAsset), unknownAsset))
        coEvery { constants.parachainMinimumStaking(any()) } returns BigInteger.ONE

        assertEquals(0, interactor.observeNetworkInfoState().first().lockupPeriodInHours)
        coVerify(exactly = 0) { constants.parachainLockupPeriodInRounds(any()) }
    }

    @Test
    fun `staking state uses the selected chain when it can be staked`() = runTest {
        val state = delegator()
        every { stakingInteractor.selectedChainFlow() } returns flowOf(chain)
        every { accountRepository.selectedMetaAccountFlow() } returns flowOf(wallet())
        coEvery { sharedState.availableToSelect() } returns listOf(StakingAssetSelection.ParachainStaking(moonbeamId, chainAsset.id))
        every { repository.stakingStateFlow(chain, accountId) } returns flowOf(state)

        assertSame(state, interactor.stakingStateFlow().first())
        verify(exactly = 0) { sharedState.update(any()) }
    }

    @Test
    fun `staking state falls back to polkadot when the selected chain cannot be staked`() = runTest {
        val polkadot = StakingLaneFixtures.chain()
        val fallback = StakingAssetSelection.RelayChainStaking(polkadotChainId, "dot-id")
        val state = StakingState.Parachain.None(polkadot, accountId)
        every { stakingInteractor.selectedChainFlow() } returns flowOf(chain)
        every { accountRepository.selectedMetaAccountFlow() } returns flowOf(wallet())
        coEvery { sharedState.availableToSelect() } returns listOf(StakingAssetSelection.ParachainStaking("other", "x"), fallback)
        coEvery { stakingInteractor.getChain(polkadotChainId) } returns polkadot
        every { repository.stakingStateFlow(polkadot, accountId) } returns flowOf(state)

        assertSame(state, interactor.stakingStateFlow().first())
        verify { sharedState.update(fallback) }
    }

    @Test
    fun `identities, rounds, stake and totals read the repositories`() = runTest {
        selectChain()
        val identity = RootIdentity("Collator", null, null, null, null, null, null, null)
        coEvery { identityRepository.getIdentitiesFromIds(chain, listOf(collatorId.toHexString(false))) } returns mapOf<String, Identity?>("c" to identity)
        val atStake = AtStake(BigInteger.ONE, BigInteger.TEN, emptyList())
        coEvery { repository.getAtStakeOfCollator(moonbeamId, collatorId, BigInteger.valueOf(5)) } returns atStake
        coEvery { repository.total(moonbeamId) } returns BigInteger.valueOf(1_000)

        assertTrue(interactor.getIdentities(emptyList()).isEmpty())
        assertSame(identity, interactor.getIdentities(listOf(collatorId))["c"])
        assertEquals(round, interactor.getCurrentRound(moonbeamId).getOrThrow())
        assertSame(atStake, interactor.getAtStake(moonbeamId, collatorId).getOrThrow())
        assertEquals(BigInteger.valueOf(1_000), interactor.total(moonbeamId).getOrThrow())
    }

    @Test
    fun `failed repository reads become failed results`() = runTest {
        coEvery { repository.getCurrentRound(any()) } throws IllegalStateException("offline")
        coEvery { repository.total(any()) } throws IllegalStateException("offline")

        assertTrue(interactor.getCurrentRound(moonbeamId).isFailure)
        assertTrue(interactor.getAtStake(moonbeamId, collatorId).isFailure)
        assertTrue(interactor.total(moonbeamId).isFailure)
    }

    @Test
    fun `selected account state follows the selection`() = runTest {
        val state = delegator()
        val selected = SingleAssetSharedState.AssetWithChain(chain, chainAsset)
        every { repository.stakingStateFlow(chain, accountId) } returns flowOf(state)
        every { stakingInteractor.selectionStateFlow() } returns flowOf(wallet() to selected)

        assertSame(state, interactor.getSelectedAccountStakingState())
        assertTrue(interactor.selectedAccountStakingStateFlow(wallet(substrate = null), selected).toList().isEmpty())
    }

    @Test
    fun `delegation cap compares the delegator state with the constant`() = runTest {
        selectChain()
        coEvery { stakingInteractor.getChain(moonbeamId) } returns chain
        coEvery { constants.maxDelegationsPerDelegator(moonbeamId) } returns 2
        val two = DelegatorState(accountId, listOf(Delegation(collatorId, BigInteger.ONE), Delegation(otherDelegator, BigInteger.ONE)), BigInteger.TWO, DelegatorStateStatus.ACTIVE)

        coEvery { repository.getDelegatorState(moonbeamId, accountId) } returns null
        assertFalse(interactor.maxNumberOfStakesIsReached(moonbeamId))
        coEvery { repository.getDelegatorState(moonbeamId, accountId) } returns two
        assertTrue(interactor.maxNumberOfStakesIsReached(moonbeamId))
        coEvery { repository.getDelegatorState(moonbeamId, accountId) } returns two.copy(delegations = two.delegations.take(1))
        assertFalse(interactor.maxNumberOfStakesIsReached(moonbeamId))
        assertEquals(2, interactor.maxStakersPerBlockProducer())
    }

    @Test
    fun `current unbondings join own scheduled requests with the history`() = runTest {
        selectChain()
        coEvery { stakingInteractor.currentBlockNumber() } returns BigInteger.valueOf(105)
        val reward = Unbonding(BigInteger.ONE, 0, 0, DelegationAction.REWARD)
        coEvery { historyFetcher.fetchDelegationHistory(moonbeamId, accountId.toHexString(true), collatorHex) } returns listOf(reward)
        every { repository.getDelegationScheduledRequestsFlow(moonbeamId, any()) } returns
            flowOf(listOf(request(accountId, 7, 4), request(otherDelegator, 7, 9)))

        val unbondings = interactor.currentUnbondingsFlow(collatorHex).toList().last()

        assertEquals(2, unbondings.size)
        // Five blocks left of round 5 plus all of round 6, at 6 hours per 10-block round.
        assertEquals(32_400_000L, unbondings.first().timeLeft)
        assertEquals(DelegationAction.UNSTAKE, unbondings.first().type)
        assertSame(reward, unbondings.last())
    }

    @Test
    fun `rebonding offers only requests that are not executable yet`() = runTest {
        selectChain()
        coEvery { stakingInteractor.currentBlockNumber() } returns BigInteger.valueOf(105)
        every { repository.getDelegationScheduledRequestsFlow(moonbeamId, any()) } returns
            flowOf(listOf(request(accountId, 7, 4), request(accountId, 3, 6)))

        val unbondings = interactor.getRebondingUnbondings(collatorHex)

        assertEquals(listOf(StakingLaneFixtures.planks(4, 18)), unbondings.map { it.amount })
        assertTrue(interactor.getRebondingUnbondings(null).isEmpty())
    }

    @Test
    fun `selected account address uses the account kind`() = runTest {
        coEvery { iconGenerator.createEthereumAddressIcon(any<ByteArray>(), any<Int>(), any<Int>()) } returns mockk<PictureDrawable>(relaxed = true)
        coEvery { iconGenerator.createAddressIcon(any<ByteArray>(), any<Int>(), any<Int>()) } returns mockk<PictureDrawable>(relaxed = true)
        every { stakingInteractor.selectedAccountProjectionFlow() } returns
            flowOf(StakingAccount("0xabc", "Evm", true), StakingAccount(chain.addressOf(accountId), "Sub", false))

        val models = interactor.getSelectedAccountAddress().toList().map { it.get() }

        assertEquals(listOf("0xabc", chain.addressOf(accountId)), models.map { it.address })
        assertEquals(listOf("Evm", "Sub"), models.map { it.name })
    }

    @Test
    fun `collator address carries the identity name`() = runTest {
        selectChain()
        coEvery { iconGenerator.createEthereumAddressIcon(any<ByteArray>(), any<Int>(), any<Int>()) } returns mockk<PictureDrawable>(relaxed = true)
        coEvery { identityRepository.getIdentitiesFromIds(chain, any()) } returns
            mapOf<String, Identity?>(collatorId.toHexString() to RootIdentity("Collator", null, null, null, null, null, null, null))

        val model = interactor.getCollatorAddress(collatorHex).first().get()

        assertEquals(collatorHex, model.address)
        assertEquals("Collator", model.name)
        assertTrue(runCatching { interactor.getCollatorAddress(null) }.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `stake more bonds as candidate or delegator`() = runTest {
        selectChain()
        val builder = builder()

        interactor.stakeMore(builder, BigInteger.TEN, accountId.toHexString(true))
        interactor.stakeMore(builder, BigInteger.ONE, collatorHex)

        verify { builder.call("ParachainStaking", "candidate_bond_more", mapOf("more" to BigInteger.TEN)) }
        verify { builder.call("ParachainStaking", "delegator_bond_more", any()) }
        assertTrue(runCatching { interactor.stakeMore(builder, BigInteger.ONE, null) }.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `revoke executes the delegation request and rebond cancels it`() = runTest {
        selectChain()
        val builder = builder()

        interactor.confirmRevoke(builder, collatorHex, delegator())
        interactor.rebond(builder, BigInteger.ONE, collatorHex)

        verify { builder.call("ParachainStaking", "execute_delegation_request", any()) }
        verify { builder.call("ParachainStaking", "cancel_delegation_request", any()) }
        val relayState = StakingState.NonStash(chain, accountId)
        assertTrue(runCatching { interactor.confirmRevoke(builder, collatorHex, relayState) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { interactor.confirmRevoke(builder, null, delegator()) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { interactor.rebond(builder, BigInteger.ONE, null) }.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `unbond hint uses hours below a day and days above`() = runTest {
        selectChain()
        every { resourceManager.getString(R.string.parachain_staking_unbonding_hint_template, *anyVararg()) } returns "hint"

        coEvery { constants.candidateBondLessDelay(moonbeamId) } returns 2
        assertEquals("hint", interactor.overrideUnbondHint())
        verify { resourceManager.getQuantityString(R.plurals.common_hours_format, 12, 12) }

        coEvery { constants.candidateBondLessDelay(moonbeamId) } returns 8
        assertEquals("hint", interactor.overrideUnbondHint())
        verify { resourceManager.getQuantityString(R.plurals.common_days_format, 2, 2) }
        verify { resourceManager.getQuantityString(R.plurals.rounds_format, 8, 8) }
    }

    @Test
    fun `static parachain answers`() = runTest {
        assertEquals(setOf(RebondKind.ALL), interactor.getRebondTypes())
        assertEquals(R.string.parachain_staking_unlock, interactor.overrideRedeemActionTitle())
        assertNull(interactor.overrideUnbondAvailableLabel())
        assertEquals(BigDecimal.TEN, interactor.getRebondAvailableAmount(asset, BigDecimal.TEN))
        assertTrue(interactor.accountIsNotController("any"))
        assertNull(interactor.ledger())
        assertTrue(interactor.checkEnoughToRebondValidation(RebondValidationPayload(asset, BigDecimal.ZERO, BigDecimal.ONE)))
        assertTrue(interactor.checkAccountRequiredValidation(null))
    }

    @Test
    fun `account requirement checks the account exists`() = runTest {
        every { repository.stakingStateFlow(chain, accountId) } returns flowOf(delegator())
        every { stakingInteractor.selectionStateFlow() } returns flowOf(wallet() to SingleAssetSharedState.AssetWithChain(chain, chainAsset))
        coEvery { accountRepository.isAccountExists(any()) } returns false

        assertFalse(interactor.checkAccountRequiredValidation(chain.addressOf(accountId)))
    }

    @Test
    fun `periods come from the round length`() = runTest {
        selectChain()
        coEvery { constants.parachainLockupPeriodInRounds(moonbeamId) } returns BigInteger.valueOf(4)

        assertEquals(24, interactor.unstakingPeriod())
        assertEquals(6, interactor.stakePeriodInHours())

        every { stakingInteractor.selectedChainFlow() } returns flowOf(StakingLaneFixtures.chain())
        assertTrue(runCatching { interactor.stakePeriodInHours() }.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `reward destination is the delegator account`() = runTest {
        val destination = interactor.getRewardDestination(delegator()) as RewardDestination.Payout
        assertTrue(destination.targetAccountId.contentEquals(accountId))
        assertTrue(runCatching { interactor.getRewardDestination(StakingState.NonStash(chain, accountId)) }.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `collator lookups pass through to the repositories`() = runTest {
        selectChain()
        val info = candidate()
        val identity = RootIdentity("C", null, null, null, null, null, null, null)
        coEvery { repository.getCandidateInfo(moonbeamId, collatorId) } returns info
        coEvery { identityRepository.getIdentitiesFromIdsBytes(chain, listOf(collatorId)) } returns mapOf<String, Identity?>(collatorId.toHexString() to identity)
        coEvery { repository.getCandidateInfos(moonbeamId, any()) } returns mapOf("c" to info)
        coEvery { repository.getBottomDelegations(moonbeamId, any()) } returns mapOf("c" to listOf(Delegation(accountId, BigInteger.ONE)))

        assertSame(info, interactor.getCollator(collatorId))
        assertSame(identity, interactor.getIdentity(collatorId))
        assertSame(info, interactor.getCandidateInfos(moonbeamId, listOf(collatorId))["c"])
        assertEquals(1, interactor.getBottomDelegations(moonbeamId, listOf(collatorId))["c"]!!.size)
    }

    @Test
    fun `leave candidates delay is a result`() = runTest {
        selectChain()
        coEvery { constants.parachainLeaveCandidatesDelay(moonbeamId) } returns BigInteger.valueOf(3)
        assertEquals(3, interactor.getLeaveCandidatesDelay().getOrThrow())
        coEvery { constants.parachainLeaveCandidatesDelay(moonbeamId) } throws IllegalStateException("no constant")
        assertTrue(interactor.getLeaveCandidatesDelay().isFailure)
    }

    @Test
    fun `collators with executable own requests are ready to unlock`() = runTest {
        selectChain()
        val ready = collatorId.toHexString()
        coEvery { repository.getScheduledRequests(moonbeamId, any()) } returns mapOf(
            ready to listOf(request(accountId, 3, 1)),
            ByteArray(32) { 4 }.toHexString() to listOf(request(accountId, 9, 1)),
            ByteArray(32) { 5 }.toHexString() to listOf(request(otherDelegator, 1, 1))
        )

        val ids = interactor.getCollatorIdsWithReadyToUnlockingTokens(listOf(collatorId), accountId)
        assertEquals(listOf(ready), ids.map { it.toHexString() })

        coEvery { repository.getScheduledRequests(moonbeamId, any()) } throws IllegalStateException("offline")
        assertTrue(interactor.getCollatorIdsWithReadyToUnlockingTokens(listOf(collatorId), accountId).isEmpty())
    }

    @Test
    fun `balance splits own requests into unstaking and ready amounts`() = runTest {
        selectChain()
        every { accountRepository.selectedMetaAccountFlow() } returns flowOf(wallet())
        every { stakingInteractor.currentAssetFlow() } returns flowOf(asset)
        every { repository.stakingStateFlow(chain, accountId) } returns flowOf(delegator(30))
        every { repository.getDelegationScheduledRequestsFlow(moonbeamId, collatorId) } returns
            flowOf(listOf(request(accountId, 7, 4), request(accountId, 3, 6), request(otherDelegator, 3, 9)))

        val balance = interactor.getStakingBalanceFlow(collatorId).first()

        assertEquals(0, balance.staked.amount!!.compareTo(BigDecimal(30)))
        assertEquals(0, balance.unstaking.amount!!.compareTo(BigDecimal(4)))
        assertEquals(0, balance.redeemable.amount!!.compareTo(BigDecimal(6)))
    }

    @Test
    fun `balance of a wallet without an account is empty`() = runTest {
        every { stakingInteractor.selectedChainFlow() } returns flowOf(chain)
        every { accountRepository.selectedMetaAccountFlow() } returns flowOf(wallet(substrate = null))

        assertTrue(interactor.getStakingBalanceFlow(collatorId).toList().isEmpty())
    }

    @Test
    fun `unbond validation without a collator has nothing to unbond`() = runTest {
        coEvery { constants.parachainMinimumStaking(any()) } returns StakingLaneFixtures.planks(1, 18)
        val payload = UnbondValidationPayload(delegator(), BigDecimal("0.1"), BigDecimal.ONE, asset)

        val status = interactor.getUnbondValidationSystem().validate(payload).getOrThrow()

        assertSame(UnbondValidationFailure.NotEnoughBonded, (status as ValidationStatus.NotValid).reason)
        assertFalse(interactor.checkCrossExistentialValidation(payload))
        assertTrue(interactor.checkCrossExistentialValidation(payload.copy(amount = BigDecimal.ZERO)))
    }

    @Test
    fun `fee, rebond and redeem validations`() = runTest {
        every { stakingInteractor.currentAssetFlow() } returns flowOf(asset)
        coEvery { sharedState.chain() } returns chain
        coEvery { accountRepository.getSelectedMetaAccount() } returns wallet()
        coEvery { walletRepository.getAsset(1L, any(), chainAsset, null) } returns asset
        val address = chain.addressOf(accountId)

        val bondMore = interactor.provideBondMoreValidationSystem()
        assertTrue(bondMore.validate(BondMoreValidationPayload(address, BigDecimal("0.1"), BigDecimal.ONE, chainAsset)).getOrThrow() is ValidationStatus.Valid)
        val zero = bondMore.validate(BondMoreValidationPayload(address, BigDecimal("0.1"), BigDecimal.ZERO, chainAsset)).getOrThrow()
        assertEquals(BondMoreValidationFailure.ZERO_BOND, (zero as ValidationStatus.NotValid).reason)

        val rebond = interactor.getRebondValidationSystem().validate(RebondValidationPayload(asset, BigDecimal("0.1"), BigDecimal.ZERO)).getOrThrow()
        assertEquals(RebondValidationFailure.ZERO_AMOUNT, (rebond as ValidationStatus.NotValid).reason)
        val redeem = interactor.provideRedeemValidationSystem().validate(RedeemValidationPayload(BigDecimal(1_000), asset)).getOrThrow()
        assertEquals(RedeemValidationFailure.CANNOT_PAY_FEES, (redeem as ValidationStatus.NotValid).reason)
        assertEquals(asset.availableForStaking, interactor.getAvailableForBondMoreBalance())
    }

    @Test
    fun `manage staking validations accept parachain stakes`() = runTest {
        val payload = ManageStakingValidationPayload(null)

        assertTrue(interactor.getRedeemValidation().validate(payload).getOrThrow() is ValidationStatus.Valid)
        assertTrue(interactor.getBondMoreValidation().validate(payload).getOrThrow() is ValidationStatus.Valid)
        assertTrue(interactor.getRebondValidation().validate(payload).getOrThrow() is ValidationStatus.Valid)
        assertTrue(interactor.getUnbondingValidation().validate(payload).getOrThrow() is ValidationStatus.Valid)
        val setup = interactor.getSetupStakingValidationSystem().value as CompositeValidation<*, *>
        assertEquals(3, setup.validations.size)
    }
}
