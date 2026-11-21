package jp.co.soramitsu.staking.api.domain.model

import java.math.BigInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PoolMemberTest {

    private val pool = OwnPool(
        poolId = BigInteger.valueOf(9), name = "North pool", myStakeInPlanks = BigInteger.TEN,
        totalStakedInPlanks = BigInteger.valueOf(100), lastRecordedRewardCounter = BigInteger.valueOf(7),
        state = NominationPoolState.Open, redeemable = BigInteger.ONE, unbonding = BigInteger.TWO,
        pendingRewards = BigInteger.valueOf(3), members = BigInteger.valueOf(4),
        depositor = byteArrayOf(1), root = byteArrayOf(2), nominator = byteArrayOf(3), stateToggler = byteArrayOf(4)
    )

    @Test
    fun shouldDeduplicateOwnPoolsUsingAccountContents() {
        val same = pool.copy(
            depositor = pool.depositor.copyOf(), root = pool.root?.copyOf(),
            nominator = pool.nominator?.copyOf(), stateToggler = pool.stateToggler?.copyOf()
        )
        assertEquals(pool, same)
        assertEquals(pool.hashCode(), same.hashCode())
        assertEquals(1, setOf(pool, same).size)
        assertEquals(pool, pool)
        assertFalse(pool.equals(null))
        assertFalse(pool.equals("pool"))
    }

    @Test
    fun shouldDistinguishOwnPoolBalancesRolesAndMetadata() {
        val alternatives = listOf(
            pool.copy(poolId = BigInteger.ONE), pool.copy(name = "South"),
            pool.copy(myStakeInPlanks = BigInteger.ZERO), pool.copy(totalStakedInPlanks = BigInteger.ZERO),
            pool.copy(lastRecordedRewardCounter = BigInteger.ZERO), pool.copy(state = NominationPoolState.Blocked),
            pool.copy(redeemable = BigInteger.ZERO), pool.copy(unbonding = BigInteger.ZERO),
            pool.copy(pendingRewards = BigInteger.ZERO), pool.copy(members = BigInteger.ZERO),
            pool.copy(depositor = byteArrayOf(8)), pool.copy(root = null),
            pool.copy(nominator = null), pool.copy(stateToggler = null)
        )
        alternatives.forEach { assertNotEquals(pool, it) }
        val roleless = pool.copy(name = null, root = null, nominator = null, stateToggler = null)
        assertEquals(roleless, roleless.copy())
        assertEquals(roleless.hashCode(), roleless.copy().hashCode())
    }

    @Test
    fun shouldRecognizeRolesByAccountBytesAndApplyRolePriority() {
        assertEquals(RoleInPool.Depositor, pool.getUserRole(byteArrayOf(1)))
        assertEquals(RoleInPool.Root, pool.getUserRole(byteArrayOf(2)))
        assertEquals(RoleInPool.Nominator, pool.getUserRole(byteArrayOf(3)))
        assertEquals(RoleInPool.StateToggler, pool.getUserRole(byteArrayOf(4)))
        assertNull(pool.getUserRole(byteArrayOf(9)))
        assertEquals(RoleInPool.Depositor, pool.copy(root = byteArrayOf(1)).getUserRole(byteArrayOf(1)))
        assertEquals(RoleInPool.Root, pool.copy(stateToggler = byteArrayOf(2)).getUserRole(byteArrayOf(2)))
        assertEquals(RoleInPool.StateToggler, pool.copy(nominator = byteArrayOf(4)).getUserRole(byteArrayOf(4)))
        assertNull(pool.copy(root = null, nominator = null, stateToggler = null).getUserRole(byteArrayOf(9)))
    }

    @Test
    fun shouldProjectPoolTotalsAndUseFallbackNameOnlyWhenAbsent() {
        val info = pool.toPoolInfo()
        assertEquals(pool.poolId, info.poolId)
        assertEquals("North pool", info.name)
        assertEquals(pool.totalStakedInPlanks, info.stakedInPlanks)
        assertEquals(pool.state, info.state)
        assertEquals(pool.members, info.members)
        assertTrue(pool.depositor.contentEquals(info.depositor))
        assertTrue(pool.root.contentEquals(info.root))
        assertTrue(pool.nominator.contentEquals(info.nominator))
        assertTrue(pool.stateToggler.contentEquals(info.stateToggler))
        assertEquals("Pool #9", pool.copy(name = null).toPoolInfo().name)
        assertEquals("", pool.copy(name = "").toPoolInfo().name)
    }

    @Test
    fun shouldDeduplicatePoolInfoUsingAccountContents() {
        val info = pool.toPoolInfo()
        val same = info.copy(
            depositor = info.depositor.copyOf(), root = info.root?.copyOf(),
            nominator = info.nominator?.copyOf(), stateToggler = info.stateToggler?.copyOf()
        )
        assertEquals(info, same)
        assertEquals(info.hashCode(), same.hashCode())
        assertEquals(1, setOf(info, same).size)
        assertEquals(info, info)
        assertFalse(info.equals(null))
        assertFalse(info.equals(pool))
        val roleless = info.copy(root = null, nominator = null, stateToggler = null)
        assertEquals(roleless.hashCode(), roleless.copy().hashCode())
    }

    @Test
    fun shouldDistinguishPoolInfoBalancesRolesAndMetadata() {
        val info = pool.toPoolInfo()
        listOf(
            info.copy(poolId = BigInteger.ONE), info.copy(name = "South"),
            info.copy(stakedInPlanks = BigInteger.ZERO), info.copy(state = NominationPoolState.Destroying),
            info.copy(members = BigInteger.ZERO), info.copy(depositor = byteArrayOf(8)),
            info.copy(root = null), info.copy(nominator = null), info.copy(stateToggler = null)
        ).forEach { assertNotEquals(info, it) }
    }

    @Test
    fun shouldDecodeOnChainPoolStatesAndRejectUnknownTags() {
        assertEquals(NominationPoolState.Open, NominationPoolState.from("Open"))
        assertEquals(NominationPoolState.Blocked, NominationPoolState.from("Blocked"))
        assertEquals(NominationPoolState.Destroying, NominationPoolState.from("Destroying"))
        val error = runCatching { NominationPoolState.from("unknown") }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        assertEquals("Nomination pool state cannot be parsed", error?.message)
    }
}
