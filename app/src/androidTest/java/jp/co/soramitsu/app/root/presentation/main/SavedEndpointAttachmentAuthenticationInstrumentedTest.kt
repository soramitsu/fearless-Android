package jp.co.soramitsu.app.root.presentation.main

import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityNodeInfo
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import android.os.Process
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavBackStackEntry
import androidx.navigation.fragment.NavHostFragment
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import jp.co.soramitsu.account.impl.presentation.recovery.SavedEndpointAttachmentStatus
import jp.co.soramitsu.account.impl.presentation.pincode.PincodeFragment
import jp.co.soramitsu.account.impl.presentation.recovery.SavedEndpointsFragment
import jp.co.soramitsu.app.R
import jp.co.soramitsu.app.root.domain.InitializationStep
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.app.root.presentation.WalletRootActivity
import jp.co.soramitsu.app.root.presentation.RootViewModel
import jp.co.soramitsu.app.root.presentation.WalletStartupActivity
import jp.co.soramitsu.common.data.storage.encrypt.PinHealth
import jp.co.soramitsu.common.R as CommonR
import jp.co.soramitsu.common.di.modules.SHARED_PREFERENCES_FILE
import jp.co.soramitsu.onboarding.impl.welcome.WelcomeFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasClickAction

import android.os.CancellationSignal
import jp.co.soramitsu.coredb.recovery.EndpointInventoryReader
import jp.co.soramitsu.coredb.recovery.EndpointInventorySource
import jp.co.soramitsu.coredb.recovery.EndpointField
import jp.co.soramitsu.coredb.recovery.EndpointRecordRef
import jp.co.soramitsu.coredb.recovery.EndpointValue
import jp.co.soramitsu.coredb.recovery.EndpointTextState
import jp.co.soramitsu.coredb.recovery.EndpointChunkState
import jp.co.soramitsu.coredb.recovery.EndpointStorage
import jp.co.soramitsu.coredb.recovery.EndpointOwnership
import jp.co.soramitsu.coredb.recovery.EndpointSourceProvenance
import androidx.compose.ui.test.hasTestTag
import java.util.Locale

import androidx.compose.ui.test.performTextReplacement
import jp.co.soramitsu.common.model.UniversalWalletRegistry
import jp.co.soramitsu.coredb.recovery.decision.EndpointDecisionTarget
import jp.co.soramitsu.account.impl.presentation.recovery.SavedEndpointDecisionForm

/**
 * Actual PIN, explicit loopback verification and inactive addition from five retained sources.
 * No reseed, direct fixture DML, authorization injection, public network or node selection.
 * Host tooling must independently admit APKs, UID, exact retained83 SQL/preferences and all prior proofs.
 */
@RunWith(AndroidJUnit4::class)
class SavedEndpointAttachmentAuthenticationInstrumentedTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private lateinit var graph: SavedEndpointAuthFixtureEntryPoint
    private lateinit var database: SupportSQLiteDatabase
    private lateinit var nonce: String
    private lateinit var retainedNonce: String
    private lateinit var sourceCommit: String
    private lateinit var serial: String
    private lateinit var avd: String
    private val pinDestructions = mutableListOf<JSONObject>()
    private val pinInputReadiness = mutableListOf<JSONObject>()
    private val sourceObservations = mutableListOf<JSONObject>()
    private val initializerObservations = mutableListOf<JSONObject>()
    private val savedDecisions = mutableListOf<JSONObject>()
    private val snapshotFiles = mutableListOf<JSONObject>()
    private lateinit var expectedSavedEntry: NavBackStackEntry
    private lateinit var heartbeatEvidence: SavedEndpointHeartbeatEvidence
    private lateinit var baselinePreferences: JSONObject
    private lateinit var retainedSql83: JSONObject
    private var continuation = false
    private var retainedOwnedUrl: String? = null
    private val revision = 8L
    private var expectedProjectionCount = 1L

    @Test
    fun realPinVerifiesAndAddsFiveInactiveEndpointsWithMismatchCancellationAndPresenceControls() {
        admitRetainedFixture()
        launch()
        awaitFragment<WelcomeFragment>()
        val startupInitializer = awaitInitializerAndContinue("initial", null)
        graph = EntryPointAccessors.fromApplication(context.applicationContext, SavedEndpointAuthFixtureEntryPoint::class.java)
        database = graph.database().openHelper.writableDatabase
        var expectedSql = retainedSql83
        assertPersistentEquals(expectedSql, captureSnapshot("after-startup"))
        assertEquals(PinHealth.HEALTHY, runBlocking(Dispatchers.IO) { graph.accountRepository().getPinHealth() })
        assertEquals(PUBLIC_PIN, runBlocking(Dispatchers.IO) { graph.accountRepository().getPinCode() })
        val title = text(R.string.saved_endpoints_title)
        compose.waitUntil(15_000) { compose.onAllNodesWithText(title).fetchSemanticsNodes().size == 1 }
        compose.onNodeWithText(title).performScrollTo().assertIsDisplayed().performClick()
        assertLocked(awaitFragment()); assertSecure()
        unlock()
        val wrongPin = awaitFragment<PincodeFragment>()
        val wrongLifetime = capturePinLifetime(wrongPin)
        var mismatch = 0
        onMain { wrongPin.viewModel.matchingPincodeErrorEvent.observe(wrongPin) { mismatch++ } }
        enterPin("654321")
        compose.waitUntil(15_000) { onMain { mismatch == 1 } }
        assertSame(wrongPin, onMain { currentFragment() }); assertSecure()
        onMain { root().onBackPressedDispatcher.onBackPressed() }
        awaitPinDestroyed(wrongLifetime); assertLocked(awaitFragment())
        assertPersistentEquals(expectedSql, captureSnapshot("wrong-pin-denied"), 1)
        unlockWithPublicPin()
        assertPersistentEquals(expectedSql, captureSnapshot("correct-pin-restored"))

        // OWNED first: its original row is the only owned record before the first addition.
        val sources = listOf(EndpointInventorySource.OWNED) +
            EndpointInventorySource.values().filter { it != EndpointInventorySource.OWNED }
        sources.forEach { source ->
            LoopbackGenesisPeer(source).use { peer ->
                selectSource(source); openForm(source, if (source == EndpointInventorySource.OWNED && continuation) checkNotNull(retainedOwnedUrl) else candidateUrl(source))
                assertEquals(0, peer.requests.get())
                replaceField("saved_endpoint_choice_url", peer.url)
                clickTag("saved_endpoint_choice_save")
                compose.waitUntil(15_000) { onMain { currentForm().let { !it.saving && (it.saved || it.saveFailed) } } }
                onMain { assertTrue(currentForm().saved); assertTrue(currentForm().attachmentReady); assertFalse(currentForm().saveFailed) }
                expectedSql = SavedEndpointAttachmentRawOracle.revised(expectedSql, source, peer.url, if (source == EndpointInventorySource.OWNED && continuation) 2L else 1L)
                assertPersistentEquals(expectedSql, captureSnapshot("saved-${source.name.lowercase(Locale.ROOT)}"))
                assertEquals("Save must be offline", 0, peer.requests.get())

                if (source == EndpointInventorySource.OWNED) {
                    peer.mode.set("STALL")
                    clickTag("saved_endpoint_attachment_action")
                    assertTrue(peer.stalled.await(5, TimeUnit.SECONDS))
                    onMain { assertTrue(currentForm().attaching) }
                    val outgoing = awaitFragment<SavedEndpointsFragment>()
                    val task = onMain { root().taskId }
                    context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    compose.waitUntil(15_000) { onMain {
                        ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).none { it is WalletRootActivity }
                    } }
                    assertDataCleared(outgoing)
                    peer.release.countDown()
                    assertTrue(peer.stallCompleted.await(5, TimeUnit.SECONDS))
                    assertPersistentEquals(expectedSql, captureSnapshot("cancelled-owned"))
                    context.startActivity(Intent(context, WalletRootActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                    assertLocked(awaitFragment()); assertEquals(task, onMain { root().taskId }); assertSecure()
                    unlockWithPublicPin()
                    selectSource(source); openForm(source, peer.url)
                    assertEquals(1, peer.requests.get())
                    assertPersistentEquals(expectedSql, captureSnapshot("cancelled-owned-return"))
                }
                if (source == EndpointInventorySource.LEGACY) {
                    peer.mode.set("MISMATCH")
                    clickTag("saved_endpoint_attachment_action")
                    awaitAttachment(SavedEndpointAttachmentStatus.MISMATCH)
                    assertEquals(1, peer.requests.get())
                    assertPersistentEquals(expectedSql, captureSnapshot("mismatch-legacy"))
                }
                peer.mode.set("MATCH")
                val before = peer.requests.get()
                clickTag("saved_endpoint_attachment_action")
                awaitAttachment(SavedEndpointAttachmentStatus.ADDED_INACTIVE)
                assertEquals(before + 1, peer.requests.get())
                expectedSql = SavedEndpointAttachmentRawOracle.added(expectedSql, candidateName(source), peer.url)
                expectedProjectionCount++
                assertPersistentEquals(expectedSql, captureSnapshot("added-${source.name.lowercase(Locale.ROOT)}"))
                savedDecisions += JSONObject().put("source", source.name).put("url", peer.url)
                    .put("decisionRevision", if (source == EndpointInventorySource.OWNED && continuation) 3 else 2).put("inactiveAddition", true).put("requests", peer.requests.get())
                click(R.string.saved_endpoints_part_return); assertSource(source)
                if (source == EndpointInventorySource.LEGACY) {
                    // The persisted exact pair is an observation; reopening and clicking must not request again.
                    openForm(source, peer.url)
                    val observedBefore = peer.requests.get()
                    clickTag("saved_endpoint_attachment_action")
                    awaitAttachment(SavedEndpointAttachmentStatus.PRESENT_INACTIVE)
                    assertEquals(observedBefore, peer.requests.get())
                    assertPersistentEquals(expectedSql, captureSnapshot("present-legacy"))
                    click(R.string.saved_endpoints_part_return); assertSource(source)
                }
            }
        }
        val recreatedFrom = awaitFragment<SavedEndpointsFragment>()
        val oldActivity = onMain { root() }
        onMain { oldActivity.recreate() }
        compose.waitUntil(30_000) { onMain {
            resumedRoot()?.let { it !== oldActivity && currentFragment() is SavedEndpointsFragment } == true
        } }
        awaitInitializerAndContinue("after-recreation", startupInitializer)
        assertLocked(awaitFragment()); assertDataCleared(recreatedFrom); assertSecure()
        unlockWithPublicPin()
        assertPersistentEquals(expectedSql, captureSnapshot("after-recreation"))
        requirePriorProofsExact()
        assertEquals(4, pinDestructions.size); assertEquals(4, pinInputReadiness.size)
        assertEquals(5, savedDecisions.size)
        writeProof("proof", JSONObject().put("formatVersion", 1).put("api", Build.VERSION.SDK_INT)
            .put("uid", Process.myUid()).put("nonce", nonce).put("retainedNonce", retainedNonce)
            .put("sourceCommit", sourceCommit).put("serial", serial).put("avd", avd)
            .put("realKeypadPin", true).put("wrongPinDenied", true).put("fiveExplicitInactiveAdditions", true)
            .put("mismatchNoSqlDelta", true).put("backgroundCancellationNoSqlDelta", true)
            .put("existingPairNoRequest", true).put("allOriginalSqlRetained", true)
            .put("allPriorProofsExact", true).put("recreationRelocked", true)
            .put("restoreActionQualified", false).put("selectionQualified", false).put("publicNetworkQualified", false)
            .put("decisions", JSONArray(savedDecisions)).put("snapshots", JSONArray(snapshotFiles))
            .put("pinDestructions", JSONArray(pinDestructions)).put("pinInputReadiness", JSONArray(pinInputReadiness))
            .put("initializerObservations", JSONArray(initializerObservations)))
        onMain { root().finish() }
    }

    private fun awaitAttachment(expected: SavedEndpointAttachmentStatus) {
        compose.waitUntil(15_000) { onMain { currentForm().let { !it.attaching && it.attachmentStatus != null } } }
        onMain { assertEquals(expected, currentForm().attachmentStatus) }
        scrollTag("saved_endpoint_attachment_status")
        compose.onNodeWithTag("saved_endpoint_attachment_status").assertIsDisplayed()
    }

    private fun admitRetainedFixture() = runBlocking(Dispatchers.IO) {
        val args = InstrumentationRegistry.getArguments()
        val profile = checkNotNull(PROFILES[Build.VERSION.SDK_INT])
        serial = profile.first; avd = profile.second; retainedNonce = profile.third
        check(context.packageName == "jp.co.soramitsu.fearless.debug")
        check(args.getString("fearlessAttachmentOwnedUid") == "retained-qualified-public-pin-fixture")
        check(args.getString("fearlessAttachmentSerial") == serial && args.getString("fearlessAttachmentAvd") == avd)
        check(args.getString("fearlessAttachmentRetainedNonce") == retainedNonce)
        check(args.getString("fearlessAttachmentUid") == Process.myUid().toString())
        check(Process.myUid() == context.applicationInfo.uid)
        nonce = checkNotNull(args.getString("fearlessAttachmentRunNonce")).also {
            check(it.matches(Regex("[0-9a-f]{32}")) && it != retainedNonce)
        }
        sourceCommit = checkNotNull(args.getString("fearlessAttachmentSourceCommit")).also { check(it.matches(Regex("[0-9a-f]{40}"))) }
        continuation = args.getString("fearlessAttachmentContinuation") == "reviewed-failed-owned-save-v1"
        check(continuation || args.getString("fearlessAttachmentContinuation") == null)
        if (!continuation) check(context.cacheDir.listFiles().orEmpty().none { it.name.startsWith("endpoint-attachment-auth-") })
        check(context.cacheDir.listFiles().orEmpty().none { it.name.contains(nonce) })
        requirePriorProofsExact()
        val marker = context.getSharedPreferences(MARKER, Context.MODE_PRIVATE)
        check(marker.getString("fixture", null) == FIXTURE && marker.getString("nonce", null) == retainedNonce)
        check(marker.getInt("uid", -1) == Process.myUid() && marker.getString("serial", null) == serial && marker.getString("avd", null) == avd)
        val baseline = JSONObject(File(context.cacheDir, BASELINE_PROOF.getValue(Build.VERSION.SDK_INT)).readText())
        check(baseline.getInt("api") == Build.VERSION.SDK_INT && baseline.getInt("uid") == Process.myUid())
        val oldAfter = JSONObject(File(context.cacheDir, "global-endpoint-auth-after-$retainedNonce.json").readText())
        retainedSql83 = SavedEndpointDecisionRawSnapshot.upgraded(oldAfter.getJSONObject("sql"))
        EndpointInventorySource.values().forEach { source ->
            retainedSql83 = SavedEndpointDecisionRawSnapshot.inserted(retainedSql83, source, candidateName(source), candidateUrl(source))
        }
        assertEquals(SavedEndpointInspectionRawSnapshot.canonical(SavedEndpointDecisionRawSnapshot.persistedSql(retainedSql83)),
            SavedEndpointInspectionRawSnapshot.canonical(SavedEndpointDecisionRawSnapshot.persistedSql(baseline.getJSONObject("sql"))))
        baselinePreferences = baseline.getJSONObject("preferences")
        if (continuation) {
            val diagnostic = JSONObject(File(context.cacheDir, CONTINUATION_DIAGNOSTIC.getValue(Build.VERSION.SDK_INT)).readText())
            check(diagnostic.getInt("api") == Build.VERSION.SDK_INT && diagnostic.getInt("uid") == Process.myUid())
            check(diagnostic.getString("status") == "diagnostic-only")
            check(diagnostic.getLong("savedDecisionRevision") == 2L)
            check(diagnostic.getJSONObject("observedPreparation").getString("status") == "threw")
            val failed = JSONObject(File(context.cacheDir, diagnostic.getString("baselineProof")).readText())
            check(failed.getString("phase") == "saved-owned")
            val decisions = failed.getJSONObject("sql").getJSONObject("tables")
                .getJSONObject("endpoint_recovery_decisions").getJSONArray("rows")
            val owned = (0 until decisions.length()).map(decisions::getJSONObject).single {
                it.getJSONObject("sourceKind").getString("hex") == "4F574E4544"
            }
            check(owned.getJSONObject("revision").getLong("integer") == 2L)
            val hex = owned.getJSONObject("candidateUrl").getString("hex")
            check(hex.length in 2..8192 && hex.length % 2 == 0 && hex.matches(Regex("[0-9A-F]+")))
            retainedOwnedUrl = String(hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray(), Charsets.UTF_8)
            retainedSql83 = SavedEndpointAttachmentRawOracle.revised(retainedSql83, EndpointInventorySource.OWNED, checkNotNull(retainedOwnedUrl))
            for (observed in listOf(failed.getJSONObject("sql"), diagnostic.getJSONObject("beforeSql"), diagnostic.getJSONObject("afterSql"))) {
                assertEquals(SavedEndpointInspectionRawSnapshot.canonical(SavedEndpointDecisionRawSnapshot.persistedSql(retainedSql83)),
                    SavedEndpointInspectionRawSnapshot.canonical(SavedEndpointDecisionRawSnapshot.persistedSql(observed)))
            }
            baselinePreferences = diagnostic.getJSONObject("afterPreferences")
        }
        heartbeatEvidence = SavedEndpointHeartbeatEvidence(baselinePreferences, args)
        val path = context.getDatabasePath("app.db")
        check(path.isFile && path.length() in 1..67_108_864)
        val sql = SQLiteDatabase.openDatabase(path.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            check(db.version == 83)
            db.execSQL(";BEGIN DEFERRED TRANSACTION")
            try { SavedEndpointDecisionRawSnapshot.sql { db.rawQuery(it, null) } }
            finally { db.execSQL(";ROLLBACK") }
        }
        val before = JSONObject().put("phase", "before-startup").put("sql", sql)
            .put("preferences", SavedEndpointDecisionRawSnapshot.preferences(context))
            .put("api", Build.VERSION.SDK_INT).put("uid", Process.myUid()).put("nonce", nonce)
            .put("sourceCommit", sourceCommit).put("selectedAccount", false)
        assertPersistentEquals(retainedSql83, before, exactPreferenceBytes = true)
    }

    private fun requirePriorProofsExact() {
        val expected = PRIOR_PROOFS.getValue(Build.VERSION.SDK_INT) +
            (if (continuation) CONTINUATION_PROOFS.getValue(Build.VERSION.SDK_INT) else emptyMap())
        for ((name, digest) in expected) {
            val file = File(context.cacheDir, name)
            check(file.isFile && file.length() in 1..4_194_304)
            check(SavedEndpointInspectionRawSnapshot.sha256(file.readBytes()) == digest)
        }
        val names = context.cacheDir.listFiles().orEmpty().filter {
            it.name.startsWith("global-endpoint-auth-") || it.name.startsWith("endpoint-decision-auth-") ||
                (continuation && (it.name.startsWith("endpoint-attachment-auth-") || it.name.startsWith("endpoint-attachment-diagnostic-")) &&
                    !it.name.endsWith("-$nonce.json"))
        }.map { it.name }.toSet()
        check(names == expected.keys)
    }

    private fun captureSnapshot(phase: String): JSONObject = runBlocking(Dispatchers.IO) {
        check(!database.inTransaction())
        database.beginTransactionNonExclusive()
        val sql = try {
            assertEquals(83L, scalar("PRAGMA user_version"))
            assertEquals(0L, scalar("SELECT COUNT(*) FROM meta_accounts"))
            assertEquals(0L, scalar("SELECT COUNT(*) FROM chain_accounts"))
            assertEquals(2L, scalar("SELECT COUNT(*) FROM chains"))
            assertEquals(expectedProjectionCount, scalar("SELECT COUNT(*) FROM chain_nodes"))
            assertEquals(8L, scalar("SELECT revision FROM custom_endpoint_records WHERE id=-9003"))
            SavedEndpointDecisionRawSnapshot.sql { database.query(it) }.also {
                assertEquals("Coherent primary transaction FK", "[[1]]", it.getJSONArray("foreignKeys").toString())
            }
        } finally { database.endTransaction() }
        assertFalse(graph.accountRepository().isAccountSelected())
        val result = JSONObject().put("phase", phase).put("sql", sql)
            .put("preferences", SavedEndpointDecisionRawSnapshot.preferences(context)).put("selectedAccount", false)
            .put("api", Build.VERSION.SDK_INT).put("uid", Process.myUid()).put("nonce", nonce).put("sourceCommit", sourceCommit)
        result
    }

    /** Exact SQL and preferences except the explicit PIN counter and validated one-day Firebase append. */
    private fun assertPersistentEquals(expectedSql: JSONObject, observed: JSONObject, failedAttempts: Int = 0, exactPreferenceBytes: Boolean = false) {
        try { heartbeatEvidence.observe(observed) }
        finally { writeProof(observed.getString("phase"), observed) }
        assertEquals(SavedEndpointInspectionRawSnapshot.canonical(SavedEndpointDecisionRawSnapshot.persistedSql(expectedSql)),
            SavedEndpointInspectionRawSnapshot.canonical(SavedEndpointDecisionRawSnapshot.persistedSql(observed.getJSONObject("sql"))))
        check(failedAttempts in 0..2)
        val actual = observed.getJSONObject("preferences")
        assertEquals(baselinePreferences.keys().asSequence().toSet(), actual.keys().asSequence().toSet())
        for (name in baselinePreferences.keys()) {
            val baseline = baselinePreferences.getJSONObject(name)
            val current = actual.getJSONObject(name)
            if (name == SavedEndpointHeartbeatOracle.NAME) {
                assertEquals(current.getString("rawXmlSHA256"), observed.getJSONObject("heartbeatObservation").getString("observedSHA256"))
            } else if (name != SHARED_PREFERENCES_FILE || exactPreferenceBytes) {
                assertEquals(SavedEndpointInspectionRawSnapshot.canonical(baseline), SavedEndpointInspectionRawSnapshot.canonical(current))
            } else {
                assertEquals(baseline.getString("path"), current.getString("path"))
                val expected = JSONObject(SavedEndpointInspectionRawSnapshot.canonical(baseline.getJSONObject("typedMap")))
                check(!expected.has("prefs_pin_failed_attempts") && !expected.has("prefs_pin_lock_until_ms"))
                if (failedAttempts > 0) expected.put("prefs_pin_failed_attempts", JSONObject().put("type", "int").put("value", failedAttempts.toString()))
                assertEquals(SavedEndpointInspectionRawSnapshot.canonical(expected), SavedEndpointInspectionRawSnapshot.canonical(current.getJSONObject("typedMap")))
            }
        }
        assertFalse(observed.getBoolean("selectedAccount"))
    }

    private fun writeProof(phase: String, value: JSONObject) {
        check(phase in setOf("before-startup", "after-startup", "wrong-pin-denied", "correct-pin-restored",
            "cancelled-owned", "cancelled-owned-return", "mismatch-legacy", "present-legacy", "after-recreation", "proof") ||
            EndpointInventorySource.values().any { phase == "saved-${it.name.lowercase(Locale.ROOT)}" ||
                phase == "added-${it.name.lowercase(Locale.ROOT)}" })
        val file = File(context.cacheDir, "endpoint-attachment-auth-v1-$phase-$nonce.json")
        val hash = SavedEndpointInspectionRawSnapshot.writeNew(file, value)
        if (phase != "proof") snapshotFiles += JSONObject().put("name", file.name).put("sha256", hash)
    }

    private fun currentForm(): SavedEndpointDecisionForm = checkNotNull((currentFragment() as SavedEndpointsFragment).viewModel.state.value.decisionForm)

    private fun openForm(source: EndpointInventorySource, expectedUrl: String) {
        click(R.string.saved_endpoint_choice_edit)
        compose.waitUntil(15_000) { onMain {
            val form = (currentFragment() as? SavedEndpointsFragment)?.viewModel?.state?.value?.decisionForm
            form != null && !form.loading && !form.targetsLoading
        } }
        onMain {
            val form = currentForm()
            assertEquals(ref(source), form.ref); assertFalse(form.loadFailed); assertFalse(form.targetsFailed)
            assertEquals(candidateName(source), form.name); assertEquals(expectedUrl, form.url)
            assertEquals(UniversalWalletRegistry.solanaMainnet.id, form.target!!.chainId)
            assertEquals(EndpointDecisionTarget.Family.SOLANA, form.target!!.family)
            assertEquals(UniversalWalletRegistry.solanaMainnet.genesisHash, form.target!!.expectedGenesis)
            assertTrue(form.attachmentReady); assertFalse(form.attachmentSourceChanged)
        }
        assertSecure()
    }
    private fun replaceField(tag: String, value: String) {
        scrollTag(tag)
        compose.onNodeWithTag(tag).assertIsDisplayed().assertIsEnabled().performTextReplacement(value)
    }
    private fun scrollTag(tag: String) = compose.onNodeWithTag("saved_endpoints_list").performScrollToNode(hasTestTag(tag))
    private fun clickTag(tag: String) {
        scrollTag(tag); compose.onNodeWithTag(tag).assertIsDisplayed().assertIsEnabled().performClick()
    }
    private fun candidateName(source: EndpointInventorySource) = "Public offline ${source.name} 雪"
    private fun candidateUrl(source: EndpointInventorySource) = "https://explicit-choice.invalid/${source.name.lowercase(Locale.ROOT)}?public=1"
    private fun ref(source: EndpointInventorySource) = EndpointRecordRef(source,
        if (source == EndpointInventorySource.LEGACY) 9001L else ROW_ID, if (source == EndpointInventorySource.OWNED) 8L else null)

    private fun awaitInitializerAndContinue(
        phase: String,
        previous: Pair<RootViewModel, Event<InitializationStep>>?
    ): Pair<RootViewModel, Event<InitializationStep>> {
        val automation = instrumentation.uiAutomation
        val originalFlags = automation.serviceInfo.flags
        automation.serviceInfo = automation.serviceInfo.apply {
            // API 26 otherwise keeps a null active root after the real dialog dismisses.
            flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        // Query the real dialog window through the platform test service; no view injection.
        val nodes: (Int) -> List<AccessibilityNodeInfo>? = { id ->
            automation.rootInActiveWindow?.let { window ->
                try {
                    if (window.packageName?.toString() == context.packageName && window.isVisibleToUser) {
                        window.findAccessibilityNodeInfosByViewId(context.resources.getResourceName(id))
                    } else null
                } finally {
                    window.recycle()
                }
            }
        }
        val noContinue: () -> Boolean = {
            nodes(CommonR.id.alertSecondaryButton)?.let { found ->
                try { found.isEmpty() } finally { found.forEach { it.recycle() } }
            } == true
        }
        try {
            val viewModel = onMain { ViewModelProvider(root())[RootViewModel::class.java] }
            if (previous != null && viewModel === previous.first) {
                val event = onMain { checkNotNull(viewModel.showNoInternetConnectionAlert.value) }
                assertSame(previous.second, event)
                assertTrue(onMain { event.hasBeenHandled })
                compose.waitUntil(5_000, noContinue)
                initializerObservations += JSONObject().put("phase", phase).put("branch", "RETAINED")
                    .put("sameViewModel", true).put("sameCompletionEvent", true)
                    .put("handledByActivity", true).put("continueClicked", false).put("step", event.peekContent().name)
                return previous
            }
            // Read the actual initializer's completion; never invoke or retry initialization.
            compose.waitUntil(30_000) { onMain {
                viewModel.showNoInternetConnectionAlert.value?.hasBeenHandled == true
            } }
            val event = onMain { checkNotNull(viewModel.showNoInternetConnectionAlert.value) }
            if (previous != null) assertNotSame(previous.second, event)
            compose.waitUntil(5_000) {
                nodes(CommonR.id.alertSecondaryButton)?.let { found ->
                    try { found.size == 1 && found.single().isVisibleToUser }
                    finally { found.forEach { it.recycle() } }
                } == true
            }
            val expectedMessage = if (event.peekContent() == InitializationStep.AppConfig) {
                CommonR.string.startup_settings_refresh_unavailable
            } else {
                CommonR.string.startup_network_refresh_unavailable
            }
            for ((id, label) in listOf(
                CommonR.id.alertTitle to CommonR.string.common_warning,
                CommonR.id.alertMessage to expectedMessage,
                CommonR.id.alertSecondaryButton to CommonR.string.common_continue
            )) {
                val found = checkNotNull(nodes(id))
                try {
                    assertEquals("Exactly one real startup alert view", 1, found.size)
                    val node = found.single()
                    assertEquals(context.packageName, node.packageName.toString())
                    assertEquals(text(label), node.text.toString())
                    assertTrue(node.isVisibleToUser)
                    if (id == CommonR.id.alertSecondaryButton) {
                        assertTrue(node.isEnabled && node.isClickable)
                        assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                    }
                } finally {
                    found.forEach { it.recycle() }
                }
            }
            compose.waitUntil(5_000, noContinue)
            initializerObservations += JSONObject().put("phase", phase).put("branch", if (previous == null) "INITIAL" else "REPLACED")
                .put("sameViewModel", false).put("sameCompletionEvent", false)
                .put("handledByActivity", true).put("continueClicked", true).put("step", event.peekContent().name)
            return viewModel to event
        } finally {
            automation.serviceInfo = automation.serviceInfo.apply { flags = originalFlags }
        }
    }

    private fun assertDataCleared(fragment: SavedEndpointsFragment) {
        compose.waitUntil(15_000) { onMain { fragment.viewModel.state.value.let {
            it.locked && !it.loading && it.page == null && it.inspection == null && it.inventoryPage == null && it.inventoryInspection == null && it.decisionForm == null
        } } }
    }
    private fun assertLocked(fragment: SavedEndpointsFragment) {
        assertDataCleared(fragment)
        compose.onNodeWithTag("saved_endpoint_collection").assertDoesNotExist()
        for (tag in listOf("saved_endpoint_choice_name", "saved_endpoint_choice_url", "saved_endpoint_choice_save", "saved_endpoint_choice_selected"))
            compose.onNodeWithTag(tag).assertDoesNotExist()
        compose.onNodeWithTag("saved_endpoint_inventory_chunk").assertDoesNotExist()
        compose.onNodeWithTag("saved_endpoint_chunk").assertDoesNotExist()
        compose.onNodeWithText("\"$PUBLIC_ENDPOINT\"").assertDoesNotExist()
        compose.onNodeWithText("\"N\\xff\\u0000\"").assertDoesNotExist()
        val locked = text(R.string.saved_endpoints_locked); scrollTo(locked); compose.onNodeWithText(locked).assertIsDisplayed()
    }
    private fun assertUnlocked(fragment: SavedEndpointsFragment) {
        compose.waitUntil(15_000) { onMain { fragment.viewModel.state.value.let {
            !it.locked && !it.loading && it.page != null && it.inspection == null && it.inventoryInspection == null
        } } }
        assertSource(EndpointInventorySource.LEGACY)
    }
    private fun selectSource(source: EndpointInventorySource) {
        compose.onNodeWithTag("saved_endpoints_list").performScrollToNode(hasTestTag("saved_endpoint_collection"))
        compose.onNodeWithTag("saved_endpoint_collection").assertIsDisplayed().performClick()
        compose.onNodeWithTag("saved_endpoint_source_${source.name.lowercase(Locale.ROOT)}")
            .performScrollTo().assertIsDisplayed().performClick()
        assertSource(source)
        sourceObservations += JSONObject().put("source", source.name).put("actualMenuSelection", true)
            .put("rowId", if (source == EndpointInventorySource.LEGACY) 9001 else ROW_ID)
    }
    private fun assertSource(source: EndpointInventorySource) {
        val fragment = awaitFragment<SavedEndpointsFragment>()
        compose.waitUntil(15_000) { onMain { fragment.viewModel.state.value.let {
            !it.locked && !it.loading && it.source == source && it.decisionForm == null && it.inspection == null && it.inventoryInspection == null &&
                (if (source == EndpointInventorySource.LEGACY) it.page != null else it.inventoryPage != null)
        } } }
        onMain {
            val state = fragment.viewModel.state.value; assertFalse(state.readFailed)
            if (source == EndpointInventorySource.LEGACY) {
                assertNull(state.inventoryPage)
                val row = checkNotNull(state.page).records.single()
                assertEquals(9001L, row.originalRowId); assertEquals(PUBLIC_NAME, row.name.value); assertEquals(PUBLIC_ENDPOINT, row.endpoint.value)
            } else {
                assertNull(state.page)
                val page = checkNotNull(state.inventoryPage); assertEquals(source, page.source)
                val row = page.records.single { it.ref.rowId == ROW_ID }; assertEquals(source, row.ref.source); assertEquals(ROW_ID, row.ref.rowId)
                assertEquals(EndpointValue.Text(CHAIN_REFERENCE.toByteArray().size.toLong(), EndpointTextState.AVAILABLE, CHAIN_REFERENCE),
                    row.values[EndpointField.CHAIN_REFERENCE])
                when (source) {
                    EndpointInventorySource.HISTORICAL -> {
                        assertEquals(EndpointSourceProvenance(39, 28), page.provenance)
                        assertEquals("Earliest public record", (row.values[EndpointField.NAME] as EndpointValue.Text).value)
                    }
                    EndpointInventorySource.UPGRADE_ENTRY -> {
                        assertEquals(EndpointSourceProvenance(76, 71), page.provenance)
                        assertEquals("Upgrade entry public record", (row.values[EndpointField.NAME] as EndpointValue.Text).value)
                    }
                    EndpointInventorySource.CURRENT, EndpointInventorySource.OWNED -> {
                        assertEquals(EndpointValue.Text(3, EndpointTextState.INVALID_UTF8, null), row.values[EndpointField.NAME])
                        assertEquals(EndpointValue.Text(4097, EndpointTextState.OVERSIZED, null), row.values[EndpointField.ENDPOINT])
                        if (source == EndpointInventorySource.CURRENT) assertEquals(EndpointSourceProvenance(81, 81), page.provenance)
                        else {
                            assertNull(page.provenance); assertEquals(revision, row.ref.revision)
                            assertEquals(EndpointOwnership(false, false, ROW_ID), row.ownership)
                        }
                    }
                    else -> error("Unexpected source")
                }
                if (source != EndpointInventorySource.OWNED) { assertNull(row.ref.revision); assertNull(row.ownership) }
            }
        }
        val rendered = when (source) {
            EndpointInventorySource.LEGACY -> "\"$PUBLIC_ENDPOINT\""
            EndpointInventorySource.HISTORICAL -> "\"Earliest public record\""
            EndpointInventorySource.UPGRADE_ENTRY -> "\"Upgrade entry public record\""
            EndpointInventorySource.CURRENT -> text(R.string.saved_endpoint_historical_notice)
            EndpointInventorySource.OWNED -> text(R.string.saved_endpoint_pending)
        }
        scrollTo(rendered); compose.onNodeWithText(rendered).assertIsDisplayed(); assertSecure()
    }
    private fun scrollTo(label: String) = compose.onNodeWithTag("saved_endpoints_list").performScrollToNode(hasText(label))
    private fun click(id: Int) {
        val label = text(id); scrollTo(label); compose.onNodeWithText(label).assertIsDisplayed().assertIsEnabled().performClick()
    }

    private fun launch() = context.startActivity(Intent(context, WalletStartupActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))

    private fun unlock() {
        compose.onNodeWithTag("saved_endpoints_list").performScrollToNode(hasText(text(R.string.saved_endpoints_unlock)))
        compose.waitUntil(15_000) { onMain {
            val controller = (root().supportFragmentManager.findFragmentById(R.id.navHost) as NavHostFragment).navController
            controller.currentBackStackEntry?.let { it.destination.id == R.id.savedEndpointsFragment &&
                it.lifecycle.currentState == Lifecycle.State.RESUMED } == true
        } }
        expectedSavedEntry = onMain {
            val controller = (root().supportFragmentManager.findFragmentById(R.id.navHost) as NavHostFragment).navController
            checkNotNull(controller.currentBackStackEntry).also {
                check(it.destination.id == R.id.savedEndpointsFragment)
                check(it.lifecycle.currentState == Lifecycle.State.RESUMED)
            }
        }
        compose.onNodeWithText(text(R.string.saved_endpoints_unlock)).performClick()
    }

    private fun unlockWithPublicPin() {
        unlock()
        val pin = awaitFragment<PincodeFragment>()
        val lifetime = capturePinLifetime(pin)
        enterPin(PUBLIC_PIN)
        assertUnlocked(awaitFragment())
        // Check only after the outgoing PIN has run its secure-window cleanup.
        awaitPinDestroyed(lifetime)
        assertSecure()
    }

    private class PinLifetime(
        val fragment: PincodeFragment,
        val fragmentLifecycle: Lifecycle,
        val viewLifecycle: Lifecycle
    )

    private fun capturePinLifetime(pin: PincodeFragment): PinLifetime = onMain {
        check(pin.lifecycle.currentState == Lifecycle.State.RESUMED)
        check(pin.view != null)
        PinLifetime(pin, pin.lifecycle, pin.viewLifecycleOwner.lifecycle)
    }

    private fun awaitPinDestroyed(lifetime: PinLifetime) {
        // Permanent removal resets Fragment.lifecycle to a new INITIALIZED registry.
        // Observe the registries that owned the actual resumed PIN and view instead.
        compose.waitUntil(15_000) { onMain {
            lifetime.fragment.view == null &&
                lifetime.fragmentLifecycle.currentState == Lifecycle.State.DESTROYED &&
                lifetime.viewLifecycle.currentState == Lifecycle.State.DESTROYED
        } }
        pinDestructions += onMain {
            JSONObject().put("capturedFragmentLifecycle", lifetime.fragmentLifecycle.currentState.name)
                .put("capturedViewLifecycle", lifetime.viewLifecycle.currentState.name)
                .put("viewGone", lifetime.fragment.view == null)
                .put("fragmentRegistryReplaced", lifetime.fragment.lifecycle !== lifetime.fragmentLifecycle)
                .put("currentFragmentLifecycle", lifetime.fragment.lifecycle.currentState.name)
        }
    }

    private fun enterPin(value: String) {
        val pin = awaitFragment<PincodeFragment>()
        val controller = onMain { NavHostFragment.findNavController(pin) }
        val pinEntry = onMain { checkNotNull(controller.currentBackStackEntry).also {
            check(it.destination.id == R.id.pincodeFragment)
            check(controller.previousBackStackEntry === expectedSavedEntry)
        } }
        // Wait for both actual navigation readiness and asynchronous startAuth Checking.
        compose.waitUntil(15_000) { onMain {
            currentFragment() === pin && pin.lifecycle.currentState == Lifecycle.State.RESUMED &&
                controller.currentBackStackEntry === pinEntry &&
                controller.previousBackStackEntry === expectedSavedEntry &&
                pinEntry.lifecycle.currentState == Lifecycle.State.RESUMED &&
                !pin.viewModel.recoveryState.value!!.visible
        } }
        onMain {
            check(currentFragment() === pin && pin.lifecycle.currentState == Lifecycle.State.RESUMED)
            check(controller.currentBackStackEntry === pinEntry)
            check(controller.previousBackStackEntry === expectedSavedEntry)
            check(pinEntry.lifecycle.currentState == Lifecycle.State.RESUMED)
            check(!pin.viewModel.recoveryState.value!!.visible)
            pinInputReadiness += JSONObject().put("samePinEntry", true).put("sameSavedEntry", true)
                .put("fragmentLifecycle", pin.lifecycle.currentState.name)
                .put("entryLifecycle", pinEntry.lifecycle.currentState.name).put("checking", true)
            value.forEach { digit ->
                val id = context.resources.getIdentifier("btn$digit", "id", context.packageName)
                check(id != 0)
                val button = pin.requireView().findViewById<View>(id)
                check(button.isShown && button.isEnabled && button.performClick())
            }
        }
    }

    private fun assertSecure() = onMain {
        assertTrue(root().window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
    }

    private inline fun <reified T : Fragment> awaitFragment(): T {
        compose.waitUntil(30_000) { onMain {
            val current = currentFragment()
            current is T && current.lifecycle.currentState == Lifecycle.State.RESUMED
        } }
        return onMain { currentFragment() as T }
    }

    private fun currentFragment(): Fragment? = resumedRoot()?.let { activity ->
        (activity.supportFragmentManager.findFragmentById(R.id.navHost) as? NavHostFragment)
            ?.childFragmentManager?.primaryNavigationFragment
    }
    private fun resumedRoot(): WalletRootActivity? = ActivityLifecycleMonitorRegistry.getInstance()
        .getActivitiesInStage(Stage.RESUMED).filterIsInstance<WalletRootActivity>().singleOrNull()
    private fun root(): WalletRootActivity = checkNotNull(resumedRoot())
    private fun text(id: Int, vararg args: Any): String = onMain { root().getString(id, *args) }
    private fun <T> onMain(action: () -> T): T {
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(action) }
        return checkNotNull(result).getOrThrow()
    }
    private fun scalar(sql: String): Long = database.query(sql).use { check(it.moveToFirst()); it.getLong(0) }

    private companion object {
        private val CONTINUATION_DIAGNOSTIC = mapOf(
            26 to "endpoint-attachment-diagnostic-v1-97c4416252914a2086851bdb1b6de1c9.json",
            34 to "endpoint-attachment-diagnostic-v1-0d0349e80b5545ba92f9d51532ab0e4a.json",
            36 to "endpoint-attachment-diagnostic-v1-d513b111c7d54d039baf70a6920bc852.json"
        )
        private val CONTINUATION_PROOFS = mapOf(
            26 to mapOf(
                "endpoint-attachment-auth-v1-after-startup-5d1655f1583342f08d3fe88d491fb034.json" to "229774ac7ece3c2bc72818e41cec716bc0c247bd6c8f826ce9e85238fd25b404",
                "endpoint-attachment-auth-v1-before-startup-5d1655f1583342f08d3fe88d491fb034.json" to "fccc6c8cc9ee283b064a03c15c9769be5d9222ac552f03c9002e5677a95490af",
                "endpoint-attachment-auth-v1-correct-pin-restored-5d1655f1583342f08d3fe88d491fb034.json" to "80fdaf89c9577b9b34ba612b044b810cc8ec72ad4f2f0e2899410ac570324097",
                "endpoint-attachment-auth-v1-saved-owned-5d1655f1583342f08d3fe88d491fb034.json" to "f3aeb83416498857c704f4f3197d1c22562804b8e47d30f1a19b357130694776",
                "endpoint-attachment-auth-v1-wrong-pin-denied-5d1655f1583342f08d3fe88d491fb034.json" to "9ebef52670b3c3af9f33b6c2df4a6801950ab79fcf9be13105dfcee85e3bee77",
                "endpoint-attachment-diagnostic-v1-97c4416252914a2086851bdb1b6de1c9.json" to "cf2318f232d33eacaf1a3f67e51cb9851ef82c84df84529fdc893cca0b071e7b"
            ),
            34 to mapOf(
                "endpoint-attachment-auth-v1-after-startup-b7281ff047594dbf84eee31063307017.json" to "935a20e96a26fa2dff881ead34a58d6cc158f34a46f98ac1d60f6ab714c8628e",
                "endpoint-attachment-auth-v1-before-startup-b7281ff047594dbf84eee31063307017.json" to "626ab797b080278b72cb7f0a46f61559e466dbd9b304200b23fbfdda4f6102e4",
                "endpoint-attachment-auth-v1-correct-pin-restored-b7281ff047594dbf84eee31063307017.json" to "46e03a6c1f26725060e13e213312a52733fe55f2a9ee2c49825c56bbb62ae548",
                "endpoint-attachment-auth-v1-saved-owned-b7281ff047594dbf84eee31063307017.json" to "3d5b0d0acea540ab4accbdae1f87a64b50c56a28701e0320f8a13388b7c30c6f",
                "endpoint-attachment-auth-v1-wrong-pin-denied-b7281ff047594dbf84eee31063307017.json" to "25a161b4f11505b670811c5485878f385ce9b3cb690ccef99e32cdeae20bedf2",
                "endpoint-attachment-diagnostic-v1-0d0349e80b5545ba92f9d51532ab0e4a.json" to "5764ba8657080fcf7509971425a3685c9799839b8337405881a41019628ee3f6"
            ),
            36 to mapOf(
                "endpoint-attachment-auth-v1-after-startup-c5f6804213ee4e6196c86fa087f813cd.json" to "69c8a013c4ff01ab18b81ca2dca70fdefc68c757d3df5259ab8d5fd36769f912",
                "endpoint-attachment-auth-v1-before-startup-c5f6804213ee4e6196c86fa087f813cd.json" to "401e68c768d383deb572727d94815a0da20dcb19ea4e8ba5836c99a68e917ce6",
                "endpoint-attachment-auth-v1-correct-pin-restored-c5f6804213ee4e6196c86fa087f813cd.json" to "cf95f99142a1faeb5e462e0f0847d7dc07046909d431797623ee79fb1ed7f79e",
                "endpoint-attachment-auth-v1-saved-owned-c5f6804213ee4e6196c86fa087f813cd.json" to "2fa0daa5f43801df01398f73b5f614e286fb2b6ffbd4d1958a3f8f8348db3f0a",
                "endpoint-attachment-auth-v1-wrong-pin-denied-c5f6804213ee4e6196c86fa087f813cd.json" to "bd91e480abc450d5d053515d3b378f7449e143eb7e851f9237a31d4e98cf5a89",
                "endpoint-attachment-diagnostic-v1-d513b111c7d54d039baf70a6920bc852.json" to "dc0fda24232b56e91a30891edd4d97551619d440ad96a4411ff014bd214db67c"
            )
        )

        val PROFILES = mapOf(
            36 to Triple("emulator-5748", "FearlessGlobalEndpointApi36_20261020q3", "02d404a37f81495a987dd8c00d918ffa"),
            34 to Triple("emulator-5742", "FearlessGlobalEndpointApi34_20261020", "0b20c87948f84c588daf2d56815f77c9"),
            26 to Triple("emulator-5752", "FearlessGlobalEndpointApi26_20261020q3", "d3a8b1ca1c6842018d887f0764cdc2af")
        )
        const val MARKER = "global_endpoint_auth_fixture_v1"
        const val FIXTURE = "global-endpoint-auth-public-v1"
        const val ROW_ID = -9003L
        const val INITIAL_REVISION = 8L
        const val PUBLIC_PIN = "123456"
        const val PUBLIC_NAME = "Retained endpoint 雪"
        const val PUBLIC_ENDPOINT = "wss://saved-endpoint.invalid/?token=PUBLIC_FIXTURE_ONLY"
        const val CHAIN_REFERENCE = "unattached-public-chain-reference"
        val BASELINE_PROOF = mapOf(
            26 to "endpoint-decision-auth-v3-after-recreation-and-reread-a34d53124ea84e85973c550dede797d8.json",
            34 to "endpoint-decision-auth-v3-after-recreation-and-reread-863b4a57178149f29ab5f319fd8efcad.json",
            36 to "endpoint-decision-auth-v3-after-recreation-and-reread-4f283b5f9a26417488d04be93f6a35c8.json"
        )
        val PRIOR_PROOFS = mapOf(
            26 to mapOf(
                "endpoint-decision-auth-v3-after-background-return-a34d53124ea84e85973c550dede797d8.json" to "05eaa37365a5e7eed85340ada6bb0fa521be32c67184700fff0c9aa143b9a79e",
                "endpoint-decision-auth-v3-after-recreation-and-reread-a34d53124ea84e85973c550dede797d8.json" to "db17107d18d6d030a90af93e94fdc325900d287560b35ad6bfc9054657abc111",
                "endpoint-decision-auth-v3-after-startup-a34d53124ea84e85973c550dede797d8.json" to "449a18d3babf85cbf022e11bb2b985ff0de5527ea41278707917997d0d837463",
                "endpoint-decision-auth-v3-before-startup-a34d53124ea84e85973c550dede797d8.json" to "9f363d936f583d65cc90cf6cc9519ebf6948e0e15391acc7c52f78cabfcefa29",
                "endpoint-decision-auth-v3-correct-pin-restored-a34d53124ea84e85973c550dede797d8.json" to "c39ec19681768b58e4eb6bb3c34c39b34effbec79e699eecaa1bb9a9e24a3ba6",
                "endpoint-decision-auth-v3-proof-a34d53124ea84e85973c550dede797d8.json" to "12693f355a7cdb4b169a05c7a9223561f97e3bb9e2bcb335cb644ecc2d461913",
                "endpoint-decision-auth-v3-saved-current-a34d53124ea84e85973c550dede797d8.json" to "a24573f3284f1a14c3c1ba01f2aa1a1b7dea57d3e71f44c78b7044b02bd20dbe",
                "endpoint-decision-auth-v3-saved-historical-a34d53124ea84e85973c550dede797d8.json" to "73fb1dabfa1e34e943e65b95f0df5cfc44b50d82ddecac2d8684f1ba70488640",
                "endpoint-decision-auth-v3-saved-legacy-a34d53124ea84e85973c550dede797d8.json" to "eae9de85ff97bf9645bb4b02db5110b9dff14b475291854aadb116de5c0995d6",
                "endpoint-decision-auth-v3-saved-owned-a34d53124ea84e85973c550dede797d8.json" to "7b3805dfe8c2867794f7f6afdce9775c12fc7acbad550124240e33c68d5765ee",
                "endpoint-decision-auth-v3-saved-upgrade_entry-a34d53124ea84e85973c550dede797d8.json" to "d0b266daa55e02c337e1da0ae4e4f4676e3381c2eaf15f4193cfb838268a2081",
                "endpoint-decision-auth-v3-wrong-pin-denied-a34d53124ea84e85973c550dede797d8.json" to "a79b1898851547edfd51254bc9669fd38069149ca2edffe12f490df94d80bc03",
                "global-endpoint-auth-after-d3a8b1ca1c6842018d887f0764cdc2af.json" to "e453c3013894ed3252632876549e9d420842a2b69c31a7f571eebbafe4506620",
                "global-endpoint-auth-before-d3a8b1ca1c6842018d887f0764cdc2af.json" to "ce7ea63b0f0f0be9c0a9cf53ffe3b5f49eb06a9de42240f26cb1649d73c078d7",
                "global-endpoint-auth-mutation-d3a8b1ca1c6842018d887f0764cdc2af.json" to "f150e942a0fd64ef9f27c6c3552d87ef6c7cef9f7dbde715d10089d68c2fad0b",
                "global-endpoint-auth-proof-d3a8b1ca1c6842018d887f0764cdc2af.json" to "9a4546b7cd624538da4fde456c4f6ede72e75e877399166b1bf1f7413c3af1ec",
                "global-endpoint-auth-startup-d3a8b1ca1c6842018d887f0764cdc2af.json" to "1816f03c26b1944a0fec644548f68e3af61a5d93ec7ee49545f97ee93eaa70df"
            ),
            34 to mapOf(
                "endpoint-decision-auth-v3-after-background-return-863b4a57178149f29ab5f319fd8efcad.json" to "24b127732d34bd4be81ee8f344db4a7e7f53bcd6badf86969b25074b09321219",
                "endpoint-decision-auth-v3-after-recreation-and-reread-863b4a57178149f29ab5f319fd8efcad.json" to "7b8c92a6ed12c9ee61edace165d99548964d93d0a6014397765283500b76e8bf",
                "endpoint-decision-auth-v3-after-startup-863b4a57178149f29ab5f319fd8efcad.json" to "a91fbf522ef46d017ebf53c116ba2758adae8693191105d85429e0f0ad5e2faa",
                "endpoint-decision-auth-v3-before-startup-863b4a57178149f29ab5f319fd8efcad.json" to "5195e9b6d25dccbfd5badf07ddf2f13d48f6d5776c13f5b226787d882bd80dde",
                "endpoint-decision-auth-v3-correct-pin-restored-863b4a57178149f29ab5f319fd8efcad.json" to "06bc789a779b0881ca3a01b37143daa18390b241d8faed98dc673f8a6d0c820b",
                "endpoint-decision-auth-v3-proof-863b4a57178149f29ab5f319fd8efcad.json" to "f3c8db23ac2ecefee3b03894e3412b3e50ddb39a85ad1def162ba89e73c188b6",
                "endpoint-decision-auth-v3-saved-current-863b4a57178149f29ab5f319fd8efcad.json" to "31ac0e62930ab0a81872e17e707c33fa3c1472104d2d16d7f1f5eff068e32aa5",
                "endpoint-decision-auth-v3-saved-historical-863b4a57178149f29ab5f319fd8efcad.json" to "78ff39f5ce57b21a2b8277a9cd8b9353f9f10011a6e31e6484699dbab7f30637",
                "endpoint-decision-auth-v3-saved-legacy-863b4a57178149f29ab5f319fd8efcad.json" to "dffd3874fcedbc81d9bae71a8412acc43f051cb0788add82bcbc66fd16f3f0f6",
                "endpoint-decision-auth-v3-saved-owned-863b4a57178149f29ab5f319fd8efcad.json" to "6f5610648cab642c1125eb53261126def7962672f0b2c5c8937a954460e0d64d",
                "endpoint-decision-auth-v3-saved-upgrade_entry-863b4a57178149f29ab5f319fd8efcad.json" to "8ba918eb5c1dfed3d38d778b95002eb7cb265b17a63a0012e70e2e8a386fec95",
                "endpoint-decision-auth-v3-wrong-pin-denied-863b4a57178149f29ab5f319fd8efcad.json" to "c40993dd4280356ad05f70bc3391d16b1071b2468a2a0196eba0905d2eb440d0",
                "global-endpoint-auth-after-0b20c87948f84c588daf2d56815f77c9.json" to "8275acf9cd625b370261b5d41770a1260129646e602427bbc45e830b90143f9a",
                "global-endpoint-auth-before-0b20c87948f84c588daf2d56815f77c9.json" to "fce20bf7dd0f6f0d7eeb9c996430969c3ce7544e6a7407326cf977407d267fe8",
                "global-endpoint-auth-mutation-0b20c87948f84c588daf2d56815f77c9.json" to "0ea43b2fe7f2cb4a0a62c59e652ab24379d4147d7594439e9c51417a9e3b3204",
                "global-endpoint-auth-proof-0b20c87948f84c588daf2d56815f77c9.json" to "9141b08365a13ebd1a5eccf0442e6c20c9f995ae4de11ce63dbdd09009575e14",
                "global-endpoint-auth-startup-0b20c87948f84c588daf2d56815f77c9.json" to "a0387f9b2fb2c3dabbc271a9456a95cfb1106a81f30a9a1c26a9e516e88668a5"
            ),
            36 to mapOf(
                "endpoint-decision-auth-v1-after-actual-upgrade-c923620f6fe5493b9c5117a5fe827622.json" to "a408941b41f8f6e540a20767306ed4a02ab12ecca59efc684a35a4913acccec3",
                "endpoint-decision-auth-v1-before-actual-upgrade-c923620f6fe5493b9c5117a5fe827622.json" to "485872df90e56eacdb94a1edc36f01109241c077de89ef0ad72ced3b7531b0c1",
                "endpoint-decision-auth-v1-wrong-pin-denied-c923620f6fe5493b9c5117a5fe827622.json" to "fcd1e5904dfe368c52fb6b8b2889e5483c60c04bc2c99283bbdd2511dd970a9a",
                "endpoint-decision-auth-v2-after-startup-a63d12b3e64847e987fb8b10372b7045.json" to "ebd6c60d35f05eef49016027a414879c793d671abe9e5e6624a1764bc1dd6148",
                "endpoint-decision-auth-v2-before-startup-a63d12b3e64847e987fb8b10372b7045.json" to "b229d8518709f86b10b5800b66c34b647792b3e4abb6a58d1925a9e60cb3c4dc",
                "endpoint-decision-auth-v2-correct-pin-restored-a63d12b3e64847e987fb8b10372b7045.json" to "43d74b81d96a96d5a6a8d9390b660d6a45cd5fad7b913b20afdc710982142924",
                "endpoint-decision-auth-v2-saved-current-a63d12b3e64847e987fb8b10372b7045.json" to "19e6683bed472498be6e05d25126f8539662be21ae794acef81a5999ff488f87",
                "endpoint-decision-auth-v2-saved-historical-a63d12b3e64847e987fb8b10372b7045.json" to "8d907447c77e1d71bde279475e5a57de6de6296d9a0f1baca656399b1feb4471",
                "endpoint-decision-auth-v2-saved-legacy-a63d12b3e64847e987fb8b10372b7045.json" to "aa8697d91b361c02bff6930ddb1a7193bf3a5a7321f28fb7ae0e0a6b20d9025f",
                "endpoint-decision-auth-v2-saved-owned-a63d12b3e64847e987fb8b10372b7045.json" to "70c4b143e76272cf562c120eed5d2d810012f7a5ddefb7b473d0707ed971cf39",
                "endpoint-decision-auth-v2-saved-upgrade_entry-a63d12b3e64847e987fb8b10372b7045.json" to "51f21ec8f0e82437810c89ac3939bb2bd49fee279d3b7f041a1c30cddf96ce32",
                "endpoint-decision-auth-v2-wrong-pin-denied-a63d12b3e64847e987fb8b10372b7045.json" to "c9c15b9d0f77dd0869b742c681f542904bfc8ed76096664ec6b259b84bfb9c8c",
                "endpoint-decision-auth-v3-after-background-return-4f283b5f9a26417488d04be93f6a35c8.json" to "62133837a7ff6f3133c1935110f9140bdd1c9ec3206e6dbc2db1fa704ddadffd",
                "endpoint-decision-auth-v3-after-recreation-and-reread-4f283b5f9a26417488d04be93f6a35c8.json" to "5cb62e9ee21950506dc66b73956544b009d830ad0493dcafdb77353ad453723a",
                "endpoint-decision-auth-v3-after-startup-4f283b5f9a26417488d04be93f6a35c8.json" to "0eaba9fbe08a0952853829b81402b3c4ed5eb4c0f7a6c1d943f4d12b2542ffaf",
                "endpoint-decision-auth-v3-before-startup-4f283b5f9a26417488d04be93f6a35c8.json" to "023d38cc1a83c665033b5cdb01deaee49ee3f9adfef09b002dbf0a01502293a2",
                "endpoint-decision-auth-v3-correct-pin-restored-4f283b5f9a26417488d04be93f6a35c8.json" to "e339d1066adc7d969f6db2e503053fb289c0f7e39046ed1d02d01112f88b495f",
                "endpoint-decision-auth-v3-proof-4f283b5f9a26417488d04be93f6a35c8.json" to "1e60c10b046d44ffd970f1b196039bbd217bc83ba340652830440332710581bd",
                "endpoint-decision-auth-v3-saved-current-4f283b5f9a26417488d04be93f6a35c8.json" to "7595936e42a60291b342d0fe24caa95b5a80ce9f06743620f731b23bb0d318e7",
                "endpoint-decision-auth-v3-saved-historical-4f283b5f9a26417488d04be93f6a35c8.json" to "b038bb4c3062f81ba6a4cfaec8acc4c15101a01afb21f4eda38dfb07ed398c9c",
                "endpoint-decision-auth-v3-saved-legacy-4f283b5f9a26417488d04be93f6a35c8.json" to "32f94063ab93a2d9ed0ef647a287155b0d8837684d55264433b8d3659d313033",
                "endpoint-decision-auth-v3-saved-owned-4f283b5f9a26417488d04be93f6a35c8.json" to "23fd982ff5fb689e5d37561de977afa75d8df52f2fcf0a5399c7ce8ed28a631a",
                "endpoint-decision-auth-v3-saved-upgrade_entry-4f283b5f9a26417488d04be93f6a35c8.json" to "d193ac99ba9cbdcaf9bafb93fcbbabf8205bee3c1c20b049839d4d75595f161d",
                "endpoint-decision-auth-v3-wrong-pin-denied-4f283b5f9a26417488d04be93f6a35c8.json" to "9446e7b6f8b8bd23037c1e0a463668d1c91885e9cc5897cb8c9dad73fd93f3ed",
                "global-endpoint-auth-after-02d404a37f81495a987dd8c00d918ffa.json" to "33017f8f2f7ac747254c1925bc0d008a6d5e0982de9b8fa0b9d9e156ea7e8fd9",
                "global-endpoint-auth-before-02d404a37f81495a987dd8c00d918ffa.json" to "38d7479109b918562717e3ad55de5537580e2d537840b2e79e67bc68d9dbef13",
                "global-endpoint-auth-mutation-02d404a37f81495a987dd8c00d918ffa.json" to "49b8751338d524c4ced5a5df3d432de89f66bbb699508a260978e51f91dd020b",
                "global-endpoint-auth-proof-02d404a37f81495a987dd8c00d918ffa.json" to "0cd3c9b3b32669120034903542f88fb6ce5cee05a1aefa745efee2236fd88968",
                "global-endpoint-auth-startup-02d404a37f81495a987dd8c00d918ffa.json" to "4655c633d5ba528eadff208d20a3980925f3d0d6692a25941376a2e4098a3a4e"
            )
        )
    }

    /** No DNS, fixed port, shared service or production probe replacement. */
    private class LoopbackGenesisPeer(source: EndpointInventorySource) : AutoCloseable {
        private val server = ServerSocket(0, 4, InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)))
        private val path = "/${source.name.lowercase(Locale.ROOT)}"
        val url = "http://127.0.0.1:${server.localPort}$path"
        val mode = AtomicReference("MATCH")
        val requests = AtomicInteger()
        val stalled = CountDownLatch(1)
        val release = CountDownLatch(1)
        val stallCompleted = CountDownLatch(1)
        private val failure = AtomicReference<Throwable?>()
        @Volatile private var closing = false
        @Volatile private var socket: Socket? = null
        private val thread = Thread {
            try {
                while (!closing) server.accept().use { accepted ->
                    socket = accepted; accepted.soTimeout = 5_000
                    val input = accepted.getInputStream()
                    val header = ByteArrayOutputStream()
                    while (true) {
                        check(header.size() < 4096)
                        val byte = input.read(); check(byte >= 0); header.write(byte)
                        if (header.toString("US-ASCII").endsWith("\r\n\r\n")) break
                    }
                    val lines = header.toString("US-ASCII").split("\r\n")
                    check(lines.first() == "POST $path HTTP/1.1")
                    val fields = lines.drop(1).filter { it.isNotEmpty() }.map { it.split(":", limit = 2) }
                    check(fields.all { it.size == 2 })
                    val headers = fields.associate { it[0].lowercase(Locale.ROOT) to it[1].trim() }
                    check(headers.size == fields.size)
                    check(headers.keys.none { it in setOf("authorization", "cookie", "proxy-authorization") })
                    val size = checkNotNull(headers["content-length"]).toInt(); check(size in 1..1024)
                    val bytes = ByteArray(size)
                    for (i in bytes.indices) { val byte = input.read(); check(byte >= 0); bytes[i] = byte.toByte() }
                    check(bytes.toString(Charsets.UTF_8) == """{"jsonrpc":"2.0","id":1,"method":"getGenesisHash"}""")
                    requests.incrementAndGet()
                    val selected = mode.get()
                    if (selected == "STALL") {
                        stalled.countDown()
                        check(release.await(20, TimeUnit.SECONDS))
                    }
                    val genesis = if (selected == "MISMATCH") "11111111111111111111111111111111"
                        else "5eykt4UsFv8P8NJdTREpY1vzqKqZKvdpKuc147dw2N9d"
                    val body = """{"jsonrpc":"2.0","id":1,"result":"$genesis"}""".toByteArray()
                    try {
                        accepted.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n").toByteArray())
                        accepted.getOutputStream().write(body); accepted.getOutputStream().flush()
                    } catch (error: IOException) { if (selected != "STALL") throw error }
                    finally { if (selected == "STALL") stallCompleted.countDown() }
                    socket = null
                }
            } catch (error: Throwable) { if (!closing || error !is IOException) failure.set(error) }
        }.apply { isDaemon = true; start() }
        override fun close() {
            closing = true; release.countDown(); server.close(); socket?.close(); thread.join(5_000)
            assertFalse("Loopback peer did not stop", thread.isAlive)
            failure.get()?.let { throw AssertionError("Loopback peer failed", it) }
        }
    }
}
