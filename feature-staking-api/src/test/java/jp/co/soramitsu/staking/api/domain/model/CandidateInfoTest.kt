package jp.co.soramitsu.staking.api.domain.model

import android.os.Parcel
import java.math.BigInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CandidateInfoTest {

    @Test
    fun shouldDecodeCandidateStatusesAndUnknownValues() {
        assertSame(CandidateInfoStatus.ACTIVE, CandidateInfoStatus.from("Active"))
        assertSame(CandidateInfoStatus.IDLE, CandidateInfoStatus.from("Idle"))
        assertNull((CandidateInfoStatus.from("Leaving") as CandidateInfoStatus.LEAVING).leavingBlock)
        listOf(null, "", "unknown").forEach { assertSame(CandidateInfoStatus.EMPTY, CandidateInfoStatus.from(it)) }
    }

    @Test
    fun shouldDecodeDelegatorStatusesAndUnknownValues() {
        assertEquals(DelegatorStateStatus.ACTIVE, DelegatorStateStatus.from("Active"))
        assertEquals(DelegatorStateStatus.LEAVING, DelegatorStateStatus.from("Leaving"))
        assertEquals(DelegatorStateStatus.IDLE, DelegatorStateStatus.from("Idle"))
        listOf(null, "", "unknown").forEach { assertEquals(DelegatorStateStatus.EMPTY, DelegatorStateStatus.from(it)) }
    }

    @Test
    fun shouldDecodeCapacityAndTreatUnknownCapacityAsEmpty() {
        assertEquals(CandidateCapacity.Full, CandidateCapacity.from("Full"))
        assertEquals(CandidateCapacity.Partial, CandidateCapacity.from("Partial"))
        listOf(null, "Empty", "unknown").forEach { assertEquals(CandidateCapacity.Empty, CandidateCapacity.from(it)) }
    }

    @Test
    fun shouldPreserveCandidateBalancesAndLeavingRoundInParcel() {
        val candidate = CandidateInfo(
            BigInteger.valueOf(100), BigInteger.TWO, BigInteger.valueOf(400),
            BigInteger.valueOf(30), BigInteger.valueOf(20), BigInteger.TEN,
            CandidateCapacity.Full, CandidateCapacity.Partial, "pending", CandidateInfoStatus.LEAVING(42)
        )
        val parcel = Parcel.obtain()
        try {
            parcel.writeParcelable(candidate, 0)
            parcel.setDataPosition(0)
            val restored = requireNotNull(parcel.readParcelable<CandidateInfo>(CandidateInfo::class.java.classLoader))

            assertEquals(candidate.bond, restored.bond)
            assertEquals(candidate.delegationCount, restored.delegationCount)
            assertEquals(candidate.totalCounted, restored.totalCounted)
            assertEquals(candidate.lowestTopDelegationAmount, restored.lowestTopDelegationAmount)
            assertEquals(candidate.highestBottomDelegationAmount, restored.highestBottomDelegationAmount)
            assertEquals(candidate.lowestBottomDelegationAmount, restored.lowestBottomDelegationAmount)
            assertEquals(candidate.topCapacity, restored.topCapacity)
            assertEquals(candidate.bottomCapacity, restored.bottomCapacity)
            assertEquals("pending", restored.request)
            assertEquals(42L, (restored.status as CandidateInfoStatus.LEAVING).leavingBlock)
        } finally {
            parcel.recycle()
        }
    }
}
