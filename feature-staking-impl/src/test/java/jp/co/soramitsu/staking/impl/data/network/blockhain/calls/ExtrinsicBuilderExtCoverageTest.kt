package jp.co.soramitsu.staking.impl.data.network.blockhain.calls

import io.mockk.every
import io.mockk.mockk
import java.math.BigInteger
import jp.co.soramitsu.core.models.MultiAddress
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.DictEnum
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.fearless_utils.runtime.metadata.ExtrinsicMetadata
import jp.co.soramitsu.fearless_utils.runtime.metadata.RuntimeMetadata
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.FunctionArgument
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.MetadataFunction
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.Module
import jp.co.soramitsu.staking.api.domain.model.RewardDestination
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Staking, parachain and pool calls added to a builder over a runtime without Asset Hub staking. */
class ExtrinsicBuilderExtCoverageTest {

    private class Recorded(val module: String, val call: String, val arguments: Map<String, Any?>)

    private val recorded = mutableListOf<Recorded>()
    private val alice = ByteArray(32) { 1 }
    private val bob = ByteArray(32) { 2 }
    private val bobHex = bob.toHexString(true)

    private fun runtime(modules: Map<String, Module> = emptyMap()) =
        RuntimeSnapshot(mockk(relaxed = true), RuntimeMetadata(BigInteger.ONE, modules, ExtrinsicMetadata(BigInteger.valueOf(4), emptyList())))

    private fun builder(runtime: RuntimeSnapshot = runtime()): ExtrinsicBuilder {
        val builder = mockk<ExtrinsicBuilder>()
        every { builder.runtime } returns runtime
        every { builder.call(any<String>(), any<String>(), any()) } answers {
            recorded += Recorded(firstArg(), secondArg(), thirdArg())
            builder
        }
        return builder
    }

    private fun stakingWithBond(vararg argumentNames: String): Map<String, Module> {
        val bond = MetadataFunction("bond", argumentNames.map { FunctionArgument(it, null) }, emptyList(), 0 to 0)
        return mapOf("Staking" to Module("Staking", null, mapOf("bond" to bond), null, emptyMap(), emptyMap(), BigInteger.ZERO))
    }

    private fun last() = recorded.last()

    @Test
    fun `controller calls`() {
        val builder = builder()

        builder.setController(MultiAddress.Id(alice))
        assertEquals("set_controller", last().call)
        assertEquals(MultiAddress.TYPE_ID, (last().arguments["controller"] as DictEnum.Entry<*>).name)

        builder.setController()
        assertTrue(last().arguments.isEmpty())

        builder.setControllerSora(alice)
        assertSame(alice, last().arguments["controller"])
    }

    @Test
    fun `sora bond and nominate pass raw account ids`() {
        val builder = builder()

        builder.bondSora(alice, BigInteger.TEN, RewardDestination.Restake)
        assertEquals(setOf("controller", "value", "payee"), last().arguments.keys)
        assertEquals(BigInteger.TEN, last().arguments["value"])

        builder.nominateSora(listOf(bob))
        assertEquals(listOf(bob), last().arguments["targets"])

        builder.nominate(listOf(MultiAddress.Id(bob)))
        assertEquals(1, (last().arguments["targets"] as List<*>).size)
        assertTrue(recorded.all { it.module == "Staking" })
    }

    @Test
    fun `legacy bond keeps the controller and unknown bond arguments are refused`() {
        builder(runtime(stakingWithBond("controller", "value", "payee"))).bond(MultiAddress.Id(alice), BigInteger.ONE, RewardDestination.Payout(bob))
        assertEquals(setOf("controller", "value", "payee"), last().arguments.keys)

        builder(runtime(stakingWithBond("value", "payee"))).bond(MultiAddress.Id(alice), BigInteger.ONE, RewardDestination.Restake)
        assertEquals(setOf("value", "payee"), last().arguments.keys)

        val refused = runCatching {
            builder(runtime(stakingWithBond("value"))).bond(MultiAddress.Id(alice), BigInteger.ONE, RewardDestination.Restake)
        }
        assertTrue(refused.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `parachain delegation calls`() {
        val builder = builder()

        builder.delegate(bob, BigInteger.TEN, BigInteger.valueOf(3), BigInteger.TWO)
        assertEquals(mapOf("candidate" to bob, "amount" to BigInteger.TEN, "candidate_delegation_count" to BigInteger.valueOf(3), "delegation_count" to BigInteger.TWO), last().arguments)
        builder.parachainCandidateBondMore(BigInteger.ONE)
        assertEquals(mapOf("more" to BigInteger.ONE), last().arguments)
        builder.parachainScheduleCandidateBondLess(BigInteger.ONE)
        assertEquals(mapOf("less" to BigInteger.ONE), last().arguments)
        builder.parachainDelegatorBondMore(bobHex, BigInteger.ONE)
        assertArrayEquals(bob, last().arguments["candidate"] as ByteArray)
        builder.parachainScheduleDelegatorBondLess(bobHex, BigInteger.ONE)
        assertEquals(BigInteger.ONE, last().arguments["less"])
        builder.parachainScheduleRevokeDelegation(bobHex)
        assertArrayEquals(bob, last().arguments["collator"] as ByteArray)
        builder.parachainExecuteDelegationRequest(bob, alice)
        assertSame(alice, last().arguments["delegator"])
        builder.parachainCancelDelegationRequest(bobHex)
        assertArrayEquals(bob, last().arguments["candidate"] as ByteArray)

        assertEquals(
            listOf(
                "delegate", "candidate_bond_more", "schedule_candidate_bond_less", "delegator_bond_more",
                "schedule_delegator_bond_less", "schedule_revoke_delegation", "execute_delegation_request", "cancel_delegation_request"
            ),
            recorded.map { it.call }
        )
        assertTrue(recorded.all { it.module == "ParachainStaking" })
    }

    @Test
    fun `payouts are paged only when a page is given`() {
        val builder = builder()

        builder.payoutStakers(BigInteger.TEN, bob)
        assertEquals("payout_stakers", last().call)
        builder.payoutStakers(BigInteger.TEN, bob, BigInteger.ONE)
        assertEquals("payout_stakers_by_page", last().call)
        assertEquals(BigInteger.ONE, last().arguments["page"])
        assertTrue(runCatching { builder.payoutStakers(BigInteger.TEN, bob, BigInteger.valueOf(-1)) }.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `staking ledger calls`() {
        val builder = builder()

        builder.bondMore(BigInteger.ONE)
        builder.chill()
        builder.unbond(BigInteger.TWO)
        builder.withdrawUnbonded(BigInteger.ZERO)
        builder.rebond(BigInteger.TEN)
        builder.setPayee(RewardDestination.Payout(alice))

        assertEquals(listOf("bond_extra", "chill", "unbond", "withdraw_unbonded", "rebond", "set_payee"), recorded.map { it.call })
        assertEquals(BigInteger.TWO, recorded[2].arguments["value"])
        assertEquals("Account", (last().arguments["payee"] as DictEnum.Entry<*>).name)
    }

    @Test
    fun `pool creation and management calls`() {
        val builder = builder()
        val root = MultiAddress.Id(alice)
        val noop = DictEnum.Entry<ByteArray?>("Noop", null)

        builder.joinPool(BigInteger.TEN, BigInteger.ONE)
        builder.createPoolBouncer(BigInteger.TEN, root, root, root)
        builder.createPoolStateToggler(BigInteger.TEN, root, root, root)
        builder.setPoolMetadata(BigInteger.ONE, "pool".toByteArray())
        builder.nominatePool(BigInteger.ONE, listOf(bob))
        builder.claimPayout()
        builder.bondExtra(BigInteger.TWO)
        builder.updateRolesStateToggler(BigInteger.ONE, noop, noop, noop)
        builder.updateRolesBouncer(BigInteger.ONE, noop, noop, noop)

        assertTrue(recorded.all { it.module == "NominationPools" })
        assertTrue("bouncer" in recorded[1].arguments)
        assertTrue("state_toggler" in recorded[2].arguments)
        assertEquals(DictEnum.Entry("FreeBalance", BigInteger.TWO), recorded[6].arguments["extra"])
        assertTrue("new_state_toggler" in recorded[7].arguments)
        assertTrue("new_bouncer" in recorded[8].arguments)
        assertEquals(listOf(bob), recorded[4].arguments["validators"])
    }
}
