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
 * Actual retained startup and offline decisions; API26/34 upgrade82, API36 continues its admitted83 five-decision pilot.
 * No reseed, direct decision write, authorization injection, network probe or node selection.
 * Host tooling must independently admit old app/test APKs, UID, all raw state and the five proofs.
 */
@RunWith(AndroidJUnit4::class)
class SavedEndpointDecisionAuthenticationInstrumentedTest {
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
    private lateinit var startMode: String
    private lateinit var baselineSql82: JSONObject
    private lateinit var baselinePreferences: JSONObject
    private val initialFailedAttempts = 0
    private val continuingSavedDecisions get() = startMode == "retained83-after-five-decisions"
    private lateinit var retainedSql83: JSONObject
    private val revision = 8L

    @Test
    fun realPinSavesFiveOfflineDecisionsAfterRetainedStartup() {
        admitRetainedFixture()
        launch()
        awaitFragment<WelcomeFragment>()
        val startupInitializer = awaitInitializerAndContinue("initial", null)
        // No graph/database resolution before actual WalletStartupActivity opens the admitted retained database.
        graph = EntryPointAccessors.fromApplication(context.applicationContext, SavedEndpointAuthFixtureEntryPoint::class.java)
        database = graph.database().openHelper.writableDatabase
        val after83 = captureSnapshot("after-startup")
        var expectedSql = retainedSql83
        assertPersistentEquals(expectedSql, after83, initialFailedAttempts, exactPreferenceBytes = !continuingSavedDecisions)
        assertEquals(PinHealth.HEALTHY, runBlocking(Dispatchers.IO) { graph.accountRepository().getPinHealth() })
        assertEquals(PUBLIC_PIN, runBlocking(Dispatchers.IO) { graph.accountRepository().getPinCode() })

        val title = text(R.string.saved_endpoints_title)
        compose.waitUntil(15_000) { compose.onAllNodesWithText(title).fetchSemanticsNodes().size == 1 }
        compose.onNodeWithText(title).performScrollTo().assertIsDisplayed().performClick()
        val first = awaitFragment<SavedEndpointsFragment>()
        assertLocked(first); assertSecure()
        unlock()
        val wrongPin = awaitFragment<PincodeFragment>()
        val wrongLifetime = capturePinLifetime(wrongPin)
        var mismatch = 0
        onMain { wrongPin.viewModel.matchingPincodeErrorEvent.observe(wrongPin) { mismatch++ } }
        enterPin("654321")
        compose.waitUntil(15_000) { onMain { mismatch == 1 } }
        assertSame(wrongPin, onMain { currentFragment() }); assertDataCleared(first); assertSecure()
        onMain { root().onBackPressedDispatcher.onBackPressed() }
        awaitPinDestroyed(wrongLifetime); assertLocked(awaitFragment()); assertSecure()
        assertPersistentEquals(expectedSql, captureSnapshot("wrong-pin-denied"), initialFailedAttempts + 1)

        unlockWithPublicPin()
        assertPersistentEquals(expectedSql, captureSnapshot("correct-pin-restored"))
        EndpointInventorySource.values().forEach { source ->
            selectSource(source)
            openForm(source, existing = continuingSavedDecisions)
            if (!continuingSavedDecisions) {
                replaceField("saved_endpoint_choice_name", candidateName(source))
                replaceField("saved_endpoint_choice_url", candidateUrl(source))
                val option = onMain { currentForm().targets!!.options.single { it.target?.chainId == UniversalWalletRegistry.solanaMainnet.id } }
                val target = checkNotNull(option.target)
                assertEquals(EndpointDecisionTarget.Family.SOLANA, target.family)
                assertEquals(UniversalWalletRegistry.solanaMainnet.genesisHash, target.expectedGenesis)
                clickTag("saved_endpoint_choice_target_${option.rowId}")
                onMain {
                    val form = currentForm()
                    assertSame(target, form.target); assertFalse(form.choosingTarget)
                    assertEquals(candidateName(source), form.name); assertEquals(candidateUrl(source), form.url)
                }
                clickTag("saved_endpoint_choice_save")
                compose.waitUntil(15_000) { onMain { currentForm().let { !it.saving && (it.saved || it.saveFailed) } } }
                onMain { assertTrue(currentForm().saved); assertFalse(currentForm().saveFailed) }
                scrollTag("saved_endpoint_choice_saved")
                compose.onNodeWithTag("saved_endpoint_choice_saved").assertIsDisplayed()
                expectedSql = SavedEndpointDecisionRawSnapshot.inserted(expectedSql, source, candidateName(source), candidateUrl(source))
            }
            val target = onMain { checkNotNull(currentForm().target) }
            assertEquals(UniversalWalletRegistry.solanaMainnet.id, target.chainId)
            assertEquals(EndpointDecisionTarget.Family.SOLANA, target.family)
            assertEquals(UniversalWalletRegistry.solanaMainnet.genesisHash, target.expectedGenesis)
            assertPersistentEquals(expectedSql, captureSnapshot("saved-${source.name.lowercase(Locale.ROOT)}"))
            savedDecisions += JSONObject().put("source", source.name).put("rowId", ref(source).rowId)
                .put("ownedRevision", ref(source).revision ?: JSONObject.NULL)
                .put("candidateName", candidateName(source)).put("candidateUrl", candidateUrl(source))
                .put("actualDisplayedSave", !continuingSavedDecisions).put("actualDisplayedTargetChoice", !continuingSavedDecisions)
                .put("priorReviewedSaveRetained", continuingSavedDecisions)
                .put("targetChainId", target.chainId).put("targetFamily", target.family.name)
                .put("state", "PENDING").put("decisionRevision", 1)
            click(R.string.saved_endpoints_part_return)
            assertSource(source)
        }

        // Leave real unsaved text in the rendered form before losing the lease.
        openForm(EndpointInventorySource.OWNED, existing = true)
        replaceField("saved_endpoint_choice_name", "Unsaved public background draft")
        val backgrounded = awaitFragment<SavedEndpointsFragment>()
        val taskId = onMain { root().taskId }
        context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        compose.waitUntil(15_000) { onMain {
            ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).none { it is WalletRootActivity }
        } }
        assertDataCleared(backgrounded)
        context.startActivity(Intent(context, WalletRootActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        val returned = awaitFragment<SavedEndpointsFragment>()
        assertEquals(taskId, onMain { root().taskId })
        assertLocked(returned); assertSecure(); unlockWithPublicPin()
        assertPersistentEquals(expectedSql, captureSnapshot("after-background-return"))
        selectSource(EndpointInventorySource.OWNED)
        openForm(EndpointInventorySource.OWNED, existing = true)
        replaceField("saved_endpoint_choice_name", "Unsaved public recreation draft")
        val recreatedFrom = awaitFragment<SavedEndpointsFragment>()
        val oldActivity = onMain { root() }
        onMain { oldActivity.recreate() }
        compose.waitUntil(30_000) { onMain {
            resumedRoot()?.let { it !== oldActivity && currentFragment() is SavedEndpointsFragment } == true
        } }
        awaitInitializerAndContinue("after-recreation", startupInitializer)
        assertLocked(awaitFragment()); onMain { assertTrue(oldActivity.isDestroyed) }
        assertDataCleared(recreatedFrom); assertSecure(); unlockWithPublicPin()
        EndpointInventorySource.values().forEach { source ->
            selectSource(source); openForm(source, existing = true)
            click(R.string.saved_endpoints_part_return); assertSource(source)
        }
        assertPersistentEquals(expectedSql, captureSnapshot("after-recreation-and-reread"))
        requirePriorProofsExact()
        assertEquals(4, pinDestructions.size); assertEquals(4, pinInputReadiness.size)
        assertEquals(5, savedDecisions.size)
        assertEquals(listOf("initial", "after-recreation"), initializerObservations.map { it.getString("phase") })
        val completions = initializerObservations.count { it.getString("branch") != "RETAINED" }
        assertTrue(completions in 1..2)
        assertEquals(completions, initializerObservations.count { it.getBoolean("continueClicked") })
        writeProof("proof", JSONObject().put("formatVersion", 2).put("api", Build.VERSION.SDK_INT)
            .put("uid", Process.myUid()).put("nonce", nonce).put("retainedNonce", retainedNonce)
            .put("sourceCommit", sourceCommit).put("serial", serial).put("avd", avd)
            .put("startMode", startMode).put("initialFailedAttempts", initialFailedAttempts)
            .put("actual82To83Upgrade", startMode == "retained82").put("continuedAdmitted83Pilot", continuingSavedDecisions).put("priorFiveDecisionsPreservedWithoutResave", continuingSavedDecisions).put("realKeypadPin", true).put("wrongPinDenied", true)
            .put("allFiveOfflineDecisions", true).put("allPending", true).put("operationalProjectionUnchanged", true)
            .put("allPriorPersistentSqlExact", true).put("allPreferenceValuesRestored", true).put("onlyExpectedTransientPinCounter", true)
            .put("otherPreferenceFilesByteExact", true).put("allRawPreferenceSnapshotsRetained", true).put("priorFiveProofsExact", true)
            .put("backgroundRelockedAndDraftCleared", true).put("recreationRelockedAndDraftCleared", true)
            .put("networkVerificationQualified", false).put("activationQualified", false).put("processDeathQualified", false)
            .put("pinDestructions", JSONArray(pinDestructions)).put("pinInputReadiness", JSONArray(pinInputReadiness))
            .put("initializerObservations", JSONArray(initializerObservations)).put("sources", JSONArray(sourceObservations))
            .put("decisions", JSONArray(savedDecisions)).put("snapshots", JSONArray(snapshotFiles)))
        onMain { root().finish() }
    }

    private fun admitRetainedFixture(): JSONObject = runBlocking(Dispatchers.IO) {
        val args = InstrumentationRegistry.getArguments()
        val profile = checkNotNull(PROFILES[Build.VERSION.SDK_INT])
        serial = profile.first; avd = profile.second; retainedNonce = profile.third
        check(context.packageName == "jp.co.soramitsu.fearless.debug")
        check(args.getString("fearlessDecisionOwnedUid") == "retained-qualified-public-pin-fixture")
        check(args.getString("fearlessDecisionSerial") == serial && args.getString("fearlessDecisionAvd") == avd)
        check(args.getString("fearlessDecisionRetainedNonce") == retainedNonce)
        check(args.getString("fearlessDecisionUid") == Process.myUid().toString())
        check(Process.myUid() == context.applicationInfo.uid)
        nonce = checkNotNull(args.getString("fearlessDecisionRunNonce")).also {
            check(it.matches(Regex("[0-9a-f]{32}")) && it != retainedNonce)
        }
        sourceCommit = checkNotNull(args.getString("fearlessDecisionSourceCommit")).also { check(it.matches(Regex("[0-9a-f]{40}"))) }
        startMode = checkNotNull(args.getString("fearlessDecisionStartMode"))
        check(startMode == if (Build.VERSION.SDK_INT == 36) "retained83-after-five-decisions" else "retained82")
        val expectedPrior = if (continuingSavedDecisions) FAILED_PILOT_PROOFS.keys else emptySet()
        check(context.cacheDir.listFiles().orEmpty().filter { it.name.startsWith("endpoint-decision-auth-") }.map { it.name }.toSet() == expectedPrior)
        check(nonce != FAILED_PILOT_NONCE && nonce != SAVED_PILOT_NONCE)
        requirePriorProofsExact()
        val marker = context.getSharedPreferences(MARKER, Context.MODE_PRIVATE)
        check(marker.getString("fixture", null) == FIXTURE && marker.getString("nonce", null) == retainedNonce)
        check(marker.getInt("uid", -1) == Process.myUid() && marker.getString("serial", null) == serial && marker.getString("avd", null) == avd)
        val oldAfter = JSONObject(File(context.cacheDir, "global-endpoint-auth-after-$retainedNonce.json").readText())
        check(oldAfter.getInt("api") == Build.VERSION.SDK_INT && oldAfter.getInt("uid") == Process.myUid())
        val path = context.getDatabasePath("app.db")
        check(path.isFile && path.length() in 1..67_108_864)
        val sql = SQLiteDatabase.openDatabase(path.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            check(db.version == if (continuingSavedDecisions) 83 else 82) { "Require exact admitted retained version before real startup" }
            // Older Android intercepts bare BEGIN as an exclusive write transaction.
            // Execute the paired deferred read/rollback directly on this read-only connection.
            db.execSQL(";BEGIN DEFERRED TRANSACTION")
            try { SavedEndpointDecisionRawSnapshot.sql { db.rawQuery(it, null) } }
            finally { db.execSQL(";ROLLBACK") }
        }
        baselineSql82 = oldAfter.getJSONObject("sql")
        retainedSql83 = SavedEndpointDecisionRawSnapshot.upgraded(baselineSql82)
        if (continuingSavedDecisions) EndpointInventorySource.values().forEach { source ->
            retainedSql83 = SavedEndpointDecisionRawSnapshot.inserted(retainedSql83, source, candidateName(source), candidateUrl(source))
        }
        val admittedSql = if (continuingSavedDecisions) retainedSql83 else baselineSql82
        assertEquals(SavedEndpointInspectionRawSnapshot.canonical(SavedEndpointDecisionRawSnapshot.persistedSql(admittedSql)),
            SavedEndpointInspectionRawSnapshot.canonical(SavedEndpointDecisionRawSnapshot.persistedSql(sql)))
        val prefs = SavedEndpointDecisionRawSnapshot.preferences(context)
        // Resumption derives every original preference from the exact preserved pre-upgrade proof.
        baselinePreferences = if (continuingSavedDecisions) {
            val failedBefore = JSONObject(File(context.cacheDir, "endpoint-decision-auth-v1-before-actual-upgrade-$FAILED_PILOT_NONCE.json").readText())
            assertEquals(SavedEndpointInspectionRawSnapshot.canonical(SavedEndpointDecisionRawSnapshot.persistedSql(baselineSql82)),
                SavedEndpointInspectionRawSnapshot.canonical(SavedEndpointDecisionRawSnapshot.persistedSql(failedBefore.getJSONObject("sql"))))
            failedBefore.getJSONObject("preferences")
        } else prefs
        for (name in listOf(SHARED_PREFERENCES_FILE, "key_alias", MARKER)) {
            assertEquals(SavedEndpointInspectionRawSnapshot.canonical(oldAfter.getJSONObject("preferences").getJSONObject(name)),
                SavedEndpointInspectionRawSnapshot.canonical(baselinePreferences.getJSONObject(name)))
        }
        check(!baselinePreferences.getJSONObject(SHARED_PREFERENCES_FILE).getJSONObject("typedMap").has("prefs_pin_failed_attempts"))
        check(!baselinePreferences.getJSONObject(SHARED_PREFERENCES_FILE).getJSONObject("typedMap").has("prefs_pin_lock_until_ms"))
        val before = JSONObject().put("phase", "before-startup").put("sql", sql).put("preferences", prefs)
            .put("api", Build.VERSION.SDK_INT).put("uid", Process.myUid()).put("nonce", nonce)
            .put("retainedForeignKeysObservation", oldAfter.getJSONObject("sql").getJSONArray("foreignKeys"))
            .put("sourceCommit", sourceCommit).put("selectedAccount", false)
        assertPersistentEquals(admittedSql, before, initialFailedAttempts, exactPreferenceBytes = !continuingSavedDecisions)
        writeProof("before-startup", before)
        before
    }

    private fun requirePriorProofsExact() {
        for ((name, expected) in PRIOR_PROOFS.getValue(Build.VERSION.SDK_INT)) {
            val file = File(context.cacheDir, name)
            check(file.isFile && file.length() in 1..4_194_304)
            check(SavedEndpointInspectionRawSnapshot.sha256(file.readBytes()) == expected) { "Retained proof differs" }
        }
        val found = context.cacheDir.listFiles().orEmpty().filter { it.name.startsWith("global-endpoint-auth-") }.map { it.name }.toSet()
        check(found == PRIOR_PROOFS.getValue(Build.VERSION.SDK_INT).keys)
        if (continuingSavedDecisions) for ((name, hash) in FAILED_PILOT_PROOFS) {
            val file = File(context.cacheDir, name)
            check(file.isFile && file.length() in 1..4_194_304)
            check(SavedEndpointInspectionRawSnapshot.sha256(file.readBytes()) == hash) { "Failed pilot proof differs" }
        }
    }

    private fun captureSnapshot(phase: String): JSONObject = runBlocking(Dispatchers.IO) {
        check(!database.inTransaction())
        database.beginTransactionNonExclusive()
        val sql = try {
            assertEquals(83L, scalar("PRAGMA user_version"))
            assertEquals(0L, scalar("SELECT COUNT(*) FROM meta_accounts"))
            assertEquals(0L, scalar("SELECT COUNT(*) FROM chain_accounts"))
            assertEquals(2L, scalar("SELECT COUNT(*) FROM chains"))
            assertEquals(1L, scalar("SELECT COUNT(*) FROM chain_nodes"))
            assertEquals(8L, scalar("SELECT revision FROM custom_endpoint_records WHERE id=-9003"))
            SavedEndpointDecisionRawSnapshot.sql { database.query(it) }.also {
                assertEquals("Coherent primary transaction FK", "[[1]]", it.getJSONArray("foreignKeys").toString())
            }
        } finally { database.endTransaction() }
        assertFalse(graph.accountRepository().isAccountSelected())
        val result = JSONObject().put("phase", phase).put("sql", sql)
            .put("preferences", SavedEndpointDecisionRawSnapshot.preferences(context)).put("selectedAccount", false)
            .put("api", Build.VERSION.SDK_INT).put("uid", Process.myUid()).put("nonce", nonce).put("sourceCommit", sourceCommit)
        writeProof(phase, result)
        result
    }

    /** Exact SQL and preference values; only the real PIN limiter's explicit transient counter may differ. */
    private fun assertPersistentEquals(expectedSql: JSONObject, observed: JSONObject, failedAttempts: Int = 0, exactPreferenceBytes: Boolean = false) {
        assertEquals(SavedEndpointInspectionRawSnapshot.canonical(SavedEndpointDecisionRawSnapshot.persistedSql(expectedSql)),
            SavedEndpointInspectionRawSnapshot.canonical(SavedEndpointDecisionRawSnapshot.persistedSql(observed.getJSONObject("sql"))))
        check(failedAttempts in 0..2)
        val actual = observed.getJSONObject("preferences")
        assertEquals(baselinePreferences.keys().asSequence().toSet(), actual.keys().asSequence().toSet())
        for (name in baselinePreferences.keys()) {
            val baseline = baselinePreferences.getJSONObject(name)
            val current = actual.getJSONObject(name)
            if (name != SHARED_PREFERENCES_FILE || exactPreferenceBytes) {
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
        check(phase in setOf(
            "before-startup", "after-startup", "wrong-pin-denied", "correct-pin-restored",
            "saved-legacy", "saved-historical", "saved-upgrade_entry",
            "saved-current", "saved-owned", "after-background-return",
            "after-recreation-and-reread", "proof"
        ))
        val file = File(context.cacheDir, "endpoint-decision-auth-v3-$phase-$nonce.json")
        val hash = SavedEndpointInspectionRawSnapshot.writeNew(file, value)
        if (phase != "proof") snapshotFiles += JSONObject().put("name", file.name).put("sha256", hash)
    }

    private fun currentForm(): SavedEndpointDecisionForm = checkNotNull((currentFragment() as SavedEndpointsFragment).viewModel.state.value.decisionForm)

    private fun openForm(source: EndpointInventorySource, existing: Boolean) {
        click(R.string.saved_endpoint_choice_edit)
        compose.waitUntil(15_000) { onMain {
            val form = (currentFragment() as? SavedEndpointsFragment)?.viewModel?.state?.value?.decisionForm
            form != null && !form.loading && !form.targetsLoading
        } }
        onMain {
            val form = currentForm()
            assertEquals(ref(source), form.ref); assertFalse(form.loadFailed); assertFalse(form.targetsFailed)
            assertEquals(if (existing) candidateName(source) else "", form.name)
            assertEquals(if (existing) candidateUrl(source) else "", form.url)
            if (existing) {
                assertEquals(UniversalWalletRegistry.solanaMainnet.id, form.target!!.chainId)
                assertEquals(EndpointDecisionTarget.Family.SOLANA, form.target!!.family)
                assertEquals(UniversalWalletRegistry.solanaMainnet.genesisHash, form.target!!.expectedGenesis)
            } else assertNull(form.target)
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
                val row = page.records.single(); assertEquals(source, row.ref.source); assertEquals(ROW_ID, row.ref.rowId)
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
        const val FAILED_PILOT_NONCE = "c923620f6fe5493b9c5117a5fe827622"
        const val SAVED_PILOT_NONCE = "a63d12b3e64847e987fb8b10372b7045"
        val FAILED_PILOT_PROOFS = mapOf(
            "endpoint-decision-auth-v2-after-startup-a63d12b3e64847e987fb8b10372b7045.json" to "ebd6c60d35f05eef49016027a414879c793d671abe9e5e6624a1764bc1dd6148",
            "endpoint-decision-auth-v2-before-startup-a63d12b3e64847e987fb8b10372b7045.json" to "b229d8518709f86b10b5800b66c34b647792b3e4abb6a58d1925a9e60cb3c4dc",
            "endpoint-decision-auth-v2-correct-pin-restored-a63d12b3e64847e987fb8b10372b7045.json" to "43d74b81d96a96d5a6a8d9390b660d6a45cd5fad7b913b20afdc710982142924",
            "endpoint-decision-auth-v2-saved-current-a63d12b3e64847e987fb8b10372b7045.json" to "19e6683bed472498be6e05d25126f8539662be21ae794acef81a5999ff488f87",
            "endpoint-decision-auth-v2-saved-historical-a63d12b3e64847e987fb8b10372b7045.json" to "8d907447c77e1d71bde279475e5a57de6de6296d9a0f1baca656399b1feb4471",
            "endpoint-decision-auth-v2-saved-legacy-a63d12b3e64847e987fb8b10372b7045.json" to "aa8697d91b361c02bff6930ddb1a7193bf3a5a7321f28fb7ae0e0a6b20d9025f",
            "endpoint-decision-auth-v2-saved-owned-a63d12b3e64847e987fb8b10372b7045.json" to "70c4b143e76272cf562c120eed5d2d810012f7a5ddefb7b473d0707ed971cf39",
            "endpoint-decision-auth-v2-saved-upgrade_entry-a63d12b3e64847e987fb8b10372b7045.json" to "51f21ec8f0e82437810c89ac3939bb2bd49fee279d3b7f041a1c30cddf96ce32",
            "endpoint-decision-auth-v2-wrong-pin-denied-a63d12b3e64847e987fb8b10372b7045.json" to "c9c15b9d0f77dd0869b742c681f542904bfc8ed76096664ec6b259b84bfb9c8c",
            "endpoint-decision-auth-v1-after-actual-upgrade-c923620f6fe5493b9c5117a5fe827622.json" to "a408941b41f8f6e540a20767306ed4a02ab12ecca59efc684a35a4913acccec3",
            "endpoint-decision-auth-v1-before-actual-upgrade-c923620f6fe5493b9c5117a5fe827622.json" to "485872df90e56eacdb94a1edc36f01109241c077de89ef0ad72ced3b7531b0c1",
            "endpoint-decision-auth-v1-wrong-pin-denied-c923620f6fe5493b9c5117a5fe827622.json" to "fcd1e5904dfe368c52fb6b8b2889e5483c60c04bc2c99283bbdd2511dd970a9a"
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
        val PRIOR_PROOFS = mapOf(
            26 to mapOf(
                "global-endpoint-auth-startup-d3a8b1ca1c6842018d887f0764cdc2af.json" to "1816f03c26b1944a0fec644548f68e3af61a5d93ec7ee49545f97ee93eaa70df",
                "global-endpoint-auth-before-d3a8b1ca1c6842018d887f0764cdc2af.json" to "ce7ea63b0f0f0be9c0a9cf53ffe3b5f49eb06a9de42240f26cb1649d73c078d7",
                "global-endpoint-auth-mutation-d3a8b1ca1c6842018d887f0764cdc2af.json" to "f150e942a0fd64ef9f27c6c3552d87ef6c7cef9f7dbde715d10089d68c2fad0b",
                "global-endpoint-auth-after-d3a8b1ca1c6842018d887f0764cdc2af.json" to "e453c3013894ed3252632876549e9d420842a2b69c31a7f571eebbafe4506620",
                "global-endpoint-auth-proof-d3a8b1ca1c6842018d887f0764cdc2af.json" to "9a4546b7cd624538da4fde456c4f6ede72e75e877399166b1bf1f7413c3af1ec"
            ),
            34 to mapOf(
                "global-endpoint-auth-startup-0b20c87948f84c588daf2d56815f77c9.json" to "a0387f9b2fb2c3dabbc271a9456a95cfb1106a81f30a9a1c26a9e516e88668a5",
                "global-endpoint-auth-before-0b20c87948f84c588daf2d56815f77c9.json" to "fce20bf7dd0f6f0d7eeb9c996430969c3ce7544e6a7407326cf977407d267fe8",
                "global-endpoint-auth-mutation-0b20c87948f84c588daf2d56815f77c9.json" to "0ea43b2fe7f2cb4a0a62c59e652ab24379d4147d7594439e9c51417a9e3b3204",
                "global-endpoint-auth-after-0b20c87948f84c588daf2d56815f77c9.json" to "8275acf9cd625b370261b5d41770a1260129646e602427bbc45e830b90143f9a",
                "global-endpoint-auth-proof-0b20c87948f84c588daf2d56815f77c9.json" to "9141b08365a13ebd1a5eccf0442e6c20c9f995ae4de11ce63dbdd09009575e14"
            ),
            36 to mapOf(
                "global-endpoint-auth-startup-02d404a37f81495a987dd8c00d918ffa.json" to "4655c633d5ba528eadff208d20a3980925f3d0d6692a25941376a2e4098a3a4e",
                "global-endpoint-auth-before-02d404a37f81495a987dd8c00d918ffa.json" to "38d7479109b918562717e3ad55de5537580e2d537840b2e79e67bc68d9dbef13",
                "global-endpoint-auth-mutation-02d404a37f81495a987dd8c00d918ffa.json" to "49b8751338d524c4ced5a5df3d432de89f66bbb699508a260978e51f91dd020b",
                "global-endpoint-auth-after-02d404a37f81495a987dd8c00d918ffa.json" to "33017f8f2f7ac747254c1925bc0d008a6d5e0982de9b8fa0b9d9e156ea7e8fd9",
                "global-endpoint-auth-proof-02d404a37f81495a987dd8c00d918ffa.json" to "0cd3c9b3b32669120034903542f88fb6ce5cee05a1aefa745efee2236fd88968"
            )
        )
    }
}
