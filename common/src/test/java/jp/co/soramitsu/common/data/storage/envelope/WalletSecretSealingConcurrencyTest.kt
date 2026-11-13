package jp.co.soramitsu.common.data.storage.envelope

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecureStorageHealth
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

class WalletSecretSealingConcurrencyTest {

    @Before
    fun setUp() {
        resetEnvelopeGlobals()
        WalletSecureStorageHealth.resetForTest()
    }

    @After
    fun tearDown() {
        resetEnvelopeGlobals()
        WalletSecureStorageHealth.resetForTest()
    }

    @Test
    fun `PIN operations cannot discard the SK of a write waiting to commit`() {
        checkPendingSeal(migrate = false)
    }

    @Test
    fun `PIN operations cannot discard the SK of a migration waiting to commit`() {
        checkPendingSeal(migrate = true)
    }

    private fun checkPendingSeal(migrate: Boolean) {
        for (operation in PinOperation.entries) {
            resetEnvelopeGlobals()
            val harness = EnvelopeStorageHarness()
            val original = SyntheticSecrets.substrateSr25519(4)
            harness.putLegacy("pin_code", PIN)
            harness.putLegacy(SECRET, original)
            assertEquals(PinVerification.CORRECT, harness.authority.verify(PIN))
            val oldKey = checkNotNull(harness.authority.sessionKey())
            val expected = harness.authority.captureResetSnapshot()
            harness.loseFactor()

            val sealReady = CountDownLatch(1)
            val commitAllowed = CountDownLatch(1)
            val firstSeal = AtomicBoolean(true)
            whenever(harness.crypto.encrypt(any())).thenAnswer {
                val value = it.getArgument<String>(0)
                if (WalletSecretEnvelope.isSealed(value) && firstSeal.compareAndSet(true, false)) {
                    sealReady.countDown()
                    check(commitAllowed.await(10, TimeUnit.SECONDS))
                }
                "v2:test:$value"
            }
            val executor = Executors.newFixedThreadPool(2)
            try {
                val writer = executor.submit {
                    if (migrate) runBlocking { harness.migration.run() } else harness.storage.putEncryptedString(SECRET, original)
                }
                assertTrue(sealReady.await(10, TimeUnit.SECONDS))
                // The migration/writer already owns a copy of SK1. A lock clears only the session copy.
                WalletSecretSession.clear()
                val pinStarted = CountDownLatch(1)
                val pinFinished = CountDownLatch(1)
                val pinOperation = executor.submit<Boolean> {
                    pinStarted.countDown()
                    try {
                        when (operation) {
                            PinOperation.VERIFY -> harness.authority.verify(PIN) == PinVerification.UNREADABLE
                            PinOperation.CHANGE -> runCatching { harness.authority.save(NEW_PIN) }.exceptionOrNull() is IllegalStateException
                            PinOperation.RECOVER -> runCatching {
                                harness.authority.saveRecovered(NEW_PIN, expected, null, forgotten = true)
                            }.exceptionOrNull() is IllegalStateException
                        }
                    } finally {
                        pinFinished.countDown()
                    }
                }
                assertTrue(pinStarted.await(10, TimeUnit.SECONDS))
                // Give an unprotected PIN operation time to run while the writer is parked.
                // The fixed operation waits on the storage monitor until the sealed value commits.
                pinFinished.await(300, TimeUnit.MILLISECONDS)
                commitAllowed.countDown()
                writer.get(10, TimeUnit.SECONDS)
                assertTrue("$operation must keep the SK that sealed the value", pinOperation.get(10, TimeUnit.SECONDS))
                assertArrayEquals(oldKey.keyId, harness.keyring.currentKeyId())
                assertEquals(PIN, harness.inner("pin_code"))
                val stored = checkNotNull(harness.inner(SECRET))
                assertTrue(WalletSecretEnvelope.isSealed(stored))
                assertEquals(original, WalletSecretEnvelopes.open(stored, oldKey.keyId, oldKey.key))
            } finally {
                commitAllowed.countDown()
                executor.shutdownNow()
                assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS))
                oldKey.close()
            }
        }
    }

    @Test
    fun `a write starting during disposable keyring replacement uses the new SK`() {
        val harness = EnvelopeStorageHarness()
        harness.putLegacy("pin_code", PIN)
        assertEquals(PinVerification.CORRECT, harness.authority.verify(PIN))
        val oldId = checkNotNull(harness.keyring.currentKeyId())
        harness.loseFactor()
        val replacementReady = CountDownLatch(1)
        val replacementAllowed = CountDownLatch(1)
        val firstFactor = AtomicBoolean(true)
        whenever(harness.crypto.encrypt(any())).thenAnswer {
            val value = it.getArgument<String>(0)
            if (firstFactor.compareAndSet(true, false)) {
                replacementReady.countDown()
                check(replacementAllowed.await(10, TimeUnit.SECONDS))
            }
            "v2:test:$value"
        }
        val executor = Executors.newFixedThreadPool(2)
        try {
            val replacement = executor.submit<PinVerification> { harness.authority.verify(PIN) }
            assertTrue(replacementReady.await(10, TimeUnit.SECONDS))
            val writerStarted = CountDownLatch(1)
            val writerFinished = CountDownLatch(1)
            val original = SyntheticSecrets.substrateSr25519(5)
            val writer = executor.submit {
                writerStarted.countDown()
                try {
                    harness.storage.putEncryptedString(SECRET, original)
                } finally {
                    writerFinished.countDown()
                }
            }
            assertTrue(writerStarted.await(10, TimeUnit.SECONDS))
            writerFinished.await(300, TimeUnit.MILLISECONDS)
            replacementAllowed.countDown()
            assertEquals(PinVerification.CORRECT, replacement.get(10, TimeUnit.SECONDS))
            writer.get(10, TimeUnit.SECONDS)
            assertFalse(oldId.contentEquals(checkNotNull(harness.keyring.currentKeyId())))
            val stored = checkNotNull(harness.inner(SECRET))
            assertTrue(WalletSecretEnvelope.isSealed(stored))
            assertEquals(original, WalletSecretEnvelopes.open(stored))
        } finally {
            replacementAllowed.countDown()
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS))
        }
    }

    private enum class PinOperation { VERIFY, CHANGE, RECOVER }

    private companion object {
        const val PIN = "604917"
        const val NEW_PIN = "246813"
        const val SECRET = "7:SUBSTRATE_SECRETS"
    }
}
