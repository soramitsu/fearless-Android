package jp.co.soramitsu.wallet.impl.data.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.math.BigInteger
import java.util.concurrent.ConcurrentHashMap
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.core.extrinsic.mortality.ExtrinsicValidity
import jp.co.soramitsu.core.rpc.FinalizedExtrinsicReader
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossChainSubmissionReconcilerTest {

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
    private val reader = mockk<FinalizedExtrinsicReader>()
    private val reconciler = CrossChainSubmissionReconciler(journal, reader)

    @Test
    fun `the exact bytes in a finalized block settle the transfer as included`() = runBlocking<Unit> {
        val retained = retain(VALIDITY)
        head(1_010)
        val inclusion = inclusionAt(1_004)
        coEvery { reader.search(CHAIN, SIGNED, null, 64, ANCHOR) } returns
            FinalizedExtrinsicReader.SearchResult(inclusion, null, 7)

        val outcome = reconciler.check(SCOPE, retained.attemptId)

        assertEquals(CrossChainSubmissionReconciler.Outcome.Included(retained, inclusion), outcome)
        assertNull(journal.read(SCOPE))
        // Kept as sent, so a screen restored after another restart does not offer to send it again.
        assertEquals(retained, journal.readIncluded(SCOPE))
    }

    @Test
    fun `a fully finalized validity window without the bytes proves it was not executed`() = runBlocking<Unit> {
        val retained = retain(VALIDITY) // valid in blocks 1002 to 1065
        head(1_200)
        coEvery { reader.search(CHAIN, SIGNED, BigInteger.valueOf(1_065), 64, ANCHOR) } returns
            FinalizedExtrinsicReader.SearchResult(null, BigInteger.valueOf(1_001), 64)
        coEvery { reader.search(CHAIN, SIGNED, BigInteger.valueOf(1_001), 64, ANCHOR) } returns
            FinalizedExtrinsicReader.SearchResult(null, null, 1)

        val outcome = reconciler.check(SCOPE, retained.attemptId)

        assertEquals(CrossChainSubmissionReconciler.Outcome.Expired(retained), outcome)
        assertNull(journal.read(SCOPE))
    }

    @Test
    fun `a window that is not finalized yet keeps the transfer unsettled`() = runBlocking<Unit> {
        val retained = retain(VALIDITY)
        head(1_030)
        coEvery { reader.search(CHAIN, SIGNED, null, 64, ANCHOR) } returns
            FinalizedExtrinsicReader.SearchResult(null, null, 30)

        val outcome = reconciler.check(SCOPE, retained.attemptId)

        assertEquals(CrossChainSubmissionReconciler.Outcome.Unsettled(retained, BigInteger.valueOf(1_065)), outcome)
        assertNotNull(journal.read(SCOPE))
    }

    @Test
    fun `without a validity window only finding the bytes settles the transfer`() = runBlocking<Unit> {
        val retained = retain(null)
        head(100_000)
        coEvery { reader.search(CHAIN, SIGNED, null, 64, ANCHOR) } returns
            FinalizedExtrinsicReader.SearchResult(null, null, 64)

        val outcome = reconciler.check(SCOPE, retained.attemptId)

        assertTrue(outcome is CrossChainSubmissionReconciler.Outcome.Unsettled)
        assertNotNull(journal.read(SCOPE))
    }

    @Test
    fun `a long window continues where the previous check stopped`() = runBlocking<Unit> {
        val retained = retain(ExtrinsicValidity(BigInteger.valueOf(1_002), 1_024)) // valid up to block 2025
        head(3_000)
        for (page in 0 until 8) {
            val from = BigInteger.valueOf(2_025L - 64L * page)
            coEvery { reader.search(CHAIN, SIGNED, from, 64, ANCHOR) } returns
                FinalizedExtrinsicReader.SearchResult(null, from - BigInteger.valueOf(64), 64)
        }
        val resumeAt = BigInteger.valueOf(2_025L - 64L * 8)
        coEvery { reader.search(CHAIN, SIGNED, resumeAt, 64, ANCHOR) } returns
            FinalizedExtrinsicReader.SearchResult(null, null, 1)

        val first = reconciler.check(SCOPE, retained.attemptId)
        val second = reconciler.check(SCOPE, retained.attemptId)

        assertTrue(first is CrossChainSubmissionReconciler.Outcome.Unsettled)
        assertEquals(CrossChainSubmissionReconciler.Outcome.Expired(retained), second)
        coVerify(exactly = 1) { reader.search(CHAIN, SIGNED, resumeAt, 64, ANCHOR) }
    }

    @Test
    fun `an unreadable chain leaves the transfer unsettled`() = runBlocking<Unit> {
        val retained = retain(VALIDITY)
        coEvery { reader.anchor(CHAIN) } throws IllegalStateException("Reconciliation network changed")

        assertThrows(IllegalStateException::class.java) { runBlocking { reconciler.check(SCOPE, retained.attemptId) } }
        assertNotNull(journal.read(SCOPE))
    }

    @Test
    fun `a check for another attempt is refused and an empty scope is settled`() = runBlocking<Unit> {
        assertEquals(CrossChainSubmissionReconciler.Outcome.Settled, reconciler.check(SCOPE, "unused"))
        retain(VALIDITY)

        assertThrows(IllegalStateException::class.java) {
            runBlocking { reconciler.check(SCOPE, "00000000-0000-0000-0000-000000000000") }
        }
    }

    private fun retain(validity: ExtrinsicValidity?) = journal.retain(SCOPE, DETAILS, SIGNED, INTENT, ANCHOR, validity)

    private fun head(number: Long) {
        coEvery { reader.anchor(CHAIN) } returns
            FinalizedExtrinsicReader.Anchor("0x$CHAIN", BigInteger.valueOf(number), "0x" + "cc".repeat(32))
    }

    private fun inclusionAt(block: Long) = FinalizedExtrinsicReader.Inclusion(
        genesisHash = "0x$CHAIN",
        transactionHash = "0x" + "dd".repeat(32),
        blockHash = "0x" + "ee".repeat(32),
        blockNumber = BigInteger.valueOf(block),
        extrinsicIndex = 2,
        finalizedHeadHash = "0x" + "cc".repeat(32),
        finalizedHeadNumber = BigInteger.valueOf(1_010)
    )

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
