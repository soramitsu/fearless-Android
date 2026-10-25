package jp.co.soramitsu.staking.impl.data.repository

import jp.co.soramitsu.core.rpc.FinalizedExtrinsicReader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*
import java.math.BigInteger

class PoolJoinReconcilerTest {
    private val scope = PoolJoinJournal.Scope(7, "91".repeat(32), "05".repeat(32))

    @Test fun `positive inclusion archives before synchronous original review completion`() = runBlocking<Unit> {
        val f = PoolJoinPreferencesFixture()
        val payment = retained(f)
        val reader = mock<FinalizedExtrinsicReader>()
        val inclusion = inclusion(payment)
        whenever(reader.search(scope.chainId, payment.signedExtrinsic, null, 32, payment.anchor))
            .thenReturn(FinalizedExtrinsicReader.SearchResult(inclusion, null, 3))
        var completed = 0
        val result = PoolJoinReconciler(f.journal(), reader).check(scope, payment.attemptId) {
            assertNull(f.journal().read(scope))
            assertEquals(payment, f.journal().readFinalized(scope, payment.attemptId)?.payment)
            completed++
        }
        assertEquals(payment, result.payment)
        assertEquals(inclusion, result.search.inclusion)
        assertEquals(1, completed)
        verify(reader).search(scope.chainId, payment.signedExtrinsic, null, 32, payment.anchor)
        verifyNoMoreInteractions(reader)
    }

    @Test fun `miss keeps byte exact original and allows only an older read cursor`() = runBlocking<Unit> {
        val f = PoolJoinPreferencesFixture()
        val payment = retained(f)
        val before = f.values.toMap()
        val reader = mock<FinalizedExtrinsicReader>()
        val cursor = BigInteger.valueOf(100)
        whenever(reader.search(scope.chainId, payment.signedExtrinsic, cursor, 32, payment.anchor))
            .thenReturn(FinalizedExtrinsicReader.SearchResult(null, BigInteger.valueOf(68), 32))
        val result = PoolJoinReconciler(f.journal(), reader).check(scope, payment.attemptId, cursor) { fail("Miss consumed review") }
        assertNull(result.search.inclusion)
        assertEquals(BigInteger.valueOf(68), result.search.nextBeforeHeight)
        assertEquals(before, f.values)
        assertThrows(PoolJoinPendingException::class.java) { f.journal().requireAvailable(scope) }
    }

    @Test fun `cancelled or unavailable rpc preserves pending record and no callback`() {
        for (error in listOf(CancellationException("cancelled"), IllegalStateException("pruned"))) {
            val f = PoolJoinPreferencesFixture()
            val payment = retained(f)
            val reader = mock<FinalizedExtrinsicReader>()
            runBlocking { whenever(reader.search(scope.chainId, payment.signedExtrinsic, null, 32, payment.anchor)).thenThrow(error) }
            val actual = assertThrows(error.javaClass) {
                runBlocking { PoolJoinReconciler(f.journal(), reader).check(scope, payment.attemptId) { fail("Failure consumed review") } }
            }
            assertSame(error, actual)
            assertEquals(payment, f.journal().read(scope))
        }
    }

    @Test fun `stale caller or different scope cannot reconcile another original`() {
        val f = PoolJoinPreferencesFixture()
        val payment = retained(f)
        val reader = mock<FinalizedExtrinsicReader>()
        val reconciler = PoolJoinReconciler(f.journal(), reader)
        assertThrows(IllegalStateException::class.java) {
            runBlocking { reconciler.check(scope, java.util.UUID.randomUUID().toString()) {} }
        }
        assertThrows(IllegalStateException::class.java) {
            runBlocking { reconciler.check(scope.copy(walletId = 8), payment.attemptId) {} }
        }
        verifyNoInteractions(reader)
        assertEquals(payment, f.journal().read(scope))
    }

    @Test fun `durable archive failure cannot consume review or report completion`() = runBlocking<Unit> {
        val f = PoolJoinPreferencesFixture()
        val payment = retained(f)
        val reader = mock<FinalizedExtrinsicReader>()
        whenever(reader.search(scope.chainId, payment.signedExtrinsic, null, 32, payment.anchor))
            .thenReturn(FinalizedExtrinsicReader.SearchResult(inclusion(payment), null, 1))
        val beforeFailure = f.values.toMap()
        f.failCommit = true
        assertThrows(IllegalStateException::class.java) {
            runBlocking { PoolJoinReconciler(f.journal(), reader).check(scope, payment.attemptId) { fail("Failed archive consumed review") } }
        }
        assertEquals(beforeFailure, f.values)
        assertThrows(IllegalStateException::class.java) { f.journal().read(scope) }
    }

    @Test fun `acknowledgement without inclusion remains unresolved even after all searched blocks`() = runBlocking<Unit> {
        val f = PoolJoinPreferencesFixture()
        val payment = retained(f)
        f.journal().acknowledge(payment)
        val reader = mock<FinalizedExtrinsicReader>()
        whenever(reader.search(scope.chainId, payment.signedExtrinsic, null, 32, payment.anchor))
            .thenReturn(FinalizedExtrinsicReader.SearchResult(null, null, 11))
        PoolJoinReconciler(f.journal(), reader).check(scope, payment.attemptId) { fail("Ack is not finality") }
        assertEquals(payment.copy(acknowledged = true), f.journal().read(scope))
    }

    @Test fun `unanchored original cannot be freed by an older identical signed transaction`() = runBlocking<Unit> {
        val f = PoolJoinPreferencesFixture()
        val payment = f.journal().retain(scope, BigInteger.TEN, BigInteger.ONE, "0x01020304", "12".repeat(32))
        val reader = mock<FinalizedExtrinsicReader>()
        whenever(reader.search(scope.chainId, payment.signedExtrinsic, null, 32, payment.anchor))
            .thenReturn(FinalizedExtrinsicReader.SearchResult(inclusion(payment), null, 1))
        assertThrows(IllegalStateException::class.java) {
            runBlocking { PoolJoinReconciler(f.journal(), reader).check(scope, payment.attemptId) {} }
        }
        assertEquals(payment, f.journal().read(scope))
    }

    private fun retained(f: PoolJoinPreferencesFixture) = f.journal().retain(scope, BigInteger.TEN, BigInteger.ONE, "0x01020304", "12".repeat(32),
        FinalizedExtrinsicReader.Anchor("0x" + scope.chainId, BigInteger.ZERO, "0x" + "20".repeat(32)))

    private fun inclusion(payment: PoolJoinJournal.Payment) = FinalizedExtrinsicReader.Inclusion(
        "0x" + scope.chainId, payment.transactionHash, "0x" + "21".repeat(32), BigInteger.TEN, 1,
        "0x" + "22".repeat(32), BigInteger.valueOf(12)
    )
}
