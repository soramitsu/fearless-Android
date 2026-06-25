package jp.co.soramitsu.backup.passkey

import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import java.util.Base64

object PasskeyBackupContract {
    const val PASSKEY_RP_ID = "fearlesswallet.io"
    const val passkeyRelyingPartyId = PASSKEY_RP_ID
    const val SCHEMA_VERSION = 1

    private const val MAX_ENCRYPTED_PAYLOAD_BYTES = 256 * 1024
    private const val MIN_CHALLENGE_BYTES = 16
    private const val MAX_CHALLENGE_BYTES = 1024
    private const val MIN_USER_ID_BYTES = 16
    private const val MAX_USER_ID_BYTES = 64
    private const val MAX_CREATED_AT_MILLIS = 4_102_444_800_000L
    private const val MIN_JSON_CONTROL_CHAR_CODE = 0x20
    private const val JSON_UNICODE_ESCAPE_RADIX = 16
    private const val JSON_UNICODE_ESCAPE_WIDTH = 4
    private const val JSON_UNICODE_ESCAPE_PAD_CHAR = '0'
    private val storageKeyPattern = Regex("^[A-Za-z0-9._:-]{8,128}$")

    fun registrationOptionsJson(
        challenge: ByteArray,
        userId: ByteArray,
        userName: String,
        displayName: String,
        rpId: String = PASSKEY_RP_ID
    ): String {
        requireValidRpId(rpId)
        requireChallenge(challenge)
        requireUserId(userId)
        val normalizedUserName = GoogleDrivePasskeyBackup.requireAccountName(userName)
        require(displayName.isNotBlank()) { "Passkey display name is required" }

        return buildString {
            append("{")
            append("\"challenge\":").append(jsonString(base64Url(challenge))).append(",")
            append("\"rp\":{\"id\":").append(jsonString(rpId)).append(",\"name\":\"Fearless Wallet\"},")
            append("\"user\":{")
            append("\"id\":").append(jsonString(base64Url(userId))).append(",")
            append("\"name\":").append(jsonString(normalizedUserName)).append(",")
            append("\"displayName\":").append(jsonString(displayName.trim()))
            append("},")
            append("\"pubKeyCredParams\":[")
            append("{\"type\":\"public-key\",\"alg\":-7},")
            append("{\"type\":\"public-key\",\"alg\":-257}")
            append("],")
            append("\"authenticatorSelection\":{")
            append("\"residentKey\":\"required\",")
            append("\"requireResidentKey\":true,")
            append("\"userVerification\":\"required\"")
            append("},")
            append("\"attestation\":\"none\",")
            append("\"timeout\":60000")
            append("}")
        }
    }

    fun assertionOptionsJson(challenge: ByteArray, rpId: String = PASSKEY_RP_ID): String {
        requireValidRpId(rpId)
        requireChallenge(challenge)

        return buildString {
            append("{")
            append("\"challenge\":").append(jsonString(base64Url(challenge))).append(",")
            append("\"rpId\":").append(jsonString(rpId)).append(",")
            append("\"userVerification\":\"required\",")
            append("\"timeout\":60000")
            append("}")
        }
    }

    fun requireStorageKey(storageKey: String): String {
        val normalized = storageKey.trim()
        require(storageKeyPattern.matches(normalized)) {
            "Passkey backup storage key must be 8-128 URL-safe characters"
        }
        return normalized
    }

    fun requireWalletId(walletId: String): String {
        val normalized = walletId.trim()
        require(storageKeyPattern.matches(normalized)) {
            "Passkey backup walletId must be 8-128 URL-safe characters"
        }
        return normalized
    }

    fun requireEncryptedPayload(payload: ByteArray): ByteArray {
        require(payload.isNotEmpty()) { "Passkey backup payload must be encrypted before storage" }
        require(payload.size <= MAX_ENCRYPTED_PAYLOAD_BYTES) { "Passkey backup payload is too large" }
        return payload
    }

    fun requireCreatedAtMillis(createdAtMillis: Long): Long {
        require(createdAtMillis in 1..MAX_CREATED_AT_MILLIS) {
            "Passkey backup createdAtMillis must be a positive Unix epoch millisecond timestamp"
        }
        return createdAtMillis
    }

    fun requireValidRpId(rpId: String): String {
        require(rpId == PASSKEY_RP_ID) { "Unsupported passkey relyingPartyId: $rpId" }
        return rpId
    }

    fun requireChallenge(challenge: ByteArray): ByteArray {
        require(challenge.size in MIN_CHALLENGE_BYTES..MAX_CHALLENGE_BYTES) {
            "Passkey challenge must be $MIN_CHALLENGE_BYTES-$MAX_CHALLENGE_BYTES bytes"
        }
        return challenge
    }

    fun requireUserId(userId: ByteArray): ByteArray {
        require(userId.size in MIN_USER_ID_BYTES..MAX_USER_ID_BYTES) {
            "Passkey user id must be $MIN_USER_ID_BYTES-$MAX_USER_ID_BYTES bytes"
        }
        return userId
    }

    fun decodeBase64Url(value: String, label: String): ByteArray {
        val normalized = value.trim()
        require(normalized.isNotEmpty()) { "Passkey $label is required" }

        return try {
            Base64.getUrlDecoder().decode(normalized)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Passkey $label must be base64url encoded", e)
        }
    }

    private fun base64Url(value: ByteArray): String {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value)
    }

    private fun jsonString(value: String): String {
        return buildString {
            append('"')
            value.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\b' -> append("\\b")
                    '\u000c' -> append("\\f")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> {
                        if (char.code < MIN_JSON_CONTROL_CHAR_CODE) {
                            append("\\u").append(
                                char.code.toString(JSON_UNICODE_ESCAPE_RADIX).padStart(
                                    JSON_UNICODE_ESCAPE_WIDTH,
                                    JSON_UNICODE_ESCAPE_PAD_CHAR
                                )
                            )
                        } else {
                            append(char)
                        }
                    }
                }
            }
            append('"')
        }
    }
}

object PasskeyBackupReleaseConfig {
    const val CHALLENGE_SERVICE_BASE_URL = "https://backup.fearlesswallet.io"
    const val challengeServiceBaseUrl = CHALLENGE_SERVICE_BASE_URL
    const val PASSKEY_BACKUP_ENABLED = false
    const val passkeyBackupEnabled = PASSKEY_BACKUP_ENABLED

    fun requireEnabled(isEnabled: Boolean = PASSKEY_BACKUP_ENABLED) {
        check(isEnabled) {
            "Passkey backup is disabled for this release"
        }
    }
}

data class PasskeyBackupEncryptedPayload(
    val storageKey: String,
    val walletId: String,
    val accountName: String,
    val createdAtMillis: Long,
    val encryptedPayload: ByteArray,
    val schemaVersion: Int = PasskeyBackupContract.SCHEMA_VERSION
) {
    init {
        PasskeyBackupContract.requireStorageKey(storageKey)
        PasskeyBackupContract.requireWalletId(walletId)
        GoogleDrivePasskeyBackup.requireAccountName(accountName)
        PasskeyBackupContract.requireCreatedAtMillis(createdAtMillis)
        PasskeyBackupContract.requireEncryptedPayload(encryptedPayload)
        require(schemaVersion == PasskeyBackupContract.SCHEMA_VERSION) {
            "Unsupported passkey backup schemaVersion: $schemaVersion"
        }
    }
}

interface PasskeyBackupCloudStorage {
    suspend fun savePasskeyBackup(payload: PasskeyBackupEncryptedPayload)
    suspend fun loadPasskeyBackup(storageKey: String): PasskeyBackupEncryptedPayload?
    suspend fun deletePasskeyBackup(storageKey: String)
}

class UnavailablePasskeyBackupCloudStorage : PasskeyBackupCloudStorage {
    override suspend fun savePasskeyBackup(payload: PasskeyBackupEncryptedPayload) {
        unavailable()
    }

    override suspend fun loadPasskeyBackup(storageKey: String): PasskeyBackupEncryptedPayload? {
        unavailable()
    }

    override suspend fun deletePasskeyBackup(storageKey: String) {
        unavailable()
    }

    private fun unavailable(): Nothing {
        throw UnsupportedOperationException(
            "PasskeyBackup cloud storage is unavailable until the public challenge service and encrypted storage backend are configured"
        )
    }
}

class PasskeyBackupCoordinator(
    @Suppress("unused") private val credentialManager: CredentialManager,
    private val cloudBackup: PasskeyBackupCloudStorage = UnavailablePasskeyBackupCloudStorage(),
    private val relyingPartyId: String = PasskeyBackupContract.PASSKEY_RP_ID,
    private val isReleaseEnabled: Boolean = PasskeyBackupReleaseConfig.PASSKEY_BACKUP_ENABLED
) {
    init {
        PasskeyBackupContract.requireValidRpId(relyingPartyId)
    }

    fun createRegistrationRequest(
        challenge: ByteArray,
        userId: ByteArray,
        userName: String,
        displayName: String
    ): CreatePublicKeyCredentialRequest {
        PasskeyBackupReleaseConfig.requireEnabled(isReleaseEnabled)
        return CreatePublicKeyCredentialRequest(
            requestJson = PasskeyBackupContract.registrationOptionsJson(
                challenge = challenge,
                userId = userId,
                userName = userName,
                displayName = displayName,
                rpId = relyingPartyId
            )
        )
    }

    fun createRestoreOption(challenge: ByteArray): GetPublicKeyCredentialOption {
        PasskeyBackupReleaseConfig.requireEnabled(isReleaseEnabled)
        return GetPublicKeyCredentialOption(
            requestJson = PasskeyBackupContract.assertionOptionsJson(
                challenge = challenge,
                rpId = relyingPartyId
            )
        )
    }

    fun requirePublicKeyCredential(credential: PublicKeyCredential): PublicKeyCredential {
        PasskeyBackupReleaseConfig.requireEnabled(isReleaseEnabled)
        return credential
    }

    suspend fun saveEncryptedCloudBackup(payload: PasskeyBackupEncryptedPayload) {
        PasskeyBackupReleaseConfig.requireEnabled(isReleaseEnabled)
        cloudBackup.savePasskeyBackup(payload)
    }

    suspend fun loadEncryptedCloudBackup(storageKey: String): PasskeyBackupEncryptedPayload? {
        PasskeyBackupReleaseConfig.requireEnabled(isReleaseEnabled)
        return cloudBackup.loadPasskeyBackup(PasskeyBackupContract.requireStorageKey(storageKey))
    }

    suspend fun deleteEncryptedCloudBackup(storageKey: String) {
        PasskeyBackupReleaseConfig.requireEnabled(isReleaseEnabled)
        cloudBackup.deletePasskeyBackup(PasskeyBackupContract.requireStorageKey(storageKey))
    }
}

class PasskeyBackupWorkflow(
    private val challengeService: PasskeyBackupChallengeService,
    private val cloudBackup: PasskeyBackupCloudStorage,
    private val relyingPartyId: String = PasskeyBackupContract.PASSKEY_RP_ID,
    private val isReleaseEnabled: Boolean = PasskeyBackupReleaseConfig.PASSKEY_BACKUP_ENABLED,
    private val createdAtMillisProvider: () -> Long = { System.currentTimeMillis() }
) {
    init {
        PasskeyBackupContract.requireValidRpId(relyingPartyId)
    }

    suspend fun beginRegistration(
        walletId: String,
        accountName: String,
        displayName: String
    ): PendingPasskeyBackupRegistration {
        PasskeyBackupReleaseConfig.requireEnabled(isReleaseEnabled)
        val normalizedWalletId = PasskeyBackupContract.requireWalletId(walletId)
        val selectedAccountName = GoogleDrivePasskeyBackup.requireAccountName(accountName)
        val selectedDisplayName = displayName.trim()
        require(selectedDisplayName.isNotEmpty()) { "Passkey registration displayName is required" }
        val challenge = challengeService.registrationChallenge(
            walletId = normalizedWalletId,
            accountName = selectedAccountName,
            displayName = selectedDisplayName
        )
        val challengeAccountName = GoogleDrivePasskeyBackup.requireMatchingAccountName(
            expected = selectedAccountName,
            actual = challenge.userName,
            ceremony = "registration challenge"
        )

        return PendingPasskeyBackupRegistration(
            registrationId = challenge.registrationId,
            storageKey = challenge.storageKey,
            walletId = normalizedWalletId,
            accountName = challengeAccountName,
            requestJson = PasskeyBackupContract.registrationOptionsJson(
                challenge = challenge.challenge,
                userId = challenge.userId,
                userName = challengeAccountName,
                displayName = challenge.displayName,
                rpId = relyingPartyId
            )
        )
    }

    suspend fun finishRegistration(
        pending: PendingPasskeyBackupRegistration,
        credentialResponseJson: String,
        encryptedPayload: ByteArray
    ): PasskeyBackupEncryptedPayload {
        PasskeyBackupReleaseConfig.requireEnabled(isReleaseEnabled)
        val result = challengeService.completeRegistration(
            registrationId = pending.registrationId,
            credentialResponseJson = credentialResponseJson
        )
        val storageKey = requireMatchingStorageKey(
            expected = pending.storageKey,
            actual = result.storageKey,
            ceremony = "registration"
        )
        val payload = PasskeyBackupEncryptedPayload(
            storageKey = storageKey,
            walletId = pending.walletId,
            accountName = pending.accountName,
            createdAtMillis = PasskeyBackupContract.requireCreatedAtMillis(createdAtMillisProvider()),
            encryptedPayload = PasskeyBackupContract.requireEncryptedPayload(encryptedPayload)
        )

        cloudBackup.savePasskeyBackup(payload)
        return payload
    }

    suspend fun beginRestore(storageKey: String): PendingPasskeyBackupAssertion {
        PasskeyBackupReleaseConfig.requireEnabled(isReleaseEnabled)
        val normalizedStorageKey = PasskeyBackupContract.requireStorageKey(storageKey)
        val challenge = challengeService.assertionChallenge(normalizedStorageKey)
        val challengeStorageKey = requireMatchingStorageKey(
            expected = normalizedStorageKey,
            actual = challenge.storageKey,
            ceremony = "assertion challenge"
        )

        return PendingPasskeyBackupAssertion(
            assertionId = challenge.assertionId,
            storageKey = challengeStorageKey,
            requestJson = PasskeyBackupContract.assertionOptionsJson(
                challenge = challenge.challenge,
                rpId = relyingPartyId
            )
        )
    }

    suspend fun finishRestore(
        pending: PendingPasskeyBackupAssertion,
        credentialResponseJson: String
    ): PasskeyBackupEncryptedPayload {
        PasskeyBackupReleaseConfig.requireEnabled(isReleaseEnabled)
        val result = challengeService.completeAssertion(
            assertionId = pending.assertionId,
            credentialResponseJson = credentialResponseJson
        )
        val storageKey = requireMatchingStorageKey(
            expected = pending.storageKey,
            actual = result.storageKey,
            ceremony = "assertion"
        )

        return requireNotNull(cloudBackup.loadPasskeyBackup(storageKey)) {
            "Passkey backup cloud storage did not contain storageKey: $storageKey"
        }
    }

    suspend fun deleteBackup(storageKey: String) {
        PasskeyBackupReleaseConfig.requireEnabled(isReleaseEnabled)
        cloudBackup.deletePasskeyBackup(PasskeyBackupContract.requireStorageKey(storageKey))
    }

    private fun requireMatchingStorageKey(
        expected: String,
        actual: String,
        ceremony: String
    ): String {
        val normalizedExpected = PasskeyBackupContract.requireStorageKey(expected)
        val normalizedActual = PasskeyBackupContract.requireStorageKey(actual)
        require(normalizedActual == normalizedExpected) {
            "Passkey $ceremony returned a mismatched storageKey"
        }
        return normalizedActual
    }
}
