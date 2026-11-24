package jp.co.soramitsu.wallet.impl.data.repository.tranfser

import jp.co.soramitsu.common.data.network.send.PendingWalletSend
import jp.co.soramitsu.common.data.network.send.WalletSendJournal
import jp.co.soramitsu.common.data.storage.Preferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.*
import java.io.IOException
import java.util.UUID

class NativeSendExecutorTest {
    @Test fun `ambiguous submit survives process loss and reuses exact bytes without signing again`() = runTest {
        val fixture = Fixture()
        val payment = fixture.payment()
        val submissions = mutableListOf<String>()
        fixture.execute(payment, submit = { assertEquals(payment, fixture.journal.read(binding.scope)); submissions += it.signedPayload; throw IOException() })
        val restarted = NativeSendExecutor(WalletSendJournal(fixture.preferences))
        val result = restarted.execute(binding, payment.quoteId, PendingWalletSend.Kind.Bitcoin, {},
            prepare = { error("Must not sign again") }, submit = { submissions += it.signedPayload }, status = { null })
        assertEquals(payment.transactionId, result)
        assertEquals(listOf(payment.signedPayload, payment.signedPayload), submissions)
        assertEquals(payment, fixture.journal.read(binding.scope))
    }

    @Test fun `confirmed during retry returns original result and a different pending intent stays blocked`() = runTest {
        val fixture = Fixture(); val payment = fixture.payment()
        fixture.journal.retain(binding.scope, payment)
        val blocked = runCatching { fixture.executor.execute(binding.copy(recipient = "other"), UUID.randomUUID().toString(), payment.kind,
            {}, { error("prepare") }, { error("submit") }, { null }) }.exceptionOrNull()
        assertEquals(NativeSendException.Code.PAYMENT_PENDING, (blocked as NativeSendException).code)
        assertEquals(payment.transactionId, fixture.executor.execute(binding, payment.quoteId, payment.kind,
            {}, { error("prepare") }, { error("submit") }, { PendingWalletSend.State.Confirmed }))
        assertEquals(PendingWalletSend.State.Confirmed, fixture.journal.read(binding.scope)?.state)
    }

    @Test fun `failed storage or wallet switch prevents first network submission`() = runTest {
        val fixture = Fixture(); var submitted = false
        fixture.commit = false
        assertNotNull(runCatching { fixture.execute(fixture.payment(), submit = { submitted = true }) }.exceptionOrNull())
        assertFalse(submitted)
        val second = Fixture(); var checks = 0
        val result = runCatching { second.executor.execute(binding, id, PendingWalletSend.Kind.Bitcoin,
            { if (++checks == 2) throw NativeSendException(NativeSendException.Code.WALLET_CHANGED) },
            { second.payment() }, { submitted = true }, { null }) }
        assertTrue(result.isFailure); assertFalse(submitted); assertNull(second.journal.read(binding.scope))
    }

    @Test fun `cancellation after retention leaves recovery guard and definitive failure never resubmits`() = runTest {
        val fixture = Fixture(); val payment = fixture.payment()
        assertTrue(runCatching { fixture.execute(payment, submit = { throw CancellationException() }) }.exceptionOrNull() is CancellationException)
        assertEquals(payment, fixture.journal.read(binding.scope))
        val failure = runCatching { fixture.execute(payment, submit = { error("Must not submit failed payment") },
            status = { PendingWalletSend.State.Failed }) }.exceptionOrNull() as NativeSendException
        assertEquals(NativeSendException.Code.PAYMENT_FAILED, failure.code)
        assertEquals(PendingWalletSend.State.Failed, fixture.journal.read(binding.scope)?.state)
    }

    @Test fun `missing review is never silently signed and wallet and asset identities affect fingerprint`() = runTest {
        val fixture = Fixture()
        val error = runCatching { fixture.executor.execute(binding, null, PendingWalletSend.Kind.Bitcoin, {}, { error("prepare") }, {}, { null }) }
            .exceptionOrNull() as NativeSendException
        assertEquals(NativeSendException.Code.REVIEW_REQUIRED, error.code)
        assertNotEquals(binding.fingerprint, binding.copy(metaId = 2).fingerprint)
        assertNotEquals(binding.fingerprint, binding.copy(currencyId = "mint").fingerprint)
        assertEquals(binding.copy(sendMax = true).fingerprint, binding.copy(sendMax = true, requestedRawAmount = "999").fingerprint)
    }

    @Test fun `new Max quote after restart cannot authorize retained bytes with different amount or fees`() = runTest {
        val fixture = Fixture()
        val max = binding.copy(sendMax = true)
        val original = fixture.payment().copy(fingerprint = max.fingerprint,
            reviewed = jp.co.soramitsu.common.data.network.send.ReviewedWalletSend("bitcoin", "Bitcoin", "btc", null,
                "BTC", 8, "sender", "recipient", "50000"))
        fixture.journal.retain(binding.scope, original)
        val restarted = NativeSendExecutor(WalletSendJournal(fixture.preferences))
        val error = runCatching { restarted.execute(max.copy(requestedRawAmount = "90000"), UUID.randomUUID().toString(), original.kind,
            {}, { error("must not sign changed Max") }, { error("must not submit original as new Max") }, { null }) }.exceptionOrNull()
        assertEquals(NativeSendException.Code.PAYMENT_PENDING, (error as NativeSendException).code)
        assertEquals(original, fixture.journal.read(binding.scope))
        assertEquals("50000", fixture.journal.read(binding.scope)?.reviewed?.amountUnits)
    }

    @Test fun `ownership lost during status is neither settled nor returned as success`() = runTest {
        for (prior in listOf(false, true)) {
            val f = Fixture(); val payment = f.payment(); var valid = true
            if (prior) f.journal.retain(binding.scope, payment)
            val result = runCatching { f.executor.execute(binding, payment.quoteId, payment.kind,
                { if (!valid) throw NativeSendException(NativeSendException.Code.WALLET_CHANGED) }, { payment }, {},
                { valid = false; PendingWalletSend.State.Confirmed }) }
            assertEquals(NativeSendException.Code.WALLET_CHANGED, (result.exceptionOrNull() as NativeSendException).code)
            assertEquals(payment, f.journal.read(binding.scope))
        }
    }

    @Test fun `a first submission no node received or every node refused is released and reported as not sent`() = runTest {
        for ((reason, code) in listOf(NativeSendNeverLandedException.Reason.NOT_SENT to NativeSendException.Code.PAYMENT_NOT_SENT,
            NativeSendNeverLandedException.Reason.REFUSED to NativeSendException.Code.PAYMENT_REFUSED)) {
            val fixture = Fixture(); val payment = fixture.payment()
            val error = runCatching { fixture.execute(payment, submit = {
                assertEquals(payment, fixture.journal.read(binding.scope))
                throw NativeSendNeverLandedException(reason)
            }, status = { error("A released payment is not polled") }) }.exceptionOrNull() as NativeSendException
            assertEquals(code, error.code)
            assertEquals(payment.transactionId, error.transactionId)
            assertNull(fixture.journal.read(binding.scope))
            assertEquals(listOf(payment to true), fixture.released)
            // Nothing is pending, so the same review may be sent again.
            assertEquals(payment.transactionId, fixture.execute(payment, submit = {}))
        }
    }

    @Test fun `a refused retry proves nothing about earlier attempts and stays pending`() = runTest {
        val fixture = Fixture(); val payment = fixture.payment()
        fixture.journal.retain(binding.scope, payment)
        for (reason in listOf(NativeSendNeverLandedException.Reason.NOT_SENT, NativeSendNeverLandedException.Reason.REFUSED)) {
            assertEquals(payment.transactionId, fixture.executor.execute(binding, payment.quoteId, payment.kind, {}, { error("prepare") },
                { throw NativeSendNeverLandedException(reason) }, { null }))
            assertEquals(payment, fixture.journal.read(binding.scope))
        }
        assertTrue(fixture.released.isEmpty())
    }

    @Test fun `a pending payment proven expired is released and stops the call that found it`() = runTest {
        val fixture = Fixture(); val payment = fixture.payment()
        fixture.journal.retain(binding.scope, payment)
        val expired = runCatching { fixture.executor.execute(binding, payment.quoteId, payment.kind, {}, { error("prepare") },
            { error("An expired payment is not resubmitted") },
            { throw NativeSendNeverLandedException(NativeSendNeverLandedException.Reason.EXPIRED, corroborated = true) }) }.exceptionOrNull() as NativeSendException
        assertEquals(NativeSendException.Code.PAYMENT_EXPIRED, expired.code)
        assertNull(fixture.journal.read(binding.scope))

        // A different payment is not signed or sent in the call that released the earlier one: the user sees the
        // release first and confirms again.
        fixture.journal.retain(binding.scope, payment)
        val next = payment.copy(quoteId = "22222222-2222-4222-8222-222222222222", transactionId = "b".repeat(64))
        val stopped = runCatching { fixture.executor.execute(binding, next.quoteId, next.kind, {}, { error("Nothing new is signed") },
            { error("Nothing new is sent") }, { throw NativeSendNeverLandedException(NativeSendNeverLandedException.Reason.EXPIRED, corroborated = true) }) }
            .exceptionOrNull() as NativeSendException
        assertEquals(NativeSendException.Code.PRIOR_PAYMENT_EXPIRED, stopped.code)
        assertEquals(payment.transactionId, stopped.transactionId)
        assertNull(fixture.journal.read(binding.scope))
        assertEquals(next.transactionId, fixture.executor.execute(binding, next.quoteId, next.kind, {}, { next }, {}, { null }))
        assertEquals(next, fixture.journal.read(binding.scope))
        assertEquals(listOf(payment to true, payment to true), fixture.released)
    }

    @Test fun `expiry proven right after submission releases the payment`() = runTest {
        val fixture = Fixture(); val payment = fixture.payment()
        val error = runCatching { fixture.execute(payment, submit = { throw IOException("reply lost") },
            status = { throw NativeSendNeverLandedException(NativeSendNeverLandedException.Reason.EXPIRED, corroborated = true) }) }.exceptionOrNull() as NativeSendException
        assertEquals(NativeSendException.Code.PAYMENT_EXPIRED, error.code)
        assertNull(fixture.journal.read(binding.scope))
    }

    @Test fun `an expiry seen more than six hours after signing releases the payment as unverified`() = runTest {
        val sixHours = NativeSendExecutor.VERIFIABLE_FOR_MILLIS
        // Age at the time of the check, and whether an empty lookup still shows that nothing left the wallet.
        for ((age, verified) in listOf(60_000L to true, sixHours to true, sixHours + 1 to false, 30 * 24 * sixHours to false, -60_000L to false)) {
            val ownReview = Fixture().also { it.now = it.payment().createdAtMillis + age }
            val payment = ownReview.payment()
            ownReview.journal.retain(binding.scope, payment)
            val own = runCatching { ownReview.executor.execute(binding, payment.quoteId, payment.kind, {}, { error("prepare") },
                { error("submit") }, { throw NativeSendNeverLandedException(NativeSendNeverLandedException.Reason.EXPIRED, corroborated = true) }) }
                .exceptionOrNull() as NativeSendException
            assertEquals("age $age", if (verified) NativeSendException.Code.PAYMENT_EXPIRED else NativeSendException.Code.PAYMENT_UNVERIFIED, own.code)
            assertEquals(listOf(payment to verified), ownReview.released)
            assertNull(ownReview.journal.read(binding.scope))

            val other = Fixture().also { it.now = it.payment().createdAtMillis + age }
            other.journal.retain(binding.scope, payment)
            val stopped = runCatching { other.executor.execute(binding, "22222222-2222-4222-8222-222222222222", payment.kind, {},
                { error("Nothing new is signed") }, { error("Nothing new is sent") },
                { throw NativeSendNeverLandedException(NativeSendNeverLandedException.Reason.EXPIRED, corroborated = true) }) }.exceptionOrNull() as NativeSendException
            assertEquals("age $age", if (verified) NativeSendException.Code.PRIOR_PAYMENT_EXPIRED
                else NativeSendException.Code.PRIOR_PAYMENT_UNVERIFIED, stopped.code)
            assertEquals(listOf(payment to verified), other.released)

            // The same rule holds when the expiry is found after a retry of the retained bytes.
            val retried = Fixture().also { it.now = it.payment().createdAtMillis + age }
            retried.journal.retain(binding.scope, payment)
            var checks = 0
            val late = runCatching { retried.executor.execute(binding, payment.quoteId, payment.kind, {}, { error("prepare") }, {},
                { if (checks++ == 0) null else throw NativeSendNeverLandedException(NativeSendNeverLandedException.Reason.EXPIRED, corroborated = true) }) }
                .exceptionOrNull() as NativeSendException
            assertEquals("age $age", if (verified) NativeSendException.Code.PAYMENT_EXPIRED else NativeSendException.Code.PAYMENT_UNVERIFIED, late.code)
            assertEquals(listOf(payment to verified), retried.released)
        }
    }

    @Test fun `an expiry proven by a single endpoint is released as unverified however recent`() = runTest {
        val lone = NativeSendNeverLandedException(NativeSendNeverLandedException.Reason.EXPIRED)
        assertFalse(lone.corroborated)
        val own = Fixture(); val payment = own.payment()
        own.journal.retain(binding.scope, payment)
        val same = runCatching { own.executor.execute(binding, payment.quoteId, payment.kind, {}, { error("prepare") }, { error("submit") },
            { throw lone }) }.exceptionOrNull() as NativeSendException
        assertEquals(NativeSendException.Code.PAYMENT_UNVERIFIED, same.code)
        assertEquals(listOf(payment to false), own.released)
        assertNull(own.journal.read(binding.scope))

        // A different payment still stops, and the earlier one is reported as unverified.
        val other = Fixture(); other.journal.retain(binding.scope, payment)
        val stopped = runCatching { other.executor.execute(binding, "22222222-2222-4222-8222-222222222222", payment.kind, {},
            { error("Nothing new is signed") }, { error("Nothing new is sent") }, { throw lone }) }.exceptionOrNull() as NativeSendException
        assertEquals(NativeSendException.Code.PRIOR_PAYMENT_UNVERIFIED, stopped.code)
        assertEquals(listOf(payment to false), other.released)

        // Found right after a retry of the retained bytes: the same rule.
        val retried = Fixture(); retried.journal.retain(binding.scope, payment)
        var checks = 0
        val late = runCatching { retried.executor.execute(binding, payment.quoteId, payment.kind, {}, { error("prepare") }, {},
            { if (checks++ == 0) null else throw lone }) }.exceptionOrNull() as NativeSendException
        assertEquals(NativeSendException.Code.PAYMENT_UNVERIFIED, late.code)
        assertEquals(listOf(payment to false), retried.released)
    }

    @Test fun `a released payment's history row is closed while its record still exists`() = runTest {
        val fixture = Fixture(); val payment = fixture.payment()
        fixture.journal.retain(binding.scope, payment)
        runCatching { fixture.executor.execute(binding, payment.quoteId, payment.kind, {}, { error("prepare") }, { error("submit") },
            { throw NativeSendNeverLandedException(NativeSendNeverLandedException.Reason.EXPIRED, corroborated = true) }) }
        assertEquals(listOf(payment), fixture.retainedWhenReleased)
        assertNull(fixture.journal.read(binding.scope))

        // A row that cannot be closed keeps the record, so the release is tried again on the next check.
        val failing = Fixture(); failing.journal.retain(binding.scope, payment)
        val executor = NativeSendExecutor(failing.journal, { _, _ -> throw java.sql.SQLException("history unavailable") },
            nowMillis = { failing.now })
        val error = runCatching { executor.execute(binding, payment.quoteId, payment.kind, {}, { error("prepare") }, { error("submit") },
            { throw NativeSendNeverLandedException(NativeSendNeverLandedException.Reason.EXPIRED, corroborated = true) }) }.exceptionOrNull()
        assertTrue(error is java.sql.SQLException)
        assertEquals(payment, failing.journal.read(binding.scope))
    }

    @Test fun `a release needs the owning wallet and durable storage`() = runTest {
        val switched = Fixture(); val payment = switched.payment(); var current = true
        val changed = runCatching { switched.executor.execute(binding, payment.quoteId, payment.kind,
            { if (!current) throw NativeSendException(NativeSendException.Code.WALLET_CHANGED) }, { payment },
            { current = false; throw NativeSendNeverLandedException(NativeSendNeverLandedException.Reason.NOT_SENT) }, { null }) }.exceptionOrNull()
        assertEquals(NativeSendException.Code.WALLET_CHANGED, (changed as NativeSendException).code)
        assertEquals(payment, switched.journal.read(binding.scope))

        val unwritable = Fixture()
        val retained = unwritable.payment()
        unwritable.journal.retain(binding.scope, retained)
        unwritable.commit = false
        val storage = runCatching { unwritable.execute(retained, submit = {}, status = {
            throw NativeSendNeverLandedException(NativeSendNeverLandedException.Reason.EXPIRED, corroborated = true) }) }.exceptionOrNull()
        assertTrue(storage is jp.co.soramitsu.common.data.network.send.WalletSendJournalException)
        assertEquals(retained, WalletSendJournal(unwritable.preferences).read(binding.scope))
        // After a restart with working storage, the record that could not be removed is found and released again.
        unwritable.commit = true
        val restartedJournal = WalletSendJournal(unwritable.preferences)
        val restarted = NativeSendExecutor(restartedJournal, { payment, verified -> unwritable.released += payment to verified },
            nowMillis = { unwritable.now })
        val again = runCatching { restarted.execute(binding, retained.quoteId, retained.kind, {}, { error("prepare") }, { error("submit") },
            { throw NativeSendNeverLandedException(NativeSendNeverLandedException.Reason.EXPIRED, corroborated = true) }) }.exceptionOrNull() as NativeSendException
        assertEquals(NativeSendException.Code.PAYMENT_EXPIRED, again.code)
        assertNull(restartedJournal.read(binding.scope))
        assertEquals(listOf(retained to true, retained to true), unwritable.released)
    }

    private class Fixture {
        val values = mutableMapOf<String, String>()
        var commit = true
        val preferences = mock(Preferences::class.java).also { prefs ->
            `when`(prefs.getString(anyString())).thenAnswer { values[it.getArgument<String>(0)] }
            `when`(prefs.replaceStringsDurably(anyMap(), anySet())).thenAnswer {
                if (commit) {
                    values.putAll(it.getArgument<Map<String, String>>(0))
                    it.getArgument<Set<String>>(1).forEach(values::remove)
                }
                commit
            }
        }
        val journal = WalletSendJournal(preferences)
        val released = mutableListOf<Pair<PendingWalletSend, Boolean>>()
        val retainedWhenReleased = mutableListOf<PendingWalletSend?>()
        var now = SIGNED_AT + 60_000
        val executor = NativeSendExecutor(journal, { payment, verified ->
            released += payment to verified
            retainedWhenReleased += journal.read(binding.scope)
        }, nowMillis = { now })
        fun payment() = PendingWalletSend(WalletSendJournal.scopeHash(binding.scope), id, binding.fingerprint,
            PendingWalletSend.Kind.Bitcoin, "a".repeat(64), "00aa", "282", "0", null, SIGNED_AT)
        suspend fun execute(payment: PendingWalletSend, submit: suspend (PendingWalletSend) -> Unit,
            status: suspend (PendingWalletSend) -> PendingWalletSend.State? = { null }) =
            executor.execute(binding, payment.quoteId, payment.kind, {}, { payment }, submit, status)
    }

    private companion object {
        const val id = "11111111-1111-4111-8111-111111111111"
        const val SIGNED_AT = 1_790_000_000_000L
        val binding = NativeSendBinding(1, "bitcoin", "btc", null, 8, "sender", "recipient", "50000", false)
    }
}
