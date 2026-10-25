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

import jp.co.soramitsu.app.root.domain.WalletDatabaseStartupResult

/** Actual Restore through a fresh owned public fixture, real PIN and an in-process loopback peer.
 * The fresh setup does not qualify migration. Host tooling must admit an exclusively owned empty UID.
 */
@RunWith(AndroidJUnit4::class)
class SavedEndpointRestoreAuthenticationInstrumentedTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private lateinit var graph: SavedEndpointAuthFixtureEntryPoint
    private lateinit var database: SupportSQLiteDatabase
    private lateinit var nonce: String
    private lateinit var sourceCommit: String
    private lateinit var serial: String
    private lateinit var avd: String
    private lateinit var endpoint: String
    private lateinit var heartbeatEvidence: SavedEndpointHeartbeatEvidence
    private lateinit var baselinePreferences: JSONObject
    private lateinit var expectedSavedEntry: NavBackStackEntry
    private val pinDestructions = mutableListOf<JSONObject>()
    private val pinInputReadiness = mutableListOf<JSONObject>()
    private val initializerObservations = mutableListOf<JSONObject>()
    private val snapshotFiles = mutableListOf<JSONObject>()
    private var revision = INITIAL_REVISION
    private var expectedProjectionCount = 1L

    @Test
    fun realPinRestoresExactDetachedOwnerWithoutNewOwnershipOrSelection() {
        LoopbackGenesisPeer(EndpointInventorySource.OWNED).use { peer ->
            endpoint = peer.url
            prepareFreshOwnedFixture()
            SavedEndpointHeartbeatEvidence.awaitFreshHeartbeat(context, InstrumentationRegistry.getArguments())
            val setup = captureSnapshot("after-setup", false)
            try {
                heartbeatEvidence = SavedEndpointHeartbeatEvidence(setup.getJSONObject("preferences"), InstrumentationRegistry.getArguments())
                heartbeatEvidence.observe(setup)
            } finally { writeProof("after-setup", setup) }
            launch()
            awaitFragment<WelcomeFragment>()
            val startupInitializer = awaitInitializerAndContinue("initial", null)
            val initial = captureSnapshot("after-startup")
            try { heartbeatEvidence.observe(initial) }
            finally { writeProof("after-startup", initial) }
            var expectedSql = initial.getJSONObject("sql")
            baselinePreferences = initial.getJSONObject("preferences")
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
            assertSnapshot(expectedSql, "wrong-pin-denied", 1)
            unlockWithPublicPin()
            assertSnapshot(expectedSql, "correct-pin-restored")
            selectOwned()
            openForm(existing = false)
            replaceField("saved_endpoint_choice_name", PUBLIC_NAME)
            replaceField("saved_endpoint_choice_url", endpoint)
            val option = onMain { currentForm().targets!!.options.single {
                it.target?.chainId == UniversalWalletRegistry.solanaMainnet.id
            } }
            val target = checkNotNull(option.target)
            assertEquals(EndpointDecisionTarget.Family.SOLANA, target.family)
            assertEquals(UniversalWalletRegistry.solanaMainnet.genesisHash, target.expectedGenesis)
            clickTag("saved_endpoint_choice_target_${option.rowId}")
            onMain { assertSame(target, currentForm().target); assertFalse(currentForm().choosingTarget) }
            saveForm()
            expectedSql = SavedEndpointRestoreRawOracle.saved(expectedSql, ROW_ID, revision, PUBLIC_NAME, endpoint)
            assertSnapshot(expectedSql, "saved-detached")
            assertEquals("Save must remain offline", 0, peer.requests.get())

            // Occupied detached ownership is not a new endpoint and Add must not restore it.
            clickTag("saved_endpoint_attachment_action")
            awaitAttachment(SavedEndpointAttachmentStatus.FAILED)
            assertEquals(0, peer.requests.get())
            assertSnapshot(expectedSql, "add-rejected")
            peer.mode.set("MISMATCH")
            clickTag("saved_endpoint_attachment_restore")
            awaitAttachment(SavedEndpointAttachmentStatus.MISMATCH)
            assertEquals(1, peer.requests.get())
            assertSnapshot(expectedSql, "restore-mismatch")
            peer.mode.set("MATCH")
            clickTag("saved_endpoint_attachment_restore")
            awaitAttachment(SavedEndpointAttachmentStatus.REATTACHED_INACTIVE)
            assertEquals(2, peer.requests.get())
            expectedSql = SavedEndpointRestoreRawOracle.restored(expectedSql, ROW_ID, revision)
            revision++
            expectedProjectionCount++
            assertSnapshot(expectedSql, "restored-inactive")
            onMain {
                assertTrue(currentForm().attachmentRequiresRefresh)
                assertFalse(currentForm().attachmentReady)
                assertEquals(INITIAL_REVISION, currentForm().ref.revision)
            }
            compose.onNodeWithTag("saved_endpoint_attachment_action").assertDoesNotExist()
            compose.onNodeWithTag("saved_endpoint_attachment_restore").assertDoesNotExist()
            // A second old action is absent from the rendered UI; no injected callback is used.
            assertEquals(2, peer.requests.get())
            assertSnapshot(expectedSql, "old-action-unavailable")
            click(R.string.saved_endpoints_part_return)
            assertOwned()
            openForm(existing = true)
            onMain { assertTrue(currentForm().attachmentSourceChanged); assertFalse(currentForm().attachmentReady) }
            scrollTag("saved_endpoint_attachment_source_changed")
            compose.onNodeWithTag("saved_endpoint_attachment_source_changed").assertIsDisplayed()
            saveForm()
            expectedSql = SavedEndpointRestoreRawOracle.saved(expectedSql, ROW_ID, revision, PUBLIC_NAME, endpoint)
            assertSnapshot(expectedSql, "saved-refreshed")
            clickTag("saved_endpoint_attachment_restore")
            awaitAttachment(SavedEndpointAttachmentStatus.PRESENT_INACTIVE)
            assertEquals("Present pair is not another probe", 2, peer.requests.get())
            assertSnapshot(expectedSql, "present-inactive")
            click(R.string.saved_endpoints_part_return)
            assertOwned()
            val recreatedFrom = awaitFragment<SavedEndpointsFragment>()
            val oldActivity = onMain { root() }
            onMain { oldActivity.recreate() }
            compose.waitUntil(30_000) { onMain {
                resumedRoot()?.let { it !== oldActivity && currentFragment() is SavedEndpointsFragment } == true
            } }
            awaitInitializerAndContinue("after-recreation", startupInitializer)
            assertLocked(awaitFragment()); assertDataCleared(recreatedFrom); assertSecure()
            unlockWithPublicPin()
            selectOwned()
            assertSnapshot(expectedSql, "after-recreation")
            assertEquals(2, peer.requests.get())
            assertEquals(3, pinDestructions.size); assertEquals(3, pinInputReadiness.size)
            writeProof("proof", JSONObject().put("formatVersion", 1).put("api", Build.VERSION.SDK_INT)
                .put("uid", Process.myUid()).put("nonce", nonce).put("sourceCommit", sourceCommit)
                .put("serial", serial).put("avd", avd).put("realKeypadPin", true)
                .put("wrongPinDenied", true).put("explicitRestoreQualified", true)
                .put("freshFixtureNotMigration", true).put("addRejectedWithoutRequest", true)
                .put("mismatchWithoutSqlDelta", true).put("sameOwnerAndSequence", true)
                .put("recreationRelocked", true).put("presentPairNoRequest", true)
                .put("selectionQualified", false).put("publicNetworkQualified", false)
                .put("requests", peer.requests.get()).put("snapshots", JSONArray(snapshotFiles))
                .put("pinDestructions", JSONArray(pinDestructions)).put("pinInputReadiness", JSONArray(pinInputReadiness))
                .put("initializerObservations", JSONArray(initializerObservations)))
            onMain { root().finish() }
        }
    }

    private fun prepareFreshOwnedFixture() = runBlocking(Dispatchers.IO) {
        val args = InstrumentationRegistry.getArguments()
        check(context.packageName == "jp.co.soramitsu.fearless.debug")
        check(Build.VERSION.SDK_INT in setOf(26, 34, 36))
        check(args.getString("fearlessRestoreOwnedUid") == "exclusively-owned-disposable-app-uid")
        check(args.getString("fearlessRestoreUid") == Process.myUid().toString())
        check(Process.myUid() == context.applicationInfo.uid)
        serial = checkNotNull(args.getString("fearlessRestoreSerial")).also { check(it.matches(Regex("emulator-[0-9]{4}"))) }
        nonce = checkNotNull(args.getString("fearlessRestoreNonce")).also { check(it.matches(Regex("[0-9a-f]{32}"))) }
        avd = checkNotNull(args.getString("fearlessRestoreAvd")).also { check(it == "FearlessRestoreApi${Build.VERSION.SDK_INT}_$nonce") }
        sourceCommit = checkNotNull(args.getString("fearlessRestoreSourceCommit")).also { check(it.matches(Regex("[0-9a-f]{40}"))) }
        check(context.cacheDir.listFiles().orEmpty().none { it.name.contains("endpoint") && it.extension == "json" })
        for (name in listOf(MARKER, "global_endpoint_auth_fixture_v1", "saved_endpoint_auth_fixture_v1", "saved_endpoint_inspection_fixture_v1")) {
            check(context.getSharedPreferences(name, Context.MODE_PRIVATE).all.isEmpty())
            check(!File(context.applicationInfo.dataDir, "shared_prefs/$name.xml").exists())
        }
        val preferences = context.getSharedPreferences(SHARED_PREFERENCES_FILE, Context.MODE_PRIVATE)
        check(!preferences.contains("pin_code"))
        val path = context.getDatabasePath("app.db")
        if (path.exists()) SQLiteDatabase.openDatabase(path.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            requireFreshSchema { db.rawQuery(it, null) }
        }
        graph = EntryPointAccessors.fromApplication(context.applicationContext, SavedEndpointAuthFixtureEntryPoint::class.java)
        assertEquals(WalletDatabaseStartupResult.Ready, graph.startupGate().open())
        database = graph.database().openHelper.writableDatabase
        requireFreshSchema { database.query(it) }
        assertFalse(graph.accountRepository().isAccountSelected())
        assertEquals(PinHealth.ABSENT, graph.accountRepository().getPinHealth())
        database.beginTransaction()
        try {
            database.execSQL("INSERT INTO custom_endpoint_records(id,revision,chainId,url,name,attached,deleted,originalSnapshotRowId) " +
                "VALUES(?,?,?,?,?,0,0,?)", arrayOf<Any?>(ROW_ID, INITIAL_REVISION, "solana-mainnet", endpoint, PUBLIC_NAME, ORIGIN_ID))
            database.setTransactionSuccessful()
        } finally { database.endTransaction() }
        graph.accountRepository().savePinCode(PUBLIC_PIN)
        assertEquals(PinHealth.HEALTHY, graph.accountRepository().getPinHealth())
        assertEquals(PUBLIC_PIN, graph.accountRepository().getPinCode())
        check(context.getSharedPreferences(MARKER, Context.MODE_PRIVATE).edit().putString("fixture", "restore-auth-public-v1")
            .putString("nonce", nonce).putInt("uid", Process.myUid()).putString("serial", serial)
            .putString("avd", avd).putString("sourceCommit", sourceCommit).commit())
    }

    private fun requireFreshSchema(query: (String) -> android.database.Cursor) {
        val bytes = instrumentation.context.assets.open("endpoint-restore-fresh83-schema.json").use { it.readBytes() }
        check(SavedEndpointInspectionRawSnapshot.sha256(bytes) == "2e8380d2dd40e7ad50bc5a842137045c0413b73cfe90e6826a5357811b6e43de")
        val expected = JSONArray(bytes.toString(Charsets.UTF_8))
        val actual = JSONArray()
        query("SELECT type,name,tbl_name,sql FROM sqlite_master ORDER BY type,name").use { cursor ->
            while (cursor.moveToNext()) actual.put(JSONArray().also { row ->
                repeat(4) { index -> row.put(if (cursor.isNull(index)) JSONObject.NULL else cursor.getString(index)) }
            })
        }
        assertEquals(SavedEndpointInspectionRawSnapshot.canonical(expected), SavedEndpointInspectionRawSnapshot.canonical(actual))
        query("PRAGMA user_version").use { check(it.moveToFirst()); assertEquals(83, it.getInt(0)); check(!it.moveToNext()) }
        query("SELECT id,identity_hash FROM room_master_table").use {
            check(it.moveToFirst()); assertEquals(42L, it.getLong(0)); assertEquals("7dcfe60d9e078af3223a27e8b74e784f", it.getString(1))
            check(!it.moveToNext())
        }
        query("SELECT typeof(locale),locale FROM android_metadata").use {
            check(it.moveToFirst()); assertEquals("text", it.getString(0)); assertEquals(Locale.getDefault().toString(), it.getString(1))
            check(!it.moveToNext())
        }
        query("SELECT name FROM sqlite_master WHERE type='table' AND name NOT IN ('room_master_table','android_metadata')").use { tables ->
            while (tables.moveToNext()) {
                val name = tables.getString(0)
                check(name.matches(Regex("[A-Za-z0-9_]+")))
                query("SELECT 1 FROM \"$name\" LIMIT 1").use { check(!it.moveToFirst()) { "Refuse nonempty prior database" } }
            }
        }
    }

    private fun captureSnapshot(phase: String, initialized: Boolean = true): JSONObject = runBlocking(Dispatchers.IO) {
        check(!database.inTransaction())
        database.beginTransactionNonExclusive()
        val sql = try {
            assertEquals(83L, scalar("PRAGMA user_version"))
            assertEquals(0L, scalar("SELECT COUNT(*) FROM meta_accounts"))
            assertEquals(0L, scalar("SELECT COUNT(*) FROM chain_accounts"))
            assertEquals(if (initialized) 2L else 0L, scalar("SELECT COUNT(*) FROM chains"))
            assertEquals(if (initialized) expectedProjectionCount else 0L, scalar("SELECT COUNT(*) FROM chain_nodes"))
            assertEquals(1L, scalar("SELECT COUNT(*) FROM custom_endpoint_records"))
            assertEquals(revision, scalar("SELECT revision FROM custom_endpoint_records WHERE id=$ROW_ID"))
            assertEquals(ORIGIN_ID, scalar("SELECT originalSnapshotRowId FROM custom_endpoint_records WHERE id=$ROW_ID"))
            assertEquals(0L, scalar("SELECT deleted FROM custom_endpoint_records WHERE id=$ROW_ID"))
            assertEquals(if (revision == INITIAL_REVISION) 0L else 1L, scalar("SELECT attached FROM custom_endpoint_records WHERE id=$ROW_ID"))
            database.query("SELECT chainId,url,name FROM custom_endpoint_records WHERE id=$ROW_ID").use {
                check(it.moveToFirst()); assertEquals("solana-mainnet", it.getString(0))
                assertEquals(endpoint, it.getString(1)); assertEquals(PUBLIC_NAME, it.getString(2)); check(!it.moveToNext())
            }
            SavedEndpointDecisionRawSnapshot.sql { database.query(it) }.also {
                assertEquals("[[1]]", it.getJSONArray("foreignKeys").toString())
            }
        } finally { database.endTransaction() }
        assertFalse(graph.accountRepository().isAccountSelected())
        return@runBlocking JSONObject().put("phase", phase).put("sql", sql)
            .put("preferences", SavedEndpointDecisionRawSnapshot.preferences(context)).put("selectedAccount", false)
            .put("api", Build.VERSION.SDK_INT).put("uid", Process.myUid()).put("nonce", nonce).put("sourceCommit", sourceCommit)
    }

    private fun assertSnapshot(expected: JSONObject, phase: String, failedAttempts: Int = 0) {
        val observed = captureSnapshot(phase)
        try { heartbeatEvidence.observe(observed) }
        finally { writeProof(phase, observed) }
        assertPersistentEquals(expected, observed, failedAttempts)
    }

    private fun writeProof(phase: String, value: JSONObject) {
        check(phase in setOf("after-setup", "after-startup", "wrong-pin-denied", "correct-pin-restored", "saved-detached",
            "add-rejected", "restore-mismatch", "restored-inactive", "old-action-unavailable", "saved-refreshed",
            "present-inactive", "after-recreation", "proof"))
        val file = File(context.cacheDir, "endpoint-restore-auth-v1-$phase-$nonce.json")
        val hash = SavedEndpointInspectionRawSnapshot.writeNew(file, value)
        if (phase != "proof") snapshotFiles += JSONObject().put("name", file.name).put("sha256", hash)
    }

    private fun currentForm(): SavedEndpointDecisionForm = checkNotNull((currentFragment() as SavedEndpointsFragment).viewModel.state.value.decisionForm)
    private fun openForm(existing: Boolean) {
        click(R.string.saved_endpoint_choice_edit)
        compose.waitUntil(15_000) { onMain {
            val form = (currentFragment() as? SavedEndpointsFragment)?.viewModel?.state?.value?.decisionForm
            form != null && !form.loading && !form.targetsLoading
        } }
        onMain {
            val form = currentForm()
            assertEquals(EndpointRecordRef(EndpointInventorySource.OWNED, ROW_ID, revision), form.ref)
            assertFalse(form.loadFailed); assertFalse(form.targetsFailed)
            assertEquals(if (existing) PUBLIC_NAME else "", form.name)
            assertEquals(if (existing) endpoint else "", form.url)
            if (existing) {
                assertEquals("solana-mainnet", form.target!!.chainId)
                assertEquals(EndpointDecisionTarget.Family.SOLANA, form.target!!.family)
            } else assertNull(form.target)
        }
        assertSecure()
    }
    private fun saveForm() {
        clickTag("saved_endpoint_choice_save")
        compose.waitUntil(15_000) { onMain { currentForm().let { !it.saving && (it.saved || it.saveFailed) } } }
        onMain { assertTrue(currentForm().saved); assertFalse(currentForm().saveFailed); assertTrue(currentForm().attachmentReady) }
    }
    private fun assertUnlocked(fragment: SavedEndpointsFragment) {
        compose.waitUntil(15_000) { onMain { fragment.viewModel.state.value.let {
            !it.locked && !it.loading && it.page != null && it.inspection == null && it.inventoryInspection == null
        } } }
    }
    private fun selectOwned() {
        scrollTag("saved_endpoint_collection")
        compose.onNodeWithTag("saved_endpoint_collection").assertIsDisplayed().performClick()
        compose.onNodeWithTag("saved_endpoint_source_owned").performScrollTo().assertIsDisplayed().performClick()
        assertOwned()
    }
    private fun assertOwned() {
        val fragment = awaitFragment<SavedEndpointsFragment>()
        compose.waitUntil(15_000) { onMain { fragment.viewModel.state.value.let {
            !it.locked && !it.loading && it.source == EndpointInventorySource.OWNED && it.decisionForm == null && it.inventoryPage != null
        } } }
        onMain {
            val state = fragment.viewModel.state.value
            assertFalse(state.readFailed)
            val row = state.inventoryPage!!.records.single()
            assertEquals(EndpointRecordRef(EndpointInventorySource.OWNED, ROW_ID, revision), row.ref)
            assertEquals(EndpointOwnership(revision > INITIAL_REVISION, false, ORIGIN_ID), row.ownership)
            assertEquals(PUBLIC_NAME, (row.values[EndpointField.NAME] as EndpointValue.Text).value)
            assertEquals(endpoint, (row.values[EndpointField.ENDPOINT] as EndpointValue.Text).value)
            assertEquals("solana-mainnet", (row.values[EndpointField.CHAIN_REFERENCE] as EndpointValue.Text).value)
        }
        assertSecure()
    }
    private fun awaitAttachment(expected: SavedEndpointAttachmentStatus) {
        compose.waitUntil(15_000) { onMain { currentForm().let { !it.attaching && it.attachmentStatus != null } } }
        onMain { assertEquals(expected, currentForm().attachmentStatus) }
        scrollTag("saved_endpoint_attachment_status")
        compose.onNodeWithTag("saved_endpoint_attachment_status").assertIsDisplayed()
    }

    /** Exact SQL and preferences except the explicit PIN counter and validated one-day Firebase append. */
    private fun assertPersistentEquals(expectedSql: JSONObject, observed: JSONObject, failedAttempts: Int = 0, exactPreferenceBytes: Boolean = false) {
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

    private fun replaceField(tag: String, value: String) {
        scrollTag(tag)
        compose.onNodeWithTag(tag).assertIsDisplayed().assertIsEnabled().performTextReplacement(value)
    }
    private fun scrollTag(tag: String) = compose.onNodeWithTag("saved_endpoints_list").performScrollToNode(hasTestTag(tag))
    private fun clickTag(tag: String) {
        scrollTag(tag); compose.onNodeWithTag(tag).assertIsDisplayed().assertIsEnabled().performClick()
    }
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
        compose.onNodeWithText(endpoint).assertDoesNotExist()
        val locked = text(R.string.saved_endpoints_locked); scrollTo(locked); compose.onNodeWithText(locked).assertIsDisplayed()
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
        const val MARKER = "endpoint_restore_auth_fixture_v1"
        const val ROW_ID = 47001L
        const val ORIGIN_ID = -9107L
        const val INITIAL_REVISION = 8L
        const val PUBLIC_PIN = "123456"
        const val PUBLIC_NAME = "Public restore 雪"
    }
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
