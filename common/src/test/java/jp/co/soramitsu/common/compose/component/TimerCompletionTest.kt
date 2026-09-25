package jp.co.soramitsu.common.compose.component

import org.junit.Assert.assertEquals
import org.junit.Test

class TimerCompletionTest {

    @Test
    fun `completion callback is delivered exactly once`() {
        var calls = 0
        val completion = TimerCompletion { calls += 1 }

        repeat(10) { completion.finish() }

        assertEquals(1, calls)
    }
}
