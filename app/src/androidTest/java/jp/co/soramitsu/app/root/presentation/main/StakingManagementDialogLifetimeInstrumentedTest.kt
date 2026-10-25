package jp.co.soramitsu.app.root.presentation.main

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.graphics.Rect
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.view.inspector.WindowInspector
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.navigation.fragment.NavHostFragment
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleCallback
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import jp.co.soramitsu.app.R
import jp.co.soramitsu.app.root.presentation.WalletRootActivity
import jp.co.soramitsu.app.root.presentation.WalletStartupActivity
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.staking.impl.presentation.staking.main.ManageStakeAction
import jp.co.soramitsu.staking.impl.presentation.staking.main.StakingFragment
import jp.co.soramitsu.staking.impl.presentation.staking.main.StakingViewState
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

/** Actual startup, PIN, StakingFragment subscriber and Material windows on the opt-in public host.
 * Only the rendered RelayChain.Stake input is synthetic. No management row is activated, no
 * eligibility or staking transaction is simulated, and no wallet/secret/journal fixture is written.
 * This synchronized fixture requires the exact current publication-owning app. Historical mixed
 * APK baselines remain separate evidence; they are not rerun with this helper.
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class StakingManagementDialogLifetimeInstrumentedTest {
    @get:Rule val testName = TestName()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private lateinit var directory: File
    private var sequence = 0
    private var observedFragment: StakingFragment? = null
    private var settledBodyPublication: Triple<Any, Any, Any>? = null
    private var pendingManagementAuthority: (() -> Boolean)? = null
    private val observedManagers = mutableListOf<Pair<FragmentManager, FragmentManager.FragmentLifecycleCallbacks>>()
    private val hostLifecycleObserver = ActivityLifecycleCallback { activity, stage ->
        if (activity is WalletRootActivity && stage == Stage.PRE_ON_CREATE) {
            val observer = object : FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentViewCreated(manager: FragmentManager, fragment: Fragment, view: View, savedState: Bundle?) {
                    if (fragment !is StakingFragment) return
                    observedFragment = fragment
                    settledBodyPublication = null
                    val model = fragment.viewModel
                    model.showManageStakeEvent.observe(fragment.viewLifecycleOwner) { event ->
                        val stack = Thread.currentThread().stackTrace
                        val publication = model.javaClass.getDeclaredField("relayPublication").apply { isAccessible = true }.get(model)
                        val generation = model.javaClass.getDeclaredField("rootScreenGeneration").apply { isAccessible = true }.get(model)
                        val revision = (model.javaClass.getDeclaredField("bodyPublicationRevision").apply { isAccessible = true }
                            .get(model) as kotlinx.coroutines.flow.StateFlow<*>).value
                        if (observedFragment === fragment && stack.any { it.methodName == "publishStakingBody" }) {
                            // Observe a terminal qualification with no relay authority. This does not
                            // infer a null body from the debounced public state snapshot.
                            // Initial StateFlow null and the initial fail-closed clear are insufficient.
                            settledBodyPublication = if (event?.peekContent() == null && publication == null &&
                                stack.any { it.methodName == "qualifyStakingBody" }) Triple(model, generation, requireNotNull(revision)) else null
                        }
                        writeSnapshot("public-management-event", windowSnapshot("public-management-event")
                            .put("observerStack", JSONArray(stack.map { it.toString() }))
                            .put("observedViewModelIdentity", System.identityHashCode(model))
                            .put("observedGenerationIdentity", System.identityHashCode(generation))
                            .put("observedRevisionIdentity", revision?.let(System::identityHashCode))
                            .put("terminalPublicationWithoutRelayAuthority", settledBodyPublication?.first === model))
                    }
                }
            }
            activity.supportFragmentManager.registerFragmentLifecycleCallbacks(observer, true)
            observedManagers += activity.supportFragmentManager to observer
        }
    }

    @Before fun startPublicWallet() {
        requirePublicOfflineFixture()
        val label = InstrumentationRegistry.getArguments().getString("managementDialogEvidence")
            ?: error("A unique managementDialogEvidence label is required")
        require(label.matches(Regex("[a-zA-Z0-9_-]+")))
        directory = File(context.getExternalFilesDir(null), "staking-management-dialog-$label/${testName.methodName}")
        check(!directory.exists()) { "Never overwrite prior native evidence: $directory" }
        check(directory.mkdirs())
        onMain { ActivityLifecycleMonitorRegistry.getInstance().addLifecycleCallback(hostLifecycleObserver) }
        launchStartup()
        enterStaking(awaitUnlockedRoot())
        record("actual-staking-ready")
        assertTrue(onMain { managementRoots().isEmpty() })
    }

    @After fun closeOwnedHost() {
        if (!::directory.isInitialized) return
        // Cleanup cannot replace the original assertion with a secondary host/focus exception.
        runCatching {
            repeat(4) {
                if (onMain { managementRoots().any { it.hasWindowFocus() } }) {
                    instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
                    SystemClock.sleep(100)
                }
            }
            record("cleanup-before-finish")
            onMain { resumedRoot()?.finish() }
        }.onFailure { failure ->
            File(directory, "cleanup-error.txt").writeText(failure.stackTraceToString())
        }
        onMain {
            ActivityLifecycleMonitorRegistry.getInstance().removeLifecycleCallback(hostLifecycleObserver)
            observedManagers.forEach { (manager, observer) -> manager.unregisterFragmentLifecycleCallbacks(observer) }
        }
    }

    @Test fun actualManagementWindowSupportsOsBackAndFreshPresentation() {
        val first = presentManagement("first-focused")
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        assertOldWindowAbsent(first, "after-os-back")
        assertTrue(onMain { currentStaking() != null })
        val second = presentManagement("fresh-after-back")
        assertNotSame(first, second)
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        assertOldWindowAbsent(second, "fresh-closed")
    }

    @Test fun actualManagementWindowCannotReturnAfterHomeAndResume() {
        val root = onMain { requireNotNull(resumedRoot()) }
        val first = presentManagement("before-home")
        assertTrue(instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME))
        await("actual host paused after Home") { resumedRoot() !== root }
        record("home-host-paused")
        launchStartup()
        enterStaking(awaitUnlockedRoot())
        assertOldWindowAbsent(first, "after-actual-resume")
        assertNotSame(first, presentManagement("fresh-after-resume"))
    }

    @Test fun actualManagementWindowCannotFollowWalletTabDepartureAndReturn() {
        val original = onMain { requireNotNull(currentStaking()) }
        val first = presentManagement("before-wallet-tab")
        onMain { assertTrue(selectMainTab(tabs(requireNotNull(resumedRoot())), R.id.portfolioGraph)) }
        await("actual Wallet destination") {
            resumedRoot()?.let { tabs(it).currentDestination?.id == R.id.walletFragment } == true && !original.isResumed
        }
        record("actual-wallet-tab")
        enterStaking(onMain { requireNotNull(resumedRoot()) })
        assertOldWindowAbsent(first, "after-staking-tab-return")
        assertNotSame(first, presentManagement("fresh-after-tab-return"))
    }

    @Test fun actualManagementWindowDoesNotSurviveHostRecreation() {
        val originalRoot = onMain { requireNotNull(resumedRoot()) }
        val originalFragment = onMain { requireNotNull(currentStaking()) }
        val first = presentManagement("before-recreate")
        onMain { originalRoot.recreate() }
        await("replacement root Activity") { resumedRoot()?.let { it !== originalRoot } == true }
        val replacement = awaitUnlockedRoot()
        enterStaking(replacement)
        assertNotSame(originalRoot, replacement)
        assertNotSame(originalFragment, onMain { currentStaking() })
        assertOldWindowAbsent(first, "after-recreation")
        assertNotSame(first, presentManagement("fresh-after-recreation"))
    }

    @Test fun replacementManagementPresentationOwnsOnlyOneActualWindow() {
        val first = presentManagement("presentation-a")
        publishSyntheticPresentation()
        await("replacement B owns one focused window, A detached") {
            val roots = managementRoots()
            roots.size == 1 && roots.single() !== first && !first.isAttachedToWindow && roots.single().hasWindowFocus()
        }
        record("presentation-b-only")
        val second = onMain { managementRoots().single() }
        assertNotSame(first, second)
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        assertOldWindowAbsent(second, "replacement-b-closed")
        assertNotSame(second, presentManagement("fresh-after-replacement-dismissal"))
    }

    private fun presentManagement(phase: String): View {
        publishSyntheticPresentation()
        await("one actual focused management window") {
            managementRoots().let { it.size == 1 && it.single().hasWindowFocus() && it.single().isShown }
        }
        record(phase)
        return onMain { managementRoots().single() }
    }

    private fun publishSyntheticPresentation() {
        var admittedProof: Triple<Any, Any, Any>? = null
        await("actual terminal source publication without relay authority for the current root revision") {
            val fragment = currentStaking() ?: return@await false
            val model = fragment.viewModel
            val proof = settledBodyPublication ?: return@await false
            val current = proof.first === model && observedFragment === fragment &&
                proof.second === model.javaClass.getDeclaredField("rootScreenGeneration").apply { isAccessible = true }.get(model) &&
                proof.third === (model.javaClass.getDeclaredField("bodyPublicationRevision").apply { isAccessible = true }
                    .get(model) as kotlinx.coroutines.flow.StateFlow<*>).value
            if (current) admittedProof = proof
            current
        }
        onMain {
            val fragment = requireNotNull(currentStaking())
            assertTrue(fragment.isResumed && fragment.requireView().isAttachedToWindow)
            assertEquals(R.id.stakingFragment, tabs(requireNotNull(resumedRoot())).currentDestination?.id)
            val settledSourceProof = requireNotNull(admittedProof)
            assertSame("No publication may replace the proof between readiness and injection", settledSourceProof, settledBodyPublication)
            assertSame(fragment, observedFragment)
            assertSame(fragment.viewModel, settledSourceProof.first)
            assertSame(settledSourceProof.second, fragment.viewModel.javaClass.getDeclaredField("rootScreenGeneration")
                .apply { isAccessible = true }.get(fragment.viewModel))
            assertSame(settledSourceProof.third, (fragment.viewModel.javaClass.getDeclaredField("bodyPublicationRevision")
                .apply { isAccessible = true }.get(fragment.viewModel) as kotlinx.coroutines.flow.StateFlow<*>).value)
            recordOnMain("before-synthetic-publication")
            // Synthetic raw body only; actual Fragment and Material window still handle the event.
            val raw = StakingViewState.RelayChain.Stake(
                StakeInfoViewState.RelayChainStakeInfoViewState(
                    "Public synthetic presentation only", TitleValueViewState("Stake"),
                    TitleValueViewState("Rewards"), StakeStatus.Inactive("")
                ),
                setOf(ManageStakeAction.BALANCE),
                StakingViewState.StatusInfo("Public fixture", "No eligibility or submission claim")
            )
            val publicationMethod = fragment.viewModel.javaClass.declaredMethods.single {
                it.name == "publishStakingBody" && it.parameterCount == 3
            }.apply { isAccessible = true }
            val shared = fragment.viewModel.javaClass.getDeclaredField("stakingSharedState")
                .apply { isAccessible = true }.get(fragment.viewModel) as jp.co.soramitsu.staking.api.data.StakingSharedState
            val published = publicationMethod.invoke(fragment.viewModel, raw,
                jp.co.soramitsu.common.data.storage.WalletSelectionEpoch.snapshot(), shared.selectionRevision.capture()
            ) as StakingViewState.RelayChain.Stake
            // Preserve the settled-source proof across this one intentional synthetic publication.
            val proof = settledSourceProof
            fragment.viewModel.onManageRelayChainStake(published)
            val model = fragment.viewModel
            val payload = requireNotNull(model.showManageStakeEvent.value?.peekContent())
            val publication = requireNotNull(model.javaClass.getDeclaredField("relayPublication").apply { isAccessible = true }.get(model))
            pendingManagementAuthority = {
                currentStaking() === fragment && model.isManagementPresentationCurrent(payload) &&
                    model.showManageStakeEvent.value?.peekContent() === payload &&
                    model.javaClass.getDeclaredField("relayPublication").apply { isAccessible = true }.get(model) === publication
            }
            settledBodyPublication = proof
            recordOnMain("after-synthetic-publication")
        }
    }

    private fun assertOldWindowAbsent(old: View, phase: String) {
        // Test the actual window lifetime, not just a cleared ViewModel event or hidden host.
        record("$phase-before-assert")
        await("old native window detached and no management window remains") {
            !old.isAttachedToWindow && WindowInspector.getGlobalWindowViews().none { it === old } && managementRoots().isEmpty()
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
                // A retained management window is the object under test after Home. Do not
                // dismiss it or interact with PIN behind it; the strict absence check follows.
                if (focused !in managementRoots()) {
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

    private fun managementRoots(): List<View> = WindowInspector.getGlobalWindowViews().filter { root ->
        root.isAttachedToWindow && root.findViewById<TextView>(CommonR.id.fixedListSheetTitle)?.text?.toString() ==
            context.getString(StakingR.string.staking_manage_title)
    }

    private fun await(description: String, timeout: Long = 10_000, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeout
        do {
            if (onMain {
                    pendingManagementAuthority?.let { authority ->
                        val current = authority()
                        if (!current) recordOnMain("authority-revoked-before-native-window")
                        assertTrue("Exact synthetic payload and publication must remain current while awaiting its window", current)
                    }
                    val focused = WindowInspector.getGlobalWindowViews().singleOrNull {
                        it.isAttachedToWindow && it.isShown && it.hasWindowFocus()
                    }
                    // Offline refresh warnings can arrive after setup. Continue only dismisses
                    // this recognized warning; it cannot dismiss a management window under test.
                    if (focused != null && dismissKnownStartupWarning(focused)) false else condition()
                }) {
                onMain { pendingManagementAuthority = null }
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
        val model = staking?.viewModel
        val event = model?.showManageStakeEvent?.value
        val payload = event?.peekContent()
        // Read the individual production predicates without mutating or extending their lease.
        // Object identities and process-local revisions contain no wallet addresses or secrets.
        val presentation = model?.let { it.javaClass.getDeclaredField("managementPresentation").apply { isAccessible = true }.get(it) }
        val request = presentation?.let { it.javaClass.getDeclaredField("request").apply { isAccessible = true }.get(it) }
        val requestWallet = request?.let { it.javaClass.getDeclaredField("wallet").apply { isAccessible = true }.getLong(it) }
        val requestSelection = request?.let { it.javaClass.getDeclaredField("selection").apply { isAccessible = true }.get(it) }
        val shared = model?.let { it.javaClass.getDeclaredField("stakingSharedState").apply { isAccessible = true }.get(it) }
            as? jp.co.soramitsu.staking.api.data.StakingSharedState
        val relay = model?.let { it.javaClass.getDeclaredField("relayPublication").apply { isAccessible = true }.get(it) }
        val relayWallet = relay?.let { it.javaClass.getDeclaredField("wallet").apply { isAccessible = true }.getLong(it) }
        val relaySelection = relay?.let { it.javaClass.getDeclaredField("selection").apply { isAccessible = true }.get(it) }
        val currentSelection = shared?.selectionRevision?.capture()
        val authority = JSONObject()
            .put("presentationIdentity", presentation?.let(System::identityHashCode))
            .put("presentationPayloadMatches", presentation?.let {
                it.javaClass.getDeclaredField("payload").apply { isAccessible = true }.get(it) === payload
            })
            .put("requestIdentity", request?.let(System::identityHashCode))
            .put("requestActive", request?.let { it.javaClass.getDeclaredField("active").apply { isAccessible = true }.getBoolean(it) })
            .put("requestScreenIdentity", request?.let { it.javaClass.getDeclaredField("screen").apply { isAccessible = true }.get(it) }?.let(System::identityHashCode))
            .put("screenCallbackCurrent", request?.let {
                @Suppress("UNCHECKED_CAST")
                val callback = it.javaClass.getDeclaredField("isScreenCurrent").apply { isAccessible = true }.get(it) as () -> Boolean
                callback()
            })
            .put("ownerJobActive", model?.let { it.javaClass.getDeclaredMethod("isRootOwnerActive").apply { isAccessible = true }.invoke(it) })
            .put("requestWalletEpoch", requestWallet)
            .put("walletEpochSignal", jp.co.soramitsu.common.data.storage.WalletSelectionEpoch.changes.value)
            .put("walletEpochStable", runCatching { jp.co.soramitsu.common.data.storage.WalletSelectionEpoch.snapshot() }.getOrNull())
            .put("requestWalletCurrent", requestWallet?.let(jp.co.soramitsu.common.data.storage.WalletSelectionEpoch::isCurrent))
            .put("requestSelectionIdentity", requestSelection?.let(System::identityHashCode))
            .put("currentSelectionIdentity", currentSelection?.let(System::identityHashCode))
            .put("requestSelectionCurrent", requestSelection != null && requestSelection === currentSelection)
            .put("requestRelayIdentity", request?.let { it.javaClass.getDeclaredField("relay").apply { isAccessible = true }.get(it) }?.let(System::identityHashCode))
            .put("relayWalletEpoch", relayWallet)
            .put("relayWalletCurrent", relayWallet?.let(jp.co.soramitsu.common.data.storage.WalletSelectionEpoch::isCurrent))
            .put("relaySelectionIdentity", relaySelection?.let(System::identityHashCode))
            .put("relaySelectionCurrent", relaySelection != null && relaySelection === currentSelection)
        return JSONObject().put("phase", phase).put("pid", Process.myPid()).put("elapsedRealtime", SystemClock.elapsedRealtime())
            .put("rootIdentity", root?.let(System::identityHashCode))
            .put("rootDecorIdentity", root?.window?.decorView?.let(System::identityHashCode))
            .put("stakingIdentity", staking?.let(System::identityHashCode))
            .put("stakingViewIdentity", staking?.view?.let(System::identityHashCode))
            .put("rootResumed", root != null).put("stakingResumed", staking?.isResumed == true)
            .put("viewModelIdentity", model?.let(System::identityHashCode))
            .put("managementEventIdentity", event?.let(System::identityHashCode))
            .put("managementPayloadIdentity", payload?.let(System::identityHashCode))
            .put("authorityPredicates", authority)
            .put("managementCurrent", if (model != null && payload != null) model.isManagementPresentationCurrent(payload) else false)
            .put("recoveryScreenActive", model?.let { it.javaClass.getDeclaredField("recoveryScreenActive").apply { isAccessible = true }.get(it) })
            .put("relayPublicationIdentity", model?.let { it.javaClass.getDeclaredField("relayPublication").apply { isAccessible = true }.get(it) }?.let(System::identityHashCode))
            .put("bodyRevisionIdentity", model?.let { (it.javaClass.getDeclaredField("bodyPublicationRevision").apply { isAccessible = true }.get(it) as kotlinx.coroutines.flow.StateFlow<*>).value }?.let(System::identityHashCode))
            .put("screenGenerationIdentity", model?.let { it.javaClass.getDeclaredField("rootScreenGeneration").apply { isAccessible = true }.get(it) }?.let(System::identityHashCode))
            .put("actualRootBodyClass", model?.state?.value?.stakingViewState?.javaClass?.name)
            .put("destination", root?.let { runCatching { tabs(it).currentDestination?.id }.getOrNull() })
            .put("windows", JSONArray(WindowInspector.getGlobalWindowViews().map { view ->
                JSONObject().put("identity", System.identityHashCode(view)).put("class", view.javaClass.name)
                    .put("attached", view.isAttachedToWindow)
                    .put("shown", view.isShown).put("focused", view.hasWindowFocus()).put("windowVisibility", view.windowVisibility)
                    .put("managementTitle", view.findViewById<TextView>(CommonR.id.fixedListSheetTitle)?.text?.toString())
                    .put("alertTitle", view.findViewById<TextView>(CommonR.id.alertTitle)?.text?.toString())
                    .put("alertMessage", view.findViewById<TextView>(CommonR.id.alertMessage)?.text?.toString())
            }))
    }

    private fun writeSnapshot(phase: String, value: JSONObject) {
        val output = File(directory, "%03d-%s.json".format(++sequence, phase))
        check(!output.exists())
        output.writeText(value.toString(2))
        println("Management native evidence: ${output.name} $value")
    }

    private fun requirePublicOfflineFixture() {
        check(InstrumentationRegistry.getArguments().getString("allowPublicManagementPresentation") == "audit-public-two-offline-v1")
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
