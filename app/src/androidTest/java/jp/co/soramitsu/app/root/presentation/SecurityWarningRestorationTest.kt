package jp.co.soramitsu.app.root.presentation

import android.content.Intent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import java.io.FileInputStream
import jp.co.soramitsu.common.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class SecurityWarningRestorationTest {

    private val instrumentation
        get() = InstrumentationRegistry.getInstrumentation()

    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Before
    fun setUp() {
        SecurityWarningRestorationTestController.reset()
    }

    @After
    fun tearDown() {
        SecurityWarningRestorationTestController.reset()
    }

    @Test
    fun restoredCancelButtonEmitsOnceAndExitsThroughResultListener() {
        launchHost().use { scenario ->
            var originalSheetIdentity = 0
            scenario.onActivity { activity ->
                assertNotNull(activity.currentSheet())
                originalSheetIdentity =
                    System.identityHashCode(activity.currentSheet())
                assertEquals(1, activity.activeSheetCount())
            }

            scenario.recreate()
            waitForIdle()

            scenario.onActivity { activity ->
                assertEquals(
                    1,
                    SecurityWarningRestorationTestController
                        .restoredActivityCount.get()
                )
                assertNotNull(activity.currentSheet())
                assertNotEquals(
                    originalSheetIdentity,
                    System.identityHashCode(activity.currentSheet())
                )
                assertEquals(1, activity.activeSheetCount())
                assertEquals(
                    "Activity recreation must not be treated as cancellation",
                    0,
                    SecurityWarningRestorationTestController
                        .cancellationCount.get()
                )
            }

            val cancelText = instrumentation.targetContext.getString(
                R.string.common_cancel
            )
            waitForButton(cancelText)
            composeRule.onNodeWithText(cancelText).performTouchInput {
                doubleClick()
            }

            waitForCancellationAndExit(scenario)
        }
    }

    @Test
    fun restoredSystemCancelEmitsOnceAndExitsThroughResultListener() {
        launchHost().use { scenario ->
            scenario.recreate()
            waitForIdle()

            scenario.onActivity { activity ->
                assertNotNull(activity.currentSheet())
                activity.cancelCurrentSheet()
            }

            waitForCancellationAndExit(scenario)
        }
    }

    @Test
    fun restoredSystemBackEmitsOnceAndExitsThroughResultListener() {
        launchHost().use { scenario ->
            scenario.recreate()
            waitForIdle()

            scenario.onActivity { activity ->
                assertNotNull(activity.currentSheet())
            }
            waitForSheetWindowFocus(scenario)
            pressSystemBack()

            waitForCancellationAndExit(scenario)
        }
    }

    private fun waitForCancellationAndExit(
        scenario: ActivityScenario<SecurityWarningRestorationActivityTestHost>
    ) {
        composeRule.waitUntil(timeoutMillis = UI_TIMEOUT_MILLIS) {
            SecurityWarningRestorationTestController.cancellationCount.get() == 1 &&
                scenario.state == Lifecycle.State.DESTROYED
        }
        assertEquals(
            1,
            SecurityWarningRestorationTestController.resultCount.get()
        )
        assertEquals(
            1,
            SecurityWarningRestorationTestController.cancellationCount.get()
        )
    }

    private fun waitForButton(text: String) {
        composeRule.waitUntil(timeoutMillis = UI_TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText(text)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private fun waitForSheetWindowFocus(
        scenario: ActivityScenario<SecurityWarningRestorationActivityTestHost>
    ) {
        composeRule.waitUntil(timeoutMillis = UI_TIMEOUT_MILLIS) {
            var focused = false
            scenario.onActivity { activity ->
                focused = activity.currentSheet()
                    ?.dialog
                    ?.window
                    ?.decorView
                    ?.hasWindowFocus() == true
            }
            focused
        }
    }

    private fun launchHost():
        ActivityScenario<SecurityWarningRestorationActivityTestHost> {
        val context = instrumentation.targetContext
        return ActivityScenario.launch(
            Intent(
                context,
                SecurityWarningRestorationActivityTestHost::class.java
            )
        )
    }

    private fun waitForIdle() {
        instrumentation.waitForIdleSync()
        composeRule.waitForIdle()
    }

    private fun pressSystemBack() {
        instrumentation.uiAutomation
            .executeShellCommand("input keyevent KEYCODE_BACK")
            .use { commandOutput ->
                FileInputStream(commandOutput.fileDescriptor).use {
                    it.readBytes()
                }
            }
        waitForIdle()
    }

    private companion object {
        const val UI_TIMEOUT_MILLIS = 10_000L
    }
}
