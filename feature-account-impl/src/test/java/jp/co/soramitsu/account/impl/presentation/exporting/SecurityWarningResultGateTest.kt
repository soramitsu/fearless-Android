package jp.co.soramitsu.account.impl.presentation.exporting

import android.os.Bundle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SecurityWarningResultGateTest {

    @Test
    fun `only an exact confirm result is accepted without cancelling`() {
        val result = resultWithAction(SecurityWarningBottomSheet.ACTION_CONFIRM)
        val gate = SecurityWarningResultGate(null)

        assertFalse(gate.consumeResult(result))
        assertTrue(gate.consumeCancellation())
        assertFalse(gate.consumeCancellation())
    }

    @Test
    fun `cancel malformed and duplicate results fail closed exactly once`() {
        listOf(
            SecurityWarningBottomSheet.ACTION_CANCEL,
            "CONFIRM",
            "",
            null
        ).forEach { action ->
            val gate = SecurityWarningResultGate(null)

            assertTrue(gate.consumeResult(resultWithAction(action)))
            assertFalse(gate.consumeResult(resultWithAction(action)))
            assertFalse(gate.consumeCancellation())
        }
    }

    @Test
    fun `saved cancellation state prevents redelivery after recreation`() {
        val savedState = mock<Bundle>()
        whenever(savedState.getBoolean(any())).thenReturn(true)
        val restoredGate = SecurityWarningResultGate(savedState)
        val outputState = mock<Bundle>()

        assertFalse(restoredGate.consumeCancellation())
        assertFalse(
            restoredGate.consumeResult(
                resultWithAction(SecurityWarningBottomSheet.ACTION_CANCEL)
            )
        )
        restoredGate.saveState(outputState)

        verify(outputState).putBoolean(any(), eq(true))
    }

    private fun resultWithAction(action: String?): Bundle {
        return mock<Bundle>().also {
            whenever(
                it.getString(SecurityWarningBottomSheet.RESULT_ACTION_KEY)
            ).thenReturn(action)
        }
    }
}
