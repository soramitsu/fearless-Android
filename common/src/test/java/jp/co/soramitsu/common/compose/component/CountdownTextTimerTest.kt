package jp.co.soramitsu.common.compose.component

import android.content.res.Resources
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.doNothing
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy

/**
 * A Timer composable with a positive duration counts down with [CountdownTextTimer]. When the countdown ends,
 * onFinish() must hand over to the screen's callback once; calling itself instead overflowed the stack and closed
 * the app.
 */
class CountdownTextTimerTest {

    @Test
    fun `a finished countdown clears its text and notifies the screen once`() {
        val texts = mutableListOf<String>()
        var finished = 0
        val timer = spy(
            CountdownTextTimer(
                millis = 5_000,
                deltaTime = 0,
                hideZeroTimer = true,
                extraMessage = null,
                resources = mock(Resources::class.java),
                textUpdate = { texts += it },
                onFinish = { finished++ }
            )
        )
        // CountDownTimer.cancel() is an Android framework method the JVM test runtime does not implement.
        doNothing().`when`(timer).cancel()

        timer.onFinish()

        assertEquals(1, finished)
        assertEquals(listOf(""), texts)
    }
}
