package jp.co.soramitsu.backup.passkey

import android.app.Activity
import androidx.credentials.CredentialManager
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.OkHttpClient
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Base64

private const val MAX_OWNER_RESPONSE_BYTES = 4 * 1024
private const val MAX_JSON_FIELDS = 16
private const val MAX_SAFE_JS_INTEGER = 9_007_199_254_740_991L
private const val OPAQUE_BYTES = 32
private const val MILLIS_PER_SECOND = 1_000L

/**
 * Disabled portable-recovery candidate for discovering an existing owner passkey.
 * It authenticates only; it does not read a backup, unwrap a key, or install a wallet.
 */
class PasskeyBackupOwnerAuthenticationClient(
    private val credentialGateway: AndroidPasskeyCredentialManagerGateway,
    private val transport: GoogleDriveHttpTransport = OkHttpGoogleDriveHttpTransport(OkHttpClient()),
    baseUrl: String = PasskeyBackupReleaseConfig.CHALLENGE_SERVICE_BASE_URL,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val isReleaseEnabled: Boolean = PasskeyBackupReleaseConfig.PASSKEY_BACKUP_ENABLED
) {
    private val normalizedBaseUrl = normalizeBaseUrl(baseUrl)

    constructor(
        activity: Activity,
        credentialManager: CredentialManager = CredentialManager.create(activity),
        transport: GoogleDriveHttpTransport = OkHttpGoogleDriveHttpTransport(OkHttpClient()),
        baseUrl: String = PasskeyBackupReleaseConfig.CHALLENGE_SERVICE_BASE_URL,
        isReleaseEnabled: Boolean = PasskeyBackupReleaseConfig.PASSKEY_BACKUP_ENABLED
    ) : this(
        credentialGateway = SystemAndroidPasskeyCredentialManagerGateway(activity, credentialManager),
        transport = transport,
        baseUrl = baseUrl,
        isReleaseEnabled = isReleaseEnabled
    )

    suspend fun authenticateOwner(): PasskeyBackupOwnerSession {
        PasskeyBackupReleaseConfig.requireEnabled(isReleaseEnabled)
        val challenge = beginChallenge()
        val requestJson = PasskeyBackupContract.assertionOptionsJson(challenge.challenge)
        currentCoroutineContext().ensureActive()
        val nativeResponse = credentialGateway.getCredential(requestJson)
        currentCoroutineContext().ensureActive()
        requireFresh(challenge.expiresAt, MAX_CHALLENGE_EXPIRY_AHEAD_SECONDS, "challenge")
        return PasskeyBackupNativeCeremonyResult.assertion(nativeResponse, requestJson).use { result ->
            require(!result.hasLocalPrfOutput) { "Owner authentication must not evaluate PRF" }
            val publicCredential = JsonParser.parseString(result.serverCredentialJson).asJsonObject
            val session = completeChallenge(challenge.ceremonyId, publicCredential)
            requireFresh(session.expiresAt, MAX_SESSION_EXPIRY_AHEAD_SECONDS, "session")
            session
        }
    }

    /**
     * Initial-backup candidate: release local PRF bytes only after the authority verifies the
     * selected credential and a fresh owner session still names the new, empty-head owner.
     * This callback must only prepare local encryption; it does not commit or complete a backup.
     */
    suspend fun <T> withVerifiedFirstOwnerPrf(
        expectedOwner: PasskeyBackupOwnerSession,
        expectedCredentialId: String,
        prfSalt: ByteArray,
        ownerHead: PasskeyBackupOwnerHeadHttpClient,
        onVerified: suspend (PasskeyBackupOwnerSession, PasskeyBackupAuthenticatedHead, ByteArray) -> T
    ): T {
        PasskeyBackupReleaseConfig.requireEnabled(isReleaseEnabled)
        require(expectedOwner.platform == ANDROID_PLATFORM && expectedOwner.generation == 0L) {
            "First-owner session is invalid"
        }
        requireFresh(expectedOwner.expiresAt, MAX_SESSION_EXPIRY_AHEAD_SECONDS, "first-owner session")
        val credentialId = requireCredentialId(expectedCredentialId)
        PasskeyBackupContract.requirePrfSalt(prfSalt)
        val challenge = beginChallenge()
        val requestJson = PasskeyBackupContract.discoverableAssertionOptionsJsonWithPrf(
            challenge.challenge, prfSalt
        )
        currentCoroutineContext().ensureActive()
        val nativeResponse = credentialGateway.getCredential(requestJson)
        currentCoroutineContext().ensureActive()
        requireFresh(challenge.expiresAt, MAX_CHALLENGE_EXPIRY_AHEAD_SECONDS, "challenge")
        return PasskeyBackupNativeCeremonyResult.assertion(nativeResponse, requestJson).use { result ->
            val publicCredential = JsonParser.parseString(result.serverCredentialJson).asJsonObject
            require(publicCredential.get("id")?.asString == credentialId && result.hasLocalPrfOutput) {
                "First-owner credential or local PRF result mismatch"
            }
            val session = completeChallenge(challenge.ceremonyId, publicCredential)
            requireFresh(session.expiresAt, MAX_SESSION_EXPIRY_AHEAD_SECONDS, "session")
            require(
                session.subject == expectedOwner.subject &&
                    session.namespace == expectedOwner.namespace &&
                    session.generation == expectedOwner.generation
            ) { "Verified assertion changed first owner" }
            val head = ownerHead.readHead(session)
            require(
                head.ownerSubject == expectedOwner.subject &&
                    head.backupNamespace == expectedOwner.namespace &&
                    head.head == null && head.previous == null
            ) { "First-owner backup head is no longer empty" }
            result.withRequiredLocalPrfOutput { prf ->
                currentCoroutineContext().ensureActive()
                onVerified(session, head, prf)
            }
        }
    }

    private suspend fun beginChallenge(): OwnerChallenge {
        val body = JsonObject().apply {
            addProperty("schemaVersion", PasskeyBackupContract.SCHEMA_VERSION)
            addProperty("platform", ANDROID_PLATFORM)
        }
        val response = post(AUTH_CHALLENGE_PATH, body, oneShot = false)
        require(response.keySet() == CHALLENGE_RESPONSE_KEYS) {
            "Owner authentication challenge has an unexpected response shape"
        }
        require(requiredString(response, "kind") == "authentication") {
            "Owner authentication challenge has the wrong kind"
        }
        require(requiredString(response, "platform") == ANDROID_PLATFORM) {
            "Owner authentication challenge has the wrong platform"
        }
        PasskeyBackupContract.requireValidRpId(requiredString(response, "rpId"))
        require(
            response.get("subject")?.isJsonNull == true &&
                response.get("namespace")?.isJsonNull == true &&
                response.get("userHandle")?.isJsonNull == true
        ) { "Owner authentication challenge must be discoverable" }
        val challenge = canonicalBase64Url(requiredString(response, "challenge"), "challenge")
        require(challenge.size == CHALLENGE_BYTES) { "Owner authentication challenge has the wrong length" }
        val expiresAt = requiredLong(response, "expiresAt")
        requireFresh(expiresAt, MAX_CHALLENGE_EXPIRY_AHEAD_SECONDS, "challenge")
        return OwnerChallenge(
            ceremonyId = requireOpaque(requiredString(response, "ceremonyId"), "ceremony."),
            challenge = challenge,
            expiresAt = expiresAt
        )
    }

    private suspend fun completeChallenge(ceremonyId: String, credential: JsonObject): PasskeyBackupOwnerSession {
        val body = JsonObject().apply {
            addProperty("schemaVersion", PasskeyBackupContract.SCHEMA_VERSION)
            addProperty("ceremonyId", ceremonyId)
            add("credential", credential)
        }
        currentCoroutineContext().ensureActive()
        val response = post(AUTH_COMPLETE_PATH, body, oneShot = true)
        require(response.keySet() == SESSION_RESPONSE_KEYS) {
            "Owner authentication session has an unexpected response shape"
        }
        require(requiredString(response, "platform") == ANDROID_PLATFORM) {
            "Owner authentication session has the wrong platform"
        }
        val generation = requiredLong(response, "generation")
        val expiresAt = requiredLong(response, "expiresAt")
        requireFresh(expiresAt, MAX_SESSION_EXPIRY_AHEAD_SECONDS, "session")
        return PasskeyBackupOwnerSession(
            sessionToken = requireOpaque(requiredString(response, "sessionToken"), "session."),
            subject = requireOpaque(requiredString(response, "subject"), "owner:"),
            namespace = requireOpaque(requiredString(response, "namespace"), "backup:"),
            generation = generation,
            platform = ANDROID_PLATFORM,
            expiresAt = expiresAt
        )
    }

    private suspend fun post(
        path: String,
        body: JsonObject,
        oneShot: Boolean
    ): JsonObject {
        val response = transport.execute(
            GoogleDriveHttpRequest(
                method = "POST",
                url = "$normalizedBaseUrl$path",
                headers = mapOf("Content-Type" to "application/json; charset=utf-8"),
                body = body.toString().toByteArray(Charsets.UTF_8),
                isOneShot = oneShot,
                maxResponseBytes = MAX_OWNER_RESPONSE_BYTES
            )
        )
        currentCoroutineContext().ensureActive()
        require(response.code == HTTP_OK) { "Owner authentication request failed with HTTP ${response.code}" }
        return parseFlatResponse(response.body)
    }

    private fun requireFresh(
        expiresAt: Long,
        maximumLifetimeSeconds: Long,
        label: String
    ) {
        val currentTimeMillis = nowMillis()
        require(currentTimeMillis >= 0L) { "Owner authentication clock is invalid" }
        val now = currentTimeMillis / MILLIS_PER_SECOND
        require(expiresAt > now && expiresAt <= now + maximumLifetimeSeconds) {
            "Owner authentication $label is expired or has an invalid lifetime"
        }
    }

    private companion object {
        const val ANDROID_PLATFORM = "android"
        const val AUTH_CHALLENGE_PATH = "/api/passkey-backup/v1/owner/authentication/challenge"
        const val AUTH_COMPLETE_PATH = "/api/passkey-backup/v1/owner/authentication/complete"
        const val HTTP_OK = 200
        const val CHALLENGE_BYTES = 32

        // The authority issues 120s/600s expiries. Permit bounded client clock
        // lag on receipt, as the iOS peer does; the authority still enforces its
        // own expiry and we check the challenge again after native UI returns.
        const val MAX_CHALLENGE_EXPIRY_AHEAD_SECONDS = 300L
        const val MAX_SESSION_EXPIRY_AHEAD_SECONDS = 660L
        val CHALLENGE_RESPONSE_KEYS = setOf(
            "ceremonyId", "kind", "challenge", "rpId", "platform", "subject", "namespace", "userHandle", "expiresAt"
        )
        val SESSION_RESPONSE_KEYS = setOf(
            "sessionToken", "subject", "namespace", "generation", "platform", "expiresAt"
        )
    }
}

private data class OwnerChallenge(val ceremonyId: String, val challenge: ByteArray, val expiresAt: Long)

private fun parseFlatResponse(bytes: ByteArray): JsonObject {
    require(bytes.isNotEmpty() && bytes.size <= MAX_OWNER_RESPONSE_BYTES) {
        "Owner authentication response is too large or empty"
    }
    val text = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes)).toString()
    JsonReader(StringReader(text)).use { reader ->
        reader.isLenient = false
        require(reader.peek() == JsonToken.BEGIN_OBJECT) { "Owner authentication response must be an object" }
        reader.beginObject()
        val names = mutableSetOf<String>()
        while (reader.hasNext()) {
            val name = reader.nextName()
            require(names.add(name) && names.size <= MAX_JSON_FIELDS) {
                "Owner authentication response has duplicate or excess fields"
            }
            consumeFlatValue(reader)
        }
        reader.endObject()
        require(reader.peek() == JsonToken.END_DOCUMENT) { "Owner authentication response has trailing content" }
    }
    return JsonParser.parseString(text).asJsonObject
}

private fun consumeFlatValue(reader: JsonReader) {
    when (reader.peek()) {
        JsonToken.STRING, JsonToken.NUMBER -> reader.nextString()
        JsonToken.BOOLEAN -> reader.nextBoolean()
        JsonToken.NULL -> reader.nextNull()
        else -> throw IllegalArgumentException("Owner authentication response must be flat")
    }
}

private fun requiredString(response: JsonObject, name: String): String {
    val value = response.get(name)
    require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isString) {
        "Owner authentication response field $name must be a string"
    }
    return value.asString
}

private fun requiredLong(response: JsonObject, name: String): Long {
    val value = response.get(name)
    require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isNumber) {
        "Owner authentication response field $name must be an integer"
    }
    val encoded = value.asString
    require(Regex("^(0|[1-9][0-9]*)$").matches(encoded)) {
        "Owner authentication response field $name must be a canonical integer"
    }
    return encoded.toLongOrNull()?.takeIf { it <= MAX_SAFE_JS_INTEGER }
        ?: throw IllegalArgumentException("Owner authentication response field $name is out of range")
}

private fun requireOpaque(value: String, prefix: String): String {
    require(value.startsWith(prefix)) { "Owner authentication response has an invalid identifier" }
    require(canonicalBase64Url(value.removePrefix(prefix), "identifier").size == OPAQUE_BYTES) {
        "Owner authentication response has an invalid identifier"
    }
    return value
}

private fun canonicalBase64Url(value: String, name: String): ByteArray {
    require(Regex("^[A-Za-z0-9_-]{1,2048}$").matches(value)) {
        "Owner authentication $name is not canonical base64url"
    }
    val decoded = Base64.getUrlDecoder().decode(value)
    require(Base64.getUrlEncoder().withoutPadding().encodeToString(decoded) == value) {
        "Owner authentication $name is not canonical base64url"
    }
    return decoded
}
