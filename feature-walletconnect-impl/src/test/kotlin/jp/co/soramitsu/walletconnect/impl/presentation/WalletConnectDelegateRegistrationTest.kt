package jp.co.soramitsu.walletconnect.impl.presentation

import com.reown.walletkit.client.Wallet
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class WalletConnectDelegateRegistrationTest {

    @Test
    fun `loading wallet connect delegate does not require initialized clients`() {
        assertNotNull(WCDelegate.walletEvents)
    }

    @Test
    fun `active sessions are empty while wallet client is unavailable`() = runBlocking {
        assertTrue(WCDelegate.activeSessionFlow.first().isEmpty())
    }

    @Test
    fun `retries both delegates when core is initially unavailable`() {
        val coreAttempts = AtomicInteger()
        val walletAttempts = AtomicInteger()
        val registration = WalletConnectDelegateRegistration(
            registerCoreDelegate = {
                if (coreAttempts.incrementAndGet() == 1) {
                    error("Core client is not initialized")
                }
            },
            registerWalletDelegate = {
                walletAttempts.incrementAndGet()
            }
        )

        assertTrue(registration.registerIfReady().isFailure)
        assertEquals(1, coreAttempts.get())
        assertEquals(0, walletAttempts.get())

        assertTrue(registration.registerIfReady().isSuccess)
        assertEquals(2, coreAttempts.get())
        assertEquals(1, walletAttempts.get())
    }

    @Test
    fun `recoverable registration failure stays silent and retries successfully`() {
        val originalErrorStream = System.err
        val capturedErrorStream = ByteArrayOutputStream()
        val coreAttempts = AtomicInteger()
        val successCallbacks = AtomicInteger()
        val registration = WalletConnectDelegateRegistration(
            registerCoreDelegate = {
                if (coreAttempts.incrementAndGet() == 1) {
                    error("Core client is not initialized")
                }
            },
            registerWalletDelegate = {}
        )

        try {
            System.setErr(PrintStream(capturedErrorStream, true, Charsets.UTF_8.name()))
            registration.registerIfReady()
                .onSuccess { successCallbacks.incrementAndGet() }
            registration.registerIfReady()
                .onSuccess { successCallbacks.incrementAndGet() }
        } finally {
            System.setErr(originalErrorStream)
        }

        assertEquals("", capturedErrorStream.toString(Charsets.UTF_8.name()))
        assertEquals(2, coreAttempts.get())
        assertEquals(1, successCallbacks.get())
    }

    @Test
    fun `retries only wallet delegate after core registration succeeds`() {
        val coreAttempts = AtomicInteger()
        val walletAttempts = AtomicInteger()
        val registration = WalletConnectDelegateRegistration(
            registerCoreDelegate = {
                coreAttempts.incrementAndGet()
            },
            registerWalletDelegate = {
                if (walletAttempts.incrementAndGet() == 1) {
                    error("Wallet client is not initialized")
                }
            }
        )

        assertTrue(registration.registerIfReady().isFailure)
        assertEquals(1, coreAttempts.get())
        assertEquals(1, walletAttempts.get())

        assertTrue(registration.registerIfReady().isSuccess)
        assertEquals(1, coreAttempts.get())
        assertEquals(2, walletAttempts.get())
    }

    @Test
    fun `registers delegates only once across concurrent callers`() {
        val coreAttempts = AtomicInteger()
        val walletAttempts = AtomicInteger()
        val registration = WalletConnectDelegateRegistration(
            registerCoreDelegate = {
                coreAttempts.incrementAndGet()
            },
            registerWalletDelegate = {
                walletAttempts.incrementAndGet()
            }
        )
        val callerCount = 16
        val ready = CountDownLatch(callerCount)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(callerCount)
        val results = (1..callerCount).map {
            executor.submit<Boolean> {
                ready.countDown()
                start.await()
                registration.registerIfReady().isSuccess
            }
        }

        try {
            assertTrue(ready.await(5, TimeUnit.SECONDS))
            start.countDown()
            assertTrue(results.all { it.get(5, TimeUnit.SECONDS) })
        } finally {
            start.countDown()
            executor.shutdownNow()
        }

        assertEquals(1, coreAttempts.get())
        assertEquals(1, walletAttempts.get())
    }

    @Test
    fun `contains non readiness runtime failure and permits retry`() {
        val expected = UnsupportedOperationException("Reown storage is unavailable")
        var attempts = 0
        val registration = WalletConnectDelegateRegistration(
            registerCoreDelegate = {
                attempts += 1
                if (attempts == 1) throw expected
            },
            registerWalletDelegate = {}
        )

        val first = registration.registerIfReady()

        assertTrue(first.isFailure)
        assertSame(expected, first.exceptionOrNull())
        assertTrue(registration.registerIfReady().isSuccess)
        assertEquals(2, attempts)
    }

    @Test
    fun `registration does not swallow fatal errors`() {
        val expected = OutOfMemoryError("fatal")
        val registration = WalletConnectDelegateRegistration(
            registerCoreDelegate = { throw expected },
            registerWalletDelegate = {}
        )

        try {
            registration.registerIfReady()
            fail("fatal error was swallowed")
        } catch (actual: OutOfMemoryError) {
            assertSame(expected, actual)
        }
    }

    @Test
    fun `sdk call reports synchronous runtime failure through callback`() {
        val expected = UnsupportedOperationException("Reown is unavailable")
        var reported: Wallet.Model.Error? = null

        callWalletConnect(
            onError = { reported = it },
            operation = { throw expected }
        )

        assertSame(expected, reported?.throwable)
    }

    @Test
    fun `sdk call does not swallow fatal errors`() {
        val expected = OutOfMemoryError("fatal")

        try {
            callWalletConnect(
                onError = { fail("fatal error must not reach recoverable callback") },
                operation = { throw expected }
            )
            fail("fatal error was swallowed")
        } catch (actual: OutOfMemoryError) {
            assertSame(expected, actual)
        }
    }

    @Test
    fun `sdk call does not swallow coroutine cancellation`() {
        val expected = CancellationException("cancelled")

        try {
            callWalletConnect(
                onError = { fail("cancellation must not reach recoverable callback") },
                operation = { throw expected }
            )
            fail("cancellation was swallowed")
        } catch (actual: CancellationException) {
            assertSame(expected, actual)
        }
    }

    @Test
    fun `missing session routes back without dereference`() {
        var backCalls = 0

        val session = walletConnectValueOrBack<String>(
            value = null,
            onUnavailable = { backCalls += 1 }
        )

        assertNull(session)
        assertEquals(1, backCalls)
    }

    @Test
    fun `empty pending request race routes back without indexing`() {
        var backCalls = 0

        val session = newestWalletConnectValueOrBack<String>(
            values = emptyList(),
            onUnavailable = { backCalls += 1 },
            order = { it.length.toLong() }
        )

        assertNull(session)
        assertEquals(1, backCalls)
    }

    @Test
    fun `newest pending request is selected without routing back`() {
        var backCalls = 0

        val session = newestWalletConnectValueOrBack(
            values = listOf("old", "newest"),
            onUnavailable = { backCalls += 1 },
            order = { it.length.toLong() }
        )

        assertEquals("newest", session)
        assertEquals(0, backCalls)
    }
}
