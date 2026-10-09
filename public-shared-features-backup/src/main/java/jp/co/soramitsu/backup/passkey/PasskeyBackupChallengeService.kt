package jp.co.soramitsu.backup.passkey

import android.os.Build
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import java.net.URI
import java.security.MessageDigest
import java.util.Base64

private const val MAX_CREDENTIALS_PER_STORAGE_KEY = 32
private const val MAX_CREDENTIAL_ID_LENGTH = 512

data class PasskeyBackupRegistrationChallenge(
    val registrationId: String,
    val challenge: ByteArray,
    val userId: ByteArray,
    val userName: String,
    val displayName: String,
    val storageKey: String,
    val schemaVersion: Int = PasskeyBackupContract.SCHEMA_VERSION
) {
    init {
        requireCeremonyId(registrationId, "registrationId")
        PasskeyBackupContract.requireChallenge(challenge)
        PasskeyBackupContract.requireUserId(userId)
        GoogleDrivePasskeyBackup.requireAccountName(userName)
        PasskeyBackupContract.requireDisplayName(displayName)
        PasskeyBackupContract.requireStorageKey(storageKey)
        require(schemaVersion == PasskeyBackupContract.SCHEMA_VERSION) {
            "Unsupported passkey backup schemaVersion: $schemaVersion"
        }
    }
}

data class PasskeyBackupAssertionChallenge(
    val assertionId: String,
    val challenge: ByteArray,
    val storageKey: String,
    val schemaVersion: Int = PasskeyBackupContract.SCHEMA_VERSION
) {
    init {
        requireCeremonyId(assertionId, "assertionId")
        PasskeyBackupContract.requireChallenge(challenge)
        PasskeyBackupContract.requireStorageKey(storageKey)
        require(schemaVersion == PasskeyBackupContract.SCHEMA_VERSION) {
            "Unsupported passkey backup schemaVersion: $schemaVersion"
        }
    }
}

data class PasskeyBackupChallengeResult(
    val storageKey: String,
    val schemaVersion: Int = PasskeyBackupContract.SCHEMA_VERSION
) {
    init {
        PasskeyBackupContract.requireStorageKey(storageKey)
        require(schemaVersion == PasskeyBackupContract.SCHEMA_VERSION) {
            "Unsupported passkey backup schemaVersion: $schemaVersion"
        }
    }
}

data class PasskeyBackupCredentialSummary(
    val id: String,
    val aaguid: String,
    val registrationPlatform: String,
    val deviceType: String,
    val backedUp: Boolean,
    val transports: List<String>? = null
) {
    init {
        requireCredentialId(id)
        require(AAGUID_PATTERN.matches(aaguid)) { "Passkey credential aaguid must be a canonical UUID" }
        require(registrationPlatform in REGISTRATION_PLATFORMS) {
            "Passkey credential registrationPlatform is unsupported"
        }
        require(deviceType in DEVICE_TYPES) { "Passkey credential deviceType is unsupported" }
        require(deviceType != "singleDevice" || !backedUp) {
            "Passkey credential backup flags are inconsistent"
        }
        transports?.let { values ->
            require(values.size <= AUTHENTICATOR_TRANSPORTS.size) {
                "Passkey credential transports contains too many values"
            }
            require(values.toSet().size == values.size && values.all(AUTHENTICATOR_TRANSPORTS::contains)) {
                "Passkey credential transports contains duplicate or unsupported values"
            }
        }
    }

    private companion object {
        val AAGUID_PATTERN = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
        val REGISTRATION_PLATFORMS = setOf("android", "ios")
        val DEVICE_TYPES = setOf("singleDevice", "multiDevice")
        val AUTHENTICATOR_TRANSPORTS = setOf("ble", "cable", "hybrid", "internal", "nfc", "smart-card", "usb")
    }
}

data class PasskeyBackupCredentialListResult(
    val storageKey: String,
    val credentials: List<PasskeyBackupCredentialSummary>,
    val schemaVersion: Int = PasskeyBackupContract.SCHEMA_VERSION
) {
    init {
        PasskeyBackupContract.requireStorageKey(storageKey)
        require(credentials.size <= MAX_CREDENTIALS_PER_STORAGE_KEY) {
            "Passkey credential list exceeds the supported limit"
        }
        require(credentials.map { it.id }.toSet().size == credentials.size) {
            "Passkey credential list contains duplicate credential ids"
        }
        require(schemaVersion == PasskeyBackupContract.SCHEMA_VERSION) {
            "Unsupported passkey backup schemaVersion: $schemaVersion"
        }
    }
}

data class PasskeyBackupCredentialRevokeResult(
    val storageKey: String,
    val credentialId: String?,
    val remainingCredentials: Int,
    val schemaVersion: Int = PasskeyBackupContract.SCHEMA_VERSION
) {
    init {
        PasskeyBackupContract.requireStorageKey(storageKey)
        credentialId?.let(::requireCredentialId)
        require(remainingCredentials in 0..MAX_CREDENTIALS_PER_STORAGE_KEY) {
            "Passkey remainingCredentials must be between 0 and $MAX_CREDENTIALS_PER_STORAGE_KEY"
        }
        require(schemaVersion == PasskeyBackupContract.SCHEMA_VERSION) {
            "Unsupported passkey backup schemaVersion: $schemaVersion"
        }
    }
}

data class PendingPasskeyBackupRegistration(
    val registrationId: String,
    val storageKey: String,
    val walletId: String,
    val accountName: String,
    val requestJson: String
) {
    fun createRequest(): CreatePublicKeyCredentialRequest {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            throw UnsupportedOperationException("Passkey registration requires Android 9 or newer")
        }
        return CreatePublicKeyCredentialRequest(requestJson = requestJson)
    }
}

data class PendingPasskeyBackupAssertion(
    val assertionId: String,
    val storageKey: String,
    val requestJson: String
) {
    fun createOption(): GetPublicKeyCredentialOption {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            throw UnsupportedOperationException("Passkey authentication requires Android 9 or newer")
        }
        return GetPublicKeyCredentialOption(requestJson = requestJson)
    }
}

interface PasskeyBackupChallengeService {
    suspend fun registrationChallenge(
        walletId: String,
        accountName: String,
        displayName: String
    ): PasskeyBackupRegistrationChallenge

    suspend fun completeRegistration(
        registrationId: String,
        credentialResponseJson: String
    ): PasskeyBackupChallengeResult

    suspend fun assertionChallenge(storageKey: String): PasskeyBackupAssertionChallenge

    suspend fun completeAssertion(assertionId: String, credentialResponseJson: String): PasskeyBackupChallengeResult

    suspend fun listCredentials(storageKey: String): PasskeyBackupCredentialListResult {
        throw UnsupportedOperationException("Passkey credential lifecycle service is unavailable")
    }

    suspend fun revokeCredential(storageKey: String, credentialId: String): PasskeyBackupCredentialRevokeResult {
        throw UnsupportedOperationException("Passkey credential lifecycle service is unavailable")
    }

    suspend fun revokeAllCredentials(storageKey: String): PasskeyBackupCredentialRevokeResult {
        throw UnsupportedOperationException("Passkey credential lifecycle service is unavailable")
    }
}

sealed interface PasskeyBackupRegistrationCompletionUncertain

class PasskeyBackupRegistrationCompletionUncertainException(cause: Throwable) :
    Exception("Passkey registration completion outcome is unknown", cause),
    PasskeyBackupRegistrationCompletionUncertain

class PasskeyBackupRegistrationCompletionUncertainCancellationException(cause: Throwable) :
    CancellationException("Passkey registration completion was cancelled after dispatch"),
    PasskeyBackupRegistrationCompletionUncertain {
    init {
        initCause(cause)
    }
}

data class PasskeyBackupAuthorizationRequest(
    val method: String,
    val path: String,
    val bodySha256: String
) {
    init {
        require(method == "POST") { "Passkey authorization method must be POST" }
        require(path in ALLOWED_PATHS) {
            "Passkey authorization path must be one of the supported challenge-service ceremony paths"
        }
        require(isCanonicalSha256(bodySha256)) {
            "Passkey authorization bodySha256 must be an unpadded base64url SHA-256 digest"
        }
    }

    companion object {
        const val REGISTRATION_CHALLENGE_PATH = "/api/passkey-backup/v1/registration/challenge"
        const val REGISTRATION_COMPLETE_PATH = "/api/passkey-backup/v1/registration/complete"
        const val ASSERTION_CHALLENGE_PATH = "/api/passkey-backup/v1/assertion/challenge"
        const val ASSERTION_COMPLETE_PATH = "/api/passkey-backup/v1/assertion/complete"
        const val CREDENTIALS_LIST_PATH = "/api/passkey-backup/v1/credentials/list"
        const val CREDENTIALS_REVOKE_PATH = "/api/passkey-backup/v1/credentials/revoke"
        const val CREDENTIALS_REVOKE_ALL_PATH = "/api/passkey-backup/v1/credentials/revoke-all"

        private val ALLOWED_PATHS = setOf(
            REGISTRATION_CHALLENGE_PATH,
            REGISTRATION_COMPLETE_PATH,
            ASSERTION_CHALLENGE_PATH,
            ASSERTION_COMPLETE_PATH,
            CREDENTIALS_LIST_PATH,
            CREDENTIALS_REVOKE_PATH,
            CREDENTIALS_REVOKE_ALL_PATH
        )
        private const val SHA256_LENGTH_BYTES = 32
        private val SHA256_BASE64URL_PATTERN = Regex("^[A-Za-z0-9_-]{43}$")

        private fun isCanonicalSha256(value: String): Boolean {
            if (!SHA256_BASE64URL_PATTERN.matches(value)) return false

            return runCatching {
                val decoded = Base64.getUrlDecoder().decode(value)
                decoded.size == SHA256_LENGTH_BYTES &&
                    Base64.getUrlEncoder().withoutPadding().encodeToString(decoded) == value
            }.getOrDefault(false)
        }
    }
}

fun interface PasskeyBackupAuthorizationProvider {
    /**
     * Returns a one-time opaque grant bound to [request]. The external issuer's
     * subject must identify stable Fearless wallet ownership across platforms;
     * it must not be a raw Google account, Apple account, or device identifier.
     */
    suspend fun authorizationToken(request: PasskeyBackupAuthorizationRequest): String
}

class UnavailablePasskeyBackupAuthorizationProvider : PasskeyBackupAuthorizationProvider {
    override suspend fun authorizationToken(request: PasskeyBackupAuthorizationRequest): String {
        throw UnsupportedOperationException(
            "Passkey backup authorization is unavailable until a production mobile-attestation issuer is configured"
        )
    }
}

class HttpPasskeyBackupChallengeService(
    baseUrl: String,
    private val transport: GoogleDriveHttpTransport,
    private val authorizationProvider: PasskeyBackupAuthorizationProvider =
        UnavailablePasskeyBackupAuthorizationProvider()
) : PasskeyBackupChallengeService {
    private val normalizedBaseUrl = normalizeBaseUrl(baseUrl)

    override suspend fun registrationChallenge(
        walletId: String,
        accountName: String,
        displayName: String
    ): PasskeyBackupRegistrationChallenge {
        val normalizedWalletId = PasskeyBackupContract.requireWalletId(walletId)
        val selectedAccountName = GoogleDrivePasskeyBackup.requireAccountName(accountName)
        val normalizedDisplayName = PasskeyBackupContract.requireDisplayName(displayName)

        val response = post(
            path = PasskeyBackupAuthorizationRequest.REGISTRATION_CHALLENGE_PATH,
            body = JsonObject().apply {
                addProperty("walletId", normalizedWalletId)
                addProperty("accountName", selectedAccountName)
                addProperty("displayName", normalizedDisplayName)
                addProperty("rpId", PasskeyBackupContract.PASSKEY_RP_ID)
                addProperty("schemaVersion", PasskeyBackupContract.SCHEMA_VERSION)
            }
        )

        requireRpId(response)
        val schemaVersion = requiredInt(response, "schemaVersion")

        return PasskeyBackupRegistrationChallenge(
            registrationId = requiredString(response, "registrationId"),
            challenge = PasskeyBackupContract.requireChallenge(
                PasskeyBackupContract.decodeBase64Url(requiredString(response, "challenge"), "registration challenge")
            ),
            userId = PasskeyBackupContract.requireUserId(
                PasskeyBackupContract.decodeBase64Url(requiredString(response, "userId"), "registration userId")
            ),
            userName = GoogleDrivePasskeyBackup.requireAccountName(requiredString(response, "userName")),
            displayName = requiredString(response, "displayName").trim(),
            storageKey = PasskeyBackupContract.requireStorageKey(requiredString(response, "storageKey")),
            schemaVersion = schemaVersion
        )
    }

    override suspend fun completeRegistration(
        registrationId: String,
        credentialResponseJson: String
    ): PasskeyBackupChallengeResult {
        val normalizedRegistrationId = requireCeremonyId(registrationId, "registrationId")

        val response = post(
            path = PasskeyBackupAuthorizationRequest.REGISTRATION_COMPLETE_PATH,
            registrationCompletion = true,
            body = JsonObject().apply {
                addProperty("registrationId", normalizedRegistrationId)
                addProperty("rpId", PasskeyBackupContract.PASSKEY_RP_ID)
                add("credential", credentialJsonObject(credentialResponseJson))
            }
        )

        return try {
            challengeResult(response)
        } catch (error: RuntimeException) {
            throw PasskeyBackupRegistrationCompletionUncertainException(error)
        }
    }

    override suspend fun assertionChallenge(storageKey: String): PasskeyBackupAssertionChallenge {
        val normalizedStorageKey = PasskeyBackupContract.requireStorageKey(storageKey)

        val response = post(
            path = PasskeyBackupAuthorizationRequest.ASSERTION_CHALLENGE_PATH,
            body = JsonObject().apply {
                addProperty("storageKey", normalizedStorageKey)
                addProperty("rpId", PasskeyBackupContract.PASSKEY_RP_ID)
                addProperty("schemaVersion", PasskeyBackupContract.SCHEMA_VERSION)
            }
        )

        requireRpId(response)
        val responseStorageKey = PasskeyBackupContract.requireStorageKey(requiredString(response, "storageKey"))
        require(responseStorageKey == normalizedStorageKey) {
            "Passkey assertion challenge returned a mismatched storageKey"
        }

        return PasskeyBackupAssertionChallenge(
            assertionId = requiredString(response, "assertionId"),
            challenge = PasskeyBackupContract.requireChallenge(
                PasskeyBackupContract.decodeBase64Url(requiredString(response, "challenge"), "assertion challenge")
            ),
            storageKey = responseStorageKey,
            schemaVersion = requiredInt(response, "schemaVersion")
        )
    }

    override suspend fun completeAssertion(
        assertionId: String,
        credentialResponseJson: String
    ): PasskeyBackupChallengeResult {
        val normalizedAssertionId = requireCeremonyId(assertionId, "assertionId")

        val response = post(
            path = PasskeyBackupAuthorizationRequest.ASSERTION_COMPLETE_PATH,
            body = JsonObject().apply {
                addProperty("assertionId", normalizedAssertionId)
                addProperty("rpId", PasskeyBackupContract.PASSKEY_RP_ID)
                add("credential", credentialJsonObject(credentialResponseJson))
            }
        )

        return challengeResult(response)
    }

    override suspend fun listCredentials(storageKey: String): PasskeyBackupCredentialListResult {
        val normalizedStorageKey = PasskeyBackupContract.requireStorageKey(storageKey)
        val response = post(
            path = PasskeyBackupAuthorizationRequest.CREDENTIALS_LIST_PATH,
            body = lifecycleBody(normalizedStorageKey)
        )
        requireRpId(response)
        requireResponseStorageKey(response, normalizedStorageKey)
        val credentialsValue = response.get("credentials")
        require(credentialsValue != null && credentialsValue.isJsonArray) {
            "Passkey challenge response field credentials is required"
        }
        val credentials = credentialsValue.asJsonArray.map { element ->
            require(element.isJsonObject) { "Passkey credential summary must be an object" }
            val credential = element.asJsonObject
            val requiredKeys = setOf("id", "aaguid", "registrationPlatform", "deviceType", "backedUp")
            require(credential.keySet() == requiredKeys || credential.keySet() == requiredKeys + "transports") {
                "Passkey credential summary has an unexpected response shape"
            }
            val transports = credential.get("transports")?.let { value ->
                require(value.isJsonArray) { "Passkey credential transports must be an array" }
                value.asJsonArray.map { transport ->
                    require(transport.isJsonPrimitive && transport.asJsonPrimitive.isString) {
                        "Passkey credential transport must be a string"
                    }
                    transport.asString
                }
            }
            PasskeyBackupCredentialSummary(
                id = requiredString(credential, "id"),
                aaguid = requiredString(credential, "aaguid"),
                registrationPlatform = requiredString(credential, "registrationPlatform"),
                deviceType = requiredString(credential, "deviceType"),
                backedUp = requiredBoolean(credential, "backedUp"),
                transports = transports
            )
        }
        return PasskeyBackupCredentialListResult(
            storageKey = normalizedStorageKey,
            credentials = credentials,
            schemaVersion = requiredInt(response, "schemaVersion")
        )
    }

    override suspend fun revokeCredential(
        storageKey: String,
        credentialId: String
    ): PasskeyBackupCredentialRevokeResult {
        val normalizedStorageKey = PasskeyBackupContract.requireStorageKey(storageKey)
        val normalizedCredentialId = requireCredentialId(credentialId)
        val response = post(
            path = PasskeyBackupAuthorizationRequest.CREDENTIALS_REVOKE_PATH,
            body = lifecycleBody(normalizedStorageKey).apply {
                addProperty("credentialId", normalizedCredentialId)
            }
        )
        requireRpId(response)
        requireResponseStorageKey(response, normalizedStorageKey)
        require(requiredString(response, "credentialId") == normalizedCredentialId) {
            "Passkey credential revoke returned a mismatched credentialId"
        }
        return PasskeyBackupCredentialRevokeResult(
            storageKey = normalizedStorageKey,
            credentialId = normalizedCredentialId,
            remainingCredentials = requiredInt(response, "remainingCredentials"),
            schemaVersion = requiredInt(response, "schemaVersion")
        )
    }

    override suspend fun revokeAllCredentials(storageKey: String): PasskeyBackupCredentialRevokeResult {
        val normalizedStorageKey = PasskeyBackupContract.requireStorageKey(storageKey)
        val response = post(
            path = PasskeyBackupAuthorizationRequest.CREDENTIALS_REVOKE_ALL_PATH,
            body = lifecycleBody(normalizedStorageKey)
        )
        requireRpId(response)
        requireResponseStorageKey(response, normalizedStorageKey)
        val remaining = requiredInt(response, "remainingCredentials")
        require(remaining == 0) { "Passkey credential revoke-all must return zero remaining credentials" }
        return PasskeyBackupCredentialRevokeResult(
            storageKey = normalizedStorageKey,
            credentialId = null,
            remainingCredentials = remaining,
            schemaVersion = requiredInt(response, "schemaVersion")
        )
    }

    private fun lifecycleBody(storageKey: String): JsonObject {
        return JsonObject().apply {
            addProperty("storageKey", storageKey)
            addProperty("rpId", PasskeyBackupContract.PASSKEY_RP_ID)
            addProperty("schemaVersion", PasskeyBackupContract.SCHEMA_VERSION)
        }
    }

    private fun requireResponseStorageKey(response: JsonObject, expected: String) {
        require(PasskeyBackupContract.requireStorageKey(requiredString(response, "storageKey")) == expected) {
            "Passkey credential lifecycle response returned a mismatched storageKey"
        }
    }

    private suspend fun post(
        path: String,
        body: JsonObject,
        registrationCompletion: Boolean = false
    ): JsonObject {
        val bodyBytes = body.toString().toByteArray(Charsets.UTF_8)
        val authorizationRequest = PasskeyBackupAuthorizationRequest(
            method = "POST",
            path = path,
            bodySha256 = sha256Base64Url(bodyBytes)
        )
        val authorizationToken = requireAuthorizationToken(
            authorizationProvider.authorizationToken(authorizationRequest)
        )
        val response = try {
            transport.execute(
                GoogleDriveHttpRequest(
                    method = "POST",
                    url = "$normalizedBaseUrl$path",
                    headers = mapOf(
                        "Content-Type" to "application/json; charset=utf-8",
                        "Authorization" to "Bearer $authorizationToken"
                    ),
                    body = bodyBytes,
                    isOneShot = registrationCompletion
                )
            )
        } catch (error: CancellationException) {
            if (registrationCompletion) {
                throwRegistrationCompletionAware(
                    PasskeyBackupRegistrationCompletionUncertainCancellationException(error),
                    registrationCompletion = false
                )
            }
            throwRegistrationCompletionAware(error, registrationCompletion = false)
        } catch (error: Exception) {
            throwRegistrationCompletionAware(error, registrationCompletion)
        }

        if (response.code != HTTP_SUCCESS_CODE) {
            val error = IllegalArgumentException(
                "Passkey backup challenge service request failed with HTTP ${response.code}"
            )
            throwRegistrationCompletionAware(
                error,
                registrationCompletion && isAmbiguousRegistrationCompletionStatus(response.code)
            )
        }

        val responseText = response.body.toString(Charsets.UTF_8)
        if (responseText.isBlank()) {
            val error = IllegalStateException("Passkey backup challenge service returned an empty response")
            val reportedError = if (registrationCompletion) error else IllegalArgumentException(error.message, error)
            throwRegistrationCompletionAware(reportedError, registrationCompletion)
        }

        return try {
            JsonParser.parseString(responseText).asJsonObject.also { responseObject ->
                val expectedKeys = EXPECTED_RESPONSE_KEYS_BY_PATH.getValue(path)
                require(responseObject.keySet() == expectedKeys) {
                    "Passkey backup challenge service returned an unexpected response shape"
                }
            }
        } catch (e: RuntimeException) {
            val malformed = IllegalStateException("Malformed passkey backup challenge service response", e)
            throwRegistrationCompletionAware(malformed, registrationCompletion)
        }
    }

    private fun challengeResult(response: JsonObject): PasskeyBackupChallengeResult {
        requireRpId(response)
        return PasskeyBackupChallengeResult(
            storageKey = PasskeyBackupContract.requireStorageKey(requiredString(response, "storageKey")),
            schemaVersion = requiredInt(response, "schemaVersion")
        )
    }

    private fun isAmbiguousRegistrationCompletionStatus(statusCode: Int): Boolean {
        return statusCode in HTTP_SERVER_ERROR_MIN..HTTP_SERVER_ERROR_MAX ||
            statusCode in RETRYABLE_CLIENT_ERROR_CODES
    }

    private fun requireRpId(response: JsonObject) {
        PasskeyBackupContract.requireValidRpId(requiredString(response, "rpId"))
    }

    private fun credentialJsonObject(credentialResponseJson: String): JsonObject {
        val normalized = credentialResponseJson.trim()
        require(normalized.isNotEmpty()) { "Passkey credential response JSON is required" }

        val element = try {
            JsonParser.parseString(normalized)
        } catch (e: RuntimeException) {
            throw IllegalArgumentException("Passkey credential response must be valid JSON", e)
        }

        require(element.isJsonObject) { "Passkey credential response must be a JSON object" }
        return element.asJsonObject
    }

    private fun requiredString(response: JsonObject, name: String): String {
        val value = response.get(name)
        require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isString) {
            "Passkey challenge response field $name is required"
        }
        return value.asString
    }

    private fun requiredInt(response: JsonObject, name: String): Int {
        val value: JsonElement? = response.get(name)
        require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isNumber) {
            "Passkey challenge response field $name is required"
        }
        val encoded = value.asString
        require(CANONICAL_NON_NEGATIVE_INTEGER_PATTERN.matches(encoded)) {
            "Passkey challenge response field $name must be a canonical integer"
        }
        val parsed = encoded.toLongOrNull()
        require(parsed != null && parsed <= Int.MAX_VALUE) {
            "Passkey challenge response field $name must fit a 32-bit integer"
        }
        return parsed.toInt()
    }

    private fun requiredBoolean(response: JsonObject, name: String): Boolean {
        val value = response.get(name)
        require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isBoolean) {
            "Passkey challenge response field $name must be boolean"
        }
        return value.asBoolean
    }

    private fun sha256Base64Url(value: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    private fun requireAuthorizationToken(value: String): String {
        require(value.length in 1..MAX_AUTHORIZATION_TOKEN_LENGTH && AUTHORIZATION_TOKEN_PATTERN.matches(value)) {
            "Passkey backup authorization token must be a bounded RFC 6750 bearer token"
        }
        return value
    }

    private companion object {
        const val MAX_AUTHORIZATION_TOKEN_LENGTH = 4096
        val AUTHORIZATION_TOKEN_PATTERN = Regex("^[A-Za-z0-9._~+/-]+={0,}$")
        val CANONICAL_NON_NEGATIVE_INTEGER_PATTERN = Regex("^(0|[1-9][0-9]*)$")
        const val HTTP_SUCCESS_CODE = 200
        const val HTTP_SERVER_ERROR_MIN = 500
        const val HTTP_SERVER_ERROR_MAX = 599
        val RETRYABLE_CLIENT_ERROR_CODES = setOf(408, 425, 429)
        val EXPECTED_RESPONSE_KEYS_BY_PATH = mapOf(
            PasskeyBackupAuthorizationRequest.REGISTRATION_CHALLENGE_PATH to setOf(
                "registrationId",
                "challenge",
                "userId",
                "userName",
                "displayName",
                "storageKey",
                "rpId",
                "schemaVersion"
            ),
            PasskeyBackupAuthorizationRequest.REGISTRATION_COMPLETE_PATH to setOf(
                "storageKey",
                "rpId",
                "schemaVersion"
            ),
            PasskeyBackupAuthorizationRequest.ASSERTION_CHALLENGE_PATH to setOf(
                "assertionId",
                "challenge",
                "storageKey",
                "rpId",
                "schemaVersion"
            ),
            PasskeyBackupAuthorizationRequest.ASSERTION_COMPLETE_PATH to setOf(
                "storageKey",
                "rpId",
                "schemaVersion"
            ),
            PasskeyBackupAuthorizationRequest.CREDENTIALS_LIST_PATH to setOf(
                "storageKey",
                "credentials",
                "rpId",
                "schemaVersion"
            ),
            PasskeyBackupAuthorizationRequest.CREDENTIALS_REVOKE_PATH to setOf(
                "storageKey",
                "credentialId",
                "remainingCredentials",
                "rpId",
                "schemaVersion"
            ),
            PasskeyBackupAuthorizationRequest.CREDENTIALS_REVOKE_ALL_PATH to setOf(
                "storageKey",
                "remainingCredentials",
                "rpId",
                "schemaVersion"
            )
        )
    }
}

private fun normalizeBaseUrl(baseUrl: String): String {
    require(baseUrl.isNotEmpty() && baseUrl == baseUrl.trim()) {
        "Passkey backup challenge service baseUrl must be canonical"
    }

    val uri = URI(baseUrl)
    require(uri.scheme == "https") {
        "Passkey backup challenge service baseUrl must use HTTPS"
    }
    require(!uri.host.isNullOrBlank()) {
        "Passkey backup challenge service baseUrl must include a host"
    }
    require(uri.userInfo == null) {
        "Passkey backup challenge service baseUrl must not include credentials"
    }
    require(uri.port == -1) {
        "Passkey backup challenge service baseUrl must not include an explicit port"
    }
    require(uri.query == null && uri.fragment == null) {
        "Passkey backup challenge service baseUrl must not include query or fragment"
    }
    require(uri.rawPath.isEmpty() || uri.rawPath == "/") {
        "Passkey backup challenge service baseUrl must not include a path"
    }
    val host = requireNotNull(uri.host)
    require(host == host.lowercase() && (baseUrl == "https://$host" || baseUrl == "https://$host/")) {
        "Passkey backup challenge service baseUrl must be canonical"
    }

    return "https://$host"
}

private fun throwRegistrationCompletionAware(error: Exception, registrationCompletion: Boolean): Nothing {
    if (registrationCompletion) {
        throw PasskeyBackupRegistrationCompletionUncertainException(error)
    }
    throw error
}

private val ceremonyIdPattern = Regex("^[A-Za-z0-9._:-]{8,128}$")

private fun requireCeremonyId(id: String, fieldName: String): String {
    val normalized = id.trim()
    require(ceremonyIdPattern.matches(normalized)) {
        "Passkey $fieldName must be 8-128 URL-safe characters"
    }
    return normalized
}

internal fun requireCredentialId(id: String): String {
    require(id.length in 1..MAX_CREDENTIAL_ID_LENGTH) {
        "Passkey credentialId must be a non-empty canonical base64url identifier"
    }
    val decoded = PasskeyBackupContract.decodeBase64Url(id, "credentialId")
    require(decoded.isNotEmpty()) { "Passkey credentialId must not decode to empty bytes" }
    return id
}
