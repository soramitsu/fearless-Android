package jp.co.soramitsu.backup.passkey

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PasskeyBackupWorkflowTest {
    @Test
    fun `begin registration creates credential request from remote challenge`() = runBlocking {
        val challenge = ByteArray(32) { it.toByte() }
        val userId = ByteArray(16) { (it + 17).toByte() }
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
                userId = ByteArray(16) { (it + 17).toByte() },
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
                encryptedPayload = byteArrayOf(1)
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
            workflow.finishRegistration(
                pending = pendingRegistration(storageKey = "wallet-1234"),
                credentialResponseJson = """{"id":"cred-1"}""",
                encryptedPayload = byteArrayOf(9)
            )
        }
        expectReleaseDisabled {
            workflow.beginRestore("wallet-1234")
        }
        expectReleaseDisabled {
            workflow.finishRestore(
                pending = pendingAssertion(storageKey = "wallet-1234"),
                credentialResponseJson = """{"id":"cred-1"}"""
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
    fun `finish registration completes ceremony and saves encrypted cloud payload`() = runBlocking {
        val encryptedPayload = byteArrayOf(9, 8, 7)
        val service = FakeChallengeService(
            registrationResult = PasskeyBackupChallengeResult(storageKey = "wallet-1234")
        )
        val storage = FakeCloudStorage()
        val workflow = workflow(service = service, storage = storage)
        val pending = pendingRegistration(storageKey = "wallet-1234")

        val saved = workflow.finishRegistration(
            pending = pending,
            credentialResponseJson = """{"id":"cred-1"}""",
            encryptedPayload = encryptedPayload
        )

        assertEquals("registration-1234", service.completedRegistrationId)
        assertEquals("""{"id":"cred-1"}""", service.completedRegistrationCredential)
        assertEquals("wallet-1234", saved.storageKey)
        assertEquals("wallet-001", saved.walletId)
        assertEquals("alice@example.com", saved.accountName)
        assertEquals(1_767_225_600_000L, saved.createdAtMillis)
        assertArrayEquals(encryptedPayload, saved.encryptedPayload)
        assertEquals("wallet-001", storage.savedPayload("wallet-1234")?.walletId)
        assertEquals("alice@example.com", storage.savedPayload("wallet-1234")?.accountName)
        assertEquals(1_767_225_600_000L, storage.savedPayload("wallet-1234")?.createdAtMillis)
        assertArrayEquals(encryptedPayload, storage.savedPayload("wallet-1234")?.encryptedPayload)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `finish registration rejects mismatched storage key before saving`() = runBlocking {
        val service = FakeChallengeService(
            registrationResult = PasskeyBackupChallengeResult(storageKey = "wallet-5678")
        )
        val storage = FakeCloudStorage()
        val workflow = workflow(service = service, storage = storage)

        workflow.finishRegistration(
            pending = pendingRegistration(storageKey = "wallet-1234"),
            credentialResponseJson = """{"id":"cred-1"}""",
            encryptedPayload = byteArrayOf(1)
        )

        assertFalse(storage.contains("wallet-5678"))
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
        val encryptedPayload = byteArrayOf(1, 2, 3, 4)
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
            credentialResponseJson = """{"id":"cred-1"}"""
        )

        assertEquals("assertion-1234", service.completedAssertionId)
        assertEquals("""{"id":"cred-1"}""", service.completedAssertionCredential)
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
                encryptedPayload = byteArrayOf(1)
            )
        )
        val workflow = workflow(service = service, storage = storage)

        workflow.finishRestore(
            pending = pendingAssertion(storageKey = "wallet-1234"),
            credentialResponseJson = """{"id":"cred-1"}"""
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
            credentialResponseJson = """{"id":"cred-1"}"""
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
                encryptedPayload = byteArrayOf(1)
            )
        )
        val workflow = workflow(storage = storage)

        workflow.deleteBackup(" wallet-1234 ")

        assertEquals("wallet-1234", storage.deletedStorageKey)
        assertFalse(storage.contains("wallet-1234"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `workflow rejects unsupported relying party`() {
        PasskeyBackupWorkflow(
            challengeService = FakeChallengeService(),
            cloudBackup = FakeCloudStorage(),
            relyingPartyId = "example.com"
        )
    }

    private fun workflow(
        service: FakeChallengeService = FakeChallengeService(),
        storage: FakeCloudStorage = FakeCloudStorage()
    ): PasskeyBackupWorkflow {
        return PasskeyBackupWorkflow(
            challengeService = service,
            cloudBackup = storage,
            isReleaseEnabled = true,
            createdAtMillisProvider = { 1_767_225_600_000L }
        )
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
                userId = ByteArray(16),
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
            PasskeyBackupChallengeResult(storageKey = "wallet-1234")
    ) : PasskeyBackupChallengeService {
        var registrationWalletId: String? = null
        var registrationAccountName: String? = null
        var registrationDisplayName: String? = null
        var completedRegistrationId: String? = null
        var completedRegistrationCredential: String? = null
        var assertionStorageKey: String? = null
        var completedAssertionId: String? = null
        var completedAssertionCredential: String? = null

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
    }

    private class FakeCloudStorage(
        vararg records: Pair<String, PasskeyBackupEncryptedPayload>
    ) : PasskeyBackupCloudStorage {
        private val records = records.toMap().toMutableMap()
        var deletedStorageKey: String? = null

        override suspend fun savePasskeyBackup(payload: PasskeyBackupEncryptedPayload) {
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
}
