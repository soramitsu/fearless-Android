package jp.co.soramitsu.app

import java.util.concurrent.CancellationException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class WalletConnectInitializationTest {

    @Test
    fun `contains synchronous sdk runtime failure`() {
        val expected = IllegalStateException("Core client is not initialized")
        var reported: RuntimeException? = null

        val initialized = initializeWalletConnectSafely(
            initialize = { throw expected },
            onFailure = { reported = it }
        )

        assertFalse(initialized)
        assertSame(expected, reported)
    }

    @Test
    fun `reports successful sdk initialization`() {
        var invoked = false

        val initialized = initializeWalletConnectSafely(
            initialize = { invoked = true },
            onFailure = { fail("successful initialization must not report a failure") }
        )

        assertTrue(initialized)
        assertTrue(invoked)
    }

    @Test
    fun `does not swallow fatal virtual machine errors`() {
        val expected = OutOfMemoryError("fatal")

        try {
            initializeWalletConnectSafely(
                initialize = { throw expected },
                onFailure = { fail("fatal errors must not be converted into SDK failures") }
            )
            fail("fatal error was swallowed")
        } catch (actual: OutOfMemoryError) {
            assertSame(expected, actual)
        }
    }

    @Test
    fun `does not swallow cancellation`() {
        val expected = CancellationException("cancelled")

        try {
            initializeWalletConnectSafely(
                initialize = { throw expected },
                onFailure = { fail("cancellation must not be converted into an SDK failure") }
            )
            fail("cancellation was swallowed")
        } catch (actual: CancellationException) {
            assertSame(expected, actual)
        }
    }
}
