package jp.co.soramitsu.wallet.impl.data.repository

import io.mockk.every
import io.mockk.mockk
import java.math.BigInteger
import java.util.concurrent.ConcurrentHashMap
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.core.extrinsic.MutationExecutionGuard
import jp.co.soramitsu.core.extrinsic.mortality.ExtrinsicValidity
import jp.co.soramitsu.core.rpc.FinalizedExtrinsicReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossChainSubmissionTrackingTest {

    private val values = ConcurrentHashMap<String, String>()
    private val preferences = mockk<Preferences> {
        every { durableStorageIdentity } returns Any()
        every { getString(any()) } answers { values[firstArg()] }
        every { replaceStringsDurably(any(), any()) } answers {
            secondArg<Set<String>>().forEach(values::remove)
            values.putAll(firstArg<Map<String, String>>())
            true
        }
    }
    private val journal = CrossChainSubmissionJournal(preferences)
    private val tracking = CrossChainSubmissionTracking(journal, SCOPE, DETAILS, ANCHOR)

    @Test
    fun `the signed bytes are on disk with their validity window before any output`() {
        tracking.validity(VALIDITY)

        val guard = tracking.guard(SIGNED, INTENT, AllowAll)

        val retained = journal.read(SCOPE)!!
        assertEquals(SIGNED, retained.signedExtrinsic)
        assertEquals(VALIDITY, retained.validity)
        assertEquals(retained, tracking.submission)
        assertEquals("sent", guard.runSubmissionIfAuthorized(INTENT) { "sent" })
    }

    @Test
    fun `an attempt hands at most one output to the network`() {
        val guard = tracking.guard(SIGNED, INTENT, AllowAll)
        guard.runSubmissionIfAuthorized(INTENT) { }

        assertThrows(IllegalStateException::class.java) { guard.runSubmissionIfAuthorized(INTENT) { } }
    }

    @Test
    fun `a failure before any output frees the scope`() {
        tracking.guard(SIGNED, INTENT, AllowAll)

        tracking.finished()

        assertNull(journal.read(SCOPE))
    }

    @Test
    fun `a failure after the output keeps the transfer unsettled`() {
        val guard = tracking.guard(SIGNED, INTENT, AllowAll)
        guard.runSubmissionIfAuthorized(INTENT) { }

        tracking.finished()

        assertEquals(false, journal.read(SCOPE)?.acknowledged)
    }

    @Test
    fun `an acknowledged transfer stays unsettled until it is found on chain`() {
        val guard = tracking.guard(SIGNED, INTENT, AllowAll)
        guard.runSubmissionIfAuthorized(INTENT) { }

        tracking.acknowledged(tracking.submission!!.transactionHash)
        tracking.finished()

        assertEquals(true, journal.read(SCOPE)?.acknowledged)
    }

    @Test
    fun `no output is admitted after the attempt finished`() {
        val guard = tracking.guard(SIGNED, INTENT, AllowAll)
        tracking.finished()

        assertThrows(IllegalStateException::class.java) { guard.runSubmissionIfAuthorized(INTENT) { } }
        assertNull(journal.read(SCOPE))
    }

    @Test
    fun `a revoked authority retains nothing`() {
        val denied = object : MutationExecutionGuard {
            override fun <T> runIfAuthorized(intentSha256: String, operation: () -> T): T = error("revoked")
        }

        assertThrows(IllegalStateException::class.java) { tracking.guard(SIGNED, INTENT, denied) }
        assertTrue(values.isEmpty())
    }

    private object AllowAll : MutationExecutionGuard {
        override fun <T> runIfAuthorized(intentSha256: String, operation: () -> T): T = operation()
    }

    private companion object {
        val CHAIN = "6b".repeat(32)
        val SCOPE = CrossChainSubmissionJournal.Scope(1, CHAIN, "01".repeat(32))
        val DETAILS = CrossChainSubmissionJournal.Details(
            destinationChainId = "7e".repeat(32),
            assetId = "lld",
            symbol = "LLD",
            precision = 12,
            recipient = "recipient",
            amountInPlanks = BigInteger.TEN
        )
        const val SIGNED = "0x01020304"
        val INTENT = "aa".repeat(32)
        val ANCHOR = FinalizedExtrinsicReader.Anchor("0x$CHAIN", BigInteger.valueOf(1_000), "0x" + "bb".repeat(32))
        val VALIDITY = ExtrinsicValidity(BigInteger.valueOf(1_002), 64)
    }
}
