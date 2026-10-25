package jp.co.soramitsu.staking.impl.data.repository

import jp.co.soramitsu.core.rpc.FinalizedExtrinsicReader
import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger

/** Exact public create receipts share the existing account-scoped durability and reconciliation gate. */
class PoolCreateJournalTest {
    private val scope = PoolJoinJournal.Scope(7, "91".repeat(32), "05".repeat(32))
    private val anchor = FinalizedExtrinsicReader.Anchor("0x" + scope.chainId, BigInteger.ZERO, "0x" + "20".repeat(32))
    private val details = PoolJoinJournal.CreateDetails("Local\n池", "public-nominator", "public-toggler")
    private val signed = "0x01020304"
    private val intent = "12".repeat(32)

    @Test fun `creation retains exact name roles bytes and original anchor after repository recreation`() {
        val f = PoolJoinPreferencesFixture()
        val original = retain(f)
        assertEquals(details, f.journal().read(scope)?.create)
        assertEquals(original, f.journal().read(scope))
        assertEquals(16, f.values.getValue(scope.key).split('\n').size)
        assertTrue(f.values.getValue(scope.key).startsWith("3\n"))
        assertEquals(signed, original.signedExtrinsic)
        assertEquals(anchor, original.anchor)
    }
    @Test fun `creation and joining cannot replace each others unresolved original`() {
        val f = PoolJoinPreferencesFixture()
        val created = retain(f)
        assertThrows(PoolJoinPendingException::class.java) {
            f.journal().retain(scope, BigInteger.ONE, BigInteger.TWO, signed, intent, anchor)
        }
        assertEquals(created, f.journal().read(scope))
        f.journal().discardUnsubmitted(created)
        val joined = f.journal().retain(scope, BigInteger.ONE, BigInteger.TWO, signed, intent, anchor)
        assertThrows(PoolJoinPendingException::class.java) { retain(f) }
        assertEquals(joined, f.journal().read(scope))
    }
    @Test fun `acknowledged creation preserves exact metadata without freeing its scope`() {
        val f = PoolJoinPreferencesFixture()
        val original = retain(f)
        f.journal().acknowledge(original)
        assertEquals(original.copy(acknowledged = true), f.journal().read(scope))
        assertTrue(assertThrows(PoolJoinPendingException::class.java) { f.journal().requireAvailable(scope) }.acknowledged)
    }
    @Test fun `finalized creation archives exact original details and releases only its scope`() {
        val f = PoolJoinPreferencesFixture()
        val original = retain(f)
        val inclusion = FinalizedExtrinsicReader.Inclusion("0x" + scope.chainId, original.transactionHash,
            "0x" + "31".repeat(32), BigInteger.ONE, 0, "0x" + "32".repeat(32), BigInteger.TEN)
        f.journal().finalize(original, inclusion)
        assertNull(f.journal().read(scope))
        f.journal().requireAvailable(scope)
        assertEquals(PoolJoinJournal.FinalizedPayment(original, inclusion), f.journal().readFinalized(scope, original.attemptId))
        assertEquals(details, f.journal().readFinalized(scope, original.attemptId)?.payment?.create)
        assertEquals(23, f.values.values.single().split('\n').size)
    }
    @Test fun `malformed creation text cannot become an available scope or alter stored bytes`() {
        for (badText in listOf("ff", "0", "zz", "")) {
            val f = PoolJoinPreferencesFixture()
            retain(f)
            val fields = f.values.getValue(scope.key).split('\n').toMutableList()
            fields[13] = badText
            val damaged = fields.joinToString("\n")
            f.values[scope.key] = damaged
            assertThrows(Exception::class.java) { f.journal().requireAvailable(scope) }
            assertEquals(damaged, f.values[scope.key])
        }
    }
    @Test fun `creation cannot be retained without its original finalized anchor`() {
        val f = PoolJoinPreferencesFixture()
        assertThrows(IllegalArgumentException::class.java) {
            f.journal().retain(scope, BigInteger.TEN, BigInteger.ONE, signed, intent, create = details)
        }
        assertTrue(f.values.isEmpty())
    }
    @Test fun `creation rejects empty or oversized fields before storage`() {
        for (value in listOf("", "池".repeat(200))) {
            assertThrows(IllegalArgumentException::class.java) { details.copy(poolName = value) }
        }
        assertThrows(IllegalArgumentException::class.java) { details.copy(nominatorAddress = "") }
        assertThrows(IllegalArgumentException::class.java) { details.copy(stateTogglerAddress = "x".repeat(257)) }
    }
    private fun retain(f: PoolJoinPreferencesFixture) =
        f.journal().retain(scope, BigInteger.TEN, BigInteger.valueOf(8), signed, intent, anchor, details)
}
