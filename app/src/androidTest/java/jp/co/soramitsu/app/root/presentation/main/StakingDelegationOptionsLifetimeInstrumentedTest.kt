package jp.co.soramitsu.app.root.presentation.main

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.graphics.Rect
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import java.math.BigInteger
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import jp.co.soramitsu.staking.api.domain.model.CandidateInfo
import jp.co.soramitsu.staking.api.domain.model.CandidateInfoStatus
import jp.co.soramitsu.staking.api.domain.model.CandidateCapacity
import jp.co.soramitsu.staking.impl.presentation.staking.main.compose.CollatorStakeInfoViewState
import android.view.inspector.WindowInspector
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.navigation.fragment.NavHostFragment
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import jp.co.soramitsu.app.R
import jp.co.soramitsu.app.root.presentation.WalletRootActivity
import jp.co.soramitsu.app.root.presentation.WalletStartupActivity
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.staking.impl.presentation.staking.main.StakingFragment
import jp.co.soramitsu.staking.impl.presentation.staking.main.compose.StakeInfoViewState
import jp.co.soramitsu.staking.impl.presentation.staking.main.compose.StakeStatus
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith
import java.io.File
import jp.co.soramitsu.common.R as CommonR
import jp.co.soramitsu.feature_account_impl.R as AccountR
import jp.co.soramitsu.feature_staking_impl.R as StakingR

/** Actual StakingFragment entry and Material window lifecycle on the owned offline public host.
 * The collator model is presentation-only synthetic data. No root publication/flow is replaced,
 * no action row is activated, and no rendered-parachain provenance, eligibility or route is claimed.
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class StakingDelegationOptionsLifetimeInstrumentedTest {
    @get:Rule val testName = TestName()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private lateinit var directory: File
    private var sequence = 0
    private var lastPresentation: Presentation? = null

    private class Presentation(
        val fragment: StakingFragment,
        val actionView: View,
        val controller: NavController,
        val owner: NavBackStackEntry
    ) {
        fun isCurrent(): Boolean = fragment.view === actionView && fragment.isResumed &&
            runCatching { controller.currentBackStackEntry === owner }.getOrDefault(false)
    }

    @Before fun startPublicWallet() {
        requirePublicOfflineFixture()
        val label = InstrumentationRegistry.getArguments().getString("delegationOptionsEvidence")
            ?: error("A unique delegationOptionsEvidence label is required")
        require(label.matches(Regex("[a-zA-Z0-9_-]+")))
        directory = File(context.getExternalFilesDir(null), "staking-delegation-options-$label/${testName.methodName}")
        check(!directory.exists()) { "Never overwrite prior native evidence: $directory" }
        check(directory.mkdirs())
        launchStartup()
        enterStaking(awaitUnlockedRoot())
        record("actual-staking-ready")
        assertTrue(onMain { delegationRoots().isEmpty() })
    }

    @After fun closeOwnedHost() {
        if (!::directory.isInitialized) return
        // Cleanup is separate from the original absence assertion and never qualifies a pass.
        runCatching {
            repeat(4) {
                if (onMain { delegationRoots().any { it.hasWindowFocus() } }) {
                    instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
                    SystemClock.sleep(100)
                }
            }
            record("cleanup-before-finish")
            onMain { resumedRoot()?.finish() }
        }.onFailure { failure ->
            File(directory, "cleanup-error.txt").writeText(failure.stackTraceToString())
        }
    }

    @Test fun actualDelegationWindowSupportsOsBackAndFreshPresentation() {
        val first = presentDelegation("first-focused")
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        assertOldWindowAbsent(first, "after-os-back")
        assertTrue(onMain { currentStaking() != null })
        val second = presentDelegation("fresh-after-back")
        assertNotSame(first, second)
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        assertOldWindowAbsent(second, "fresh-closed")
    }

    @Test fun actualDelegationWindowCannotReturnAfterHomeAndResume() {
        val root = onMain { requireNotNull(resumedRoot()) }
        val first = presentDelegation("before-home")
        val presentation = onMain { requireNotNull(lastPresentation) }
        assertTrue(instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME))
        await("actual host paused after Home") { resumedRoot() !== root && !presentation.isCurrent() }
        record("home-host-paused")
        launchStartup()
        enterStaking(awaitUnlockedRoot())
        assertOldWindowAbsent(first, "after-actual-resume")
        assertNotSame(first, presentDelegation("fresh-after-resume"))
    }

    @Test fun actualDelegationWindowCannotFollowWalletTabDepartureAndReturn() {
        val original = onMain { requireNotNull(currentStaking()) }
        val first = presentDelegation("before-wallet-tab")
        val presentation = onMain { requireNotNull(lastPresentation) }
        onMain { assertTrue(selectMainTab(tabs(requireNotNull(resumedRoot())), R.id.portfolioGraph)) }
        await("actual Wallet destination") {
            resumedRoot()?.let { tabs(it).currentDestination?.id == R.id.walletFragment } == true &&
                !original.isResumed && !presentation.isCurrent()
        }
        record("actual-wallet-tab")
        enterStaking(onMain { requireNotNull(resumedRoot()) })
        assertOldWindowAbsent(first, "after-staking-tab-return")
        assertNotSame(first, presentDelegation("fresh-after-tab-return"))
    }

    @Test fun actualDelegationWindowDoesNotSurviveHostRecreation() {
        val originalRoot = onMain { requireNotNull(resumedRoot()) }
        val originalFragment = onMain { requireNotNull(currentStaking()) }
        val first = presentDelegation("before-recreate")
        val presentation = onMain { requireNotNull(lastPresentation) }
        onMain { originalRoot.recreate() }
        await("replacement root Activity") { resumedRoot()?.let { it !== originalRoot } == true && !presentation.isCurrent() }
        val replacement = awaitUnlockedRoot()
        enterStaking(replacement)
        assertNotSame(originalRoot, replacement)
        assertNotSame(originalFragment, onMain { currentStaking() })
        assertOldWindowAbsent(first, "after-recreation")
        assertNotSame(first, presentDelegation("fresh-after-recreation"))
    }

    @Test fun actualDelegationWindowRejectsRetiredScreenEntryAndAllowsCurrentRecovery() {
        val first = presentDelegation("before-retired-entry")
        val presentation = onMain { requireNotNull(lastPresentation) }
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        assertOldWindowAbsent(first, "closed-before-owner-departure")
        onMain { assertTrue(selectMainTab(tabs(requireNotNull(resumedRoot())), R.id.portfolioGraph)) }
        await("retired actual Staking screen owner") {
            resumedRoot()?.let { tabs(it).currentDestination?.id == R.id.walletFragment } == true &&
                !presentation.fragment.isResumed && !presentation.isCurrent()
        }
        onMain {
            recordOnMain("before-retired-production-entry")
            assertFalse(presentation.isCurrent())
            invokeDelegationEntry(presentation)
            recordOnMain("after-retired-production-entry")
            assertTrue("Retired screen entry must not create a native window", delegationRoots().isEmpty())
        }
        enterStaking(onMain { requireNotNull(resumedRoot()) })
        val fresh = presentDelegation("fresh-after-retired-entry")
        assertNotSame(first, fresh)
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        assertOldWindowAbsent(fresh, "fresh-after-retired-entry-closed")
    }

    private fun presentDelegation(phase: String): View {
        onMain {
            val root = requireNotNull(resumedRoot())
            val fragment = requireNotNull(currentStaking())
            val nav = tabs(root)
            val captured = Presentation(fragment, fragment.requireView(), nav, nav.getBackStackEntry(R.id.stakingFragment))
            assertTrue("Exact real view/resumed/back-stack owner admits presentation", captured.isCurrent())
            assertTrue(captured.actionView.isAttachedToWindow)
            assertTrue("No preexisting delegation sheet before fresh entry", delegationRoots().isEmpty())
            lastPresentation = captured
            recordOnMain("before-production-delegation-entry")
            assertTrue("Recheck the exact owner immediately before production entry", captured.isCurrent())
            invokeDelegationEntry(captured)
            recordOnMain("after-production-delegation-entry")
        }
        await("one actual focused row-identified delegation window") {
            delegationRoots().let { it.size == 1 && it.single().hasWindowFocus() && it.single().isShown }
        }
        record(phase)
        return onMain { delegationRoots().single() }
    }

    private fun invokeDelegationEntry(presentation: Presentation) {
        val model = CollatorStakeInfoViewState(
            collatorId = ByteArray(20) { 2 }, collatorAddress = "Public presentation fixture; never routed",
            stakeInfo = StakeInfoViewState.ParachainStakeInfoViewState(
                "Public presentation only", TitleValueViewState("Stake"), TitleValueViewState("Rewards"), StakeStatus.Inactive("")
            ),
            collator = CandidateInfo(
                BigInteger.ONE, BigInteger.ONE, BigInteger.TEN, BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO,
                CandidateCapacity.Partial, CandidateCapacity.Empty, null, CandidateInfoStatus.ACTIVE
            )
        )
        val entry = presentation.fragment.javaClass.declaredMethods.single {
            it.name == "showDelegatorOptions" && it.parameterCount == 2
        }.apply { isAccessible = true }
        entry.invoke(presentation.fragment, model, { presentation.isCurrent() })
    }

    private fun assertOldWindowAbsent(old: View, phase: String) {
        // Test the actual window lifetime, not just a cleared ViewModel event or hidden host.
        record("$phase-before-assert")
        await("old native window detached and no delegation window remains") {
            !old.isAttachedToWindow && WindowInspector.getGlobalWindowViews().none { it === old } && delegationRoots().isEmpty()
        }
        record(phase)
    }

    private fun launchStartup() {
        context.startActivity(Intent(context, WalletStartupActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun awaitUnlockedRoot(): WalletRootActivity {
        var pinEnteredFor: WalletRootActivity? = null
        var unknownFocusRecorded = false
        await("actual public startup and PIN unlock", 60_000) {
            val root = resumedRoot() ?: return@await false
            val focused = WindowInspector.getGlobalWindowViews().singleOrNull {
                it.isAttachedToWindow && it.isShown && it.hasWindowFocus()
            } ?: return@await false
            if (focused !== root.window.decorView) {
                if (dismissKnownStartupWarning(focused)) return@await false
                // A retained delegation window is the object under test after Home. Do not
                // dismiss it or interact with PIN behind it; the strict absence check follows.
                if (focused !in delegationRoots()) {
                    // Activity resume can precede transfer of focus from the startup window.
                    // Wait for readiness, but never interact with an unrecognized window.
                    if (!unknownFocusRecorded) {
                        recordOnMain("startup-awaiting-recognized-focus")
                        unknownFocusRecorded = true
                    }
                    return@await false
                }
                return@await fragments(root.supportFragmentManager).any { it is MainFragment && it.isResumed && it.view != null }
            }
            val digits = listOf(AccountR.id.btn1, AccountR.id.btn2, AccountR.id.btn3, AccountR.id.btn4, AccountR.id.btn5, AccountR.id.btn6)
                .map { root.findViewById<View>(it) }
            if (pinEnteredFor !== root && digits.all { digit ->
                    val bounds = Rect()
                    digit != null && digit.isShown && digit.isEnabled &&
                        digit.getGlobalVisibleRect(bounds) && !bounds.isEmpty
                }) {
                assertTrue("PIN host owns the interaction surface", root.window.decorView.hasWindowFocus())
                recordOnMain("focused-public-pin-before-entry")
                pinEnteredFor = root
                digits.forEach {
                    val digit = requireNotNull(it)
                    assertTrue("PIN host must remain focused for each digit", root.window.decorView.hasWindowFocus())
                    val bounds = Rect()
                    assertTrue(digit.isShown && digit.isEnabled && digit.getGlobalVisibleRect(bounds) && !bounds.isEmpty)
                    assertTrue(digit.performClick())
                }
            }
            fragments(root.supportFragmentManager).any { it is MainFragment && it.isResumed && it.view != null }
        }
        return onMain { requireNotNull(resumedRoot()) }
    }

    private fun dismissKnownStartupWarning(window: View): Boolean {
        val title = window.findViewById<TextView>(CommonR.id.alertTitle)?.text?.toString()
        val message = window.findViewById<TextView>(CommonR.id.alertMessage)?.text?.toString()
        if (title != context.getString(CommonR.string.common_warning) || message !in setOf(
                context.getString(CommonR.string.startup_settings_refresh_unavailable),
                context.getString(CommonR.string.startup_network_refresh_unavailable)
            )) return false
        val button = requireNotNull(window.findViewById<TextView>(CommonR.id.alertSecondaryButton))
        assertEquals(context.getString(CommonR.string.common_continue), button.text.toString())
        assertTrue(window.hasWindowFocus() && button.isShown && button.isEnabled && button.isClickable)
        val bounds = Rect()
        assertTrue("Continue must be visible in the focused warning", button.getGlobalVisibleRect(bounds) && !bounds.isEmpty)
        recordOnMain("focused-startup-warning-before-continue")
        assertTrue("Use the actual warning Continue callback", button.performClick())
        return true
    }

    private fun enterStaking(root: WalletRootActivity) {
        onMain {
            val nav = tabs(root)
            // Do not navigate a resumed Staking screen: doing so could itself dispose an old
            // window and hide the Home/resume regression this fixture is intended to observe.
            if (nav.currentDestination?.id != R.id.stakingFragment) {
                assertTrue(selectMainTab(nav, R.id.defiGraph))
                if (nav.currentDestination?.id != R.id.stakingFragment) nav.navigate(R.id.stakingFragment)
            }
        }
        await("actual resumed StakingFragment") { currentStaking()?.let { it.isResumed && it.view?.isAttachedToWindow == true } == true }
    }

    private fun resumedRoot(): WalletRootActivity? = ActivityLifecycleMonitorRegistry.getInstance()
        .getActivitiesInStage(Stage.RESUMED).filterIsInstance<WalletRootActivity>().singleOrNull()

    private fun currentStaking(): StakingFragment? = resumedRoot()?.let { root ->
        fragments(root.supportFragmentManager).filterIsInstance<StakingFragment>()
            .singleOrNull { it.isResumed && it.view != null && tabs(root).currentDestination?.id == R.id.stakingFragment }
    }

    private fun tabs(root: WalletRootActivity) = (fragments(root.supportFragmentManager)
        .filterIsInstance<MainFragment>().single().childFragmentManager.findFragmentById(R.id.bottomNavHost) as NavHostFragment).navController

    private fun fragments(manager: FragmentManager): List<Fragment> = manager.fragments.flatMap {
        listOf(it) + if (it.isAdded) fragments(it.childFragmentManager) else emptyList()
    }

    private fun rowLabels(root: View): List<String> {
        val container = root.findViewById<ViewGroup>(CommonR.id.fixedListSheetItemContainer) ?: return emptyList()
        return (0 until container.childCount).mapNotNull {
            container.getChildAt(it).findViewById<TextView>(CommonR.id.itemExternalActionContent)?.text?.toString()
        }
    }

    private fun delegationRoots(): List<View> = WindowInspector.getGlobalWindowViews().filter { root ->
        root.isAttachedToWindow && root.findViewById<TextView>(CommonR.id.fixedListSheetTitle)?.text?.toString() ==
            context.getString(StakingR.string.staking_manage_title) && rowLabels(root) == listOf(
                context.getString(CommonR.string.staking_balance_title), context.getString(CommonR.string.your_collator)
            )
    }

    private fun await(description: String, timeout: Long = 10_000, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeout
        do {
            if (onMain {
                    val focused = WindowInspector.getGlobalWindowViews().singleOrNull {
                        it.isAttachedToWindow && it.isShown && it.hasWindowFocus()
                    }
                    // Offline refresh warnings can arrive after setup. Continue only dismisses
                    // this recognized warning; it cannot dismiss a delegation window under test.
                    if (focused != null && dismissKnownStartupWarning(focused)) false else condition()
                }) {
                return
            }
            SystemClock.sleep(25)
        } while (SystemClock.elapsedRealtime() < deadline)
        record("timeout-${++sequence}")
        fail("Timed out waiting for $description")
    }

    private fun <T> onMain(action: () -> T): T {
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(action) }
        return requireNotNull(result).getOrThrow()
    }

    private fun record(phase: String) {
        writeSnapshot(phase, onMain { windowSnapshot(phase) })
    }

    private fun recordOnMain(phase: String) = writeSnapshot(phase, windowSnapshot(phase))

    private fun windowSnapshot(phase: String): JSONObject {
        val root = resumedRoot()
        val staking = currentStaking()
        val captured = lastPresentation
        return JSONObject().put("phase", phase).put("pid", Process.myPid()).put("elapsedRealtime", SystemClock.elapsedRealtime())
            .put("rootIdentity", root?.let(System::identityHashCode))
            .put("rootDecorIdentity", root?.window?.decorView?.let(System::identityHashCode))
            .put("stakingIdentity", staking?.let(System::identityHashCode))
            .put("stakingViewIdentity", staking?.view?.let(System::identityHashCode))
            .put("rootResumed", root != null).put("stakingResumed", staking?.isResumed == true)
            .put("presentationFragmentIdentity", captured?.fragment?.let(System::identityHashCode))
            .put("presentationViewIdentity", captured?.actionView?.let(System::identityHashCode))
            .put("presentationBackStackIdentity", captured?.owner?.let(System::identityHashCode))
            .put("presentationGuardCurrent", captured?.isCurrent())
            .put("actualRootBodyClass", staking?.viewModel?.state?.value?.stakingViewState?.javaClass?.name)
            .put("destination", root?.let { runCatching { tabs(it).currentDestination?.id }.getOrNull() })
            .put("windows", JSONArray(WindowInspector.getGlobalWindowViews().map { view ->
                JSONObject().put("identity", System.identityHashCode(view)).put("class", view.javaClass.name)
                    .put("attached", view.isAttachedToWindow).put("shown", view.isShown)
                    .put("focused", view.hasWindowFocus()).put("windowVisibility", view.windowVisibility)
                    .put("sheetTitle", view.findViewById<TextView>(CommonR.id.fixedListSheetTitle)?.text?.toString())
                    .put("sheetRows", JSONArray(rowLabels(view)))
                    .put("alertTitle", view.findViewById<TextView>(CommonR.id.alertTitle)?.text?.toString())
                    .put("alertMessage", view.findViewById<TextView>(CommonR.id.alertMessage)?.text?.toString())
            }))
    }

    private fun writeSnapshot(phase: String, value: JSONObject) {
        val output = File(directory, "%03d-%s.json".format(++sequence, phase))
        check(!output.exists())
        output.writeText(value.toString(2))
        println("Delegation options native evidence: ${output.name} $value")
    }

    private fun requirePublicOfflineFixture() {
        check(InstrumentationRegistry.getArguments().getString("allowPublicDelegationOptions") == "audit-public-two-offline-v1")
        check(context.packageName == "jp.co.soramitsu.fearless.debug")
        for (key in listOf(Settings.Global.WIFI_ON, "mobile_data")) check(Settings.Global.getInt(context.contentResolver, key) == 0)
        // Read the live installed public database only. Never open frozen evidence DB/WAL/SHM files.
        SQLiteDatabase.openDatabase(context.getDatabasePath("app.db").path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT id,name,isSelected,lower(hex(substrateAccountId)) FROM meta_accounts ORDER BY id", null).use { rows ->
                assertEquals(2, rows.count)
                assertTrue(rows.moveToFirst())
                assertEquals(815818010499064178L, rows.getLong(0)); assertEquals("AuditPublicTwo", rows.getString(1)); assertEquals(1, rows.getInt(2))
                assertEquals("625e25364c7b68e0a83065ccb40afed43f8fe933e669b24f3d69a57eddb3b715", rows.getString(3))
                assertTrue(rows.moveToNext())
                assertEquals(8387175324156294517L, rows.getLong(0)); assertEquals("AuditPublic", rows.getString(1))
                assertEquals("66933bd1f37070ef87bd1198af3dacceb095237f803f3d32b173e6b425ed7972", rows.getString(3))
            }
        }
    }
}
