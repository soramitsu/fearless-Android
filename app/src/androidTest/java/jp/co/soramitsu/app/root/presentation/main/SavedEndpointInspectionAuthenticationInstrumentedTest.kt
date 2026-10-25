package jp.co.soramitsu.app.root.presentation.main

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
import jp.co.soramitsu.app.root.presentation.WalletRootActivity
import jp.co.soramitsu.app.root.presentation.WalletStartupActivity
import jp.co.soramitsu.common.data.storage.encrypt.PinHealth
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
import jp.co.soramitsu.coredb.recovery.LegacyEndpointChunkState
import jp.co.soramitsu.coredb.recovery.LegacyEndpointField
import jp.co.soramitsu.coredb.recovery.LegacyEndpointStorage
import jp.co.soramitsu.coredb.recovery.LegacyEndpointTextState

/**
 * Actual PIN-authorized malformed/oversized inspection on a NEW explicitly owned API36 UID.
 * Test-only public bytes/PIN; no auth/state injection, no existing fixture reuse or process-death claim.
 * A host ownership/build/APK record must independently corroborate the runner-supplied identities.
 */
@RunWith(AndroidJUnit4::class)
class SavedEndpointInspectionAuthenticationInstrumentedTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private lateinit var graph: SavedEndpointAuthFixtureEntryPoint
    private lateinit var database: SupportSQLiteDatabase
    private lateinit var nonce: String
    private lateinit var sourceCommit: String
    private val pinDestructions = mutableListOf<JSONObject>()
    private val pinInputReadiness = mutableListOf<JSONObject>()
    private val chunkObservations = mutableListOf<JSONObject>()
    private lateinit var expectedSavedEntry: NavBackStackEntry

    @Test
    fun realPinAuthorizesExactMalformedAndOversizedPartsAndRevokesThemOnLifecycleExit() {
        prepareFreshOwnedFixture()
        val before = captureSnapshot("before")
        val beforeFile = File(context.cacheDir, "saved-endpoint-inspection-before-$nonce.json")
        val beforeHash = SavedEndpointInspectionRawSnapshot.writeNew(beforeFile, before)
        launch()
        awaitFragment<WelcomeFragment>()
        val savedTitle = text(R.string.saved_endpoints_title)
        compose.waitUntil(15_000) { compose.onAllNodesWithText(savedTitle).fetchSemanticsNodes().size == 1 }
        compose.onNodeWithText(savedTitle).performScrollTo().assertIsDisplayed().performClick()
        val first = awaitFragment<SavedEndpointsFragment>()
        assertLocked(first)
        assertSecure()

        unlock()
        val wrongPin = awaitFragment<PincodeFragment>()
        val wrongLifetime = capturePinLifetime(wrongPin)
        var mismatch = 0
        onMain { wrongPin.viewModel.matchingPincodeErrorEvent.observe(wrongPin) { mismatch++ } }
        enterPin("654321")
        compose.waitUntil(15_000) { onMain { mismatch == 1 } }
        assertSame(wrongPin, onMain { currentFragment() })
        assertDataCleared(first)
        assertSecure()
        onMain { root().onBackPressedDispatcher.onBackPressed() }
        awaitPinDestroyed(wrongLifetime)
        assertLocked(awaitFragment())
        assertSecure()

        unlockWithPublicPin()
        click(R.string.saved_endpoints_inspect_name)
        assertChunk(LegacyEndpointField.NAME, 0)
        click(R.string.saved_endpoints_part_return)
        assertUnlocked(awaitFragment())
        click(R.string.saved_endpoints_inspect_url)
        assertChunk(LegacyEndpointField.ENDPOINT, 0)
        click(R.string.saved_endpoints_part_next)
        assertChunk(LegacyEndpointField.ENDPOINT, 512)
        click(R.string.saved_endpoints_part_previous)
        assertChunk(LegacyEndpointField.ENDPOINT, 0)
        // System Back must return to this saved list rather than leave the destination.
        val savedBeforeBack = awaitFragment<SavedEndpointsFragment>()
        onMain { root().onBackPressedDispatcher.onBackPressed() }
        assertSame(savedBeforeBack, awaitFragment<SavedEndpointsFragment>())
        assertUnlocked(savedBeforeBack)
        click(R.string.saved_endpoints_inspect_url)
        assertChunk(LegacyEndpointField.ENDPOINT, 0)
        for (offset in 512L..4096L step 512L) {
            click(R.string.saved_endpoints_part_next)
            assertChunk(LegacyEndpointField.ENDPOINT, offset)
        }

        val backgrounded = awaitFragment<SavedEndpointsFragment>()
        val taskId = onMain { root().taskId }
        context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        compose.waitUntil(15_000) { onMain {
            ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .none { it is WalletRootActivity }
        } }
        assertDataCleared(backgrounded)
        context.startActivity(Intent(context, WalletRootActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        val resumed = awaitFragment<SavedEndpointsFragment>()
        assertEquals(taskId, onMain { root().taskId })
        assertLocked(resumed)
        assertSecure()
        unlockWithPublicPin()
        click(R.string.saved_endpoints_inspect_url)
        assertChunk(LegacyEndpointField.ENDPOINT, 0)

        val recreatedFrom = awaitFragment<SavedEndpointsFragment>()
        val oldActivity = onMain { root() }
        onMain { oldActivity.recreate() }
        compose.waitUntil(30_000) { onMain {
            val activity = resumedRoot()
            activity != null && activity !== oldActivity && currentFragment() is SavedEndpointsFragment
        } }
        assertLocked(awaitFragment())
        onMain { assertTrue(oldActivity.isDestroyed) }
        assertDataCleared(recreatedFrom)
        assertSecure()
        unlockWithPublicPin()
        click(R.string.saved_endpoints_inspect_name)
        assertChunk(LegacyEndpointField.NAME, 0)

        val after = captureSnapshot("after")
        val afterFile = File(context.cacheDir, "saved-endpoint-inspection-after-$nonce.json")
        val afterHash = SavedEndpointInspectionRawSnapshot.writeNew(afterFile, after)
        assertEquals(SavedEndpointInspectionRawSnapshot.canonical(SavedEndpointInspectionRawSnapshot.protectedProjection(before)),
            SavedEndpointInspectionRawSnapshot.canonical(SavedEndpointInspectionRawSnapshot.protectedProjection(after)))
        assertEquals(4, pinDestructions.size)
        assertEquals(4, pinInputReadiness.size)
        assertEquals(15, chunkObservations.size)
        val proof = JSONObject().put("formatVersion", 1).put("api", Build.VERSION.SDK_INT).put("uid", Process.myUid())
            .put("nonce", nonce).put("sourceCommit", sourceCommit).put("serial", OWNED_SERIAL).put("avd", OWNED_AVD)
            .put("realGraph", true).put("realKeypadPin", true).put("wrongPinDenied", true)
            .put("backgroundRelocked", true).put("activityRecreationRelocked", true)
            .put("oldRecreatedViewModelCleared", true)
            .put("secureWindowAfterEveryReturn", true).put("archiveStorageSchemaSequenceExact", true)
            .put("pinCiphertextExact", true).put("wrappedKeyPreferencesAndXmlExact", true)
            .put("markerPreferencesAndXmlExact", true).put("walletCount", 0)
            .put("systemBackReturnedToSavedList", true).put("processDeathQualified", false)
            .put("pinDestructions", JSONArray(pinDestructions)).put("pinInputReadiness", JSONArray(pinInputReadiness))
            .put("chunks", JSONArray(chunkObservations))
            .put("beforeSnapshot", JSONObject().put("name", beforeFile.name).put("sha256", beforeHash))
            .put("afterSnapshot", JSONObject().put("name", afterFile.name).put("sha256", afterHash))
        SavedEndpointInspectionRawSnapshot.writeNew(File(context.cacheDir, "saved-endpoint-inspection-auth-$nonce.json"), proof)
        onMain { root().finish() }
    }

    private fun prepareFreshOwnedFixture() = runBlocking(Dispatchers.IO) {
        val args = InstrumentationRegistry.getArguments()
        check(Build.VERSION.SDK_INT == 36)
        check(context.packageName == "jp.co.soramitsu.fearless.debug")
        check(args.getString("fearlessInspectionOwnedUid") == "exclusively-owned-disposable-app-uid")
        check(args.getString("fearlessInspectionOwnedSerial") == OWNED_SERIAL)
        check(args.getString("fearlessInspectionOwnedAvd") == OWNED_AVD)
        check(args.getString("fearlessInspectionUid") == Process.myUid().toString())
        check(Process.myUid() == context.applicationInfo.uid)
        nonce = checkNotNull(args.getString("fearlessInspectionNonce")).also { check(it.matches(Regex("[0-9a-f]{32}"))) }
        sourceCommit = checkNotNull(args.getString("fearlessInspectionSourceCommit")).also {
            check(it.matches(Regex("[0-9a-f]{40}")))
        }
        check(context.cacheDir.listFiles().orEmpty().none { it.name.startsWith("saved-endpoint-inspection-") })
        val marker = context.getSharedPreferences(MARKER, Context.MODE_PRIVATE)
        check(marker.all.isEmpty() && !File(context.applicationInfo.dataDir, "shared_prefs/$MARKER.xml").exists())
        check(context.getSharedPreferences("saved_endpoint_auth_fixture_v1", Context.MODE_PRIVATE).all.isEmpty())
        check(!File(context.applicationInfo.dataDir, "shared_prefs/saved_endpoint_auth_fixture_v1.xml").exists())
        val preferences = context.getSharedPreferences(SHARED_PREFERENCES_FILE, Context.MODE_PRIVATE)
        check(!preferences.contains("pin_code")) { "Refuse to replace an existing PIN" }
        val path = context.getDatabasePath("app.db")
        if (path.exists()) SQLiteDatabase.openDatabase(path.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            for (table in listOf("meta_accounts", "chain_accounts")) {
                db.rawQuery("SELECT COUNT(*) FROM $table", null).use {
                    check(it.moveToFirst() && it.getLong(0) == 0L) { "Refuse existing wallet/account" }
                }
            }
            db.rawQuery("SELECT COUNT(*) FROM sqlite_master WHERE lower(name)='nodes' OR lower(tbl_name)='nodes'", null).use {
                check(it.moveToFirst() && it.getLong(0) == 0L) { "Refuse existing archive or partial fixture" }
            }
        }
        graph = EntryPointAccessors.fromApplication(context.applicationContext, SavedEndpointAuthFixtureEntryPoint::class.java)
        assertEquals(WalletDatabaseStartupResult.Ready, graph.startupGate().open())
        database = graph.database().openHelper.writableDatabase
        assertEquals(81L, scalar("PRAGMA user_version"))
        assertEquals(0L, scalar("SELECT COUNT(*) FROM meta_accounts"))
        assertEquals(0L, scalar("SELECT COUNT(*) FROM chain_accounts"))
        assertFalse(graph.accountRepository().isAccountSelected())
        check(!preferences.contains("pin_code"))
        assertEquals(PinHealth.ABSENT, graph.accountRepository().getPinHealth())
        check(scalar("SELECT COUNT(*) FROM sqlite_master WHERE lower(name)='nodes' OR lower(tbl_name)='nodes'") == 0L)
        database.beginTransaction()
        try {
            database.execSQL("CREATE TABLE nodes (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "name TEXT NOT NULL, link TEXT NOT NULL, networkType INTEGER NOT NULL, " +
                "isDefault INTEGER NOT NULL, isActive INTEGER NOT NULL)")
            database.execSQL("INSERT INTO nodes(id,name,link,networkType,isDefault,isActive) " +
                "VALUES(?,CAST(? AS TEXT),CAST(? AS TEXT),999,0,0)", arrayOf(ROW_ID, NAME_BYTES, ENDPOINT_BYTES))
            database.setTransactionSuccessful()
        } finally { database.endTransaction() }
        assertFixtureArchive()
        graph.accountRepository().savePinCode(PUBLIC_PIN)
        assertEquals(PinHealth.HEALTHY, graph.accountRepository().getPinHealth())
        assertEquals(PUBLIC_PIN, graph.accountRepository().getPinCode())
        check(marker.edit().putString("fixture", FIXTURE).putString("nonce", nonce)
            .putInt("uid", Process.myUid()).putString("serial", OWNED_SERIAL).putString("avd", OWNED_AVD)
            .putString("sourceCommit", sourceCommit).commit())
        // Marker is written only after validated seed. Any partial earlier seed is refused on the next attempt.
    }

    private fun assertFixtureArchive() {
        assertEquals(1L, scalar("SELECT COUNT(*) FROM nodes"))
        database.query("SELECT id,typeof(name),hex(CAST(name AS BLOB)),typeof(link),hex(CAST(link AS BLOB))," +
            "networkType,isDefault,isActive FROM nodes").use {
            assertTrue(it.moveToFirst())
            assertEquals(ROW_ID, it.getLong(0)); assertEquals("text", it.getString(1)); assertEquals(hex(NAME_BYTES), it.getString(2))
            assertEquals("text", it.getString(3)); assertEquals(hex(ENDPOINT_BYTES), it.getString(4))
            assertEquals(999L, it.getLong(5)); assertEquals(0L, it.getLong(6)); assertEquals(0L, it.getLong(7))
            assertFalse(it.moveToNext())
        }
        assertEquals(0L, scalar("SELECT seq FROM sqlite_sequence WHERE name='nodes'"))
    }

    private fun captureSnapshot(phase: String): JSONObject = runBlocking(Dispatchers.IO) {
        assertFixtureArchive()
        val counts = SavedEndpointInspectionRawSnapshot.noWalletCounts(database)
        assertEquals(0L, counts.getLong("meta_accounts")); assertEquals(0L, counts.getLong("chain_accounts"))
        val selected = graph.accountRepository().isAccountSelected(); assertFalse(selected)
        val preferences = JSONObject()
        for (name in listOf(SHARED_PREFERENCES_FILE, "key_alias", MARKER)) {
            preferences.put(name, SavedEndpointInspectionRawSnapshot.preferences(context, name))
        }
        val pin = preferences.getJSONObject(SHARED_PREFERENCES_FILE).getJSONObject("typedMap").getJSONObject("pin_code")
        assertEquals("string", pin.getString("type")); assertTrue(pin.getString("value").isNotEmpty())
        assertNotEquals(PUBLIC_PIN, pin.getString("value"))
        assertTrue(preferences.getJSONObject("key_alias").getJSONObject("typedMap").length() > 0)
        JSONObject().put("formatVersion", 1).put("phase", phase).put("nonce", nonce)
            .put("api", Build.VERSION.SDK_INT).put("uid", Process.myUid()).put("sourceCommit", sourceCommit)
            .put("archive", SavedEndpointInspectionRawSnapshot.archive(database))
            .put("noWalletCounts", counts).put("selectedAccount", selected).put("preferences", preferences)
    }

    private fun assertDataCleared(fragment: SavedEndpointsFragment) {
        compose.waitUntil(15_000) { onMain {
            val state = fragment.viewModel.state.value
            state.locked && state.page == null && state.inspection == null && !state.loading
        } }
    }

    private fun assertLocked(fragment: SavedEndpointsFragment) {
        assertDataCleared(fragment)
        compose.onNodeWithTag("saved_endpoint_chunk").assertDoesNotExist()
        compose.onNodeWithText("\"N\\xff\\u0000\"").assertDoesNotExist()
        val label = text(R.string.saved_endpoints_locked)
        scrollTo(label); compose.onNodeWithText(label).assertIsDisplayed()
    }

    private fun assertUnlocked(fragment: SavedEndpointsFragment) {
        compose.waitUntil(15_000) { onMain {
            val state = fragment.viewModel.state.value
            !state.locked && !state.loading && state.inspection == null && state.page != null
        } }
        onMain {
            val state = fragment.viewModel.state.value
            assertFalse(state.readFailed)
            val record = checkNotNull(state.page).records.single()
            assertEquals(ROW_ID, record.originalRowId)
            assertEquals(LegacyEndpointTextState.INVALID_UTF8, record.name.state)
            assertEquals(3L, record.name.byteLength); assertNull(record.name.value)
            assertEquals(LegacyEndpointTextState.OVERSIZED, record.endpoint.state)
            assertEquals(4097L, record.endpoint.byteLength); assertNull(record.endpoint.value)
            assertEquals(LegacyEndpointStorage.TEXT, record.name.storage)
            assertEquals(LegacyEndpointStorage.TEXT, record.endpoint.storage)
        }
        val heading = text(R.string.saved_endpoints_record, ROW_ID.toString())
        scrollTo(heading); compose.onNodeWithText(heading).assertIsDisplayed()
    }

    private fun assertChunk(field: LegacyEndpointField, offset: Long) {
        val fragment = awaitFragment<SavedEndpointsFragment>()
        compose.waitUntil(15_000) { onMain {
            val state = fragment.viewModel.state.value
            val part = state.inspection
            !state.locked && part != null && !part.loading && part.field == field &&
                part.originalRowId == ROW_ID && part.byteOffset == offset && part.chunk != null
        } }
        val allBytes = if (field == LegacyEndpointField.NAME) NAME_BYTES else ENDPOINT_BYTES
        val end = minOf(offset + 512, allBytes.size.toLong()).toInt()
        val expected = allBytes.copyOfRange(offset.toInt(), end)
        val chunk = onMain {
            val state = fragment.viewModel.state.value
            assertFalse(state.locked)
            val part = checkNotNull(state.inspection)
            assertFalse(part.readFailed)
            checkNotNull(part.chunk).also {
                assertEquals(ROW_ID, it.originalRowId); assertEquals(field, it.field); assertEquals(offset, it.byteOffset)
                assertEquals(LegacyEndpointChunkState.AVAILABLE, it.state); assertEquals(LegacyEndpointStorage.TEXT, it.storage)
                assertEquals(allBytes.size.toLong(), it.totalByteLength); assertArrayEquals(expected, it.bytes)
                assertTrue(checkNotNull(it.byteCount) <= 512)
            }
        }
        // Independent oracle: endpoint is printable ASCII with no escapes; malformed name has fixed visible escapes.
        val rendered = if (field == LegacyEndpointField.NAME) "\"N\\xff\\u0000\"" else "\"${String(expected, Charsets.US_ASCII)}\""
        val heading = text(R.string.saved_endpoints_record, ROW_ID.toString())
        scrollTo(heading); compose.onNodeWithText(heading).assertIsDisplayed()
        val range = text(R.string.saved_endpoints_part_range, (offset + 1).toString(), end.toString(), allBytes.size.toString())
        scrollTo(range); compose.onNodeWithText(range).assertIsDisplayed()
        scrollTo(rendered); compose.onNodeWithTag("saved_endpoint_chunk").assertTextEquals(rendered).assertIsDisplayed()
        compose.onNode(hasText(rendered) and hasClickAction()).assertDoesNotExist()
        for ((id, enabled) in listOf(R.string.saved_endpoints_part_previous to (chunk.previousByteOffset != null),
            R.string.saved_endpoints_part_next to (chunk.nextByteOffset != null))) {
            val label = text(id); scrollTo(label)
            if (enabled) compose.onNodeWithText(label).assertIsEnabled() else compose.onNodeWithText(label).assertIsNotEnabled()
        }
        assertSecure()
        chunkObservations += JSONObject().put("rowId", ROW_ID).put("field", field.name).put("offset", offset)
            .put("byteCount", expected.size).put("totalBytes", allBytes.size)
            .put("sha256", SavedEndpointInspectionRawSnapshot.sha256(expected))
            .put("renderedTextExact", true).put("noClickAction", true)
    }

    private fun scrollTo(label: String) = compose.onNodeWithTag("saved_endpoints_list").performScrollToNode(hasText(label))
    private fun click(id: Int) {
        val label = text(id); scrollTo(label); compose.onNodeWithText(label).assertIsDisplayed().assertIsEnabled().performClick()
    }
    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }.uppercase()

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
        const val OWNED_SERIAL = "emulator-5732"
        const val OWNED_AVD = "FearlessEndpointInspectionApi36Correction_20261020"
        const val MARKER = "saved_endpoint_inspection_fixture_v1"
        const val FIXTURE = "saved-endpoint-inspection-public-v1"
        const val ROW_ID = -9002L
        const val PUBLIC_PIN = "123456"
        val NAME_BYTES = byteArrayOf('N'.code.toByte(), 0xff.toByte(), 0)
        val ENDPOINT_BYTES = ByteArray(4097).also { bytes ->
            repeat(8) { block ->
                val prefix = "wss://inspection.invalid/public-only/part-$block/".toByteArray(Charsets.US_ASCII)
                prefix.copyInto(bytes, block * 512)
                java.util.Arrays.fill(bytes, block * 512 + prefix.size, (block + 1) * 512, ('a'.code + block).toByte())
            }
            bytes[4096] = '#'.code.toByte()
        }
    }
}
