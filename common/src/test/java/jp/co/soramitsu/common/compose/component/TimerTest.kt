package jp.co.soramitsu.common.compose.component

import android.content.Context
import android.os.Looper
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.style.TextAlign
import androidx.test.core.app.ApplicationProvider
import java.time.Duration
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class TimerTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun elapsedTimerShowsFinishedAndReportsCompletion() {
        var finished = 0
        compose.setContent {
            FearlessAppTheme {
                Column {
                    Timer(millis = 0L, onFinish = { finished++ }, modifier = Modifier.testTag("done"), textAlign = TextAlign.Start)
                    Timer(millis = -5L, hideZeroTimer = true, modifier = Modifier.testTag("hidden"), textAlign = TextAlign.End)
                }
            }
        }
        compose.waitForIdle()

        compose.onNodeWithTag("done").assertTextEquals(context.getString(R.string.parachain_staking_request_finished))
        compose.onNodeWithTag("hidden").assertDoesNotExist()
        assertEquals(1, finished)
    }

    @Test
    fun longTimersCountInDays() {
        compose.setContent {
            FearlessAppTheme {
                Timer(
                    millis = 3 * DAY + 60_000L,
                    timeLeftTimestamp = System.currentTimeMillis() - 1_000L,
                    modifier = Modifier.testTag("days"),
                    textAlign = TextAlign.Center
                )
            }
        }
        idle(Duration.ofMillis(10))

        val days = context.resources.getQuantityString(R.plurals.staking_payouts_days_left, 3, 3)
        compose.onNodeWithText(days, substring = true).assertIsDisplayed()
    }





    private fun idle(duration: Duration) {
        compose.waitForIdle()
        shadowOf(Looper.getMainLooper()).idleFor(duration)
        compose.waitForIdle()
    }

    private companion object {
        const val DAY = 24 * 60 * 60 * 1000L
    }
}
