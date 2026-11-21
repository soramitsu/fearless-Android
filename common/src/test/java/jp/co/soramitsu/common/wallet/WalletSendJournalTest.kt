package jp.co.soramitsu.common.wallet

import jp.co.soramitsu.common.data.network.send.PendingWalletSend
import jp.co.soramitsu.common.data.network.send.WalletSendJournal
import jp.co.soramitsu.common.data.network.send.WalletSendJournalException
import jp.co.soramitsu.common.data.storage.Preferences
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.ArgumentMatchers.anyMap
import org.mockito.ArgumentMatchers.anySet
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.util.UUID

class WalletSendJournalTest {
    @Test
    fun `signed bytes survive restart and identical retries cannot replace an ambiguous payment`() {
        val store = Store()
        val journal = WalletSendJournal(store.preferences)
        val payment = payment()
        assertEquals(null, journal.read(SCOPE))
        assertEquals(payment, journal.retain(SCOPE, payment))
        val restarted = WalletSendJournal(store.preferences)
        assertEquals(payment, restarted.read(SCOPE))
        assertEquals(payment, restarted.retain(SCOPE, payment))
        for (replacement in listOf(payment.copy(quoteId = UUID.randomUUID().toString()), payment.copy(signedPayload = "00bb"),
            payment.copy(feeUnits = "999"), payment.copy(fingerprint = "e".repeat(64)))) {
            val error = assertThrows(WalletSendJournalException::class.java) { restarted.retain(SCOPE, replacement) }
            assertEquals(WalletSendJournalException.Code.PENDING_PAYMENT, error.code)
            assertEquals(payment.transactionId, error.transactionId)
            assertEquals(payment, restarted.read(SCOPE))
        }
    }

    @Test
    fun `only definitive settlement permits a new quote and preserves bytes for same quote retries`() {
        val store = Store()
        val journal = WalletSendJournal(store.preferences)
        val payment = journal.retain(SCOPE, payment())
        assertThrows(WalletSendJournalException::class.java) { journal.settle(SCOPE, payment.copy(feeUnits = "777"), PendingWalletSend.State.Confirmed) }
        val settled = journal.settle(SCOPE, payment, PendingWalletSend.State.Confirmed)
        assertEquals(payment.signedPayload, settled.signedPayload)
        assertEquals(settled, journal.retain(SCOPE, payment))
        assertThrows(WalletSendJournalException::class.java) { journal.settle(SCOPE, settled, PendingWalletSend.State.Failed) }
        val next = payment.copy(quoteId = UUID.randomUUID().toString(), signedPayload = "00bb")
        assertEquals(next, journal.retain(SCOPE, next))
    }

    @Test
    fun `failed durable write or readback poisons this process before a broadcast can start`() {
        for (corrupt in listOf(false, true)) {
            val store = Store()
            store.commitResult = corrupt
            store.corruptReadback = corrupt
            val journal = WalletSendJournal(store.preferences)
            val error = assertThrows(WalletSendJournalException::class.java) { journal.retain(SCOPE, payment()) }
            assertEquals(WalletSendJournalException.Code.STORAGE_UNAVAILABLE, error.code)
            assertThrows(WalletSendJournalException::class.java) { journal.read(SCOPE) }
            // A fresh process reads persisted bytes; it never invents an empty slot after uncertainty.
            if (!corrupt) assertEquals(payment().copy(quoteId = journalPaymentId), WalletSendJournal(store.preferences).read(SCOPE))
        }
    }

    @Test
    fun `corrupt oversized or wrong-scope record fails closed and unrelated wallet scope remains independent`() {
        val store = Store()
        val journal = WalletSendJournal(store.preferences)
        journal.retain(SCOPE, payment())
        val key = store.values.keys.single()
        val valid = store.values.getValue(key)
        for (corrupt in listOf("", "1\n", "a".repeat(1001001), valid.replace("00aa", "00gg"), valid.replace(WalletSendJournal.scopeHash(SCOPE), "a".repeat(64)))) {
            store.values[key] = corrupt
            assertThrows(WalletSendJournalException::class.java) { WalletSendJournal(store.preferences).read(SCOPE) }
        }
        assertEquals(null, journal.read("wallet:2:bitcoin:mainnet"))
        assertTrue(WalletSendJournal.scopeHash(SCOPE) != WalletSendJournal.scopeHash("wallet:1:bitcoin:testnet"))
    }

    @Test
    fun `wallet locks serialize suspended submission across journal instances`() = runTest {
        val store = Store()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val order = mutableListOf<Int>()
        val first = async { WalletSendJournal(store.preferences).withWalletLock(SCOPE) { order += 1; entered.complete(Unit); release.await(); order += 2 } }
        entered.await()
        val second = async { WalletSendJournal(store.preferences).withWalletLock(SCOPE) { order += 3 } }
        assertEquals(listOf(1), order)
        release.complete(Unit)
        first.await(); second.await()
        assertEquals(listOf(1, 2, 3), order)
    }

    @Test fun `original reviewed metadata survives restart and settlement without looking up new balances`() {
        val store = Store(); val journal = WalletSendJournal(store.preferences)
        val reviewed = jp.co.soramitsu.common.data.network.send.ReviewedWalletSend("solana:devnet", "Solana 開発網", "spl-token", "mint",
            "TOKEN", 6, "sender", "recipient", "18446744073709551615")
        val original = payment().copy(reviewed = reviewed)
        journal.retain(SCOPE, original)
        val restarted = WalletSendJournal(store.preferences)
        assertEquals(original, restarted.read(SCOPE))
        assertEquals(reviewed, restarted.settle(SCOPE, original, PendingWalletSend.State.Confirmed).reviewed)
        val fresh = WalletSendJournal(store.preferences)
        assertEquals("282", fresh.read(SCOPE)?.feeUnits)
        assertEquals(reviewed, fresh.read(SCOPE)?.reviewed)
        assertThrows(IllegalArgumentException::class.java) {
            fresh.retain(SCOPE, payment().copy(quoteId = UUID.randomUUID().toString(), reviewed = reviewed.copy(recipient = "bad\nline")))
        }
    }

    @Test fun `Solana v3 retains both network identities with and without review`() {
        val network = jp.co.soramitsu.common.data.network.send.SolanaSendNetworkIdentity(
            jp.co.soramitsu.common.model.UniversalWalletRegistry.SOLANA_MAINNET_GENESIS_HASH,
            "EdP1HZdRNKaMDpiNM4suXQb4RsGZK5sCeZoPQYoJvRTL")
        val review = jp.co.soramitsu.common.data.network.send.ReviewedWalletSend("alias", "Local validator", "SOL", null,
            "SOL", 9, "sender", "recipient", "42")
        for (details in listOf(null, review)) {
            val store = Store(); val journal = WalletSendJournal(store.preferences)
            val payment = payment().copy(kind = PendingWalletSend.Kind.Solana, transactionId = "1".repeat(64), signedPayload = "AQID",
                lastValidBlockHeight = 100, reviewed = details, solanaNetwork = network)
            assertEquals(payment, journal.retain(SCOPE, payment))
            assertTrue(store.values.values.single().startsWith("3\n"))
            assertEquals(payment, WalletSendJournal(store.preferences).read(SCOPE))
            assertEquals(network, journal.settle(SCOPE, payment, PendingWalletSend.State.Confirmed).solanaNetwork)
            assertThrows(IllegalArgumentException::class.java) { journal.retain(SCOPE, payment.copy(solanaNetwork = null)) }
            assertThrows(IllegalArgumentException::class.java) { journal.retain(SCOPE, payment().copy(solanaNetwork = network)) }
        }
        assertThrows(IllegalArgumentException::class.java) { network.copy(registryGenesisHash = "invalid") }
        assertThrows(IllegalArgumentException::class.java) { network.copy(executionGenesisHash = "invalid") }
    }

    @Test fun `legacy Solana v1 v2 reads preserve bytes and cannot be settled or silently upgraded`() {
        for (reviewed in listOf(false, true)) {
            val store = Store(); val journal = WalletSendJournal(store.preferences)
            val payment = payment().copy(kind = PendingWalletSend.Kind.Solana, transactionId = "1".repeat(64), signedPayload = "AQID",
                lastValidBlockHeight = 100, solanaNetwork = jp.co.soramitsu.common.data.network.send.SolanaSendNetworkIdentity(
                    jp.co.soramitsu.common.model.UniversalWalletRegistry.SOLANA_MAINNET_GENESIS_HASH,
                    jp.co.soramitsu.common.model.UniversalWalletRegistry.SOLANA_MAINNET_GENESIS_HASH),
                reviewed = if (reviewed) jp.co.soramitsu.common.data.network.send.ReviewedWalletSend("alias", "Solana", "SOL", null,
                    "SOL", 9, "sender", "recipient", "42") else null)
            journal.retain(SCOPE, payment)
            val key = store.values.keys.single(); val fields = store.values.getValue(key).split('\n')
            val legacy = (listOf(if (reviewed) "2" else "1") + fields.drop(1).take(11) + fields.drop(14)).joinToString("\n")
            store.values[key] = legacy
            val read = requireNotNull(WalletSendJournal(store.preferences).read(SCOPE))
            assertEquals(payment.copy(solanaNetwork = null), read)
            assertThrows(IllegalArgumentException::class.java) { journal.settle(SCOPE, read, PendingWalletSend.State.Confirmed) }
            assertThrows(WalletSendJournalException::class.java) { journal.retain(SCOPE, payment) }
            assertEquals(legacy, store.values[key])
            val settledLegacy = legacy.split('\n').toMutableList().also { it[10] = "Confirmed" }.joinToString("\n")
            store.values[key] = settledLegacy
            assertThrows(WalletSendJournalException::class.java) { journal.retain(SCOPE, payment.copy(quoteId = UUID.randomUUID().toString())) }
            assertEquals(settledLegacy, store.values[key])
        }
    }

    @Test
    fun `only the exact pending payment can be released and a failed removal blocks sending`() {
        val store = Store()
        val journal = WalletSendJournal(store.preferences)
        val payment = journal.retain(SCOPE, payment())
        val changed = assertThrows(WalletSendJournalException::class.java) { journal.discard(SCOPE, payment.copy(feeUnits = "777")) }
        assertEquals(WalletSendJournalException.Code.CHANGED_RECORD, changed.code)
        assertThrows(IllegalArgumentException::class.java) { journal.discard(SCOPE, payment.copy(state = PendingWalletSend.State.Confirmed)) }
        journal.discard(SCOPE, payment)
        assertEquals(null, WalletSendJournal(store.preferences).read(SCOPE))
        // Released: a new payment may be retained.
        val next = payment.copy(quoteId = UUID.randomUUID().toString(), signedPayload = "00bb")
        assertEquals(next, journal.retain(SCOPE, next))
        // A settled payment is chain evidence and is never released.
        val settled = journal.settle(SCOPE, next, PendingWalletSend.State.Confirmed)
        assertThrows(IllegalArgumentException::class.java) { journal.discard(SCOPE, settled) }
        assertEquals(settled, journal.read(SCOPE))

        for (failure in listOf("commit", "readback")) {
            val failing = Store()
            val failingJournal = WalletSendJournal(failing.preferences)
            val retained = failingJournal.retain(SCOPE, payment())
            if (failure == "commit") failing.commitResult = false else failing.keepRemovedKeys = true
            val error = assertThrows(WalletSendJournalException::class.java) { failingJournal.discard(SCOPE, retained) }
            assertEquals(WalletSendJournalException.Code.STORAGE_UNAVAILABLE, error.code)
            assertEquals(retained, WalletSendJournal(failing.preferences).read(SCOPE))
        }
    }

    private class Store {
        val values = mutableMapOf<String, String>()
        var commitResult = true
        var corruptReadback = false
        var keepRemovedKeys = false
        val preferences = mock(Preferences::class.java).also { prefs ->
            `when`(prefs.getString(anyString())).thenAnswer { values[it.getArgument<String>(0)] }
            `when`(prefs.replaceStringsDurably(anyMap(), anySet())).thenAnswer { invocation ->
                val updates = invocation.getArgument<Map<String, String>>(0)
                updates.forEach { (key, value) -> values[key] = if (corruptReadback) "$value!" else value }
                if (commitResult && !keepRemovedKeys) invocation.getArgument<Set<String>>(1).forEach(values::remove)
                commitResult
            }
        }
    }

    private fun payment() = PendingWalletSend(WalletSendJournal.scopeHash(SCOPE), journalPaymentId, "c".repeat(64), PendingWalletSend.Kind.Bitcoin,
        "d".repeat(64), "00aa", "282", "0", null, 1234)

    private companion object {
        const val SCOPE = "wallet:1:bitcoin:mainnet"
        const val journalPaymentId = "11111111-1111-4111-8111-111111111111"
    }
}
