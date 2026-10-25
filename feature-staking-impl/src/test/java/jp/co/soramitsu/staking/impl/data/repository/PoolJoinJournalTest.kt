package jp.co.soramitsu.staking.impl.data.repository

import jp.co.soramitsu.core.extrinsic.MutationExecutionGuard
import jp.co.soramitsu.core.rpc.FinalizedExtrinsicReader
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class PoolJoinJournalTest {
    private val scope = PoolJoinJournal.Scope(7, "91".repeat(32), "05".repeat(32))
    private val anchor = FinalizedExtrinsicReader.Anchor("0x" + scope.chainId, BigInteger.ZERO, "0x" + "20".repeat(32))
    private val intent = "12".repeat(32)
    private val signed = "0x01020304"

    @Test fun `old unanchored version one bytes remain readable and unresolved without rewriting`() {
        val f = PoolJoinPreferencesFixture()
        val original = f.journal().retain(scope, BigInteger.TEN, BigInteger.ONE, signed, intent)
        val encoded = f.values.getValue(scope.key)
        assertTrue(encoded.startsWith("1\n"))
        assertNull(original.anchor)
        assertEquals(original, f.journal().read(scope))
        assertThrows(IllegalStateException::class.java) { f.journal().finalize(original, inclusion(original)) }
        assertEquals(encoded, f.values[scope.key])
        assertThrows(PoolJoinPendingException::class.java) { f.journal().requireAvailable(scope) }
    }

    @Test fun `retained public bytes and intent survive a new repository instance`() {
        val f = PoolJoinPreferencesFixture()
        val payment = f.journal().retain(scope, BigInteger.TEN, BigInteger.ONE, signed, intent, anchor)
        assertEquals(payment, f.journal().read(scope))
        assertEquals(signed, f.journal().read(scope)?.signedExtrinsic)
        val failure = assertThrows(PoolJoinPendingException::class.java) { f.journal().requireAvailable(scope) }
        assertEquals(payment.transactionHash, failure.transactionHash)
        assertFalse(failure.acknowledged)
    }

    @Test fun `acknowledgement survives recreation without claiming finality or permitting replacement`() {
        val f = PoolJoinPreferencesFixture()
        val payment = f.journal().retain(scope, BigInteger.TEN, BigInteger.ONE, signed, intent, anchor)
        f.journal().acknowledge(payment)
        assertEquals(payment.copy(acknowledged = true), f.journal().read(scope))
        assertTrue(assertThrows(PoolJoinPendingException::class.java) { f.journal().requireAvailable(scope) }.acknowledged)
    }

    @Test fun `unresolved wallet network and account scope leaves other scopes available`() {
        val f = PoolJoinPreferencesFixture()
        f.journal().retain(scope, BigInteger.TEN, BigInteger.ONE, signed, intent, anchor)
        f.journal().requireAvailable(scope.copy(walletId = 8))
        f.journal().requireAvailable(scope.copy(chainId = "92".repeat(32)))
        f.journal().requireAvailable(scope.copy(accountId = "06".repeat(32)))
        assertEquals(1, f.values.size)
    }

    @Test fun `different amount or pool cannot overwrite an unresolved original`() {
        val f = PoolJoinPreferencesFixture()
        val original = f.journal().retain(scope, BigInteger.TEN, BigInteger.ONE, signed, intent, anchor)
        assertThrows(PoolJoinPendingException::class.java) {
            f.journal().retain(scope, BigInteger.ONE, BigInteger.TWO, "0x030405", "13".repeat(32))
        }
        assertEquals(original, f.journal().read(scope))
    }

    @Test fun `unknown or damaged records never mean no pending payment`() {
        for (encoded in listOf("", "2\nfuture-version", "1\n7", "garbage")) {
            val f = PoolJoinPreferencesFixture()
            f.values[scope.key] = encoded
            assertThrows(Exception::class.java) { f.journal().requireAvailable(scope) }
            assertEquals(encoded, f.values[scope.key])
        }
    }

    @Test fun `altered signed payload or another scope fails validation without deletion`() {
        val f = PoolJoinPreferencesFixture()
        f.journal().retain(scope, BigInteger.TEN, BigInteger.ONE, signed, intent, anchor)
        val original = f.values.getValue(scope.key)
        f.values[scope.key] = original.replace(signed, "0x03040506")
        assertThrows(Exception::class.java) { f.journal().read(scope) }
        f.values[scope.key] = original.replace("\n7\n", "\n8\n")
        assertThrows(Exception::class.java) { f.journal().read(scope) }
        assertEquals(1, f.values.size)
    }

    @Test fun `only the exact owned record can be removed as unsent`() {
        val f = PoolJoinPreferencesFixture()
        val journal = f.journal()
        val original = journal.retain(scope, BigInteger.TEN, BigInteger.ONE, signed, intent, anchor)
        assertThrows(IllegalStateException::class.java) { journal.discardUnsubmitted(original.copy(attemptId = java.util.UUID.randomUUID().toString())) }
        assertEquals(original, journal.read(scope))
        journal.discardUnsubmitted(original)
        assertNull(f.journal().read(scope))
    }

    @Test fun `failed durable retention cannot reach output and latches only its scope`() {
        val f = PoolJoinPreferencesFixture()
        val journal = f.journal()
        f.failCommit = true
        val tracker = tracking(journal)
        assertThrows(IllegalStateException::class.java) { tracker.guard(signed, intent, Authority()) }
        tracker.finished()
        assertThrows(IllegalStateException::class.java) { journal.requireAvailable(scope) }
        journal.requireAvailable(scope.copy(walletId = 8))
    }

    @Test fun `failed durable removal does not silently free the payment`() {
        val f = PoolJoinPreferencesFixture()
        val journal = f.journal()
        val original = journal.retain(scope, BigInteger.TEN, BigInteger.ONE, signed, intent, anchor)
        val beforeFailure = f.values.toMap()
        f.failCommit = true
        assertThrows(IllegalStateException::class.java) { journal.discardUnsubmitted(original) }
        assertThrows(IllegalStateException::class.java) { journal.requireAvailable(scope) }
        assertEquals(beforeFailure, f.values)
        assertThrows(IllegalStateException::class.java) { f.journal().read(scope) }
    }

    @Test fun `final output sees retained bytes before admission and failure keeps them`() {
        val f = PoolJoinPreferencesFixture()
        val tracker = tracking(f.journal())
        val guard = tracker.guard(signed, intent, Authority())
        assertThrows(IllegalStateException::class.java) {
            guard.runSubmissionIfAuthorized(intent) {
                assertEquals(signed, f.journal().read(scope)?.signedExtrinsic)
                error("local partial write")
            }
        }
        tracker.finished()
        assertEquals(signed, f.journal().read(scope)?.signedExtrinsic)
        assertThrows(IllegalStateException::class.java) { guard.runSubmissionIfAuthorized(intent) { fail("duplicate output") } }
    }

    @Test fun `finished unsent attempt seals queued writer before deleting record`() {
        val f = PoolJoinPreferencesFixture()
        val tracker = tracking(f.journal())
        val guard = tracker.guard(signed, intent, Authority())
        tracker.finished()
        assertNull(f.journal().read(scope))
        assertThrows(IllegalStateException::class.java) { guard.runSubmissionIfAuthorized(intent) { fail("late output") } }
    }

    @Test fun `revocation during retention stops output and releases positively unsent bytes`() {
        val f = PoolJoinPreferencesFixture()
        val authority = Authority()
        f.beforeCommit = { authority.allowed = false }
        val tracker = tracking(f.journal())
        assertThrows(IllegalStateException::class.java) { tracker.guard(signed, intent, authority) }
        tracker.finished()
        assertNull(f.journal().read(scope))
    }

    @Test fun `matching acknowledgement consumes known success before a failed status write`() {
        val f = PoolJoinPreferencesFixture()
        val journal = f.journal()
        var consumed = 0
        val tracker = tracking(journal) { consumed++; f.failCommit = true }
        tracker.guard(signed, intent, Authority()).runSubmissionIfAuthorized(intent) { Unit }
        val payment = journal.read(scope)!!
        val beforeFailure = f.values.toMap()
        tracker.acknowledged(payment.transactionHash)
        tracker.finished()
        assertEquals(1, consumed)
        assertEquals(beforeFailure, f.values)
        assertThrows(IllegalStateException::class.java) { f.journal().read(scope) }
        assertThrows(IllegalStateException::class.java) { journal.requireAvailable(scope) }
    }

    @Test fun `wrong acknowledgement cannot consume or remove admitted payment`() {
        val f = PoolJoinPreferencesFixture()
        var consumed = 0
        val tracker = tracking(f.journal()) { consumed++ }
        tracker.guard(signed, intent, Authority()).runSubmissionIfAuthorized(intent) { Unit }
        assertThrows(IllegalArgumentException::class.java) { tracker.acknowledged("0x" + "00".repeat(32)) }
        tracker.finished()
        assertEquals(0, consumed)
        assertNotNull(f.journal().read(scope))
    }

    @Test fun `final gate rejects writer released after attempt cleanup`() {
        val f = PoolJoinPreferencesFixture()
        val entered = CountDownLatch(1)
        val resume = CountDownLatch(1)
        val authority = object : MutationExecutionGuard {
            override fun <T> runIfAuthorized(intentSha256: String, operation: () -> T): T = operation()
            override fun <T> runSubmissionIfAuthorized(intentSha256: String, operation: () -> T): T {
                entered.countDown()
                check(resume.await(5, TimeUnit.SECONDS))
                return operation()
            }
        }
        val tracker = tracking(f.journal())
        val guard = tracker.guard(signed, intent, authority)
        val writes = AtomicInteger(0)
        val rejected = AtomicInteger(0)
        val worker = kotlin.concurrent.thread {
            try { guard.runSubmissionIfAuthorized(intent) { writes.incrementAndGet() } }
            catch (_: IllegalStateException) { rejected.incrementAndGet() }
        }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            tracker.finished()
            assertNull(f.journal().read(scope))
        } finally { resume.countDown(); worker.join(5000) }
        assertEquals(0, writes.get())
        assertEquals(1, rejected.get())
    }

    @Test fun `scope lock serializes across repository instances`() = runBlocking {
        val f = PoolJoinPreferencesFixture()
        val held = kotlinx.coroutines.CompletableDeferred<Unit>()
        val release = kotlinx.coroutines.CompletableDeferred<Unit>()
        val secondEntered = kotlinx.coroutines.CompletableDeferred<Unit>()
        val first = kotlinx.coroutines.CoroutineScope(coroutineContext).launch {
            f.journal().withScope(scope) { held.complete(Unit); release.await() }
        }
        held.await()
        val second = kotlinx.coroutines.CoroutineScope(coroutineContext).launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            f.journal().withScope(scope) { secondEntered.complete(Unit) }
        }
        assertFalse(secondEntered.isCompleted)
        release.complete(Unit)
        kotlinx.coroutines.withTimeout(5000) { first.join(); second.join(); secondEntered.await() }
        Unit
    }

    @Test fun `finalized inclusion archives exact original before a new reviewed payment becomes available`() {
        val f = PoolJoinPreferencesFixture()
        val journal = f.journal()
        val original = journal.retain(scope, BigInteger.TEN, BigInteger.ONE, signed, intent, anchor)
        val inclusion = inclusion(original)
        journal.finalize(original, inclusion)
        assertNull(f.journal().read(scope))
        assertEquals(PoolJoinJournal.FinalizedPayment(original, inclusion), f.journal().readFinalized(scope, original.attemptId))
        val next = f.journal().retain(scope, BigInteger.ONE, BigInteger.TWO, "0x0506", intent)
        assertNotEquals(original.attemptId, next.attemptId)
        assertEquals(original.signedExtrinsic, f.journal().readFinalized(scope, original.attemptId)?.payment?.signedExtrinsic)
    }

    @Test fun `failed archive commit cannot free original payment`() {
        val f = PoolJoinPreferencesFixture()
        val journal = f.journal()
        val original = journal.retain(scope, BigInteger.TEN, BigInteger.ONE, signed, intent, anchor)
        val beforeFailure = f.values.toMap()
        f.failCommit = true
        assertThrows(IllegalStateException::class.java) { journal.finalize(original, inclusion(original)) }
        assertThrows(IllegalStateException::class.java) { journal.requireAvailable(scope) }
        assertEquals(beforeFailure, f.values)
        assertThrows(IllegalStateException::class.java) { f.journal().read(scope) }
        assertThrows(IllegalStateException::class.java) { f.journal().readFinalized(scope, original.attemptId) }
    }

    @Test fun `archive readback mismatch cannot report durable completion`() {
        val f = PoolJoinPreferencesFixture()
        val journal = f.journal()
        val original = journal.retain(scope, BigInteger.TEN, BigInteger.ONE, signed, intent, anchor)
        val beforeFailure = f.values.toMap()
        org.mockito.kotlin.whenever(f.preferences.replaceStringsDurably(org.mockito.kotlin.any(), org.mockito.kotlin.any())).thenReturn(true)
        assertThrows(IllegalStateException::class.java) { journal.finalize(original, inclusion(original)) }
        assertEquals(beforeFailure, f.values)
        assertThrows(IllegalStateException::class.java) { f.journal().read(scope) }
    }

    @Test fun `mutating failed archive cannot look committed to a recreated journal`() {
        val f = PoolJoinPreferencesFixture()
        val journal = f.journal()
        val original = journal.retain(scope, BigInteger.TEN, BigInteger.ONE, signed, intent, anchor)
        org.mockito.kotlin.whenever(f.preferences.replaceStringsDurably(org.mockito.kotlin.any(), org.mockito.kotlin.any())).thenAnswer { call ->
            call.getArgument<Set<String>>(1).forEach(f.values::remove)
            f.values.putAll(call.getArgument<Map<String, String>>(0))
            false // SharedPreferences can publish its new memory map even when disk acknowledgement fails.
        }
        assertThrows(IllegalStateException::class.java) { journal.finalize(original, inclusion(original)) }
        assertNull(f.values[scope.key])
        assertThrows(IllegalStateException::class.java) { f.journal().requireAvailable(scope) }
        assertThrows(IllegalStateException::class.java) { f.journal().readFinalized(scope, original.attemptId) }
    }

    @Test fun `wrong transaction network or nonfinalized proof cannot archive original`() {
        val f = PoolJoinPreferencesFixture()
        val journal = f.journal()
        val original = journal.retain(scope, BigInteger.TEN, BigInteger.ONE, signed, intent, anchor)
        val valid = inclusion(original)
        for (invalid in listOf(valid.copy(transactionHash = "0x" + "ab".repeat(32)),
            valid.copy(genesisHash = "0x" + "92".repeat(32)), valid.copy(blockHash = "bad"),
            valid.copy(blockNumber = BigInteger.valueOf(-1)), valid.copy(finalizedHeadNumber = BigInteger.ZERO),
            valid.copy(extrinsicIndex = -1))) {
            assertThrows(IllegalArgumentException::class.java) { journal.finalize(original, invalid) }
            assertEquals(original, journal.read(scope))
        }
    }

    @Test fun `stale payment cannot resolve a replacement or erase its retained bytes`() {
        val f = PoolJoinPreferencesFixture()
        val journal = f.journal()
        val original = journal.retain(scope, BigInteger.TEN, BigInteger.ONE, signed, intent, anchor)
        journal.discardUnsubmitted(original)
        val replacement = journal.retain(scope, BigInteger.TEN, BigInteger.ONE, signed, intent, anchor)
        assertThrows(IllegalStateException::class.java) { journal.finalize(original, inclusion(original)) }
        assertEquals(replacement, journal.read(scope))
    }

    @Test fun `unknown or conflicting archive is preserved and never overwrites pending payment`() {
        val f = PoolJoinPreferencesFixture()
        val journal = f.journal()
        val original = journal.retain(scope, BigInteger.TEN, BigInteger.ONE, signed, intent, anchor)
        val key = scope.key + ".finalized." + original.attemptId
        f.values[key] = "future-format"
        assertThrows(IllegalStateException::class.java) { journal.finalize(original, inclusion(original)) }
        assertThrows(IllegalArgumentException::class.java) { journal.readFinalized(scope, original.attemptId) }
        assertEquals(original, journal.read(scope))
        assertEquals("future-format", f.values[key])
    }

    private fun inclusion(payment: PoolJoinJournal.Payment) = FinalizedExtrinsicReader.Inclusion(
        "0x" + payment.scope.chainId, payment.transactionHash, "0x" + "21".repeat(32), BigInteger.TEN, 1,
        "0x" + "22".repeat(32), BigInteger.valueOf(12)
    )

    private fun tracking(journal: PoolJoinJournal, acknowledged: () -> Unit = {}) =
        PoolJoinSubmissionTracking(journal, scope, BigInteger.TEN, BigInteger.ONE, anchor, onSubmitted = acknowledged)

    private class Authority : MutationExecutionGuard {
        @Volatile var allowed = true
        override fun <T> runIfAuthorized(intentSha256: String, operation: () -> T): T {
            check(allowed)
            return operation()
        }
    }
}
