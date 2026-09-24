package jp.co.soramitsu.backup.passkey

import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class PasskeyBackupOwnerAuthenticationClientTest {
    private var nowMillis = 1_700_000_000_000L

    @Test
    fun `discovers user verified passkey and sends only public assertion fields`() = runBlocking {
        val rawCredential = ASSERTION_JSON
            .replace("\"signature\":\"AQ\"", "\"signature\":\"AQ\",\"walletSecret\":\"never-send\"")
            .replace("\"clientExtensionResults\":{}", "\"clientExtensionResults\":{\"credProps\":{\"rk\":true}}")
        val gateway = RecordingGateway(rawCredential)
        val transport = RecordingTransport(challengeResponse(), sessionResponse())

        val session = client(gateway, transport, enabled = true).authenticateOwner()

        assertEquals(SESSION_TOKEN, session.sessionToken)
        assertEquals(SUBJECT, session.subject)
        assertEquals(NAMESPACE, session.namespace)
        assertEquals(0L, session.generation)
        assertEquals("android", session.platform)
        assertEquals(nowMillis / 1_000L + 599L, session.expiresAt)
        assertFalse(session.toString().contains(SESSION_TOKEN))
        assertFalse(Gson().toJson(session).contains(SESSION_TOKEN))
        assertEquals(2, transport.requests.size)

        val challengeRequest = transport.requests[0]
        assertEquals("POST", challengeRequest.method)
        assertEquals(
            "${PasskeyBackupReleaseConfig.CHALLENGE_SERVICE_BASE_URL}/api/passkey-backup/v1/owner/authentication/challenge",
            challengeRequest.url
        )
        assertEquals(setOf("Content-Type"), challengeRequest.headers.keys)
        assertEquals(
            setOf("schemaVersion", "platform"),
            JsonParser.parseString(challengeRequest.bodyText()).asJsonObject.keySet()
        )
        assertEquals(1, JsonParser.parseString(challengeRequest.bodyText()).asJsonObject.get("schemaVersion").asInt)
        assertEquals("android", JsonParser.parseString(challengeRequest.bodyText()).asJsonObject.get("platform").asString)

        val options = JsonParser.parseString(requireNotNull(gateway.requestJson)).asJsonObject
        assertEquals(setOf("challenge", "rpId", "userVerification", "timeout"), options.keySet())
        assertEquals(CHALLENGE, options.get("challenge").asString)
        assertEquals(PasskeyBackupContract.PASSKEY_RP_ID, options.get("rpId").asString)
        assertEquals("required", options.get("userVerification").asString)
        assertFalse(gateway.requestJson!!.contains("prf"))
        assertFalse(gateway.requestJson!!.contains("allowCredentials"))

        val completion = transport.requests[1]
        assertEquals(
            "${PasskeyBackupReleaseConfig.CHALLENGE_SERVICE_BASE_URL}/api/passkey-backup/v1/owner/authentication/complete",
            completion.url
        )
        assertTrue(completion.isOneShot)
        assertEquals(
            setOf("schemaVersion", "ceremonyId", "credential"),
            JsonParser.parseString(completion.bodyText()).asJsonObject.keySet()
        )
        val credential = JsonParser.parseString(completion.bodyText()).asJsonObject.getAsJsonObject("credential")
        assertEquals(setOf("id", "rawId", "type", "response", "clientExtensionResults"), credential.keySet())
        assertEquals(
            setOf("clientDataJSON", "authenticatorData", "signature", "userHandle"),
            credential.getAsJsonObject("response").keySet()
        )
        assertEquals(setOf("credProps"), credential.getAsJsonObject("clientExtensionResults").keySet())
        assertFalse(completion.bodyText().contains("walletSecret"))
        assertFalse(completion.bodyText().contains("prf"))
        assertFalse(completion.bodyText().contains("never-send"))
    }

    @Test
    fun `accepts authority lifetimes when the device clock is slightly behind`() = runBlocking {
        val deviceNow = nowMillis / 1_000L
        val challenge = challengeJson().replace(
            "\"expiresAt\":${deviceNow + 119L}",
            "\"expiresAt\":${deviceNow + 121L}"
        )
        val session = sessionJson().replace(
            "\"expiresAt\":${deviceNow + 599L}",
            "\"expiresAt\":${deviceNow + 601L}"
        )
        val transport = RecordingTransport(jsonResponse(challenge), jsonResponse(session))

        val authenticated = client(RecordingGateway(ASSERTION_JSON), transport, true).authenticateOwner()

        assertEquals(deviceNow + 601L, authenticated.expiresAt)
        assertEquals(2, transport.requests.size)
    }

    @Test
    fun `rejects malformed duplicate and stale challenges before native UI`() {
        val normal = challengeJson()
        val cases = listOf(
            normal.replace("\"kind\":\"authentication\"", "\"kind\":\"authentication\",\"kind\":\"bootstrap\""),
            normal.replace("\"platform\":\"android\"", "\"platform\":\"ios\""),
            normal.replace("\"kind\":\"authentication\"", "\"kind\":\"bootstrap\""),
            normal.replace("\"subject\":null", "\"subject\":\"$SUBJECT\""),
            normal.replace("\"rpId\":\"fearlesswallet.io\"", "\"rpId\":\"evil.example\""),
            normal.replace("\"challenge\":\"$CHALLENGE\"", "\"challenge\":\"AQ\""),
            normal.replace("\"ceremonyId\":\"$CEREMONY_ID\"", "\"ceremonyId\":\"ceremony.bad\""),
            normal.replace("\"expiresAt\":${nowMillis / 1_000L + 119L}", "\"expiresAt\":${nowMillis / 1_000L}"),
            normal.replace("\"expiresAt\":${nowMillis / 1_000L + 119L}", "\"expiresAt\":${nowMillis / 1_000L + 301L}"),
            normal.dropLast(1) + ",\"unexpected\":true}",
            normal.dropLast(1) + ",\"nested\":{}}",
            normal + "{}"
        )
        cases.forEachIndexed { index, body ->
            val gateway = RecordingGateway(ASSERTION_JSON)
            val transport = RecordingTransport(jsonResponse(body))
            val error = runCatching { runBlocking { client(gateway, transport, true).authenticateOwner() } }.exceptionOrNull()
            assertNotNull("case $index", error)
            assertEquals("case $index", 0, gateway.calls)
            assertEquals("case $index", 1, transport.requests.size)
        }

        val invalidUtf8 = RecordingTransport(GoogleDriveHttpResponse(200, byteArrayOf(0xC3.toByte(), 0x28)))
        val gateway = RecordingGateway(ASSERTION_JSON)
        assertTrue(runCatching { runBlocking { client(gateway, invalidUtf8, true).authenticateOwner() } }.isFailure)
        assertEquals(0, gateway.calls)

        val oversized = RecordingTransport(jsonResponse("{\"pad\":\"${"x".repeat(4_096)}\"}"))
        assertTrue(runCatching { runBlocking { client(gateway, oversized, true).authenticateOwner() } }.isFailure)
        assertEquals(0, gateway.calls)
    }

    @Test
    fun `rejects invalid and expired sessions`() {
        val normal = sessionJson()
        val cases = listOf(
            normal.replace("\"platform\":\"android\"", "\"platform\":\"ios\""),
            normal.replace("\"sessionToken\":\"$SESSION_TOKEN\"", "\"sessionToken\":\"session.bad\""),
            normal.replace("\"generation\":0", "\"generation\":-1"),
            normal.replace("\"expiresAt\":${nowMillis / 1_000L + 599L}", "\"expiresAt\":${nowMillis / 1_000L}"),
            normal.replace("\"expiresAt\":${nowMillis / 1_000L + 599L}", "\"expiresAt\":${nowMillis / 1_000L + 661L}"),
            normal.replace("\"subject\":\"$SUBJECT\"", "\"subject\":null"),
            normal.replace("\"namespace\":\"$NAMESPACE\"", "\"namespace\":\"backup.bad\""),
            normal.replace("\"generation\":0", "\"generation\":0,\"generation\":1"),
            normal.replace("\"generation\":0,", ""),
            normal.replace("\"expiresAt\":${nowMillis / 1_000L + 599L}", "\"expiresAt\":\"future\""),
            normal.dropLast(1) + ",\"schemaVersion\":1}"
        )
        cases.forEachIndexed { index, body ->
            val gateway = RecordingGateway(ASSERTION_JSON)
            val transport = RecordingTransport(challengeResponse(), jsonResponse(body))
            val error = runCatching { runBlocking { client(gateway, transport, true).authenticateOwner() } }.exceptionOrNull()
            assertNotNull("case $index", error)
            assertEquals("case $index", 1, gateway.calls)
            assertEquals("case $index", 2, transport.requests.size)
        }
    }

    @Test
    fun `expired challenge after native UI never dispatches completion`() {
        val gateway = RecordingGateway(ASSERTION_JSON) { nowMillis += 120_000L }
        val transport = RecordingTransport(challengeResponse(), sessionResponse())
        assertTrue(runCatching { runBlocking { client(gateway, transport, true).authenticateOwner() } }.isFailure)
        assertEquals(1, gateway.calls)
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun `invalid extreme clock is rejected before native UI`() {
        val challenge = challengeResponse()
        nowMillis = Long.MIN_VALUE
        val gateway = RecordingGateway(ASSERTION_JSON)
        val transport = RecordingTransport(challenge)
        assertTrue(runCatching { runBlocking { client(gateway, transport, true).authenticateOwner() } }.isFailure)
        assertEquals(0, gateway.calls)
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun `unexpected native PRF output and null discoverable handle never reach server`() {
        val secret = base64Url(ByteArray(32) { 7 })
        val responses = listOf(
            ASSERTION_JSON.replace(
                "\"clientExtensionResults\":{}",
                "\"clientExtensionResults\":{\"prf\":{\"results\":{\"first\":\"$secret\"}}}"
            ),
            ASSERTION_JSON.replace("\"userHandle\":\"AQ\"", "\"userHandle\":null")
        )
        responses.forEach { nativeResponse ->
            val transport = RecordingTransport(challengeResponse(), sessionResponse())
            assertTrue(
                runCatching {
                    runBlocking { client(RecordingGateway(nativeResponse), transport, true).authenticateOwner() }
                }.isFailure
            )
            assertEquals(1, transport.requests.size)
            assertFalse(transport.requests[0].bodyText().contains(secret))
        }
    }

    @Test
    fun `disabled and cancelled attempts make no completion request`() {
        val disabledGateway = RecordingGateway(ASSERTION_JSON)
        val disabledTransport = RecordingTransport(challengeResponse())
        assertTrue(
            runCatching {
                runBlocking {
                    PasskeyBackupOwnerAuthenticationClient(
                        credentialGateway = disabledGateway,
                        transport = disabledTransport,
                        nowMillis = { nowMillis }
                    ).authenticateOwner()
                }
            }.isFailure
        )
        assertEquals(0, disabledGateway.calls)
        assertTrue(disabledTransport.requests.isEmpty())
        assertFalse(PasskeyBackupReleaseConfig.PASSKEY_BACKUP_ENABLED)

        val cancelledGateway = RecordingGateway(ASSERTION_JSON) { throw CancellationException("native cancelled") }
        val cancelledTransport = RecordingTransport(challengeResponse(), sessionResponse())
        val error = runCatching {
            runBlocking { client(cancelledGateway, cancelledTransport, true).authenticateOwner() }
        }.exceptionOrNull()
        assertTrue(error is CancellationException)
        assertEquals(1, cancelledTransport.requests.size)
    }

    private fun client(
        gateway: AndroidPasskeyCredentialManagerGateway,
        transport: GoogleDriveHttpTransport,
        enabled: Boolean
    ) = PasskeyBackupOwnerAuthenticationClient(
        credentialGateway = gateway,
        transport = transport,
        nowMillis = { nowMillis },
        isReleaseEnabled = enabled
    )

    private fun challengeResponse() = jsonResponse(challengeJson())
    private fun sessionResponse() = jsonResponse(sessionJson())

    private fun challengeJson() =
        """{"ceremonyId":"$CEREMONY_ID","kind":"authentication","challenge":"$CHALLENGE","rpId":"fearlesswallet.io","platform":"android","subject":null,"namespace":null,"userHandle":null,"expiresAt":${nowMillis / 1_000L + 119L}}"""

    private fun sessionJson() =
        """{"sessionToken":"$SESSION_TOKEN","subject":"$SUBJECT","namespace":"$NAMESPACE","generation":0,"platform":"android","expiresAt":${nowMillis / 1_000L + 599L}}"""

    private fun jsonResponse(body: String) = GoogleDriveHttpResponse(200, body.toByteArray(Charsets.UTF_8))

    private class RecordingTransport(vararg responses: GoogleDriveHttpResponse) : GoogleDriveHttpTransport {
        private val queued = responses.toMutableList()
        val requests = mutableListOf<GoogleDriveHttpRequest>()

        override suspend fun execute(request: GoogleDriveHttpRequest): GoogleDriveHttpResponse {
            requests += request
            return queued.removeAt(0)
        }
    }

    private class RecordingGateway(
        private val response: String,
        private val beforeReturn: () -> Unit = {}
    ) : AndroidPasskeyCredentialManagerGateway {
        var calls = 0
        var requestJson: String? = null

        override suspend fun createCredential(requestJson: String): String = error("registration is out of scope")

        override suspend fun getCredential(requestJson: String): String {
            calls++
            this.requestJson = requestJson
            beforeReturn()
            return response
        }
    }

    private fun GoogleDriveHttpRequest.bodyText() = requireNotNull(body).toString(Charsets.UTF_8)

    private companion object {
        val CEREMONY_ID = "ceremony.${base64Url(ByteArray(32) { 1 })}"
        val CHALLENGE = base64Url(ByteArray(32) { 2 })
        val SESSION_TOKEN = "session.${base64Url(ByteArray(32) { 3 })}"
        val SUBJECT = "owner:${base64Url(ByteArray(32) { 4 })}"
        val NAMESPACE = "backup:${base64Url(ByteArray(32) { 5 })}"
        const val ASSERTION_JSON =
            """{"id":"AQ","rawId":"AQ","type":"public-key","response":{"clientDataJSON":"AQ","authenticatorData":"AQ","signature":"AQ","userHandle":"AQ"},"clientExtensionResults":{}}"""

        fun base64Url(bytes: ByteArray) = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
