package jp.co.soramitsu.staking.api.domain.model

import java.math.BigInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DelegationScheduledRequestTest {

    private val request = DelegationScheduledRequest(
        byteArrayOf(1, 2, 3), BigInteger.TEN, DelegationAction.UNSTAKE, BigInteger.valueOf(25)
    )

    @Test
    fun shouldDeduplicateRequestsWithEqualAccountBytes() {
        val same = request.copy(delegator = request.delegator.copyOf())

        assertEquals(request, same)
        assertEquals(request.hashCode(), same.hashCode())
        assertEquals(1, setOf(request, same, request).size)
    }

    @Test
    fun shouldDistinguishEveryRequestField() {
        val different = listOf(
            request.copy(delegator = byteArrayOf(4)),
            request.copy(whenExecutable = BigInteger.ONE),
            request.copy(action = DelegationAction.DELEGATE),
            request.copy(actionValue = BigInteger.ZERO)
        )

        different.forEach { assertNotEquals(request, it) }
        assertFalse(request.equals(null))
        assertFalse(request.equals("request"))
        assertEquals(request, request)
    }

    @Test
    fun shouldDecodeOnlySupportedScheduledActions() {
        assertEquals(DelegationAction.UNSTAKE, DelegationAction.from("Revoke"))
        assertEquals(DelegationAction.UNSTAKE, DelegationAction.from("Decrease"))
        listOf(null, "", "Stake", "unknown").forEach {
            assertEquals(DelegationAction.OTHER, DelegationAction.from(it))
        }
    }

    @Test
    fun shouldMapHistoryIdsAndKeepUnknownActionsUnlabelled() {
        val expected = listOf(DelegationAction.STAKE, DelegationAction.UNSTAKE, DelegationAction.REWARD, DelegationAction.DELEGATE)
        expected.forEachIndexed { id, action -> assertEquals(action, DelegationAction.byId(id)) }
        listOf(null, -1, 4, Int.MAX_VALUE).forEach { assertEquals(DelegationAction.OTHER, DelegationAction.byId(it)) }
        assertNull(DelegationAction.OTHER.nameResId)
    }
}
