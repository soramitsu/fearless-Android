package jp.co.soramitsu.app.root.presentation.main

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import android.os.CancellationSignal
import android.os.Process
import android.security.NetworkSecurityPolicy
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import jp.co.soramitsu.app.root.domain.WalletDatabaseStartupResult
import jp.co.soramitsu.common.data.network.endpoint.EndpointProbeFamily
import jp.co.soramitsu.common.data.network.endpoint.EndpointProbeRequest
import jp.co.soramitsu.coredb.recovery.EndpointInventorySource
import jp.co.soramitsu.coredb.recovery.EndpointRecordRef
import jp.co.soramitsu.coredb.recovery.decision.EndpointAttachmentAction
import jp.co.soramitsu.coredb.recovery.decision.EndpointAttachmentStore
import jp.co.soramitsu.coredb.recovery.decision.EndpointRecoveryDecisionStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Read-only diagnosis of the exact retained failed OWNED save; never grants authority or probes. */
@RunWith(AndroidJUnit4::class)
class SavedEndpointAttachmentReadDiagnosticInstrumentedTest {
    @Test
    fun retainedFailedSaveSeparatesPreparationRequestAndPlatformPolicy() = runBlocking<Unit>(Dispatchers.IO) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val args = InstrumentationRegistry.getArguments()
        val profiles = mapOf(
            26 to listOf("emulator-5752", "FearlessGlobalEndpointApi26_20261020q3", "d3a8b1ca1c6842018d887f0764cdc2af", "endpoint-attachment-auth-v1-saved-owned-5d1655f1583342f08d3fe88d491fb034.json", "f3aeb83416498857c704f4f3197d1c22562804b8e47d30f1a19b357130694776"),
            34 to listOf("emulator-5742", "FearlessGlobalEndpointApi34_20261020", "0b20c87948f84c588daf2d56815f77c9", "endpoint-attachment-auth-v1-saved-owned-b7281ff047594dbf84eee31063307017.json", "3d5b0d0acea540ab4accbdae1f87a64b50c56a28701e0320f8a13388b7c30c6f"),
            36 to listOf("emulator-5748", "FearlessGlobalEndpointApi36_20261020q3", "02d404a37f81495a987dd8c00d918ffa", "endpoint-attachment-auth-v1-saved-owned-c5f6804213ee4e6196c86fa087f813cd.json", "2fa0daa5f43801df01398f73b5f614e286fb2b6ffbd4d1958a3f8f8348db3f0a"),
        )
        val profile = checkNotNull(profiles[Build.VERSION.SDK_INT])
        check(context.packageName == "jp.co.soramitsu.fearless.debug")
        check(args.getString("fearlessAttachmentDiagnostic") == "retained-failed-owned-save-read-only")
        check(args.getString("fearlessAttachmentSerial") == profile[0])
        check(args.getString("fearlessAttachmentAvd") == profile[1])
        check(args.getString("fearlessAttachmentRetainedNonce") == profile[2])
        check(args.getString("fearlessAttachmentUid") == Process.myUid().toString())
        check(context.applicationInfo.uid == Process.myUid())
        val marker = context.getSharedPreferences("global_endpoint_auth_fixture_v1", Context.MODE_PRIVATE)
        check(marker.getString("fixture", null) == "global-endpoint-auth-public-v1")
        check(marker.getString("nonce", null) == profile[2])
        check(marker.getInt("uid", -1) == Process.myUid())
        check(marker.getString("serial", null) == profile[0] && marker.getString("avd", null) == profile[1])
        val nonce = checkNotNull(args.getString("fearlessAttachmentRunNonce"))
        check(nonce.matches(Regex("[0-9a-f]{32}")) && nonce != profile[2])
        val source = checkNotNull(args.getString("fearlessAttachmentSourceCommit"))
        check(source.matches(Regex("[0-9a-f]{40}")))
        val output = File(context.cacheDir, "endpoint-attachment-diagnostic-v1-$nonce.json")
        check(!output.exists())
        val baselineBytes = File(context.cacheDir, profile[3]).readBytes()
        check(SavedEndpointInspectionRawSnapshot.sha256(baselineBytes) == profile[4])
        val baseline = JSONObject(String(baselineBytes, Charsets.UTF_8))
        check(baseline.getInt("uid") == Process.myUid() && baseline.getInt("api") == Build.VERSION.SDK_INT)
        val dbPath = context.getDatabasePath("app.db")
        check(dbPath.isFile && dbPath.length() in 1..67_108_864)
        val before = SQLiteDatabase.openDatabase(dbPath.path, null, SQLiteDatabase.OPEN_READONLY).use { raw ->
            raw.execSQL(";BEGIN DEFERRED TRANSACTION")
            try { SavedEndpointDecisionRawSnapshot.sql { raw.rawQuery(it, null) } }
            finally { raw.execSQL(";ROLLBACK") }
        }
        assertEquals(
            SavedEndpointInspectionRawSnapshot.canonical(SavedEndpointDecisionRawSnapshot.persistedSql(baseline.getJSONObject("sql"))),
            SavedEndpointInspectionRawSnapshot.canonical(SavedEndpointDecisionRawSnapshot.persistedSql(before))
        )
        val proof = JSONObject().put("status", "diagnostic-only").put("api", Build.VERSION.SDK_INT)
            .put("uid", Process.myUid()).put("nonce", nonce).put("sourceCommit", source)
            .put("baselineProof", profile[3]).put("baselineProofSHA256", profile[4])
            .put("beforeSql", before).put("beforePreferences", SavedEndpointDecisionRawSnapshot.preferences(context))
        val graph = EntryPointAccessors.fromApplication(context.applicationContext, SavedEndpointAuthFixtureEntryPoint::class.java)
        val opened = graph.startupGate().open()
        proof.put("startupResult", opened.javaClass.name)
        check(opened == WalletDatabaseStartupResult.Ready)
        val database = graph.database().openHelper.writableDatabase
        val signal = CancellationSignal()
        try {
            val ref = EndpointRecordRef(EndpointInventorySource.OWNED, -9003L, 8L)
            val saved = checkNotNull(EndpointRecoveryDecisionStore(database).read(signal, ref.source, ref.rowId))
            check(saved.source.ref == ref && saved.revision == 2L)
            check(saved.candidate.url.matches(Regex("http://127\\.0\\.0\\.1:[0-9]{1,5}/owned")))
            proof.put("savedDecisionId", saved.id).put("savedDecisionRevision", saved.revision)
            try {
                val capture = EndpointAttachmentStore(database).prepare(signal, ref, saved.id, saved.revision, EndpointAttachmentAction.ADD_NEW)
                proof.put("preparation", JSONObject().put("status", "returned").put("alreadyPresent", capture.alreadyPresent)
                    .put("action", capture.action.name))
            } catch (failure: Exception) {
                proof.put("preparation", JSONObject().put("status", "threw").put("class", failure.javaClass.name)
                    .put("message", failure.message).put("stack", failure.stackTraceToString()))
            }
            try {
                check(saved.target.family.name == "SOLANA")
                EndpointProbeRequest(EndpointProbeFamily.SOLANA, saved.candidate.url, checkNotNull(saved.target.expectedGenesis))
                proof.put("requestConstruction", JSONObject().put("status", "returned"))
            } catch (failure: Exception) {
                proof.put("requestConstruction", JSONObject().put("status", "threw").put("class", failure.javaClass.name)
                    .put("message", failure.message).put("stack", failure.stackTraceToString()))
            }
            // Keep an actual Room observer alive through preparation; this changes only TEMP tracking state.
            val firstEmission = CompletableDeferred<Unit>()
            val observer = launch(Dispatchers.IO) {
                graph.database().chainDao().nodesFlow(saved.target.chainId).collect { firstEmission.complete(Unit) }
            }
            try {
                withTimeout(5_000) { firstEmission.await() }
                val temporary = JSONArray()
                check(!database.inTransaction())
                database.beginTransactionNonExclusive()
                try {
                    database.query("SELECT type,name,tbl_name,sql FROM temp.sqlite_master ORDER BY type,name LIMIT 129").use { rows ->
                        while (rows.moveToNext()) {
                            check(temporary.length() < 128)
                            temporary.put(JSONArray().also { row ->
                                repeat(4) { index ->
                                    val value = if (rows.isNull(index)) null else rows.getString(index).also { check(it.length <= 4096) }
                                    row.put(value ?: JSONObject.NULL)
                                }
                            })
                        }
                    }
                } finally { database.endTransaction() }
                proof.put("observedTemporarySchema", temporary)
                try {
                    val capture = EndpointAttachmentStore(database).prepare(signal, ref, saved.id, saved.revision, EndpointAttachmentAction.ADD_NEW)
                    proof.put("observedPreparation", JSONObject().put("status", "returned").put("alreadyPresent", capture.alreadyPresent))
                } catch (failure: Exception) {
                    proof.put("observedPreparation", JSONObject().put("status", "threw").put("class", failure.javaClass.name)
                        .put("message", failure.message).put("stack", failure.stackTraceToString()))
                }
            } finally { observer.cancelAndJoin() }
            proof.put("cleartext127", NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted("127.0.0.1"))
                .put("cleartextLocalhost", NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted("localhost"))
        } finally {
            check(!database.inTransaction())
            database.beginTransactionNonExclusive()
            val after = try { SavedEndpointDecisionRawSnapshot.sql { database.query(it) } }
            finally { database.endTransaction() }
            proof.put("afterSql", after).put("afterPreferences", SavedEndpointDecisionRawSnapshot.preferences(context))
            SavedEndpointInspectionRawSnapshot.writeNew(output, proof)
            assertEquals(
                SavedEndpointInspectionRawSnapshot.canonical(SavedEndpointDecisionRawSnapshot.persistedSql(before)),
                SavedEndpointInspectionRawSnapshot.canonical(SavedEndpointDecisionRawSnapshot.persistedSql(after))
            )
        }
    }
}
