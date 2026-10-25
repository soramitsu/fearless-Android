package jp.co.soramitsu.app.root.domain.staking

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Process
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import jp.co.soramitsu.common.data.storage.PreferencesImpl
import jp.co.soramitsu.core.rpc.FinalizedExtrinsicReader
import jp.co.soramitsu.staking.impl.data.repository.PoolJoinJournal
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.math.BigInteger

/** Opt-in installed UI fixture: impossible signed bytes, known public wallets, networking disabled.
 * Never calls a signer, transport, admission gate or reconciler. Cleanup can therefore prove no output.
 */
@RunWith(AndroidJUnit4::class)
class PoolJoinInstalledPublicFixtureInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val scope = PoolJoinJournal.Scope(815818010499064178L,
        "91b171bb158e2d3848fa23a9f1c25182fb8e20313b2c1eb49219da7a70ce90c3",
        "625e25364c7b68e0a83065ccb40afed43f8fe933e669b24f3d69a57eddb3b715")
    private val signed = "0x01020304"
    private val intent = "12".repeat(32)
    private val anchor = FinalizedExtrinsicReader.Anchor("0x" + scope.chainId, BigInteger.ZERO, "0x" + "20".repeat(32))
    private val journal get() = PoolJoinJournal(PreferencesImpl(context.getSharedPreferences("fearless_prefs", Context.MODE_PRIVATE)))
    private val directory: File get() {
        val label = InstrumentationRegistry.getArguments().getString("poolInstalledEvidence") ?: "v1"
        require(label.matches(Regex("[a-zA-Z0-9_-]+")))
        return File(context.getExternalFilesDir(null), "pool-installed-public-fixture-$label").apply { mkdirs() }
    }

    @Test fun prepareNeverSubmittedPublicPendingForInstalledRecovery() {
        requirePublicOfflineFixture()
        assertNull(journal.read(scope))
        assertFalse(File(directory, "prepared.json").exists())
        val payment = journal.retain(scope, BigInteger("370000000000"), BigInteger.valueOf(8), signed, intent, anchor)
        assertFixture(payment)
        record("prepared", payment)
    }

    @Test fun readExactPublicPendingInFreshProcess() {
        requirePublicOfflineFixture()
        val before = JSONObject(File(directory, "prepared.json").readText())
        assertNotEquals(before.getInt("pid"), Process.myPid())
        val payment = checkNotNull(journal.read(scope))
        assertFixture(payment)
        assertEquals(before.getString("attemptId"), payment.attemptId)
        record("read-after-restart", payment)
    }

    @Test fun removeOnlyExactNeverSubmittedPublicFixture() {
        requirePublicOfflineFixture()
        val before = JSONObject(File(directory, "prepared.json").readText())
        val payment = checkNotNull(journal.read(scope))
        assertFixture(payment)
        assertEquals(before.getString("attemptId"), payment.attemptId)
        journal.discardUnsubmitted(payment)
        assertNull(journal.read(scope))
        record("removed-never-submitted", payment)
    }

    private fun requirePublicOfflineFixture() {
        assumeTrue("Only the explicit public-fixture host may run this class",
            InstrumentationRegistry.getArguments().getString("allowPublicFixturePending") == "audit-public-two-offline-v1")
        check(context.packageName == "jp.co.soramitsu.fearless.debug")
        for (key in listOf(Settings.Global.WIFI_ON, "mobile_data")) {
            check(Settings.Global.getInt(context.contentResolver, key) == 0) { "Public UI fixture requires offline device" }
        }
        SQLiteDatabase.openDatabase(context.getDatabasePath("app.db").path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT id,name,isSelected,lower(hex(substrateAccountId)) FROM meta_accounts ORDER BY id", null).use { rows ->
                assertEquals(2, rows.count)
                assertTrue(rows.moveToFirst())
                assertEquals(scope.walletId, rows.getLong(0))
                assertEquals("AuditPublicTwo", rows.getString(1))
                assertEquals(1, rows.getInt(2))
                assertEquals(scope.accountId, rows.getString(3))
                assertTrue(rows.moveToNext())
                assertEquals(8387175324156294517L, rows.getLong(0))
                assertEquals("AuditPublic", rows.getString(1))
                assertEquals("66933bd1f37070ef87bd1198af3dacceb095237f803f3d32b173e6b425ed7972", rows.getString(3))
            }
        }
    }

    private fun assertFixture(payment: PoolJoinJournal.Payment) {
        assertEquals(scope, payment.scope)
        assertEquals("370000000000", payment.amount)
        assertEquals("8", payment.poolId)
        assertEquals(signed, payment.signedExtrinsic)
        assertEquals(intent, payment.intent)
        assertEquals(anchor, payment.anchor)
        assertFalse(payment.acknowledged)
    }

    private fun record(phase: String, payment: PoolJoinJournal.Payment) {
        File(directory, "$phase.json").writeText(JSONObject().put("phase", phase).put("pid", Process.myPid())
            .put("attemptId", payment.attemptId).put("transactionHash", payment.transactionHash)
            .put("scopeKey", scope.key).put("amount", payment.amount).put("poolId", payment.poolId)
            .put("neverSubmitted", true).put("impossibleSignedBytes", signed).toString(2))
    }
}
