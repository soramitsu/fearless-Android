package jp.co.soramitsu.staking.impl.scenarios

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.math.BigInteger
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.runtime.ext.addressOf
import jp.co.soramitsu.staking.api.domain.api.IdentityRepository
import jp.co.soramitsu.staking.api.domain.model.Identity
import jp.co.soramitsu.staking.api.domain.model.NominatedValidator
import jp.co.soramitsu.staking.api.domain.model.NominationPoolState
import jp.co.soramitsu.staking.api.domain.model.Nominations
import jp.co.soramitsu.staking.impl.StakingLaneFixtures
import jp.co.soramitsu.staking.impl.data.model.BondedPool
import jp.co.soramitsu.staking.impl.data.model.BondedPoolState
import jp.co.soramitsu.staking.impl.data.model.PoolMember
import jp.co.soramitsu.staking.impl.data.model.PoolUnbonding
import jp.co.soramitsu.staking.impl.data.repository.StakingPoolApi
import jp.co.soramitsu.staking.impl.data.repository.StakingPoolDataSource
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.domain.validators.current.CurrentValidatorsInteractor
import jp.co.soramitsu.staking.impl.presentation.common.EditPoolFlowState
import jp.co.soramitsu.staking.impl.scenarios.relaychain.StakingRelayChainScenarioRepository
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletConstants
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pool reads, identities, validators and member actions of the real pool interactor over mocked sources. */
class StakingPoolInteractorCoverageTest {

    private val chain = StakingLaneFixtures.chain()
    private val account = ByteArray(32) { 1 }
    private val address = chain.addressOf(account)

    private val api = mockk<StakingPoolApi>()
    private val dataSource = mockk<StakingPoolDataSource>()
    private val stakingInteractor = mockk<StakingInteractor>()
    private val relay = mockk<StakingRelayChainScenarioRepository>()
    private val accounts = mockk<AccountRepository>()
    private val identities = mockk<IdentityRepository>()
    private val validators = mockk<CurrentValidatorsInteractor>()
    private val interactor = StakingPoolInteractor(
        api,
        dataSource,
        stakingInteractor,
        relay,
        accounts,
        identities,
        mockk<WalletConstants>(),
        validators
    )

    /** "modl" + pallet id + stash index 0 + little-endian pool id, zero padded to 32 bytes. */
    private val poolSevenStash = ("modl".toByteArray() + PALLET + byteArrayOf(0, 7)).copyOf(32)

    init {
        every { stakingInteractor.selectedChainFlow() } returns flowOf(chain)
        coEvery { relay.getNominationPoolPalletId(any()) } returns PALLET
    }

    private fun bonded(state: BondedPoolState) =
        BondedPool(BigInteger.valueOf(500), state, BigInteger.TWO, account, account, null, account)

    @Test
    fun `pool limits fall back to zero when unset`() = runTest {
        coEvery { dataSource.minCreateBond(ID) } returns BigInteger.valueOf(500)
        coEvery { dataSource.existingPools(ID) } returns BigInteger.valueOf(42)
        coEvery { dataSource.getPoolsCount(ID) } returns BigInteger.valueOf(43)
        coEvery { dataSource.lastPoolId(ID) } returns BigInteger.valueOf(77)
        coEvery { dataSource.maxPools(ID) } returns null
        coEvery { dataSource.maxMembersInPool(ID) } returns null
        coEvery { dataSource.maxPoolMembers(ID) } returns null

        assertEquals(BigInteger.valueOf(500), interactor.getMinToCreate(ID))
        assertEquals(BigInteger.valueOf(42), interactor.getExistingPools(ID))
        assertEquals(BigInteger.valueOf(43), interactor.getPoolsCount(ID))
        assertEquals(BigInteger.valueOf(77), interactor.getLastPoolId(ID))
        assertEquals(BigInteger.ZERO, interactor.getPossiblePools(ID))
        assertEquals(BigInteger.ZERO, interactor.getMaxMembersInPool(ID))
        assertEquals(BigInteger.ZERO, interactor.getMaxPoolsMembers(ID))

        coEvery { dataSource.maxPools(ID) } returns BigInteger.TEN
        coEvery { dataSource.maxMembersInPool(ID) } returns BigInteger.TWO
        coEvery { dataSource.maxPoolMembers(ID) } returns BigInteger.valueOf(1_000)
        assertEquals(BigInteger.TEN, interactor.getPossiblePools(ID))
        assertEquals(BigInteger.TWO, interactor.getMaxMembersInPool(ID))
        assertEquals(BigInteger.valueOf(1_000), interactor.getMaxPoolsMembers(ID))
    }

    @Test
    fun `all pools are named from metadata and skip absent entries`() = runTest {
        val three = BigInteger.valueOf(3)
        coEvery { dataSource.poolsMetadata(chain.id) } returns mapOf(BigInteger.ONE to "First")
        coEvery { dataSource.bondedPools(chain.id) } returns mapOf(
            BigInteger.ONE to bonded(BondedPoolState.Open),
            BigInteger.TWO to null,
            three to bonded(BondedPoolState.Blocked)
        )
        every { relay.observeRemoteAccountNominations(chain.id, any()) } returns flowOf(null)

        val pools = interactor.getAllPools(chain)

        assertEquals(listOf(BigInteger.ONE, three), pools.map { it.poolId })
        assertEquals(listOf("First", "Pool #3"), pools.map { it.name })
        assertEquals(listOf(NominationPoolState.Open, NominationPoolState.Blocked), pools.map { it.state })
        assertEquals(BigInteger.valueOf(500), pools.first().stakedInPlanks)
        assertNull(pools.first().nominator)
        assertSame(account, pools.first().stateToggler)
    }

    @Test
    fun `identities are read for the selected chain`() = runTest {
        val identity = mockk<Identity>()
        val hex = account.toHexString(false)
        coEvery { identities.getIdentitiesFromIds(chain, listOf(hex)) } returns mapOf(hex to identity)

        assertTrue(interactor.getIdentities(emptyList()).isEmpty())
        assertSame(identity, interactor.getIdentities(listOf(account))[hex])
        coVerify(exactly = 1) { identities.getIdentitiesFromIds(any(), any()) }
    }

    @Test
    fun `account names prefer local wallets`() = runTest {
        val wallet = mockk<MetaAccount>()
        every { wallet.name } returns "My wallet"
        coEvery { accounts.findMetaAccount(any()) } returns wallet

        assertEquals("My wallet", interactor.getAccountName(address))

        coEvery { accounts.findMetaAccount(any()) } returns null
        coEvery { identities.getIdentitiesFromIds(chain, any()) } returns emptyMap()
        assertNull(interactor.getAccountName(address))
        coVerify { identities.getIdentitiesFromIds(chain, listOf(account.toHexString(false))) }
    }

    @Test
    fun `pool validators come from the pool stash nominations`() = runTest {
        val nominations = Nominations(listOf(account), BigInteger.TEN, false)
        val grouped = flowOf(emptyMap<NominatedValidator.Status.Group, List<NominatedValidator>>())
        coEvery { relay.getRemoteAccountNominations(chain.id, any()) } returns nominations
        coEvery { validators.nominatedValidatorsFlow(chain, any(), nominations) } returns grouped

        assertSame(account, interactor.getValidatorsIds(chain, SEVEN).single())
        assertSame(grouped, interactor.nominatedValidatorsFlow(chain, SEVEN))
        coVerify { relay.getRemoteAccountNominations(chain.id, match { it.contentEquals(poolSevenStash) }) }

        coEvery { relay.getRemoteAccountNominations(chain.id, any()) } returns null
        assertTrue(interactor.getValidatorsIds(chain, SEVEN).isEmpty())
        assertTrue(interactor.nominatedValidatorsFlow(chain, SEVEN).first().isEmpty())
    }

    @Test
    fun `blocked pool with validators keeps its state`() = runTest {
        val member = PoolMember(
            SEVEN,
            BigInteger.TEN,
            BigInteger.ZERO,
            listOf(PoolUnbonding(BigInteger.ONE, BigInteger.TWO), PoolUnbonding(BigInteger.valueOf(20), BigInteger.TEN))
        )
        every { dataSource.observePoolMembers(chain.id, account) } returns flowOf(Result.success(member))
        every { dataSource.observePool(chain.id, SEVEN) } returns flowOf(Result.success(bonded(BondedPoolState.Blocked)))
        every { relay.observeRemoteAccountNominationsResult(chain.id, any()) } returns
            flowOf(Result.success(Nominations(listOf(account), BigInteger.ONE, false)))
        coEvery { dataSource.getPoolMetadata(chain.id, SEVEN) } returns null
        coEvery { dataSource.getPendingRewards(chain.id, account) } returns Result.success(BigInteger.valueOf(3))
        coEvery { relay.getCurrentEraIndex(chain.id) } returns BigInteger.TEN

        val pool = interactor.observeCurrentPool(chain, account).first { it.isSuccess }.getOrThrow()!!

        assertEquals(NominationPoolState.Blocked, pool.state)
        assertEquals("Pool #7", pool.name)
        assertEquals(BigInteger.TWO, pool.redeemable)
        assertEquals(BigInteger.TEN, pool.unbonding)
        assertEquals(BigInteger.valueOf(3), pool.pendingRewards)
        assertEquals(BigInteger.TEN, pool.myStakeInPlanks)
    }

    @Test
    fun `member actions delegate to the pool api`() = runTest {
        coEvery { api.estimateBondExtraFee(address, BigInteger.ONE) } returns BigInteger.valueOf(11)
        coEvery { api.bondExtra(address, BigInteger.ONE) } returns Result.success("bond")
        coEvery { api.estimateUnbondFee(address, BigInteger.TWO) } returns BigInteger.valueOf(12)
        coEvery { api.unbond(address, BigInteger.TWO) } returns Result.success("unbond")
        coEvery { api.estimateWithdrawUnbondedFee(address) } returns BigInteger.valueOf(13)
        coEvery { api.withdrawUnbonded(address) } returns Result.success("redeem")
        coEvery { api.estimateClaimPayoutFee(address) } returns BigInteger.valueOf(14)
        coEvery { api.claimPayout(address) } returns Result.success("claim")

        assertEquals(BigInteger.valueOf(11), interactor.estimateBondMoreFee(address, BigInteger.ONE))
        assertEquals("bond", interactor.bondMore(address, BigInteger.ONE).getOrThrow())
        assertEquals(BigInteger.valueOf(12), interactor.estimateUnstakeFee(address, BigInteger.TWO))
        assertEquals("unbond", interactor.unstake(address, BigInteger.TWO).getOrThrow())
        assertEquals(BigInteger.valueOf(13), interactor.estimateRedeemFee(address))
        assertEquals("redeem", interactor.redeem(address).getOrThrow())
        assertEquals(BigInteger.valueOf(14), interactor.estimateClaimFee(address))
        assertEquals("claim", interactor.claim(address).getOrThrow())
    }

    @Test
    fun `nomination and editing delegate to the pool api`() = runTest {
        val state = EditPoolFlowState(SEVEN, "Pool", account, account, account, account, newPoolName = "Renamed")
        coEvery { api.estimateNominatePoolFee(any(), any(), *anyVararg()) } returns BigInteger.valueOf(15)
        coEvery { api.nominatePool(any(), any(), *anyVararg()) } returns Result.success("nominate")
        coEvery { api.estimateEditPool(state, address) } returns BigInteger.valueOf(16)
        coEvery { api.editPool(state, address) } returns Result.success("edit")

        assertEquals(BigInteger.valueOf(15), interactor.estimateNominateFee(address, SEVEN, account))
        assertEquals("nominate", interactor.nominate(SEVEN, address, account).getOrThrow())
        assertEquals(BigInteger.valueOf(16), interactor.estimateEditFee(state, address))
        assertEquals("edit", interactor.edit(state, address).getOrThrow())
        coVerify { api.nominatePool(SEVEN, address, *anyVararg()) }
    }

    private companion object {
        const val ID = "pool-chain"
        val SEVEN: BigInteger = BigInteger.valueOf(7)
        val PALLET = "py/nopls".toByteArray()
    }
}
