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

    private class Fixture {
        val values = mutableMapOf<String, String>()
        var commit = true
        val preferences = mock(Preferences::class.java).also { prefs ->
            `when`(prefs.getString(anyString())).thenAnswer { values[it.getArgument<String>(0)] }
            `when`(prefs.replaceStringsDurably(anyMap(), anySet())).thenAnswer {
                values.putAll(it.getArgument<Map<String, String>>(0)); commit
            }
        }
        val journal = WalletSendJournal(preferences)
        val executor = NativeSendExecutor(journal)
        fun payment() = PendingWalletSend(WalletSendJournal.scopeHash(binding.scope), id, binding.fingerprint,
            PendingWalletSend.Kind.Bitcoin, "a".repeat(64), "00aa", "282", "0", null, 1234)
        suspend fun execute(payment: PendingWalletSend, submit: suspend (PendingWalletSend) -> Unit,
            status: suspend (PendingWalletSend) -> PendingWalletSend.State? = { null }) =
            executor.execute(binding, payment.quoteId, payment.kind, {}, { payment }, submit, status)
    }

    private companion object {
        const val id = "11111111-1111-4111-8111-111111111111"
        val binding = NativeSendBinding(1, "bitcoin", "btc", null, 8, "sender", "recipient", "50000", false)
    }
}
