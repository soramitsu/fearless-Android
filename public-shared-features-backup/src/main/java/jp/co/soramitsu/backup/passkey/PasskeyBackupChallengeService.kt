package jp.co.soramitsu.backup.passkey

import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.URI

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
        require(displayName.isNotBlank()) { "Passkey registration displayName is required" }
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

data class PendingPasskeyBackupRegistration(
    val registrationId: String,
    val storageKey: String,
    val walletId: String,
    val accountName: String,
    val requestJson: String
) {
    fun createRequest(): CreatePublicKeyCredentialRequest {
        return CreatePublicKeyCredentialRequest(requestJson = requestJson)
    }
}

data class PendingPasskeyBackupAssertion(
    val assertionId: String,
    val storageKey: String,
    val requestJson: String
) {
    fun createOption(): GetPublicKeyCredentialOption {
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
}

class HttpPasskeyBackupChallengeService(
    baseUrl: String,
    private val transport: GoogleDriveHttpTransport
) : PasskeyBackupChallengeService {
    private val normalizedBaseUrl = normalizeBaseUrl(baseUrl)

    override suspend fun registrationChallenge(
        walletId: String,
        accountName: String,
        displayName: String
    ): PasskeyBackupRegistrationChallenge {
        val normalizedWalletId = PasskeyBackupContract.requireWalletId(walletId)
        val selectedAccountName = GoogleDrivePasskeyBackup.requireAccountName(accountName)
        require(displayName.isNotBlank()) { "Passkey registration displayName is required" }

        val response = post(
            path = REGISTRATION_CHALLENGE_PATH,
            body = JsonObject().apply {
                addProperty("walletId", normalizedWalletId)
                addProperty("accountName", selectedAccountName)
                addProperty("displayName", displayName.trim())
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
            path = REGISTRATION_COMPLETE_PATH,
            body = JsonObject().apply {
                addProperty("registrationId", normalizedRegistrationId)
                addProperty("rpId", PasskeyBackupContract.PASSKEY_RP_ID)
                add("credential", credentialJsonObject(credentialResponseJson))
            }
        )

        return challengeResult(response)
    }

    override suspend fun assertionChallenge(storageKey: String): PasskeyBackupAssertionChallenge {
        val normalizedStorageKey = PasskeyBackupContract.requireStorageKey(storageKey)

        val response = post(
            path = ASSERTION_CHALLENGE_PATH,
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
            path = ASSERTION_COMPLETE_PATH,
            body = JsonObject().apply {
                addProperty("assertionId", normalizedAssertionId)
                addProperty("rpId", PasskeyBackupContract.PASSKEY_RP_ID)
                add("credential", credentialJsonObject(credentialResponseJson))
            }
        )

        return challengeResult(response)
    }

    private suspend fun post(path: String, body: JsonObject): JsonObject {
        val response = transport.execute(
            GoogleDriveHttpRequest(
                method = "POST",
                url = "$normalizedBaseUrl$path",
                headers = mapOf("Content-Type" to "application/json; charset=utf-8"),
                body = body.toString().toByteArray(Charsets.UTF_8)
            )
        )

        require(response.code in HTTP_SUCCESS_MIN..HTTP_SUCCESS_MAX) {
            "Passkey backup challenge service request failed with HTTP ${response.code}"
        }

        val responseText = response.body.toString(Charsets.UTF_8)
        require(responseText.isNotBlank()) {
            "Passkey backup challenge service returned an empty response"
        }

        return try {
            JsonParser.parseString(responseText).asJsonObject
        } catch (e: RuntimeException) {
            throw IllegalStateException("Malformed passkey backup challenge service response", e)
        }
    }

    private fun challengeResult(response: JsonObject): PasskeyBackupChallengeResult {
        requireRpId(response)
        return PasskeyBackupChallengeResult(
            storageKey = PasskeyBackupContract.requireStorageKey(requiredString(response, "storageKey")),
            schemaVersion = requiredInt(response, "schemaVersion")
        )
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
        return value.asInt
    }

    private fun normalizeBaseUrl(baseUrl: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        require(trimmed.isNotEmpty()) { "Passkey backup challenge service baseUrl is required" }

        val uri = URI(trimmed)
        require(uri.scheme == "https") {
            "Passkey backup challenge service baseUrl must use HTTPS"
        }
        require(!uri.host.isNullOrBlank()) {
            "Passkey backup challenge service baseUrl must include a host"
        }
        require(uri.query == null && uri.fragment == null) {
            "Passkey backup challenge service baseUrl must not include query or fragment"
        }

        return trimmed
    }

    private companion object {
        const val HTTP_SUCCESS_MIN = 200
        const val HTTP_SUCCESS_MAX = 299
        const val REGISTRATION_CHALLENGE_PATH = "/api/passkey-backup/v1/registration/challenge"
        const val REGISTRATION_COMPLETE_PATH = "/api/passkey-backup/v1/registration/complete"
        const val ASSERTION_CHALLENGE_PATH = "/api/passkey-backup/v1/assertion/challenge"
        const val ASSERTION_COMPLETE_PATH = "/api/passkey-backup/v1/assertion/complete"
    }
}

private val ceremonyIdPattern = Regex("^[A-Za-z0-9._:-]{8,128}$")

private fun requireCeremonyId(id: String, fieldName: String): String {
    val normalized = id.trim()
    require(ceremonyIdPattern.matches(normalized)) {
        "Passkey $fieldName must be 8-128 URL-safe characters"
    }
    return normalized
}
