package jp.co.soramitsu.app.root.presentation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusSearchExceptionHandlerTest {

    @Test
    fun `detects focus search crash`() {
        val exception = IllegalStateException(FOCUS_SEARCH_ERROR_MESSAGE)

        assertTrue(exception.isFocusSearchFailure())
    }

    @Test
    fun `ignores unrelated illegal state`() {
        val exception = IllegalStateException("Different error")

        assertFalse(exception.isFocusSearchFailure())
    }
}
