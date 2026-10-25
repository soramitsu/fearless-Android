package jp.co.soramitsu.app.root.domain.staking

import android.content.Context
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import jp.co.soramitsu.common.data.storage.PreferencesImpl
import jp.co.soramitsu.core.extrinsic.MutationExecutionGuard
import jp.co.soramitsu.staking.impl.data.repository.PoolJoinJournal
import jp.co.soramitsu.staking.impl.data.repository.PoolJoinPendingException
import jp.co.soramitsu.staking.impl.data.repository.PoolJoinSubmissionTracking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.math.BigInteger
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Isolated public fake transaction file. No keys, valid signed transaction, socket or wallet database. */
@RunWith(AndroidJUnit4::class)
class PoolCreateJournalProcessInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val shared get() = context.getSharedPreferences("pool_create_process_fixture_v1", Context.MODE_PRIVATE)
    private val scope = PoolJoinJournal.Scope(7, "91".repeat(32), "05".repeat(32))
    private val anchor = jp.co.soramitsu.core.rpc.FinalizedExtrinsicReader.Anchor("0x" + scope.chainId, BigInteger.ZERO, "0x" + "20".repeat(32))
    private val details = PoolJoinJournal.CreateDetails("Original\n池", "public-nominator", "public-bouncer")
    private val signed = "0x01020304"
    private val intent = "12".repeat(32)
    private val journal get() = PoolJoinJournal(PreferencesImpl(shared))
    private val authority = object : MutationExecutionGuard {
        override fun <T> runIfAuthorized(intentSha256: String, operation: () -> T): T = operation()
    }

    @Test fun holdAfterAdmittedLocalOutputForHostProcessKill() {
        prepare("pending")
        val tracker = PoolJoinSubmissionTracking(journal, scope, BigInteger.TEN, BigInteger.ONE, anchor, details) {
            fail("No acknowledgement was delivered")
        }
        val gate = tracker.guard(signed, intent, authority)
        gate.runSubmissionIfAuthorized(intent) {
            // Deliberately held within an admitted local operation. The host kills this process.
            report("admitted-before-kill", checkNotNull(journal.read(scope)))
            check(CountDownLatch(1).await(120, TimeUnit.SECONDS)) { "Host did not kill the held fixture" }
        }
        fail("The host must terminate this fixture before attempt completion")
    }

    @Test fun readUncertainPaymentAfterProcessKill() {
        assertEquals("pending", shared.getString("fixture.phase", null))
        assertNotEquals("Must execute in a fresh app process", shared.getInt("fixture.pid", -1), Process.myPid())
        val retained = checkNotNull(journal.read(scope))
        assertPayment(retained, false)
        val failure = assertThrows(PoolJoinPendingException::class.java) { journal.requireAvailable(scope) }
        assertEquals(retained.transactionHash, failure.transactionHash)
        journal.requireAvailable(scope.copy(walletId = 8))
        report("pending-after-process-kill", retained)
    }

    @Test fun prepareAcknowledgedLocalPayment() {
        prepare("acknowledged")
        var callbacks = 0
        val tracker = PoolJoinSubmissionTracking(journal, scope, BigInteger.TEN, BigInteger.ONE, anchor, details) { callbacks++ }
        tracker.guard(signed, intent, authority).runSubmissionIfAuthorized(intent) { Unit }
        tracker.acknowledged(checkNotNull(journal.read(scope)).transactionHash)
        tracker.finished()
        assertEquals(1, callbacks)
        report("acknowledged-before-restart", checkNotNull(journal.read(scope)))
    }

    @Test fun readAcknowledgedPaymentAfterRestart() {
        assertEquals("acknowledged", shared.getString("fixture.phase", null))
        assertNotEquals("Must execute in a fresh app process", shared.getInt("fixture.pid", -1), Process.myPid())
        val retained = checkNotNull(journal.read(scope))
        assertPayment(retained, true)
        assertTrue(assertThrows(PoolJoinPendingException::class.java) { journal.requireAvailable(scope) }.acknowledged)
        report("acknowledged-after-restart", retained)
    }

    @Test fun archiveExactOriginalAndReleasePendingScope() {
        prepare("finalized")
        val original = journal.retain(scope, BigInteger.TEN, BigInteger.ONE, signed, intent, anchor, details)
        val inclusion = jp.co.soramitsu.core.rpc.FinalizedExtrinsicReader.Inclusion("0x" + scope.chainId,
            original.transactionHash, "0x" + "31".repeat(32), BigInteger.ONE, 0, "0x" + "32".repeat(32), BigInteger.TEN)
        // Exercise the production internal journal transition without exporting mutation authority.
        val method = PoolJoinJournal::class.java.declaredMethods.single {
            it.name.startsWith("finalize") && it.parameterTypes.contentEquals(arrayOf(
                PoolJoinJournal.Payment::class.java, jp.co.soramitsu.core.rpc.FinalizedExtrinsicReader.Inclusion::class.java))
        }.apply { isAccessible = true }
        method.invoke(journal, original, inclusion)
        assertEquals(null, journal.read(scope))
        journal.requireAvailable(scope)
        assertEquals(PoolJoinJournal.FinalizedPayment(original, inclusion), journal.readFinalized(scope, original.attemptId))
        assertTrue(shared.edit().putString("fixture.attempt", original.attemptId).commit())
        reportArchive("finalized-before-restart", original.attemptId)
    }

    @Test fun readArchivedOriginalAfterRestartAndKeepItDuringNextAttempt() {
        assertEquals("finalized", shared.getString("fixture.phase", null))
        assertNotEquals(shared.getInt("fixture.pid", -1), Process.myPid())
        val attemptId = checkNotNull(shared.getString("fixture.attempt", null))
        val archived = checkNotNull(journal.readFinalized(scope, attemptId))
        assertPayment(archived.payment, false)
        assertEquals(BigInteger.ONE, archived.inclusion.blockNumber)
        assertEquals(BigInteger.TEN, archived.inclusion.finalizedHeadNumber)
        assertEquals(0, archived.inclusion.extrinsicIndex)
        assertEquals("0x" + "31".repeat(32), archived.inclusion.blockHash)
        assertEquals("0x" + "32".repeat(32), archived.inclusion.finalizedHeadHash)
        assertEquals(null, journal.read(scope))
        journal.requireAvailable(scope)
        val next = journal.retain(scope, BigInteger.valueOf(20), BigInteger.TWO, "0x05060708", intent, anchor)
        assertNotEquals(archived.payment.attemptId, next.attemptId)
        assertEquals(archived, journal.readFinalized(scope, attemptId))
        journal.discardUnsubmitted(next)
        reportArchive("finalized-after-restart", attemptId)
    }

    private fun reportArchive(phase: String, attemptId: String) {
        val archive = checkNotNull(journal.readFinalized(scope, attemptId))
        val key = scope.key + ".finalized." + attemptId
        val encoded = checkNotNull(shared.getString(key, null))
        val digest = MessageDigest.getInstance("SHA-256").digest(encoded.toByteArray()).joinToString("") { "%02x".format(it) }
        val value = JSONObject().put("phase", phase).put("pid", Process.myPid())
            .put("writerPid", shared.getInt("fixture.pid", -1)).put("recordSha256", digest)
            .put("transactionHash", archive.payment.transactionHash).put("attemptId", attemptId)
            .put("poolName", archive.payment.create?.poolName).put("nominatorAddress", archive.payment.create?.nominatorAddress)
            .put("stateTogglerAddress", archive.payment.create?.stateTogglerAddress).put("fieldCount", encoded.split('\n').size)
            .put("pendingAbsent", journal.read(scope) == null).put("fixtureOnly", true).put("networkRequests", 0)
        val directory = File(context.cacheDir, "pool-create-process-evidence-v1").apply { mkdirs() }
        File(directory, "$phase.json").writeText(value.toString(2))
    }

    private fun prepare(phase: String) {
        // Only this dedicated test fixture file is cleared; production preferences are never opened.
        assertTrue(shared.edit().clear().putString("fixture.phase", phase).putInt("fixture.pid", Process.myPid()).commit())
        journal.requireAvailable(scope)
    }

    private fun assertPayment(payment: PoolJoinJournal.Payment, acknowledged: Boolean) {
        assertEquals(scope, payment.scope)
        assertEquals(details, payment.create)
        assertEquals(anchor, payment.anchor)
        assertEquals(signed, payment.signedExtrinsic)
        assertEquals(intent, payment.intent)
        assertEquals("10", payment.amount)
        assertEquals("1", payment.poolId)
        assertEquals(acknowledged, payment.acknowledged)
    }

    private fun report(phase: String, payment: PoolJoinJournal.Payment) {
        val encoded = checkNotNull(shared.getString(scope.key, null))
        val digest = MessageDigest.getInstance("SHA-256").digest(encoded.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val value = JSONObject().put("phase", phase).put("pid", Process.myPid())
            .put("writerPid", shared.getInt("fixture.pid", -1)).put("recordSha256", digest)
            .put("transactionHash", payment.transactionHash).put("acknowledged", payment.acknowledged)
            .put("poolName", payment.create?.poolName).put("nominatorAddress", payment.create?.nominatorAddress)
            .put("stateTogglerAddress", payment.create?.stateTogglerAddress).put("fieldCount", encoded.split('\n').size)
            .put("fixtureOnly", true).put("networkRequests", 0)
        val directory = File(context.cacheDir, "pool-create-process-evidence-v1").apply { mkdirs() }
        File(directory, "$phase.json").writeText(value.toString(2))
    }
}
