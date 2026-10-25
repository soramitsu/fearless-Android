package jp.co.soramitsu.wallet.impl.data.repository

import io.mockk.every
import io.mockk.mockk
import java.math.BigInteger
import java.util.concurrent.ConcurrentHashMap
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.core.extrinsic.mortality.ExtrinsicValidity
import jp.co.soramitsu.core.rpc.FinalizedExtrinsicReader
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.hash.Hasher.blake2b256
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossChainSubmissionJournalTest {

    private val store = MemoryPreferences()

    @Test
    fun `a retained transfer survives a restart and blocks the scope`() {
        val retained = store.journal().retain(SCOPE, DETAILS, SIGNED, INTENT, ANCHOR, VALIDITY)

        val reopened = store.journal()

        assertEquals(retained, reopened.read(SCOPE))
        assertEquals(SIGNED.fromHex().blake2b256().toHexString(withPrefix = true), retained.transactionHash)
        assertFalse(retained.acknowledged)
        val pending = assertThrows(CrossChainSubmissionPendingException::class.java) { reopened.requireAvailable(SCOPE) }
        assertEquals(retained, pending.submission)
        assertThrows(CrossChainSubmissionPendingException::class.java) {
            reopened.retain(SCOPE, DETAILS, OTHER_SIGNED, INTENT, ANCHOR, VALIDITY)
        }
    }

    @Test
    fun `another wallet, network or account has its own scope`() {
        val journal = store.journal()
        journal.retain(SCOPE, DETAILS, SIGNED, INTENT, ANCHOR, VALIDITY)

        journal.requireAvailable(SCOPE.copy(walletId = 2))
        journal.requireAvailable(SCOPE.copy(accountId = "02".repeat(32)))
    }

    @Test
    fun `an acknowledgement keeps the transfer unsettled`() {
        val journal = store.journal()
        val retained = journal.retain(SCOPE, DETAILS, SIGNED, INTENT, ANCHOR, VALIDITY)

        journal.acknowledge(retained)

        assertEquals(retained.copy(acknowledged = true), journal.read(SCOPE))
    }

    @Test
    fun `only the exact record is discarded or released`() {
        val journal = store.journal()
        val retained = journal.retain(SCOPE, DETAILS, SIGNED, INTENT, ANCHOR, VALIDITY)

        assertThrows(IllegalStateException::class.java) { journal.release(retained.copy(acknowledged = true)) }
        journal.release(retained)

        assertNull(journal.read(SCOPE))
        val next = journal.retain(SCOPE, DETAILS, OTHER_SIGNED, INTENT, ANCHOR, null)
        journal.discardUnsubmitted(next)
        assertNull(journal.read(SCOPE))
    }

    @Test
    fun `details with any text and a missing validity window round trip`() {
        val details = DETAILS.copy(recipient = "0x" + "ab".repeat(20), symbol = "xcDOT ünïcode")
        val retained = store.journal().retain(SCOPE, details, SIGNED, INTENT, ANCHOR, null)

        assertEquals(retained, store.journal().read(SCOPE))
        assertNull(retained.validity)
    }

    @Test
    fun `a write that is not durable blocks the scope instead of guessing`() {
        store.failCommit = true
        val journal = store.journal()

        assertThrows(IllegalStateException::class.java) { journal.retain(SCOPE, DETAILS, SIGNED, INTENT, ANCHOR, VALIDITY) }
        store.failCommit = false

        assertThrows(IllegalStateException::class.java) { journal.read(SCOPE) }
        assertThrows(IllegalStateException::class.java) { journal.requireAvailable(SCOPE) }
    }

    @Test
    fun `a record whose bytes no longer match its hash is refused`() {
        val journal = store.journal()
        journal.retain(SCOPE, DETAILS, SIGNED, INTENT, ANCHOR, VALIDITY)
        val key = store.values.keys.single()
        store.values[key] = store.values.getValue(key).replace(SIGNED, OTHER_SIGNED)

        assertThrows(IllegalArgumentException::class.java) { store.journal().read(SCOPE) }
    }

    @Test
    fun `a validity window that ended before the anchor is refused before anything is sent`() {
        val expired = ExtrinsicValidity(ANCHOR.number - BigInteger.valueOf(64), 64)

        assertThrows(IllegalArgumentException::class.java) {
            store.journal().retain(SCOPE, DETAILS, SIGNED, INTENT, ANCHOR, expired)
        }
        assertTrue(store.values.isEmpty())
    }

    @Test
    fun `scopes and details reject malformed values`() {
        assertThrows(IllegalArgumentException::class.java) { CrossChainSubmissionJournal.Scope(0, CHAIN, ACCOUNT) }
        assertThrows(IllegalArgumentException::class.java) { CrossChainSubmissionJournal.Scope(1, "0x$CHAIN", ACCOUNT) }
        assertThrows(IllegalArgumentException::class.java) { DETAILS.copy(amountInPlanks = BigInteger.ZERO) }
        assertThrows(IllegalArgumentException::class.java) { DETAILS.copy(recipient = " ") }
        assertEquals(SCOPE.key, CrossChainSubmissionJournal.Scope(1, CHAIN, ACCOUNT).key)
        assertFalse(SCOPE.key == SCOPE.copy(walletId = 2).key)
    }

    /** In-memory preferences with the durable-write contract; not evidence of Android disk durability. */
    private class MemoryPreferences {
        private val identity = Any()
        val values = ConcurrentHashMap<String, String>()
        @Volatile var failCommit = false

        private val preferences = mockk<Preferences> {
            every { durableStorageIdentity } returns identity
            every { getString(any()) } answers { values[firstArg()] }
            every { replaceStringsDurably(any(), any()) } answers {
                if (failCommit) {
                    false
                } else {
                    secondArg<Set<String>>().forEach(values::remove)
                    values.putAll(firstArg<Map<String, String>>())
                    true
                }
            }
        }

        fun journal() = CrossChainSubmissionJournal(preferences)
    }

    private companion object {
        val CHAIN = "6b".repeat(32)
        val ACCOUNT = "01".repeat(32)
        val SCOPE = CrossChainSubmissionJournal.Scope(1, CHAIN, ACCOUNT)
        val DETAILS = CrossChainSubmissionJournal.Details(
            destinationChainId = "7e".repeat(32),
            assetId = "a6b83d39-a488-4b34-8352-280705a792ea",
            symbol = "LLD",
            precision = 12,
            recipient = "cnTxSfA2HKS1JgPk8MgMHFw7EsGckFXCQZC6HHfb9oKRySvCv",
            amountInPlanks = BigInteger("1100000000000")
        )
        const val SIGNED = "0x01020304"
        const val OTHER_SIGNED = "0x05060708"
        val INTENT = "aa".repeat(32)
        val ANCHOR = FinalizedExtrinsicReader.Anchor("0x$CHAIN", BigInteger.valueOf(1_000), "0x" + "bb".repeat(32))
        val VALIDITY = ExtrinsicValidity(BigInteger.valueOf(1_002), 64)
    }
}
