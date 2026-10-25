package jp.co.soramitsu.wallet.impl.domain

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.math.BigInteger
import java.util.concurrent.ConcurrentHashMap
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.core.extrinsic.keypair_provider.KeypairProvider
import jp.co.soramitsu.core.rpc.FinalizedExtrinsicReader
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.impl.data.repository.CrossChainSubmissionJournal
import jp.co.soramitsu.wallet.impl.data.repository.CrossChainSubmissionPendingException
import jp.co.soramitsu.wallet.impl.data.repository.CrossChainSubmissionTracking
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossChainSubmissionsTest {

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
    private val reader = mockk<FinalizedExtrinsicReader> {
        coEvery { anchor(CHAIN) } returns ANCHOR
    }
    private val submissions = CrossChainSubmissions(journal, reader)
    private val provider = mockk<KeypairProvider>()

    @Test
    fun `the scope is the wallet, the origin network and the sender account`() {
        val chain = mockk<Chain> { every { id } returns "0x" + CHAIN.uppercase() }

        val scope = submissions.scope(7, chain, ByteArray(32) { 1 })

        assertEquals(CrossChainSubmissionJournal.Scope(7, CHAIN, "01".repeat(32)), scope)
    }

    @Test
    fun `a transfer is sent with a tracked, wallet-bound submission anchored before signing`() = runBlocking<Unit> {
        val result = submissions.send(SCOPE, DETAILS, provider) { submission ->
            assertEquals(SCOPE.walletId, submission.walletId)
            assertSame(provider, submission.keypairProvider)
            assertTrue(submission.tracking is CrossChainSubmissionTracking)
            "0xhash"
        }

        assertEquals("0xhash", result)
        coVerify(exactly = 1) { reader.anchor(CHAIN) }
    }

    @Test
    fun `no transfer is signed while an earlier one is unsettled`() = runBlocking<Unit> {
        journal.retain(SCOPE, DETAILS, "0x01020304", "aa".repeat(32), ANCHOR, null)
        var sent = false

        assertThrows(CrossChainSubmissionPendingException::class.java) {
            runBlocking { submissions.send(SCOPE, DETAILS, provider) { sent = true } }
        }

        assertEquals(false, sent)
        assertEquals("0x01020304", submissions.pending(SCOPE)?.signedExtrinsic)
        coVerify(exactly = 0) { reader.anchor(any()) }
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
        val ANCHOR = FinalizedExtrinsicReader.Anchor("0x$CHAIN", BigInteger.valueOf(1_000), "0x" + "bb".repeat(32))
    }
}
