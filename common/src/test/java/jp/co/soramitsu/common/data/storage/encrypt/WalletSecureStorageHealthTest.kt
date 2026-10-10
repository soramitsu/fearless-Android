package jp.co.soramitsu.common.data.storage.encrypt

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WalletSecureStorageHealthTest {

    @Before
    fun setUp() {
        WalletSecureStorageHealth.resetForTest()
    }

    @After
    fun tearDown() {
        WalletSecureStorageHealth.resetForTest()
    }

    @Test
    fun `latch is monotonic and listener failures cannot hide it`() {
        val successfulNotifications = AtomicInteger()
        val removeBrokenListener =
            WalletSecureStorageHealth.addProcessRestartRequiredListener {
                error("injected listener failure")
            }
        val removeHealthyListener =
            WalletSecureStorageHealth.addProcessRestartRequiredListener {
                successfulNotifications.incrementAndGet()
            }

        WalletSecureStorageHealth.latchProcessRestartRequired()
        WalletSecureStorageHealth.latchProcessRestartRequired()

        assertFalse(WalletSecureStorageHealth.isHealthy())
        assertEquals(1, successfulNotifications.get())
        val failure = assertThrows(
            WalletSecureStorageUnavailableException::class.java
        ) {
            WalletSecureStorageHealth.requireHealthy()
        }
        assertEquals(
            WalletSecureStorageFailureKind.PROCESS_RESTART_REQUIRED,
            failure.kind
        )

        removeBrokenListener()
        removeHealthyListener()
    }

    @Test
    fun `concurrent registration and latch notifies every listener once`() {
        val listenerCount = 128
        val pool = Executors.newFixedThreadPool(16)
        val start = CountDownLatch(1)
        val callbacks = List(listenerCount) { AtomicInteger() }
        val removals = arrayOfNulls<() -> Unit>(listenerCount)

        try {
            val registrations = callbacks.mapIndexed { index, callbackCount ->
                pool.submit {
                    assertTrue(start.await(10, TimeUnit.SECONDS))
                    removals[index] =
                        WalletSecureStorageHealth
                            .addProcessRestartRequiredListener {
                                callbackCount.incrementAndGet()
                            }
                }
            }
            val latch = pool.submit {
                assertTrue(start.await(10, TimeUnit.SECONDS))
                WalletSecureStorageHealth.latchProcessRestartRequired()
            }

            start.countDown()
            registrations.forEach { it.get(10, TimeUnit.SECONDS) }
            latch.get(10, TimeUnit.SECONDS)
            WalletSecureStorageHealth.latchProcessRestartRequired()

            assertTrue(callbacks.all { it.get() == 1 })
            assertFalse(WalletSecureStorageHealth.isHealthy())
        } finally {
            removals.filterNotNull().forEach { it() }
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS))
        }
    }
}
