package jp.co.soramitsu.backup.passkey

import android.os.Build
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasskeyBackupNativeIntegrationTest {
    @Test
    fun `credential manager executor returns registration and assertion WebAuthn JSON`() = runBlocking {
        val registrationJson = """{"id":"registration-credential"}"""
        val assertionJson = """{"id":"assertion-credential"}"""
        val gateway = RecordingCredentialManagerGateway(
            createResponse = registrationJson,
            getResponse = assertionJson
        )
        val executor = CredentialManagerPasskeyBackupCeremonyExecutor(
            gateway = gateway,
            isReleaseEnabled = true
        )

        assertEquals(registrationJson, executor.performRegistration(pendingRegistration()))
        assertEquals(assertionJson, executor.performAssertion(pendingAssertion()))
        assertTrue(gateway.createRequestJson?.contains("\"residentKey\":\"required\"") == true)
        assertTrue(gateway.getRequestJson?.contains("\"userVerification\":\"required\"") == true)
    }

    @Test
    fun `credential manager executor rejects malformed JSON responses`() {
        listOf("", " {} ", "[]", "null", "{").forEach { responseJson ->
            val gateway = RecordingCredentialManagerGateway(
                createResponse = responseJson,
                getResponse = responseJson
            )
            val executor = CredentialManagerPasskeyBackupCeremonyExecutor(
                gateway = gateway,
                isReleaseEnabled = true
            )

            assertTrue(runCatching { runBlocking { executor.performRegistration(pendingRegistration()) } }.isFailure)
            assertTrue(runCatching { runBlocking { executor.performAssertion(pendingAssertion()) } }.isFailure)
        }
    }

    @Test
    fun `credential manager executor is fail closed behind release flag`() = runBlocking {
        val gateway = RecordingCredentialManagerGateway(
            createResponse = """{"id":"credential"}""",
            getResponse = """{"id":"credential"}"""
        )
        val executor = CredentialManagerPasskeyBackupCeremonyExecutor(gateway = gateway)

        assertTrue(runCatching { executor.performRegistration(pendingRegistration()) }.isFailure)
        assertTrue(runCatching { executor.performAssertion(pendingAssertion()) }.isFailure)
        assertEquals(null, gateway.createRequestJson)
        assertEquals(null, gateway.getRequestJson)
    }

    @Test
    fun `native passkey request construction rejects unsupported Android versions`() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            val registrationError = runCatching { pendingRegistration().createRequest() }.exceptionOrNull()
            val assertionError = runCatching { pendingAssertion().createOption() }.exceptionOrNull()

            assertTrue(registrationError is UnsupportedOperationException)
            assertTrue(assertionError is UnsupportedOperationException)
        }
    }

    private fun pendingRegistration() = PendingPasskeyBackupRegistration(
        registrationId = "registration-1234",
        storageKey = "wallet-1234",
        walletId = "wallet-001",
        accountName = "alice@example.com",
        requestJson = PasskeyBackupContract.registrationOptionsJson(
            challenge = ByteArray(32),
            userId = ByteArray(32),
            userName = "alice@example.com",
            displayName = "Alice"
        )
    )

    private fun pendingAssertion() = PendingPasskeyBackupAssertion(
        assertionId = "assertion-1234",
        storageKey = "wallet-1234",
        requestJson = PasskeyBackupContract.assertionOptionsJson(ByteArray(32))
    )

    private class RecordingCredentialManagerGateway(
        private val createResponse: String,
        private val getResponse: String
    ) : AndroidPasskeyCredentialManagerGateway {
        var createRequestJson: String? = null
        var getRequestJson: String? = null

        override suspend fun createCredential(requestJson: String): String {
            createRequestJson = requestJson
            return createResponse
        }

        override suspend fun getCredential(requestJson: String): String {
            getRequestJson = requestJson
            return getResponse
        }
    }
}
