package jp.co.soramitsu.app.root.presentation.main

import android.app.Activity
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
import java.util.UUID
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

/**
 * Real launcher, Hilt graph, Room, encrypted PIN, keypad, navigation and reader.
 * Runs only with a runner-bound disposable UID acknowledgement and offline radios.
 * No authentication callback is injected. Public fixture data is retained for
 * inspection; this same-process lifecycle case does not qualify process death.
 */
@RunWith(AndroidJUnit4::class)
class SavedEndpointsAuthenticationInstrumentedTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private lateinit var graph: SavedEndpointAuthFixtureEntryPoint
    private lateinit var database: SupportSQLiteDatabase
    private val pinDestructions = mutableListOf<JSONObject>()
    private val pinInputReadiness = mutableListOf<JSONObject>()
    private lateinit var expectedSavedEntry: NavBackStackEntry

    @Test
    fun noWalletEntryRequiresRealPinAndRelocksOnBackgroundAndActivityRecreation() {
        prepareOwnedFixture()
        val before = archiveRow()
        val pinCiphertext = context.getSharedPreferences(SHARED_PREFERENCES_FILE, Context.MODE_PRIVATE)
            .getString("pin_code", null)
        assertNotNull(pinCiphertext)
        assertNotEquals(PUBLIC_PIN, pinCiphertext)
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
        val wrongPinLifetime = capturePinLifetime(wrongPin)
        var mismatch = 0
        onMain { wrongPin.viewModel.matchingPincodeErrorEvent.observe(wrongPin) { mismatch++ } }
        enterPin("654321")
        compose.waitUntil(15_000) { onMain { mismatch == 1 } }
        assertSame(wrongPin, onMain { currentFragment() })
        onMain { assertTrue(first.viewModel.state.value.locked); assertNull(first.viewModel.state.value.page) }
        assertSecure()
        onMain { root().onBackPressedDispatcher.onBackPressed() }
        awaitPinDestroyed(wrongPinLifetime)
        assertLocked(awaitFragment())
        assertSecure()

        unlockWithPublicPin()

        val taskId = onMain { root().taskId }
        context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        compose.waitUntil(15_000) { onMain {
            ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .none { it is WalletRootActivity }
        } }
        onMain { assertTrue(first.viewModel.state.value.locked); assertNull(first.viewModel.state.value.page) }
        context.startActivity(Intent(context, WalletRootActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        val resumed = awaitFragment<SavedEndpointsFragment>()
        assertEquals(taskId, onMain { root().taskId })
        assertLocked(resumed)
        assertSecure()

        unlockWithPublicPin()
        val oldActivity = onMain { root() }
        onMain { oldActivity.recreate() }
        compose.waitUntil(30_000) { onMain {
            val activity = resumedRoot()
            activity != null && activity !== oldActivity && currentFragment() is SavedEndpointsFragment
        } }
        assertLocked(awaitFragment())
        onMain { assertTrue(oldActivity.isDestroyed) }
        assertSecure()

        unlockWithPublicPin()
        assertEquals(before, archiveRow())
        assertEquals(0L, scalar("SELECT COUNT(*) FROM meta_accounts"))
        assertFalse(runBlocking(Dispatchers.IO) { graph.accountRepository().isAccountSelected() })
        assertEquals(pinCiphertext, context.getSharedPreferences(SHARED_PREFERENCES_FILE, Context.MODE_PRIVATE)
            .getString("pin_code", null))
        assertEquals(4, pinDestructions.size)
        assertEquals(4, pinInputReadiness.size)
        File(context.cacheDir, "saved-endpoint-auth-${UUID.randomUUID()}.json").writeText(JSONObject()
            .put("api", Build.VERSION.SDK_INT).put("uid", Process.myUid())
            .put("realGraph", true).put("realKeypadPin", true).put("wrongPinDenied", true)
            .put("backgroundRelocked", true).put("activityRecreationRelocked", true)
            .put("secureWindowAfterEveryReturn", true).put("archiveValuesExact", true)
            .put("pinDestructions", JSONArray(pinDestructions))
            .put("pinInputReadiness", JSONArray(pinInputReadiness))
            .put("pinCiphertextExact", true).put("walletCount", 0)
            .put("processDeathQualified", false).toString(2))
        onMain { root().finish() }
    }

    private fun prepareOwnedFixture() {
        val args = InstrumentationRegistry.getArguments()
        check(args.getString("fearlessSavedEndpointOwnedUid") == "exclusively-owned-disposable-app-uid")
        val serial = mapOf(26 to "emulator-5724", 34 to "emulator-5720", 36 to "emulator-5722")[Build.VERSION.SDK_INT]
        check(serial != null && args.getString("fearlessSavedEndpointOwnedSerial") == serial)
        check(Process.myUid() == context.applicationInfo.uid)
        val marker = context.getSharedPreferences("saved_endpoint_auth_fixture_v1", Context.MODE_PRIVATE)
        val existing = marker.getString("fixture", null)
        check(existing == null || existing == FIXTURE)
        val raw = context.getSharedPreferences(SHARED_PREFERENCES_FILE, Context.MODE_PRIVATE)
        if (existing == null) check(!raw.contains("pin_code")) { "Refuse to replace an existing PIN" }
        val path = context.getDatabasePath("app.db")
        if (path.exists()) SQLiteDatabase.openDatabase(path.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT COUNT(*) FROM meta_accounts", null).use { cursor ->
                check(cursor.moveToFirst() && cursor.getLong(0) == 0L) { "Refuse an existing wallet" }
            }
        }
        graph = EntryPointAccessors.fromApplication(context.applicationContext, SavedEndpointAuthFixtureEntryPoint::class.java)
        assertEquals(WalletDatabaseStartupResult.Ready, runBlocking(Dispatchers.IO) { graph.startupGate().open() })
        database = graph.database().openHelper.writableDatabase
        assertEquals(0L, scalar("SELECT COUNT(*) FROM meta_accounts"))
        val hasArchive = scalar("SELECT COUNT(*) FROM sqlite_master WHERE name='nodes'") != 0L
        if (existing == null) {
            check(!hasArchive) { "Refuse to replace an existing endpoint archive" }
            check(marker.edit().putString("fixture", FIXTURE).commit())
            database.execSQL("CREATE TABLE nodes (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "name TEXT NOT NULL, link TEXT NOT NULL, networkType INTEGER NOT NULL, " +
                "isDefault INTEGER NOT NULL, isActive INTEGER NOT NULL)")
            database.execSQL("INSERT INTO nodes(id,name,link,networkType,isDefault,isActive) VALUES(9001,?,?,999,0,0)",
                arrayOf(PUBLIC_NAME, PUBLIC_ENDPOINT))
            runBlocking(Dispatchers.IO) {
                assertEquals(PinHealth.ABSENT, graph.accountRepository().getPinHealth())
                graph.accountRepository().savePinCode(PUBLIC_PIN)
            }
        } else {
            check(hasArchive)
            assertEquals(PUBLIC_PIN, runBlocking(Dispatchers.IO) { graph.accountRepository().getPinCode() })
        }
        assertEquals(1L, scalar("SELECT COUNT(*) FROM nodes"))
        assertEquals(PinHealth.HEALTHY, runBlocking(Dispatchers.IO) { graph.accountRepository().getPinHealth() })
        assertEquals(listOf("9001", PUBLIC_NAME, PUBLIC_ENDPOINT, "999", "0", "0"), archiveRow())
    }

    private fun launch() = context.startActivity(Intent(context, WalletStartupActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))

    private fun unlock() {
        compose.onNodeWithTag("saved_endpoints_list").performScrollToNode(hasText(text(R.string.saved_endpoints_unlock)))
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

    private fun assertLocked(fragment: SavedEndpointsFragment) {
        onMain { assertTrue(fragment.viewModel.state.value.locked); assertNull(fragment.viewModel.state.value.page) }
        compose.onNodeWithText("\"$PUBLIC_ENDPOINT\"").assertDoesNotExist()
        val lockedText = text(R.string.saved_endpoints_locked)
        compose.onNodeWithTag("saved_endpoints_list").performScrollToNode(hasText(lockedText))
        compose.onNodeWithText(lockedText).assertIsDisplayed()
    }

    private fun assertUnlocked(fragment: SavedEndpointsFragment) {
        compose.waitUntil(15_000) { onMain { !fragment.viewModel.state.value.locked && !fragment.viewModel.state.value.loading } }
        onMain {
            val state = fragment.viewModel.state.value
            assertFalse(state.readFailed)
            val record = checkNotNull(state.page).records.single()
            assertEquals(PUBLIC_ENDPOINT, record.endpoint.value)
        }
        compose.onNodeWithTag("saved_endpoints_list").performScrollToNode(hasText("\"$PUBLIC_ENDPOINT\""))
        compose.onNodeWithText("\"$PUBLIC_ENDPOINT\"").assertIsDisplayed()
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
    private fun text(id: Int): String = onMain { root().getString(id) }
    private fun <T> onMain(action: () -> T): T {
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(action) }
        return checkNotNull(result).getOrThrow()
    }
    private fun scalar(sql: String): Long = database.query(sql).use { check(it.moveToFirst()); it.getLong(0) }
    private fun archiveRow(): List<String> = database.query("SELECT id,name,link,networkType,isDefault,isActive FROM nodes").use {
        check(it.moveToFirst()); val row = (0..5).map(it::getString); check(!it.moveToNext()); row
    }
    private companion object {
        const val FIXTURE = "saved-endpoint-auth-public-v1"
        const val PUBLIC_PIN = "123456"
        const val PUBLIC_NAME = "Retained endpoint 雪"
        const val PUBLIC_ENDPOINT = "wss://saved-endpoint.invalid/?token=PUBLIC_FIXTURE_ONLY"
    }
}
