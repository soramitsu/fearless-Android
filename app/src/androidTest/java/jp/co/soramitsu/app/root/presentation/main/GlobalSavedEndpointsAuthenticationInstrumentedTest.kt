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
import jp.co.soramitsu.app.root.domain.WalletDatabaseStartupResult
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

/**
 * Actual five-source PIN-authorized journey only on three separately allocated public fixture UIDs.
 * No authority/state injection. Native catalog startup has an exact declared baseline delta.
 * The sole later fixture write is a declared owned revision increment.
 * Host ownership/build/APK evidence must independently corroborate every supplied run identity.
 */
@RunWith(AndroidJUnit4::class)
class GlobalSavedEndpointsAuthenticationInstrumentedTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private lateinit var graph: SavedEndpointAuthFixtureEntryPoint
    private lateinit var database: SupportSQLiteDatabase
    private lateinit var nonce: String
    private lateinit var sourceCommit: String
    private lateinit var serial: String
    private lateinit var avd: String
    private val pinDestructions = mutableListOf<JSONObject>()
    private val pinInputReadiness = mutableListOf<JSONObject>()
    private val chunkObservations = mutableListOf<JSONObject>()
    private val sourceObservations = mutableListOf<JSONObject>()
    private val initializerObservations = mutableListOf<JSONObject>()
    private lateinit var expectedSavedEntry: NavBackStackEntry
    private var revision = INITIAL_REVISION

    @Test
    fun realPinAuthorizesFiveSourcesAndOwnedBytePartsAndRevokesAllOnLifecycleExit() {
        prepareFreshOwnedFixture()
        val startup = captureSnapshot("before-native-bootstrap", expectNativeCatalog = false)
        val startupFile = File(context.cacheDir, "global-endpoint-auth-startup-$nonce.json")
        val startupHash = SavedEndpointInspectionRawSnapshot.writeNew(startupFile, startup)
        launch()
        awaitFragment<WelcomeFragment>()
        val startupInitializer = awaitInitializerAndContinue("initial", null)
        val before = captureSnapshot("before")
        val expectedBootstrap = JSONObject(SavedEndpointInspectionRawSnapshot.canonical(startup))
            .put("sql", GlobalSavedEndpointRawSnapshot.expectedNativeBootstrap(startup.getJSONObject("sql")))
        assertProtectedEquals(expectedBootstrap, before)
        val beforeFile = File(context.cacheDir, "global-endpoint-auth-before-$nonce.json")
        val beforeHash = SavedEndpointInspectionRawSnapshot.writeNew(beforeFile, before)
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

        unlockWithPublicPin()
        EndpointInventorySource.values().forEach(::selectSource)
        inspect(EndpointField.NAME); assertChunk(EndpointField.NAME, 0)
        click(R.string.saved_endpoints_part_return); assertSource(EndpointInventorySource.OWNED)
        inspect(EndpointField.ENDPOINT); assertChunk(EndpointField.ENDPOINT, 0)
        click(R.string.saved_endpoints_part_next); assertChunk(EndpointField.ENDPOINT, 512)
        click(R.string.saved_endpoints_part_previous); assertChunk(EndpointField.ENDPOINT, 0)
        for (offset in 512L..4096L step 512L) {
            click(R.string.saved_endpoints_part_next); assertChunk(EndpointField.ENDPOINT, offset)
        }
        val savedBeforeBack = awaitFragment<SavedEndpointsFragment>()
        onMain { root().onBackPressedDispatcher.onBackPressed() }
        assertSame(savedBeforeBack, awaitFragment<SavedEndpointsFragment>())
        assertSource(EndpointInventorySource.OWNED)
        inspect(EndpointField.ENDPOINT); assertChunk(EndpointField.ENDPOINT, 0)

        // Explicit owned-fixture race. This is not a product action or a reader write.
        val beforeMutation = captureSnapshot("before-declared-revision-increment")
        assertProtectedEquals(before, beforeMutation)
        runBlocking(Dispatchers.IO) {
            database.compileStatement("UPDATE custom_endpoint_records SET revision=? WHERE id=? AND revision=?").use { statement ->
                statement.bindLong(1, INITIAL_REVISION + 1)
                statement.bindLong(2, ROW_ID)
                statement.bindLong(3, INITIAL_REVISION)
                // The affected count belongs to this write, not a separate pooled query connection.
                assertEquals(1, statement.executeUpdateDelete())
            }
        }
        revision = INITIAL_REVISION + 1
        val expectedSql = GlobalSavedEndpointRawSnapshot.incrementedRevision(before.getJSONObject("sql"), ROW_ID,
            INITIAL_REVISION, revision)
        val afterMutation = captureSnapshot("after-declared-revision-increment")
        val expectedAfter = JSONObject(SavedEndpointInspectionRawSnapshot.canonical(before)).put("sql", expectedSql)
        assertProtectedEquals(expectedAfter, afterMutation)
        val mutationFile = File(context.cacheDir, "global-endpoint-auth-mutation-$nonce.json")
        val mutationHash = SavedEndpointInspectionRawSnapshot.writeNew(mutationFile,
            JSONObject().put("kind", "explicit-test-only-owned-revision-increment").put("rowId", ROW_ID)
                .put("oldRevision", INITIAL_REVISION).put("newRevision", revision)
                .put("before", beforeMutation).put("after", afterMutation).put("onlyExpectedTypedDelta", true))
        click(R.string.saved_endpoints_part_next)
        assertStaleRevision()
        click(R.string.saved_endpoints_part_return); assertSource(EndpointInventorySource.OWNED)
        inspect(EndpointField.NAME); assertChunk(EndpointField.NAME, 0)

        val backgrounded = awaitFragment<SavedEndpointsFragment>()
        val taskId = onMain { root().taskId }
        context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        compose.waitUntil(15_000) { onMain {
            ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).none { it is WalletRootActivity }
        } }
        assertDataCleared(backgrounded)
        context.startActivity(Intent(context, WalletRootActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        val resumed = awaitFragment<SavedEndpointsFragment>()
        assertEquals(taskId, onMain { root().taskId })
        assertLocked(resumed); assertSecure(); unlockWithPublicPin()
        selectSource(EndpointInventorySource.OWNED)
        inspect(EndpointField.ENDPOINT); assertChunk(EndpointField.ENDPOINT, 0)

        val recreatedFrom = awaitFragment<SavedEndpointsFragment>()
        val oldActivity = onMain { root() }
        onMain { oldActivity.recreate() }
        compose.waitUntil(30_000) { onMain {
            resumedRoot()?.let { it !== oldActivity && currentFragment() is SavedEndpointsFragment } == true
        } }
        awaitInitializerAndContinue("after-recreation", startupInitializer)
        assertLocked(awaitFragment()); onMain { assertTrue(oldActivity.isDestroyed) }
        assertDataCleared(recreatedFrom); assertSecure(); unlockWithPublicPin()
        selectSource(EndpointInventorySource.OWNED)
        inspect(EndpointField.NAME); assertChunk(EndpointField.NAME, 0)

        val after = captureSnapshot("after")
        assertProtectedEquals(expectedAfter, after)
        val afterFile = File(context.cacheDir, "global-endpoint-auth-after-$nonce.json")
        val afterHash = SavedEndpointInspectionRawSnapshot.writeNew(afterFile, after)
        assertEquals(4, pinDestructions.size); assertEquals(4, pinInputReadiness.size)
        assertEquals(16, chunkObservations.size)
        assertEquals(listOf("initial", "after-recreation"), initializerObservations.map { it.getString("phase") })
        val initializerCompletions = initializerObservations.count { it.getString("branch") != "RETAINED" }
        val startupContinues = initializerObservations.count { it.getBoolean("continueClicked") }
        assertEquals(initializerCompletions, startupContinues)
        assertTrue(initializerCompletions in 1..2)
        assertEquals(EndpointInventorySource.values().toList(), sourceObservations.take(5).map {
            EndpointInventorySource.valueOf(it.getString("source")) })
        val proof = JSONObject().put("formatVersion", 2).put("api", Build.VERSION.SDK_INT).put("uid", Process.myUid())
            .put("nonce", nonce).put("sourceCommit", sourceCommit).put("serial", serial).put("avd", avd)
            .put("realGraph", true).put("realKeypadPin", true).put("wrongPinDenied", true)
            .put("initializerCompletionObserved", true).put("startupWarningContinued", true).put("exactNativeBootstrapDelta", true)
            .put("allFiveSourcesRead", true).put("sameSignedRowIdSourceQualified", true)
            .put("backgroundRelocked", true).put("activityRecreationRelocked", true).put("allNewStateCleared", true)
            .put("secureWindowAfterEveryReturn", true).put("staleRevisionHasNoPayloadOrNavigation", true)
            .put("systemBackReturnedToSavedList", true).put("pinCiphertextExact", true)
            .put("wrappedKeyAndMarkerExact", true).put("allSqlExactExceptDeclaredRevisionIncrement", true)
            .put("walletCount", 0).put("processDeathQualified", false).put("app81UpgradeQualified", false)
            .put("initializerObservations", JSONArray(initializerObservations))
            .put("initializerCompletionCount", initializerCompletions).put("startupContinueCount", startupContinues)
            .put("pinDestructions", JSONArray(pinDestructions)).put("pinInputReadiness", JSONArray(pinInputReadiness))
            .put("chunks", JSONArray(chunkObservations)).put("sources", JSONArray(sourceObservations))
            .put("startupSnapshot", JSONObject().put("name", startupFile.name).put("sha256", startupHash))
            .put("beforeSnapshot", JSONObject().put("name", beforeFile.name).put("sha256", beforeHash))
            .put("mutationSnapshot", JSONObject().put("name", mutationFile.name).put("sha256", mutationHash))
            .put("afterSnapshot", JSONObject().put("name", afterFile.name).put("sha256", afterHash))
        SavedEndpointInspectionRawSnapshot.writeNew(File(context.cacheDir, "global-endpoint-auth-proof-$nonce.json"), proof)
        onMain { root().finish() }
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

    private fun prepareFreshOwnedFixture() = runBlocking(Dispatchers.IO) {
        val args = InstrumentationRegistry.getArguments()
        val profile = checkNotNull(PROFILES[Build.VERSION.SDK_INT])
        serial = profile.first; avd = profile.second; nonce = profile.third
        check(context.packageName == "jp.co.soramitsu.fearless.debug")
        check(args.getString("fearlessGlobalOwnedUid") == "exclusively-owned-disposable-app-uid")
        check(args.getString("fearlessGlobalSerial") == serial && args.getString("fearlessGlobalAvd") == avd)
        check(args.getString("fearlessGlobalNonce") == nonce)
        check(args.getString("fearlessGlobalUid") == Process.myUid().toString())
        check(Process.myUid() == context.applicationInfo.uid)
        sourceCommit = checkNotNull(args.getString("fearlessGlobalSourceCommit")).also { check(it.matches(Regex("[0-9a-f]{40}"))) }
        check(context.cacheDir.listFiles().orEmpty().none { it.name.startsWith("global-endpoint-auth-") })
        for (name in listOf(MARKER, "saved_endpoint_auth_fixture_v1", "saved_endpoint_inspection_fixture_v1")) {
            check(context.getSharedPreferences(name, Context.MODE_PRIVATE).all.isEmpty())
            check(!File(context.applicationInfo.dataDir, "shared_prefs/$name.xml").exists())
        }
        val preferences = context.getSharedPreferences(SHARED_PREFERENCES_FILE, Context.MODE_PRIVATE)
        check(!preferences.contains("pin_code")) { "Refuse existing PIN" }
        val path = context.getDatabasePath("app.db")
        if (path.exists()) SQLiteDatabase.openDatabase(path.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            check(db.version == 82) { "Only a fresh empty82 schema is admitted" }
            // Android owns this framework locale row even in a newly created empty app schema.
            db.rawQuery("SELECT type,name,tbl_name,sql FROM sqlite_master WHERE lower(name)='android_metadata' " +
                "OR lower(tbl_name)='android_metadata'", null).use { metadata ->
                check(metadata.moveToFirst()) { "Fresh framework locale table is missing" }
                check(metadata.getString(0) == "table" && metadata.getString(1) == "android_metadata" &&
                    metadata.getString(2) == "android_metadata" &&
                    metadata.getString(3) == "CREATE TABLE android_metadata (locale TEXT)") { "Unsupported framework locale table" }
                check(!metadata.moveToNext()) { "Framework locale namespace collision" }
            }
            db.rawQuery("SELECT typeof(locale),locale FROM android_metadata LIMIT 2", null).use { locale ->
                check(locale.moveToFirst() && locale.getString(0) == "text" &&
                    locale.getString(1) == Locale.getDefault().toString()) { "Fresh framework locale differs" }
                check(!locale.moveToNext()) { "Ambiguous framework locale" }
            }
            db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT IN ('room_master_table','sqlite_sequence','android_metadata')", null).use { tables ->
                while (tables.moveToNext()) {
                    val name = tables.getString(0)
                    check(name.matches(Regex("[A-Za-z0-9_]+")))
                    db.rawQuery("SELECT 1 FROM \"$name\" LIMIT 1", null).use { check(!it.moveToFirst()) { "Refuse nonempty prior database" } }
                }
            }
            db.rawQuery("SELECT 1 FROM sqlite_master WHERE " + ARCHIVE_NAMESPACE, null).use { check(!it.moveToFirst()) }
        }
        graph = EntryPointAccessors.fromApplication(context.applicationContext, SavedEndpointAuthFixtureEntryPoint::class.java)
        assertEquals(WalletDatabaseStartupResult.Ready, graph.startupGate().open())
        database = graph.database().openHelper.writableDatabase
        assertEquals(82L, scalar("PRAGMA user_version"))
        for (table in listOf("meta_accounts", "chain_accounts", "chains", "chain_nodes", "custom_endpoint_records")) {
            assertEquals(0L, scalar("SELECT COUNT(*) FROM $table"))
        }
        assertFalse(graph.accountRepository().isAccountSelected())
        assertEquals(PinHealth.ABSENT, graph.accountRepository().getPinHealth())
        check(!preferences.contains("pin_code"))
        database.query("SELECT 1 FROM sqlite_master WHERE " + ARCHIVE_NAMESPACE).use { check(!it.moveToFirst()) }
        database.beginTransaction()
        try {
            database.execSQL("CREATE TABLE nodes (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "name TEXT NOT NULL, link TEXT NOT NULL, networkType INTEGER NOT NULL, isDefault INTEGER NOT NULL, isActive INTEGER NOT NULL)")
            database.execSQL("INSERT INTO nodes VALUES(9001,?,?,999,0,0)", arrayOf(PUBLIC_NAME, PUBLIC_ENDPOINT))
            seedArchive("endpoint_archive", 39, 28, "Earliest public record".toByteArray(), "wss://historical.invalid/public".toByteArray())
            seedArchive("upgrade_entry_endpoint_archive", 76, 71, "Upgrade entry public record".toByteArray(), "wss://entry.invalid/public".toByteArray())
            seedArchive("current_endpoint_archive", 81, 81, NAME_BYTES, ENDPOINT_BYTES)
            database.execSQL("INSERT INTO custom_endpoint_records(id,revision,chainId,url,name,attached,deleted,originalSnapshotRowId) " +
                "VALUES(?,?,?,CAST(? AS TEXT),CAST(? AS TEXT),0,0,?)",
                arrayOf(ROW_ID, INITIAL_REVISION, CHAIN_REFERENCE, ENDPOINT_BYTES, NAME_BYTES, ROW_ID))
            database.setTransactionSuccessful()
        } finally { database.endTransaction() }
        // Admission is real production read code; these synthetic snapshots do not qualify migrations.
        for (source in EndpointInventorySource.values()) {
            val page = EndpointInventoryReader(database).readPage(CancellationSignal(), source)
            check(page.present && page.records.size == 1)
        }
        graph.accountRepository().savePinCode(PUBLIC_PIN)
        assertEquals(PinHealth.HEALTHY, graph.accountRepository().getPinHealth())
        assertEquals(PUBLIC_PIN, graph.accountRepository().getPinCode())
        check(context.getSharedPreferences(MARKER, Context.MODE_PRIVATE).edit().putString("fixture", FIXTURE)
            .putString("nonce", nonce).putInt("uid", Process.myUid()).putString("serial", serial)
            .putString("avd", avd).putString("sourceCommit", sourceCommit).commit())
    }

    private fun seedArchive(prefix: String, edge: Int, original: Int, name: ByteArray, url: ByteArray) {
        check(prefix in listOf("endpoint_archive", "upgrade_entry_endpoint_archive", "current_endpoint_archive"))
        val archive = "${prefix}_v1"; val header = "${prefix}_header_v1"; val schema = "${prefix}_schema_v1"
        database.execSQL("CREATE TABLE $archive (source_rowid INTEGER PRIMARY KEY, chainId, url, name, isActive, isDefault)")
        database.execSQL("CREATE TABLE $header (id INTEGER PRIMARY KEY, format_version INTEGER NOT NULL, source_edge_version INTEGER NOT NULL, " +
            "source_user_version INTEGER NOT NULL, source_row_count INTEGER NOT NULL, source_payload_bytes INTEGER NOT NULL, source_schema_count INTEGER NOT NULL)")
        database.execSQL("CREATE TABLE $schema (source_schema_rowid INTEGER PRIMARY KEY, type, name, tbl_name, sql)")
        database.execSQL("INSERT INTO $archive VALUES(?,?,CAST(? AS TEXT),CAST(? AS TEXT),0,0)", arrayOf(ROW_ID, CHAIN_REFERENCE, url, name))
        database.execSQL("INSERT INTO $schema VALUES(1,'table','chain_nodes','chain_nodes',?)",
            arrayOf("CREATE TABLE chain_nodes (chainId,url,name,isActive,isDefault)"))
        val bytes = CHAIN_REFERENCE.toByteArray().size.toLong() + name.size + url.size + 2L
        database.execSQL("INSERT INTO $header VALUES(1,1,?,?,1,?,1)", arrayOf(edge, original, bytes))
    }

    private fun captureSnapshot(phase: String, expectNativeCatalog: Boolean = true): JSONObject = runBlocking(Dispatchers.IO) {
        check(!database.inTransaction())
        database.beginTransactionNonExclusive()
        val (counts, sql) = try {
            // One connection and SQL snapshot; never compare unrelated pooled PRAGMA values.
            val counts = SavedEndpointInspectionRawSnapshot.noWalletCounts(database)
            assertEquals(0L, counts.getLong("meta_accounts")); assertEquals(0L, counts.getLong("chain_accounts"))
            assertEquals(if (expectNativeCatalog) 2L else 0L, scalar("SELECT COUNT(*) FROM chains"))
            assertEquals(if (expectNativeCatalog) 1L else 0L, scalar("SELECT COUNT(*) FROM chain_nodes"))
            val sql = GlobalSavedEndpointRawSnapshot.sql(database)
            assertEquals("Transaction-bound foreign keys must remain enabled", "[[1]]", sql.getJSONArray("foreignKeys").toString())
            counts to sql
        } finally {
            // No DML or success mark: release the read-only-content snapshot before other stores.
            database.endTransaction()
        }
        val selected = graph.accountRepository().isAccountSelected(); assertFalse(selected)
        val preferences = JSONObject()
        for (name in listOf(SHARED_PREFERENCES_FILE, "key_alias", MARKER))
            preferences.put(name, SavedEndpointInspectionRawSnapshot.preferences(context, name))
        val pin = preferences.getJSONObject(SHARED_PREFERENCES_FILE).getJSONObject("typedMap").getJSONObject("pin_code")
        assertEquals("string", pin.getString("type")); assertTrue(pin.getString("value").isNotEmpty()); assertNotEquals(PUBLIC_PIN, pin.getString("value"))
        return@runBlocking JSONObject().put("phase", phase).put("nonce", nonce).put("api", Build.VERSION.SDK_INT)
            .put("uid", Process.myUid()).put("sourceCommit", sourceCommit)
            .put("sql", sql).put("noWalletCounts", counts)
            .put("selectedAccount", selected).put("preferences", preferences)
    }
    private fun assertProtectedEquals(expected: JSONObject, observed: JSONObject) {
        assertEquals(SavedEndpointInspectionRawSnapshot.canonical(GlobalSavedEndpointRawSnapshot.protectedProjection(expected, MARKER)),
            SavedEndpointInspectionRawSnapshot.canonical(GlobalSavedEndpointRawSnapshot.protectedProjection(observed, MARKER)))
    }

    private fun assertDataCleared(fragment: SavedEndpointsFragment) {
        compose.waitUntil(15_000) { onMain { fragment.viewModel.state.value.let {
            it.locked && !it.loading && it.page == null && it.inspection == null && it.inventoryPage == null && it.inventoryInspection == null
        } } }
    }
    private fun assertLocked(fragment: SavedEndpointsFragment) {
        assertDataCleared(fragment)
        compose.onNodeWithTag("saved_endpoint_collection").assertDoesNotExist()
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
            !it.locked && !it.loading && it.source == source && it.inspection == null && it.inventoryInspection == null &&
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
    private fun inspect(field: EndpointField) {
        val label = text(R.string.saved_endpoint_inspect_field,
            text(if (field == EndpointField.NAME) R.string.saved_endpoints_name else R.string.saved_endpoints_url))
        scrollTo(label); compose.onNodeWithText(label).assertIsEnabled().performClick()
    }
    private fun assertChunk(field: EndpointField, offset: Long) {
        val fragment = awaitFragment<SavedEndpointsFragment>()
        compose.waitUntil(15_000) { onMain { fragment.viewModel.state.value.let { state ->
            val part = state.inventoryInspection
            !state.locked && part != null && !part.loading && part.ref == EndpointRecordRef(EndpointInventorySource.OWNED, ROW_ID, revision) &&
                part.field == field && part.byteOffset == offset && part.chunk != null
        } } }
        val allBytes = if (field == EndpointField.NAME) NAME_BYTES else ENDPOINT_BYTES
        val end = minOf(offset + 512, allBytes.size.toLong()).toInt()
        val expected = allBytes.copyOfRange(offset.toInt(), end)
        val chunk = onMain {
            val part = checkNotNull(fragment.viewModel.state.value.inventoryInspection); assertFalse(part.readFailed)
            checkNotNull(part.chunk).also {
                assertEquals(EndpointRecordRef(EndpointInventorySource.OWNED, ROW_ID, revision), it.ref)
                assertEquals(field, it.field); assertEquals(offset, it.byteOffset); assertEquals(revision, it.observedRevision)
                assertEquals(EndpointOwnership(false, false, ROW_ID), it.ownership)
                assertEquals(EndpointChunkState.AVAILABLE, it.state); assertEquals(EndpointStorage.TEXT, it.storage)
                assertEquals(allBytes.size.toLong(), it.totalByteLength); assertArrayEquals(expected, it.bytes)
                assertTrue(checkNotNull(it.bytes).size <= 512)
            }
        }
        val rendered = if (field == EndpointField.NAME) "\"N\\xff\\u0000\"" else "\"${String(expected, Charsets.US_ASCII)}\""
        val range = text(R.string.saved_endpoints_part_range, (offset + 1).toString(), end.toString(), allBytes.size.toString())
        scrollTo(range); compose.onNodeWithText(range).assertIsDisplayed()
        scrollTo(rendered); compose.onNodeWithTag("saved_endpoint_inventory_chunk").assertTextEquals(rendered).assertIsDisplayed()
        compose.onNode(hasText(rendered) and hasClickAction()).assertDoesNotExist()
        for ((id, enabled) in listOf(R.string.saved_endpoints_part_previous to (chunk.previousByteOffset != null),
            R.string.saved_endpoints_part_next to (chunk.nextByteOffset != null))) {
            val label = text(id); scrollTo(label)
            if (enabled) compose.onNodeWithText(label).assertIsEnabled() else compose.onNodeWithText(label).assertIsNotEnabled()
        }
        assertSecure()
        chunkObservations += JSONObject().put("source", "OWNED").put("rowId", ROW_ID).put("revision", revision)
            .put("field", field.name).put("offset", offset).put("byteCount", expected.size).put("totalBytes", allBytes.size)
            .put("sha256", SavedEndpointInspectionRawSnapshot.sha256(expected)).put("renderedTextExact", true)
    }
    private fun assertStaleRevision() {
        val fragment = awaitFragment<SavedEndpointsFragment>()
        compose.waitUntil(15_000) { onMain { fragment.viewModel.state.value.inventoryInspection?.let {
            !it.loading && it.chunk?.state == EndpointChunkState.STALE_REVISION
        } == true } }
        onMain {
            val part = checkNotNull(fragment.viewModel.state.value.inventoryInspection); assertFalse(part.readFailed)
            assertEquals(EndpointRecordRef(EndpointInventorySource.OWNED, ROW_ID, INITIAL_REVISION), part.ref)
            assertEquals(512L, part.byteOffset)
            val chunk = checkNotNull(part.chunk); assertEquals(part.ref, chunk.ref)
            assertEquals(revision, chunk.observedRevision); assertNull(chunk.bytes); assertNull(chunk.ownership)
            assertNull(chunk.nextByteOffset); assertNull(chunk.previousByteOffset)
        }
        val changed = text(R.string.saved_endpoint_changed); scrollTo(changed); compose.onNodeWithText(changed).assertIsDisplayed()
        compose.onNodeWithTag("saved_endpoint_inventory_chunk").assertDoesNotExist()
        compose.onNodeWithText(text(R.string.common_retry)).assertDoesNotExist()
        for (id in listOf(R.string.saved_endpoints_part_previous, R.string.saved_endpoints_part_next)) {
            val label = text(id); scrollTo(label); compose.onNodeWithText(label).assertIsNotEnabled()
        }
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
        val PROFILES = mapOf(
            36 to Triple("emulator-5748", "FearlessGlobalEndpointApi36_20261020q3", "02d404a37f81495a987dd8c00d918ffa"),
            34 to Triple("emulator-5742", "FearlessGlobalEndpointApi34_20261020", "0b20c87948f84c588daf2d56815f77c9"),
            26 to Triple("emulator-5752", "FearlessGlobalEndpointApi26_20261020q3", "d3a8b1ca1c6842018d887f0764cdc2af")
        )
        const val MARKER = "global_endpoint_auth_fixture_v1"
        const val FIXTURE = "global-endpoint-auth-public-v1"
        const val ROW_ID = -9003L
        const val INITIAL_REVISION = 7L
        const val PUBLIC_PIN = "123456"
        const val PUBLIC_NAME = "Retained endpoint 雪"
        const val PUBLIC_ENDPOINT = "wss://saved-endpoint.invalid/?token=PUBLIC_FIXTURE_ONLY"
        const val CHAIN_REFERENCE = "unattached-public-chain-reference"
        val ARCHIVE_NAMESPACE = listOf("nodes", "endpoint_archive_v1", "endpoint_archive_header_v1", "endpoint_archive_schema_v1",
            "upgrade_entry_endpoint_archive_v1", "upgrade_entry_endpoint_archive_header_v1", "upgrade_entry_endpoint_archive_schema_v1",
            "current_endpoint_archive_v1", "current_endpoint_archive_header_v1", "current_endpoint_archive_schema_v1")
            .joinToString(" OR ") { "lower(name)='$it' OR lower(tbl_name)='$it'" }
        val NAME_BYTES = byteArrayOf('N'.code.toByte(), 0xff.toByte(), 0)
        val ENDPOINT_BYTES = ByteArray(4097).also { bytes ->
            repeat(8) { block ->
                val prefix = "wss://global-inventory.invalid/public-only/part-$block/".toByteArray(Charsets.US_ASCII)
                prefix.copyInto(bytes, block * 512)
                java.util.Arrays.fill(bytes, block * 512 + prefix.size, (block + 1) * 512, ('a'.code + block).toByte())
            }
            bytes[4096] = '#'.code.toByte()
        }
    }
}
