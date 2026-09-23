package jp.co.soramitsu.backup.passkey

import android.os.Build
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class PasskeyBackupNativeIntegrationTest {
    @Test
    fun `credential manager executor returns public registration and assertion WebAuthn JSON`() = runBlocking {
        val registrationJson = REGISTRATION_CREDENTIAL_JSON
        val assertionJson = ASSERTION_CREDENTIAL_JSON
        val gateway = RecordingCredentialManagerGateway(
            createResponse = registrationJson,
            getResponse = assertionJson
        )
        val executor = CredentialManagerPasskeyBackupCeremonyExecutor(
            gateway = gateway,
            isReleaseEnabled = true
        )

        executor.performRegistration(pendingRegistration()).use { result ->
            assertEquals(registrationJson, result.serverCredentialJson)
            assertFalse(result.hasLocalPrfOutput)
        }
        executor.performAssertion(pendingAssertion()).use { result ->
            assertEquals(assertionJson, result.serverCredentialJson)
            assertFalse(result.hasLocalPrfOutput)
        }
        assertTrue(gateway.createRequestJson?.contains("\"residentKey\":\"required\"") == true)
        assertTrue(gateway.getRequestJson?.contains("\"userVerification\":\"required\"") == true)
    }

    @Test
    fun `PRF result remains local and is wiped after use`() = runBlocking {
        val secret = ByteArray(32) { it.toByte() }
        val encodedSecret = Base64.getUrlEncoder().withoutPadding().encodeToString(secret)
        val rawJson = ASSERTION_CREDENTIAL_JSON.replace(
            "\"clientExtensionResults\":{}",
            "\"clientExtensionResults\":{\"prf\":{\"results\":{\"first\":\"$encodedSecret\"}}}"
        )
        val executor = CredentialManagerPasskeyBackupCeremonyExecutor(
            gateway = RecordingCredentialManagerGateway(REGISTRATION_CREDENTIAL_JSON, rawJson),
            isReleaseEnabled = true
        )

        val result = executor.performAssertion(pendingAssertion())
        assertTrue(result.hasLocalPrfOutput)
        assertFalse(result.serverCredentialJson.contains(encodedSecret))
        assertFalse(result.serverCredentialJson.contains("prf"))
        assertFalse(result.toString().contains(encodedSecret))
        assertFalse(Gson().toJson(result).contains("localPrfOutput"))
        assertFalse(Gson().toJson(result).contains(encodedSecret))
        var callbackCopy: ByteArray? = null
        result.withLocalPrfOutput { localOutput ->
            callbackCopy = localOutput
            assertArrayEquals(secret, localOutput)
        }
        assertTrue(callbackCopy!!.all { it == 0.toByte() })
        result.close()
        assertFalse(result.hasLocalPrfOutput)
    }

    @Test
    fun `registration keeps public credential properties but removes local PRF output`() {
        val secret = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32) { 7 })
        val rawJson = REGISTRATION_CREDENTIAL_JSON.replace(
            "\"clientExtensionResults\":{}",
            "\"clientExtensionResults\":{\"credProps\":{\"rk\":true}," +
                "\"prf\":{\"enabled\":true,\"results\":{\"first\":\"$secret\"}}}"
        )
        val result = PasskeyBackupNativeCeremonyResult.registration(rawJson)
        assertTrue(result.hasLocalPrfOutput)
        assertTrue(result.serverCredentialJson.contains("\"credProps\":{\"rk\":true}"))
        assertFalse(result.serverCredentialJson.contains(secret))
        result.close()
    }

    @Test
    fun `native ceremony only serializes approved public response fields`() {
        val rawJson = ASSERTION_CREDENTIAL_JSON
            .replace("\"signature\":\"AQ\"", "\"signature\":\"AQ\",\"walletSecret\":\"never-send\"")
            .replace("\"clientExtensionResults\":{}", "\"clientExtensionResults\":{\"largeBlob\":{\"blob\":\"never-send\"}}")
        val unsupportedExtensionRejected = runCatching {
            PasskeyBackupNativeCeremonyResult.assertion(rawJson, pendingAssertion().requestJson)
        }.isFailure
        assertTrue(unsupportedExtensionRejected)

        val responseOnly = rawJson.replace(
            "\"clientExtensionResults\":{\"largeBlob\":{\"blob\":\"never-send\"}}",
            "\"clientExtensionResults\":{}"
        )
        PasskeyBackupNativeCeremonyResult.assertion(responseOnly, pendingAssertion().requestJson).use { result ->
            assertFalse(result.serverCredentialJson.contains("walletSecret"))
            assertFalse(result.serverCredentialJson.contains("never-send"))
        }
    }

    @Test
    fun `local PRF output is never paired with a substituted credential identity`() {
        val secret = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32) { 0x32 })
        val withPrf = ASSERTION_CREDENTIAL_JSON.replace(
            "\"clientExtensionResults\":{}",
            "\"clientExtensionResults\":{\"prf\":{\"results\":{\"first\":\"$secret\"}}}"
        )
        listOf(
            withPrf.replace("\"rawId\":\"AQ\"", "\"rawId\":\"Ag\""),
            withPrf.replace("\"type\":\"public-key\"", "\"type\":\"password\""),
            withPrf.replace("\"id\":\"AQ\"", "\"id\":\"AQ==\"")
        ).forEach { response ->
            val rejected = runCatching {
                PasskeyBackupNativeCeremonyResult.assertion(response, pendingAssertion().requestJson)
            }.isFailure
            assertTrue(rejected)
        }
    }

    @Test
    fun `directed PRF assertion permits null user handle only for the requested credential`() = runBlocking {
        val directedRequest = PasskeyBackupContract.assertionOptionsJsonWithPrf(
            challenge = ByteArray(32), credentialId = "AQ", prfSalt = ByteArray(32) { 7 }
        )
        val nullHandle = ASSERTION_CREDENTIAL_JSON.replace("\"userHandle\":\"AQ\"", "\"userHandle\":null")
        val executor = CredentialManagerPasskeyBackupCeremonyExecutor(
            gateway = RecordingCredentialManagerGateway(REGISTRATION_CREDENTIAL_JSON, nullHandle),
            isReleaseEnabled = true
        )
        executor.performAssertion(pendingAssertion(directedRequest)).use { result ->
            assertTrue(result.serverCredentialJson.contains("\"userHandle\":null"))
            assertFalse(result.hasLocalPrfOutput)
        }

        val wrongCredential = nullHandle.replace("\"id\":\"AQ\",\"rawId\":\"AQ\"", "\"id\":\"Ag\",\"rawId\":\"Ag\"")
        val wrongCredentialRejected = runCatching {
            PasskeyBackupNativeCeremonyResult.assertion(wrongCredential, directedRequest)
        }.isFailure
        assertTrue(wrongCredentialRejected)
        val discoverableNullHandleRejected = runCatching {
            PasskeyBackupNativeCeremonyResult.assertion(nullHandle, pendingAssertion().requestJson)
        }.isFailure
        assertTrue(discoverableNullHandleRejected)
    }

    @Test
    fun `assertion rejects malformed user handles and request credential lists`() {
        val request = pendingAssertion().requestJson
        val tooLong = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(65) { 1 })
        for (invalid in listOf("", "AQ==", tooLong)) {
            val response = ASSERTION_CREDENTIAL_JSON.replace("\"userHandle\":\"AQ\"", "\"userHandle\":\"$invalid\"")
            assertTrue(runCatching { PasskeyBackupNativeCeremonyResult.assertion(response, request) }.isFailure)
        }
        val missing = ASSERTION_CREDENTIAL_JSON.replace(",\"userHandle\":\"AQ\"", "")
        assertTrue(runCatching { PasskeyBackupNativeCeremonyResult.assertion(missing, request) }.isFailure)
        for (invalidRequest in listOf(
            "{}",
            request.replace("fearlesswallet.io", "other.example"),
            request.replace("\"timeout\":60000", "\"allowCredentials\":{\"id\":\"AQ\"},\"timeout\":60000"),
            request.replace("\"timeout\":60000", "\"allowCredentials\":[{\"type\":\"public-key\",\"id\":\"AQ\"},{\"type\":\"public-key\",\"id\":\"AQ\"}],\"timeout\":60000")
        )) {
            val rejected = runCatching {
                PasskeyBackupNativeCeremonyResult.assertion(ASSERTION_CREDENTIAL_JSON, invalidRequest)
            }.isFailure
            assertTrue(rejected)
        }
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
            createResponse = REGISTRATION_CREDENTIAL_JSON,
            getResponse = ASSERTION_CREDENTIAL_JSON
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

    private fun pendingAssertion(requestJson: String = PasskeyBackupContract.assertionOptionsJson(ByteArray(32))) =
        PendingPasskeyBackupAssertion(
        assertionId = "assertion-1234",
        storageKey = "wallet-1234",
        requestJson = requestJson
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

    private companion object {
        const val REGISTRATION_CREDENTIAL_JSON =
            """{"id":"AQ","rawId":"AQ","type":"public-key","response":{"clientDataJSON":"AQ","attestationObject":"AQ"},"clientExtensionResults":{}}"""
        const val ASSERTION_CREDENTIAL_JSON =
            """{"id":"AQ","rawId":"AQ","type":"public-key","response":{"clientDataJSON":"AQ","authenticatorData":"AQ","signature":"AQ","userHandle":"AQ"},"clientExtensionResults":{}}"""
    }
}
