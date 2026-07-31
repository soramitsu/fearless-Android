package jp.co.soramitsu.backup.passkey

import androidx.credentials.CredentialManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.lang.reflect.Proxy
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class PasskeyBackupWorkflowTest {
    @Test
    fun `begin registration creates credential request from remote challenge`() = runBlocking {
        val challenge = ByteArray(32) { it.toByte() }
        val userId = ByteArray(32) { (it + 17).toByte() }
        val service = FakeChallengeService(
            registrationChallenge = PasskeyBackupRegistrationChallenge(
                registrationId = "registration-1234",
                challenge = challenge,
                userId = userId,
                userName = "alice@example.com",
                displayName = "Alice",
                storageKey = "wallet-1234"
            )
        )
        val workflow = workflow(service = service)

        val pending = workflow.beginRegistration(
            walletId = " wallet-001 ",
            accountName = " alice@example.com ",
            displayName = " Alice "
        )

        assertEquals("registration-1234", pending.registrationId)
        assertEquals("wallet-1234", pending.storageKey)
        assertEquals("wallet-001", service.registrationWalletId)
        assertEquals("alice@example.com", service.registrationAccountName)
        assertEquals("Alice", service.registrationDisplayName)
        assertTrue(pending.requestJson.contains("\"rp\":{\"id\":\"fearlesswallet.io\""))
        assertTrue(pending.requestJson.contains("\"name\":\"alice@example.com\""))
        assertTrue(pending.requestJson.contains("\"userVerification\":\"required\""))
    }

    @Test
    fun `begin registration rejects blank Google account before challenge service`() = runBlocking {
        val service = FakeChallengeService()
        val workflow = workflow(service = service)

        try {
            workflow.beginRegistration(
                walletId = "wallet-001",
                accountName = "   ",
                displayName = "Alice"
            )
            fail("expected blank Google account to reject")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Google account is required") == true)
        }

        assertEquals(null, service.registrationWalletId)
        assertEquals(null, service.registrationAccountName)
    }

    @Test
    fun `begin registration rejects challenge account mismatch before request creation`() = runBlocking {
        val service = FakeChallengeService(
            registrationChallenge = PasskeyBackupRegistrationChallenge(
                registrationId = "registration-1234",
                challenge = ByteArray(32) { it.toByte() },
                userId = ByteArray(32) { (it + 17).toByte() },
                userName = "mallory@example.com",
                displayName = "Mallory",
                storageKey = "wallet-1234"
            )
        )
        val workflow = workflow(service = service)

        try {
            workflow.beginRegistration(
                walletId = "wallet-001",
                accountName = "alice@example.com",
                displayName = "Alice"
            )
            fail("expected mismatched Google account to reject")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("mismatched Google account") == true)
        }

        assertEquals("wallet-001", service.registrationWalletId)
        assertEquals("alice@example.com", service.registrationAccountName)
    }

    @Test
    fun `workflow fails closed while passkey backup is not release enabled`() = runBlocking {
        val service = FakeChallengeService()
        val storage = FakeCloudStorage(
            "wallet-1234" to PasskeyBackupEncryptedPayload(
                storageKey = "wallet-1234",
                walletId = "wallet-001",
                accountName = "alice@example.com",
                createdAtMillis = 1_767_225_600_000L,
                encryptedPayload = validTestEnvelope()
            )
        )
        val workflow = PasskeyBackupWorkflow(
            challengeService = service,
            cloudBackup = storage
        )

        expectReleaseDisabled {
            workflow.beginRegistration(
                walletId = "wallet-001",
                accountName = "alice@example.com",
                displayName = "Alice"
            )
        }
        expectReleaseDisabled {
            workflow.finishRegistrationWithEncryptedRecord(
                pending = pendingRegistration(storageKey = "wallet-1234"),
                credentialResponseJson = """{"id":"Y3JlZC0x"}""",
                record = encryptedRecord()
            )
        }
        expectReleaseDisabled {
            workflow.beginRestore("wallet-1234")
        }
        expectReleaseDisabled {
            workflow.finishRestore(
                pending = pendingAssertion(storageKey = "wallet-1234"),
                credentialResponseJson = """{"id":"Y3JlZC0x"}"""
            )
        }
        expectReleaseDisabled {
            workflow.deleteBackup("wallet-1234")
        }

        assertEquals(null, service.registrationWalletId)
        assertEquals(null, service.completedRegistrationId)
        assertEquals(null, service.assertionStorageKey)
        assertEquals(null, service.completedAssertionId)
        assertEquals(null, storage.deletedStorageKey)
        assertTrue(storage.contains("wallet-1234"))
    }

    @Test
    fun `encrypted record registration uses caller supplied authenticated timestamp`() = runBlocking {
        val callerCreatedAtMillis = 1_767_225_612_345L
        val encryptedPayload = validTestEnvelope(
            createdAtMillis = callerCreatedAtMillis,
            plaintext = byteArrayOf(9, 8, 7)
        )
        val record = encryptedRecord(
            createdAtMillis = callerCreatedAtMillis,
            encryptedPayload = encryptedPayload
        )
        val service = FakeChallengeService(
            registrationResult = PasskeyBackupChallengeResult(storageKey = "wallet-1234")
        )
        val storage = FakeCloudStorage()
        var clockCalls = 0
        val workflow = PasskeyBackupWorkflow(
            challengeService = service,
            cloudBackup = storage,
            backupKeyProvider = RecoverablePasskeyBackupKeyProvider {
                ByteArray(32) { (it + 1).toByte() }
            },
            isReleaseEnabled = true,
            createdAtMillisProvider = {
                clockCalls += 1
                1_767_225_699_999L
            }
        )
        val pending = pendingRegistration(storageKey = "wallet-1234")

        val saved = workflow.finishRegistrationWithEncryptedRecord(
            pending = pending,
            credentialResponseJson = """{"id":"Y3JlZC0x"}""",
            record = record
        )

        assertEquals("registration-1234", service.completedRegistrationId)
        assertEquals("""{"id":"Y3JlZC0x"}""", service.completedRegistrationCredential)
        assertEquals("wallet-1234", saved.storageKey)
        assertEquals("wallet-001", saved.walletId)
        assertEquals("alice@example.com", saved.accountName)
        assertEquals(callerCreatedAtMillis, saved.createdAtMillis)
        assertArrayEquals(encryptedPayload, saved.encryptedPayload)
        assertEquals("wallet-001", storage.savedPayload("wallet-1234")?.walletId)
        assertEquals("alice@example.com", storage.savedPayload("wallet-1234")?.accountName)
        assertEquals(callerCreatedAtMillis, storage.savedPayload("wallet-1234")?.createdAtMillis)
        assertArrayEquals(encryptedPayload, storage.savedPayload("wallet-1234")?.encryptedPayload)
        assertEquals(0, clockCalls)
    }

    @Test
    fun `encrypted payload owns constructor bytes`() {
        val callerEnvelope = validTestEnvelope(plaintext = byteArrayOf(4, 5, 6))
        val ownedEnvelope = callerEnvelope.copyOf()
        val record = encryptedRecord(encryptedPayload = callerEnvelope)

        callerEnvelope.fill(0)

        assertArrayEquals(ownedEnvelope, record.encryptedPayload)
        assertFalse(callerEnvelope.contentEquals(record.encryptedPayload))
    }

    @Test
    fun `encrypted payload getter never exposes backing bytes`() {
        val record = encryptedRecord(
            encryptedPayload = validTestEnvelope(plaintext = byteArrayOf(4, 5, 6))
        )
        val ownedEnvelope = record.encryptedPayload
        val returnedEnvelope = record.encryptedPayload
        returnedEnvelope.fill(1)

        assertArrayEquals(ownedEnvelope, record.encryptedPayload)
        assertFalse(returnedEnvelope.contentEquals(record.encryptedPayload))
        assertNotSame(returnedEnvelope, record.encryptedPayload)
    }

    @Test
    fun `pre-encrypted registration owns payload before completing registration`() = runBlocking {
        val completionStarted = CompletableDeferred<Unit>()
        val releaseCompletion = CompletableDeferred<Unit>()
        val service = FakeChallengeService(
            completeRegistrationStarted = completionStarted,
            releaseCompleteRegistration = releaseCompletion
        )
        val storage = FakeCloudStorage()
        val workflow = workflow(
            service = service,
            storage = storage,
            keyProvider = testKeyProvider()
        )
        val callerEnvelope = validTestEnvelope(plaintext = byteArrayOf(9, 8, 7))
        val authenticatedEnvelope = callerEnvelope.copyOf()
        val callerRecord = encryptedRecord(encryptedPayload = callerEnvelope)

        val registration = async {
            workflow.finishRegistrationWithEncryptedRecord(
                pending = pendingRegistration(storageKey = "wallet-1234"),
                credentialResponseJson = """{"id":"Y3JlZC0x"}""",
                record = callerRecord
            )
        }

        completionStarted.await()
        callerEnvelope.fill(0)
        releaseCompletion.complete(Unit)
        val saved = registration.await()
        val stored = requireNotNull(storage.savedPayload("wallet-1234"))

        assertArrayEquals(authenticatedEnvelope, stored.encryptedPayload)
        assertArrayEquals(authenticatedEnvelope, saved.encryptedPayload)
        assertFalse(callerEnvelope.contentEquals(stored.encryptedPayload))
        assertFalse(callerEnvelope.contentEquals(saved.encryptedPayload))
    }

    @Test
    fun `coordinator storage is isolated from caller payload mutation`() = runBlocking {
        val saveStarted = CompletableDeferred<Unit>()
        val releaseSave = CompletableDeferred<Unit>()
        val storage = FakeCloudStorage(
            saveStarted = saveStarted,
            releaseSave = releaseSave
        )
        val coordinator = PasskeyBackupCoordinator(
            credentialManager = unusedCredentialManager(),
            cloudBackup = storage,
            backupKeyProvider = testKeyProvider(),
            isReleaseEnabled = true
        )
        val callerEnvelope = validTestEnvelope(plaintext = byteArrayOf(6, 5, 4))
        val authenticatedEnvelope = callerEnvelope.copyOf()
        val callerRecord = encryptedRecord(encryptedPayload = callerEnvelope)

        val save = async {
            coordinator.saveEncryptedCloudBackup(callerRecord)
        }

        saveStarted.await()
        callerEnvelope.fill(0)
        releaseSave.complete(Unit)
        save.await()
        val stored = requireNotNull(storage.savedPayload("wallet-1234"))

        assertArrayEquals(authenticatedEnvelope, stored.encryptedPayload)
        assertFalse(callerEnvelope.contentEquals(stored.encryptedPayload))
    }

    @Test
    fun `plaintext registration encrypts and restore decrypts through recoverable key provider`() = runBlocking {
        val plaintext = "wallet-secret-material".toByteArray()
        val storage = FakeCloudStorage()
        val keyProvider = RecoverablePasskeyBackupKeyProvider { ByteArray(32) { (it + 1).toByte() } }
        val workflow = workflow(storage = storage, keyProvider = keyProvider)
        val pendingRegistration = pendingRegistration(storageKey = "wallet-1234")

        val saved = workflow.finishRegistrationWithPlaintext(
            pending = pendingRegistration,
            credentialResponseJson = """{"id":"Y3JlZC0x"}""",
            plaintextBackup = plaintext
        )
        val restored = workflow.finishRestoreWithDecryption(
            pending = pendingAssertion(storageKey = "wallet-1234"),
            credentialResponseJson = """{"id":"Y3JlZC0x"}"""
        )

        assertFalse(saved.encryptedPayload.contentEquals(plaintext))
        assertArrayEquals(plaintext, restored)
    }

    @Test
    fun `plaintext registration fails closed without external recoverable key source`() = runBlocking {
        val storage = FakeCloudStorage()
        val service = FakeChallengeService()
        val workflow = workflow(service = service, storage = storage)

        val error = runCatching {
            workflow.finishRegistrationWithPlaintext(
                pending = pendingRegistration(storageKey = "wallet-1234"),
                credentialResponseJson = """{"id":"Y3JlZC0x"}""",
                plaintextBackup = byteArrayOf(1)
            )
        }.exceptionOrNull()

        assertTrue(error is UnsupportedOperationException)
        assertEquals(null, service.completedRegistrationId)
        assertFalse(storage.contains("wallet-1234"))
    }

    @Test
    fun `cloud save failure revokes only newly registered credential`() = runBlocking {
        val service = FakeChallengeService()
        val storage = FakeCloudStorage(saveError = IllegalStateException("cloud unavailable"))
        val workflow = workflow(
            service = service,
            storage = storage,
            keyProvider = RecoverablePasskeyBackupKeyProvider { ByteArray(32) { (it + 1).toByte() } }
        )
        val credentialId = "Y3JlZGVudGlhbC0x"

        val error = runCatching {
            workflow.finishRegistrationWithPlaintext(
                pending = pendingRegistration(storageKey = "wallet-1234"),
                credentialResponseJson = """{"id":"$credentialId"}""",
                plaintextBackup = byteArrayOf(1, 2, 3)
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertEquals("registration-1234", service.completedRegistrationId)
        assertEquals("wallet-1234", service.revokedCredentialStorageKey)
        assertEquals(credentialId, service.revokedCredentialId)
        assertEquals(null, service.revokedAllStorageKey)
        assertFalse(storage.contains("wallet-1234"))
    }

    @Test
    fun `legacy raw registration fails closed before clock or ceremony without explicit metadata`() = runBlocking {
        var clockCalls = 0
        val service = FakeChallengeService()
        val storage = FakeCloudStorage()
        val workflow = PasskeyBackupWorkflow(
            challengeService = service,
            cloudBackup = storage,
            backupKeyProvider = RecoverablePasskeyBackupKeyProvider {
                error("Raw ciphertext shim must fail before key retrieval")
            },
            isReleaseEnabled = true,
            createdAtMillisProvider = {
                clockCalls += 1
                1_767_225_600_000L
            }
        )

        @Suppress("DEPRECATION")
        val error = runCatching {
            workflow.finishRegistration(
                pending = pendingRegistration(storageKey = "wallet-1234"),
                credentialResponseJson = """{"id":"Y3JlZC0x"}""",
                encryptedPayload = validTestEnvelope()
            )
        }.exceptionOrNull()

        assertTrue(error is PasskeyBackupEncryptedRecordMetadataRequiredException)
        assertEquals(0, clockCalls)
        assertEquals(null, service.completedRegistrationId)
        assertEquals(null, service.revokedCredentialId)
        assertFalse(storage.contains("wallet-1234"))
    }

    @Test
    fun `pre-encrypted registration rejects createdAt AAD mismatch before ceremony`() = runBlocking {
        val service = FakeChallengeService()
        val storage = FakeCloudStorage()
        val workflow = workflow(
            service = service,
            storage = storage,
            keyProvider = RecoverablePasskeyBackupKeyProvider {
                ByteArray(32) { (it + 1).toByte() }
            }
        )
        val envelope = validTestEnvelope(createdAtMillis = 1_767_225_600_000L)

        val error = runCatching {
            workflow.finishRegistrationWithEncryptedRecord(
                pending = pendingRegistration(storageKey = "wallet-1234"),
                credentialResponseJson = """{"id":"Y3JlZC0x"}""",
                record = encryptedRecord(
                    createdAtMillis = 1_767_225_600_001L,
                    encryptedPayload = envelope
                )
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertEquals(null, service.completedRegistrationId)
        assertEquals(null, service.revokedCredentialId)
        assertFalse(storage.contains("wallet-1234"))
    }

    @Test
    fun `pre-encrypted registration rejects record identity mismatch before ceremony`() = runBlocking {
        val service = FakeChallengeService()
        val storage = FakeCloudStorage()
        val workflow = workflow(
            service = service,
            storage = storage,
            keyProvider = RecoverablePasskeyBackupKeyProvider {
                ByteArray(32) { (it + 1).toByte() }
            }
        )
        val mismatchedRecords = listOf(
            encryptedRecord(storageKey = "wallet-9999"),
            encryptedRecord(walletId = "wallet-999"),
            encryptedRecord(accountName = "mallory@example.com")
        )

        mismatchedRecords.forEach { record ->
            val error = runCatching {
                workflow.finishRegistrationWithEncryptedRecord(
                    pending = pendingRegistration(storageKey = "wallet-1234"),
                    credentialResponseJson = """{"id":"Y3JlZC0x"}""",
                    record = record
                )
            }.exceptionOrNull()
            assertTrue(error is IllegalArgumentException)
        }

        assertEquals(null, service.completedRegistrationId)
        assertEquals(null, service.revokedCredentialId)
        assertFalse(storage.contains("wallet-1234"))
    }

    @Test
    fun `encrypted record registration compensates storage mismatch after completion`() = runBlocking {
        val service = FakeChallengeService(
            registrationResult = PasskeyBackupChallengeResult(storageKey = "wallet-5678")
        )
        val storage = FakeCloudStorage()
        val workflow = workflow(
            service = service,
            storage = storage,
            keyProvider = RecoverablePasskeyBackupKeyProvider {
                ByteArray(32) { (it + 1).toByte() }
            }
        )
        val credentialId = "Y3JlZC0x"

        val error = runCatching {
            workflow.finishRegistrationWithEncryptedRecord(
                pending = pendingRegistration(storageKey = "wallet-1234"),
                credentialResponseJson = """{"id":"$credentialId"}""",
                record = encryptedRecord()
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertEquals("registration-1234", service.completedRegistrationId)
        assertEquals("wallet-1234", service.revokedCredentialStorageKey)
        assertEquals(credentialId, service.revokedCredentialId)
        assertEquals(null, service.revokedAllStorageKey)
        assertFalse(storage.contains("wallet-1234"))
        assertFalse(storage.contains("wallet-5678"))
    }

    @Test
    fun `registration response loss after server commit still revokes credential`() = runBlocking {
        val credentialId = "Y3JlZC0x"
        val service = FakeChallengeService(
            completeRegistrationError = PasskeyBackupRegistrationCompletionUncertainException(
                IllegalStateException("response lost after commit")
            )
        )
        val storage = FakeCloudStorage()
        val workflow = workflow(
            service = service,
            storage = storage,
            keyProvider = RecoverablePasskeyBackupKeyProvider {
                ByteArray(32) { (it + 1).toByte() }
            }
        )

        val error = runCatching {
            workflow.finishRegistrationWithPlaintext(
                pending = pendingRegistration(storageKey = "wallet-1234"),
                credentialResponseJson = """{"id":"$credentialId"}""",
                plaintextBackup = byteArrayOf(1, 2, 3)
            )
        }.exceptionOrNull()

        assertTrue(error is PasskeyBackupRegistrationCompletionUncertainException)
        assertEquals("registration-1234", service.completedRegistrationId)
        assertEquals("wallet-1234", service.revokedCredentialStorageKey)
        assertEquals(credentialId, service.revokedCredentialId)
        assertFalse(storage.contains("wallet-1234"))
    }

    @Test
    fun `uncertain registration completion preserves revoke cleanup failure as suppressed`() = runBlocking {
        val primaryCause = IllegalStateException("response lost after commit")
        val cleanupError = IllegalStateException("credential revoke unavailable")
        val service = FakeChallengeService(
            completeRegistrationError = PasskeyBackupRegistrationCompletionUncertainException(primaryCause),
            revokeCredentialError = cleanupError
        )
        val workflow = workflow(
            service = service,
            keyProvider = RecoverablePasskeyBackupKeyProvider {
                ByteArray(32) { (it + 1).toByte() }
            }
        )

        val error = runCatching {
            workflow.finishRegistrationWithPlaintext(
                pending = pendingRegistration(storageKey = "wallet-1234"),
                credentialResponseJson = """{"id":"Y3JlZC0x"}""",
                plaintextBackup = byteArrayOf(1, 2, 3)
            )
        }.exceptionOrNull()

        assertTrue(error is PasskeyBackupRegistrationCompletionUncertainException)
        assertEquals(primaryCause, error?.cause)
        assertEquals(1, error?.suppressed?.size)
        assertTrue(error?.suppressed?.single() is IllegalStateException)
        assertEquals(cleanupError.message, error?.suppressed?.single()?.message)
        assertEquals("wallet-1234", service.revokedCredentialStorageKey)
        assertEquals("Y3JlZC0x", service.revokedCredentialId)
    }

    @Test
    fun `permanently suspended registration compensation returns primary failure with timeout suppressed`() =
        runBlocking {
            val primaryError = IllegalStateException("cloud unavailable")
            val service = FakeChallengeService(revokeCredentialNeverCompletes = true)
            val workflow = PasskeyBackupWorkflow(
                challengeService = service,
                cloudBackup = FakeCloudStorage(saveError = primaryError),
                backupKeyProvider = testKeyProvider(),
                isReleaseEnabled = true,
                registrationCompensationTimeoutMillis = TEST_COMPENSATION_TIMEOUT_MILLIS,
                createdAtMillisProvider = { TEST_CREATED_AT_MILLIS }
            )

            val error = runCatching {
                workflow.finishRegistrationWithPlaintext(
                    pending = pendingRegistration(storageKey = "wallet-1234"),
                    credentialResponseJson = """{"id":"Y3JlZC0x"}""",
                    plaintextBackup = byteArrayOf(1, 2, 3)
                )
            }.exceptionOrNull()

            assertSame(primaryError, error)
            assertEquals(1, error?.suppressed?.size)
            assertTrue(error?.suppressed?.single() is TimeoutCancellationException)
            assertEquals("wallet-1234", service.revokedCredentialStorageKey)
            assertEquals("Y3JlZC0x", service.revokedCredentialId)
        }

    @Test
    fun `non cooperative registration compensation cannot outlive its caller deadline`() = runBlocking {
        val primaryError = IllegalStateException("cloud unavailable")
        val revokeStarted = CompletableDeferred<Unit>()
        val releaseRevoke = CompletableDeferred<Unit>()
        val service = FakeChallengeService(
            revokeCredentialStarted = revokeStarted,
            releaseNonCooperativeRevoke = releaseRevoke
        )
        val workflow = PasskeyBackupWorkflow(
            challengeService = service,
            cloudBackup = FakeCloudStorage(saveError = primaryError),
            backupKeyProvider = testKeyProvider(),
            isReleaseEnabled = true,
            registrationCompensationTimeoutMillis = TEST_COMPENSATION_TIMEOUT_MILLIS,
            createdAtMillisProvider = { TEST_CREATED_AT_MILLIS }
        )
        val registration = async {
            runCatching {
                workflow.finishRegistrationWithPlaintext(
                    pending = pendingRegistration(storageKey = "wallet-1234"),
                    credentialResponseJson = """{"id":"Y3JlZC0x"}""",
                    plaintextBackup = byteArrayOf(1, 2, 3)
                )
            }.exceptionOrNull()
        }

        try {
            kotlinx.coroutines.withTimeout(TEST_HARD_DEADLINE_MILLIS) {
                revokeStarted.await()
            }
            val error = kotlinx.coroutines.withTimeout(TEST_HARD_DEADLINE_MILLIS) {
                registration.await()
            }

            assertSame(primaryError, error)
            assertFalse(releaseRevoke.isCompleted)
            assertEquals(1, error?.suppressed?.size)
            assertTrue(error?.suppressed?.single() is TimeoutCancellationException)
        } finally {
            releaseRevoke.complete(Unit)
            kotlinx.coroutines.withTimeout(TEST_HARD_DEADLINE_MILLIS) {
                registration.join()
            }
        }
    }

    @Test
    fun `registration compensation rejects mismatched revoke identities as suppressed failures`() = runBlocking {
        val credentialId = "Y3JlZC0x"
        val cases = listOf(
            PasskeyBackupCredentialRevokeResult(
                storageKey = "wallet-5678",
                credentialId = credentialId,
                remainingCredentials = 0
            ) to "storageKey",
            PasskeyBackupCredentialRevokeResult(
                storageKey = "wallet-1234",
                credentialId = "Y3JlZC0y",
                remainingCredentials = 0
            ) to "credentialId"
        )

        cases.forEach { (revokeResult, mismatchLabel) ->
            val primaryError = IllegalStateException("cloud unavailable for $mismatchLabel")
            val service = FakeChallengeService(revokeCredentialResult = revokeResult)
            val workflow = workflow(
                service = service,
                storage = FakeCloudStorage(saveError = primaryError),
                keyProvider = testKeyProvider()
            )

            val error = runCatching {
                workflow.finishRegistrationWithPlaintext(
                    pending = pendingRegistration(storageKey = "wallet-1234"),
                    credentialResponseJson = """{"id":"$credentialId"}""",
                    plaintextBackup = byteArrayOf(1, 2, 3)
                )
            }.exceptionOrNull()

            assertSame(primaryError, error)
            assertEquals(1, error?.suppressed?.size)
            val cleanupError = error?.suppressed?.single()
            assertTrue(cleanupError is IllegalArgumentException)
            assertTrue(cleanupError?.message?.contains(mismatchLabel) == true)
            assertEquals("wallet-1234", service.revokedCredentialStorageKey)
            assertEquals(credentialId, service.revokedCredentialId)
        }
    }

    @Test
    fun `registration compensation preserves primary when cleanup throws the same instance`() {
        val sharedError = IllegalStateException("shared cloud and revoke failure")
        sharedError.addPasskeyRegistrationCompensationFailure(sharedError)

        assertEquals(1, sharedError.suppressed.size)
        assertTrue(sharedError.suppressed.single() is IllegalStateException)
        assertTrue(sharedError.suppressed.single() !== sharedError)
        assertTrue(sharedError.suppressed.single().message?.contains("same exception instance") == true)
    }

    @Test
    fun `explicit registration rejection does not revoke an existing credential id`() = runBlocking {
        val credentialId = "Y3JlZC0x"
        val service = FakeChallengeService(
            completeRegistrationError = IllegalStateException("explicit HTTP 409 rejection")
        )
        val workflow = workflow(
            service = service,
            keyProvider = RecoverablePasskeyBackupKeyProvider {
                ByteArray(32) { (it + 1).toByte() }
            }
        )

        val error = runCatching {
            workflow.finishRegistrationWithPlaintext(
                pending = pendingRegistration(storageKey = "wallet-1234"),
                credentialResponseJson = """{"id":"$credentialId"}""",
                plaintextBackup = byteArrayOf(1, 2, 3)
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertEquals("registration-1234", service.completedRegistrationId)
        assertEquals(null, service.revokedCredentialStorageKey)
        assertEquals(null, service.revokedCredentialId)
    }

    @Test
    fun `cancellation while registration completion is in flight still revokes credential`() = runBlocking {
        val credentialId = "Y3JlZC0x"
        val completionStarted = CompletableDeferred<Unit>()
        val releaseCompletion = CompletableDeferred<Unit>()
        val service = FakeChallengeService(
            completeRegistrationStarted = completionStarted,
            releaseCompleteRegistration = releaseCompletion
        )
        val workflow = workflow(
            service = service,
            keyProvider = RecoverablePasskeyBackupKeyProvider {
                ByteArray(32) { (it + 1).toByte() }
            }
        )
        val registration = async {
            workflow.finishRegistrationWithPlaintext(
                pending = pendingRegistration(storageKey = "wallet-1234"),
                credentialResponseJson = """{"id":"$credentialId"}""",
                plaintextBackup = byteArrayOf(1, 2, 3)
            )
        }

        completionStarted.await()
        registration.cancel()
        releaseCompletion.complete(Unit)
        assertTrue(runCatching { registration.await() }.exceptionOrNull() is java.util.concurrent.CancellationException)
        assertEquals("registration-1234", service.completedRegistrationId)
        assertEquals("wallet-1234", service.revokedCredentialStorageKey)
        assertEquals(credentialId, service.revokedCredentialId)
    }

    @Test
    fun `plaintext registration compensates storage mismatch after completion`() = runBlocking {
        val service = FakeChallengeService(
            registrationResult = PasskeyBackupChallengeResult(storageKey = "wallet-5678")
        )
        val storage = FakeCloudStorage()
        val workflow = workflow(
            service = service,
            storage = storage,
            keyProvider = RecoverablePasskeyBackupKeyProvider { ByteArray(32) { (it + 1).toByte() } }
        )
        val credentialId = "Y3JlZC0x"

        val error = runCatching {
            workflow.finishRegistrationWithPlaintext(
                pending = pendingRegistration(storageKey = "wallet-1234"),
                credentialResponseJson = """{"id":"$credentialId"}""",
                plaintextBackup = byteArrayOf(1, 2, 3)
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertEquals("registration-1234", service.completedRegistrationId)
        assertEquals("wallet-1234", service.revokedCredentialStorageKey)
        assertEquals(credentialId, service.revokedCredentialId)
        assertFalse(storage.contains("wallet-1234"))
    }

    @Test
    fun `workflow rejects mismatched lifecycle response identities`() = runBlocking {
        val credentialId = "Y3JlZC0x"
        val otherCredentialId = "Y3JlZC0y"
        val listWorkflow = workflow(
            service = FakeChallengeService(
                credentialListResult = PasskeyBackupCredentialListResult(
                    storageKey = "wallet-5678",
                    credentials = emptyList()
                )
            )
        )
        val revokeStorageWorkflow = workflow(
            service = FakeChallengeService(
                revokeCredentialResult = PasskeyBackupCredentialRevokeResult(
                    storageKey = "wallet-5678",
                    credentialId = credentialId,
                    remainingCredentials = 0
                )
            )
        )
        val revokeCredentialWorkflow = workflow(
            service = FakeChallengeService(
                revokeCredentialResult = PasskeyBackupCredentialRevokeResult(
                    storageKey = "wallet-1234",
                    credentialId = otherCredentialId,
                    remainingCredentials = 0
                )
            )
        )

        assertTrue(runCatching { listWorkflow.listCredentials("wallet-1234") }.isFailure)
        assertTrue(
            runCatching {
                revokeStorageWorkflow.revokeCredential("wallet-1234", credentialId)
            }.isFailure
        )
        assertTrue(
            runCatching {
                revokeCredentialWorkflow.revokeCredential("wallet-1234", credentialId)
            }.isFailure
        )
    }

    @Test
    fun `begin restore creates assertion option from remote challenge`() = runBlocking {
        val challenge = ByteArray(32) { (it + 1).toByte() }
        val service = FakeChallengeService(
            assertionChallenge = PasskeyBackupAssertionChallenge(
                assertionId = "assertion-1234",
                challenge = challenge,
                storageKey = "wallet-1234"
            )
        )
        val workflow = workflow(service = service)

        val pending = workflow.beginRestore(" wallet-1234 ")

        assertEquals("wallet-1234", service.assertionStorageKey)
        assertEquals("assertion-1234", pending.assertionId)
        assertEquals("wallet-1234", pending.storageKey)
        assertTrue(pending.requestJson.contains("\"rpId\":\"fearlesswallet.io\""))
        assertTrue(pending.requestJson.contains("\"userVerification\":\"required\""))
    }

    @Test
    fun `finish restore completes assertion and loads encrypted cloud payload`() = runBlocking {
        val encryptedPayload = validTestEnvelope(plaintext = byteArrayOf(1, 2, 3, 4))
        val service = FakeChallengeService(
            assertionResult = PasskeyBackupChallengeResult(storageKey = "wallet-1234")
        )
        val storage = FakeCloudStorage(
            "wallet-1234" to PasskeyBackupEncryptedPayload(
                storageKey = "wallet-1234",
                walletId = "wallet-001",
                accountName = "alice@example.com",
                createdAtMillis = 1_767_225_600_000L,
                encryptedPayload = encryptedPayload
            )
        )
        val workflow = workflow(service = service, storage = storage)

        val restored = workflow.finishRestore(
            pending = pendingAssertion(storageKey = "wallet-1234"),
            credentialResponseJson = """{"id":"Y3JlZC0x"}"""
        )

        assertEquals("assertion-1234", service.completedAssertionId)
        assertEquals("""{"id":"Y3JlZC0x"}""", service.completedAssertionCredential)
        assertEquals("wallet-1234", restored.storageKey)
        assertEquals("wallet-001", restored.walletId)
        assertEquals("alice@example.com", restored.accountName)
        assertEquals(1_767_225_600_000L, restored.createdAtMillis)
        assertArrayEquals(encryptedPayload, restored.encryptedPayload)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `finish restore rejects mismatched storage key before cloud load`() = runBlocking {
        val service = FakeChallengeService(
            assertionResult = PasskeyBackupChallengeResult(storageKey = "wallet-5678")
        )
        val storage = FakeCloudStorage(
            "wallet-5678" to PasskeyBackupEncryptedPayload(
                storageKey = "wallet-5678",
                walletId = "wallet-002",
                accountName = "alice@example.com",
                createdAtMillis = 1_767_225_600_000L,
                encryptedPayload = validTestEnvelope(storageKey = "wallet-5678", walletId = "wallet-002")
            )
        )
        val workflow = workflow(service = service, storage = storage)

        workflow.finishRestore(
            pending = pendingAssertion(storageKey = "wallet-1234"),
            credentialResponseJson = """{"id":"Y3JlZC0x"}"""
        )
        Unit
    }

    @Test(expected = IllegalArgumentException::class)
    fun `finish restore fails closed when cloud payload is missing`() = runBlocking {
        val service = FakeChallengeService(
            assertionResult = PasskeyBackupChallengeResult(storageKey = "wallet-1234")
        )
        val workflow = workflow(service = service, storage = FakeCloudStorage())

        workflow.finishRestore(
            pending = pendingAssertion(storageKey = "wallet-1234"),
            credentialResponseJson = """{"id":"Y3JlZC0x"}"""
        )
        Unit
    }

    @Test
    fun `delete backup normalizes storage key`() = runBlocking {
        val storage = FakeCloudStorage(
            "wallet-1234" to PasskeyBackupEncryptedPayload(
                storageKey = "wallet-1234",
                walletId = "wallet-001",
                accountName = "alice@example.com",
                createdAtMillis = 1_767_225_600_000L,
                encryptedPayload = validTestEnvelope()
            )
        )
        val service = FakeChallengeService()
        val workflow = workflow(service = service, storage = storage)

        workflow.deleteBackup(" wallet-1234 ")

        assertEquals("wallet-1234", service.revokedAllStorageKey)
        assertEquals("wallet-1234", storage.deletedStorageKey)
        assertFalse(storage.contains("wallet-1234"))
    }

    @Test
    fun `delete backup leaves cloud record recoverable when revoke all fails`() = runBlocking {
        val service = FakeChallengeService(revokeAllError = IllegalStateException("server unavailable"))
        val storage = FakeCloudStorage(
            "wallet-1234" to PasskeyBackupEncryptedPayload(
                storageKey = "wallet-1234",
                walletId = "wallet-001",
                accountName = "alice@example.com",
                createdAtMillis = 1_767_225_600_000L,
                encryptedPayload = validTestEnvelope()
            )
        )
        val workflow = workflow(service = service, storage = storage)

        assertTrue(runCatching { workflow.deleteBackup("wallet-1234") }.isFailure)
        assertEquals("wallet-1234", service.revokedAllStorageKey)
        assertEquals(null, storage.deletedStorageKey)
        assertTrue(storage.contains("wallet-1234"))
    }

    @Test
    fun `delete paths reject credential-scoped revoke-all result before cloud deletion`() = runBlocking {
        val revokeAllResult = PasskeyBackupCredentialRevokeResult(
            storageKey = "wallet-1234",
            credentialId = "Y3JlZC0x",
            remainingCredentials = 0
        )
        val workflowStorage = FakeCloudStorage(
            "wallet-1234" to encryptedRecord()
        )
        val workflow = workflow(
            service = FakeChallengeService(revokeAllResult = revokeAllResult),
            storage = workflowStorage
        )
        val coordinatorStorage = FakeCloudStorage(
            "wallet-1234" to encryptedRecord()
        )
        val coordinator = PasskeyBackupCoordinator(
            credentialManager = unusedCredentialManager(),
            cloudBackup = coordinatorStorage,
            challengeService = FakeChallengeService(revokeAllResult = revokeAllResult),
            isReleaseEnabled = true
        )

        val workflowError = runCatching { workflow.deleteBackup("wallet-1234") }.exceptionOrNull()
        val coordinatorError = runCatching {
            coordinator.deleteEncryptedCloudBackup("wallet-1234")
        }.exceptionOrNull()

        assertTrue(workflowError is IllegalArgumentException)
        assertTrue(coordinatorError is IllegalStateException)
        assertEquals(null, workflowStorage.deletedStorageKey)
        assertEquals(null, coordinatorStorage.deletedStorageKey)
        assertTrue(workflowStorage.contains("wallet-1234"))
        assertTrue(coordinatorStorage.contains("wallet-1234"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `workflow rejects unsupported relying party`() {
        PasskeyBackupWorkflow(
            challengeService = FakeChallengeService(),
            cloudBackup = FakeCloudStorage(),
            relyingPartyId = "example.com"
        )
    }

    @Test
    fun `workflow enforces registration compensation timeout boundaries`() {
        listOf(
            MIN_COMPENSATION_TIMEOUT_MILLIS,
            MAX_COMPENSATION_TIMEOUT_MILLIS
        ).forEach { timeoutMillis ->
            PasskeyBackupWorkflow(
                challengeService = FakeChallengeService(),
                cloudBackup = FakeCloudStorage(),
                registrationCompensationTimeoutMillis = timeoutMillis
            )
        }
        listOf(
            BELOW_MIN_COMPENSATION_TIMEOUT_MILLIS,
            ABOVE_MAX_COMPENSATION_TIMEOUT_MILLIS
        ).forEach { timeoutMillis ->
            val error = runCatching {
                PasskeyBackupWorkflow(
                    challengeService = FakeChallengeService(),
                    cloudBackup = FakeCloudStorage(),
                    registrationCompensationTimeoutMillis = timeoutMillis
                )
            }.exceptionOrNull()

            assertTrue(error is IllegalArgumentException)
        }
    }

    private fun workflow(
        service: FakeChallengeService = FakeChallengeService(),
        storage: FakeCloudStorage = FakeCloudStorage(),
        keyProvider: RecoverablePasskeyBackupKeyProvider =
            UnavailableRecoverablePasskeyBackupKeyProvider()
    ): PasskeyBackupWorkflow {
        return PasskeyBackupWorkflow(
            challengeService = service,
            cloudBackup = storage,
            backupKeyProvider = keyProvider,
            isReleaseEnabled = true,
            createdAtMillisProvider = { 1_767_225_600_000L }
        )
    }

    private fun testKeyProvider(): RecoverablePasskeyBackupKeyProvider {
        return RecoverablePasskeyBackupKeyProvider { ByteArray(32) { (it + 1).toByte() } }
    }

    private fun unusedCredentialManager(): CredentialManager {
        val proxy = Proxy.newProxyInstance(
            CredentialManager::class.java.classLoader,
            arrayOf(CredentialManager::class.java)
        ) { _, method, _ ->
            error("Unexpected CredentialManager call: ${method.name}")
        }
        return CredentialManager::class.java.cast(proxy)
    }

    private suspend fun expectReleaseDisabled(block: suspend () -> Unit) {
        try {
            block()
            fail("expected passkey backup release gate to reject the operation")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("Passkey backup is disabled") == true)
        }
    }

    private fun pendingRegistration(storageKey: String): PendingPasskeyBackupRegistration {
        return PendingPasskeyBackupRegistration(
            registrationId = "registration-1234",
            storageKey = storageKey,
            walletId = "wallet-001",
            accountName = "alice@example.com",
            requestJson = "{}"
        )
    }

    private fun encryptedRecord(
        storageKey: String = "wallet-1234",
        walletId: String = "wallet-001",
        accountName: String = "alice@example.com",
        createdAtMillis: Long = 1_767_225_600_000L,
        encryptedPayload: ByteArray = validTestEnvelope(
            storageKey = storageKey,
            walletId = walletId,
            accountName = accountName,
            createdAtMillis = createdAtMillis
        )
    ): PasskeyBackupEncryptedPayload {
        return PasskeyBackupEncryptedPayload(
            storageKey = storageKey,
            walletId = walletId,
            accountName = accountName,
            createdAtMillis = createdAtMillis,
            encryptedPayload = encryptedPayload
        )
    }

    private fun pendingAssertion(storageKey: String): PendingPasskeyBackupAssertion {
        return PendingPasskeyBackupAssertion(
            assertionId = "assertion-1234",
            storageKey = storageKey,
            requestJson = "{}"
        )
    }

    private class FakeChallengeService(
        private val registrationChallenge: PasskeyBackupRegistrationChallenge =
            PasskeyBackupRegistrationChallenge(
                registrationId = "registration-1234",
                challenge = ByteArray(32),
                userId = ByteArray(32),
                userName = "alice@example.com",
                displayName = "Alice",
                storageKey = "wallet-1234"
            ),
        private val registrationResult: PasskeyBackupChallengeResult =
            PasskeyBackupChallengeResult(storageKey = "wallet-1234"),
        private val assertionChallenge: PasskeyBackupAssertionChallenge =
            PasskeyBackupAssertionChallenge(
                assertionId = "assertion-1234",
                challenge = ByteArray(32),
                storageKey = "wallet-1234"
            ),
        private val assertionResult: PasskeyBackupChallengeResult =
            PasskeyBackupChallengeResult(storageKey = "wallet-1234"),
        private val completeRegistrationError: Throwable? = null,
        private val completeRegistrationStarted: CompletableDeferred<Unit>? = null,
        private val releaseCompleteRegistration: CompletableDeferred<Unit>? = null,
        private val revokeAllError: Throwable? = null,
        private val revokeAllResult: PasskeyBackupCredentialRevokeResult? = null,
        private val revokeCredentialError: Throwable? = null,
        private val revokeCredentialNeverCompletes: Boolean = false,
        private val revokeCredentialStarted: CompletableDeferred<Unit>? = null,
        private val releaseNonCooperativeRevoke: CompletableDeferred<Unit>? = null,
        private val credentialListResult: PasskeyBackupCredentialListResult =
            PasskeyBackupCredentialListResult(storageKey = "wallet-1234", credentials = emptyList()),
        private val revokeCredentialResult: PasskeyBackupCredentialRevokeResult? = null
    ) : PasskeyBackupChallengeService {
        var registrationWalletId: String? = null
        var registrationAccountName: String? = null
        var registrationDisplayName: String? = null
        var completedRegistrationId: String? = null
        var completedRegistrationCredential: String? = null
        var assertionStorageKey: String? = null
        var completedAssertionId: String? = null
        var completedAssertionCredential: String? = null
        var revokedAllStorageKey: String? = null
        var revokedCredentialStorageKey: String? = null
        var revokedCredentialId: String? = null

        override suspend fun registrationChallenge(
            walletId: String,
            accountName: String,
            displayName: String
        ): PasskeyBackupRegistrationChallenge {
            registrationWalletId = walletId
            registrationAccountName = accountName
            registrationDisplayName = displayName
            return registrationChallenge
        }

        override suspend fun completeRegistration(
            registrationId: String,
            credentialResponseJson: String
        ): PasskeyBackupChallengeResult {
            completedRegistrationId = registrationId
            completedRegistrationCredential = credentialResponseJson
            completeRegistrationStarted?.complete(Unit)
            try {
                releaseCompleteRegistration?.await()
            } catch (error: java.util.concurrent.CancellationException) {
                throw PasskeyBackupRegistrationCompletionUncertainCancellationException(error)
            }
            completeRegistrationError?.let { throw it }
            return registrationResult
        }

        override suspend fun assertionChallenge(storageKey: String): PasskeyBackupAssertionChallenge {
            assertionStorageKey = storageKey
            return assertionChallenge
        }

        override suspend fun completeAssertion(
            assertionId: String,
            credentialResponseJson: String
        ): PasskeyBackupChallengeResult {
            completedAssertionId = assertionId
            completedAssertionCredential = credentialResponseJson
            return assertionResult
        }

        override suspend fun revokeAllCredentials(storageKey: String): PasskeyBackupCredentialRevokeResult {
            revokedAllStorageKey = storageKey
            revokeAllError?.let { throw it }
            return revokeAllResult ?: PasskeyBackupCredentialRevokeResult(
                storageKey = storageKey,
                credentialId = null,
                remainingCredentials = 0
            )
        }

        override suspend fun listCredentials(storageKey: String): PasskeyBackupCredentialListResult {
            return credentialListResult
        }

        override suspend fun revokeCredential(
            storageKey: String,
            credentialId: String
        ): PasskeyBackupCredentialRevokeResult {
            revokedCredentialStorageKey = storageKey
            revokedCredentialId = credentialId
            revokeCredentialStarted?.complete(Unit)
            releaseNonCooperativeRevoke?.let { release ->
                suspendCoroutine<Unit> { continuation ->
                    release.invokeOnCompletion { continuation.resume(Unit) }
                }
            }
            if (revokeCredentialNeverCompletes) {
                awaitCancellation()
            }
            revokeCredentialError?.let { throw it }
            return revokeCredentialResult ?: PasskeyBackupCredentialRevokeResult(
                storageKey = storageKey,
                credentialId = credentialId,
                remainingCredentials = 0
            )
        }
    }

    private class FakeCloudStorage(
        vararg records: Pair<String, PasskeyBackupEncryptedPayload>,
        private val saveError: Throwable? = null,
        private val saveStarted: CompletableDeferred<Unit>? = null,
        private val releaseSave: CompletableDeferred<Unit>? = null
    ) : PasskeyBackupCloudStorage {
        private val records = records.toMap().toMutableMap()
        var deletedStorageKey: String? = null

        override suspend fun savePasskeyBackup(payload: PasskeyBackupEncryptedPayload) {
            saveStarted?.complete(Unit)
            releaseSave?.await()
            saveError?.let { throw it }
            records[payload.storageKey] = payload
        }

        override suspend fun loadPasskeyBackup(storageKey: String): PasskeyBackupEncryptedPayload? {
            return records[storageKey]
        }

        override suspend fun deletePasskeyBackup(storageKey: String) {
            deletedStorageKey = storageKey
            records.remove(storageKey)
        }

        fun savedPayload(storageKey: String): PasskeyBackupEncryptedPayload? {
            return records[storageKey]
        }

        fun contains(storageKey: String): Boolean {
            return records.containsKey(storageKey)
        }
    }

    private companion object {
        const val TEST_COMPENSATION_TIMEOUT_MILLIS = 25L
        const val TEST_HARD_DEADLINE_MILLIS = 1_000L
        const val TEST_CREATED_AT_MILLIS = 1_767_225_600_000L
        const val BELOW_MIN_COMPENSATION_TIMEOUT_MILLIS = 0L
        const val MIN_COMPENSATION_TIMEOUT_MILLIS = 1L
        const val MAX_COMPENSATION_TIMEOUT_MILLIS = 30_000L
        const val ABOVE_MAX_COMPENSATION_TIMEOUT_MILLIS = 30_001L
    }
}
