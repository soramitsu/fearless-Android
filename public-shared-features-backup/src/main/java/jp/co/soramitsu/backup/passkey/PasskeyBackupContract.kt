package jp.co.soramitsu.backup.passkey

import android.os.Build
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.Base64

private const val DEFAULT_REGISTRATION_COMPENSATION_TIMEOUT_MILLIS = 5_000L
private const val MIN_REGISTRATION_COMPENSATION_TIMEOUT_MILLIS = 1L
private const val MAX_REGISTRATION_COMPENSATION_TIMEOUT_MILLIS = 30_000L

internal fun Exception.addPasskeyRegistrationCompensationFailure(revokeError: Exception) {
    val reportedRevokeError = if (revokeError === this) {
        IllegalStateException(
            "Passkey registration compensation failed with the same exception instance as the primary operation"
        )
    } else {
        revokeError
    }
    addSuppressed(reportedRevokeError)
}

object PasskeyBackupContract {
    const val PASSKEY_RP_ID = "fearlesswallet.io"
    const val passkeyRelyingPartyId = PASSKEY_RP_ID
    const val SCHEMA_VERSION = 1

    private const val MAX_ENCRYPTED_PAYLOAD_BYTES = 256 * 1024
    private const val MIN_CHALLENGE_BYTES = 16
    private const val MAX_CHALLENGE_BYTES = 1024
    private const val USER_ID_BYTES = 32
    private const val MAX_DISPLAY_NAME_LENGTH = 128
    private const val MAX_CREATED_AT_MILLIS = 4_102_444_800_000L
    private const val MIN_JSON_CONTROL_CHAR_CODE = 0x20
    private const val JSON_UNICODE_ESCAPE_RADIX = 16
    private const val JSON_UNICODE_ESCAPE_WIDTH = 4
    private const val JSON_UNICODE_ESCAPE_PAD_CHAR = '0'
    private const val BASE64_QUANTUM = 4
    private const val INVALID_BASE64URL_REMAINDER = 1
    private val storageKeyPattern = Regex("^[A-Za-z0-9._:-]{8,128}$")
    private val base64UrlPattern = Regex("^[A-Za-z0-9_-]+$")

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
        val normalizedDisplayName = requireDisplayName(displayName)

        return buildString {
            append("{")
            append("\"challenge\":").append(jsonString(base64Url(challenge))).append(",")
            append("\"rp\":{\"id\":").append(jsonString(rpId)).append(",\"name\":\"Fearless Wallet\"},")
            append("\"user\":{")
            append("\"id\":").append(jsonString(base64Url(userId))).append(",")
            append("\"name\":").append(jsonString(normalizedUserName)).append(",")
            append("\"displayName\":").append(jsonString(normalizedDisplayName))
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
        require(userId.size == USER_ID_BYTES) {
            "Passkey user id must be $USER_ID_BYTES bytes"
        }
        return userId
    }

    fun requireDisplayName(displayName: String): String {
        val normalized = displayName.trim()
        require(normalized.isNotEmpty() && normalized.length <= MAX_DISPLAY_NAME_LENGTH) {
            "Passkey display name must be 1-$MAX_DISPLAY_NAME_LENGTH characters"
        }
        return normalized
    }

    fun decodeBase64Url(value: String, label: String): ByteArray {
        require(value.isNotEmpty()) { "Passkey $label is required" }
        require(
            base64UrlPattern.matches(value) &&
                value.length % BASE64_QUANTUM != INVALID_BASE64URL_REMAINDER
        ) {
            "Passkey $label must be canonical unpadded base64url"
        }

        val decoded = try {
            Base64.getUrlDecoder().decode(value)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Passkey $label must be base64url encoded", e)
        }
        require(base64Url(decoded) == value) {
            "Passkey $label must be canonical unpadded base64url"
        }
        return decoded
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

class PasskeyBackupEncryptedPayload(
    val storageKey: String,
    val walletId: String,
    val accountName: String,
    val createdAtMillis: Long,
    encryptedPayload: ByteArray,
    val schemaVersion: Int = PasskeyBackupContract.SCHEMA_VERSION
) {
    private val encryptedPayloadBytes = encryptedPayload.copyOf()

    val encryptedPayload: ByteArray
        get() = encryptedPayloadBytes.copyOf()

    init {
        PasskeyBackupContract.requireStorageKey(storageKey)
        PasskeyBackupContract.requireWalletId(walletId)
        GoogleDrivePasskeyBackup.requireAccountName(accountName)
        PasskeyBackupContract.requireCreatedAtMillis(createdAtMillis)
        PasskeyBackupContract.requireEncryptedPayload(encryptedPayloadBytes)
        PasskeyBackupEnvelopeV1Format.requireCanonical(encryptedPayloadBytes)
        require(schemaVersion == PasskeyBackupContract.SCHEMA_VERSION) {
            "Unsupported passkey backup schemaVersion: $schemaVersion"
        }
        PasskeyBackupEnvelopeMetadata(
            storageKey = storageKey,
            walletId = walletId,
            accountName = accountName,
            createdAtMillis = createdAtMillis,
            schemaVersion = schemaVersion
        )
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

class PasskeyBackupEncryptedRecordMetadataRequiredException : UnsupportedOperationException(
    "Opaque encrypted passkey payload registration is unsupported; provide the complete authenticated record metadata"
)

class PasskeyBackupCoordinator(
    @Suppress("unused") private val credentialManager: CredentialManager,
    private val cloudBackup: PasskeyBackupCloudStorage = UnavailablePasskeyBackupCloudStorage(),
    private val challengeService: PasskeyBackupChallengeService? = null,
    private val backupKeyProvider: RecoverablePasskeyBackupKeyProvider =
        UnavailableRecoverablePasskeyBackupKeyProvider(),
    private val envelopeCryptography: PasskeyBackupEnvelopeCryptography =
        AesGcmPasskeyBackupEnvelopeCryptography(),
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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            throw UnsupportedOperationException("Passkey registration requires Android 9 or newer")
        }
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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            throw UnsupportedOperationException("Passkey authentication requires Android 9 or newer")
        }
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
        requireAuthenticatedEnvelope(payload)
        cloudBackup.savePasskeyBackup(payload)
    }

    suspend fun loadEncryptedCloudBackup(storageKey: String): PasskeyBackupEncryptedPayload? {
        PasskeyBackupReleaseConfig.requireEnabled(isReleaseEnabled)
        val payload = cloudBackup.loadPasskeyBackup(
            PasskeyBackupContract.requireStorageKey(storageKey)
        ) ?: return null
        requireAuthenticatedEnvelope(payload)
        return payload
    }

    suspend fun deleteEncryptedCloudBackup(storageKey: String) {
        PasskeyBackupReleaseConfig.requireEnabled(isReleaseEnabled)
        val normalizedStorageKey = PasskeyBackupContract.requireStorageKey(storageKey)
        val lifecycleService = checkNotNull(challengeService) {
            "Passkey credential lifecycle service is required before deleting cloud backup"
        }
        val revoked = lifecycleService.revokeAllCredentials(normalizedStorageKey)
        check(
            revoked.storageKey == normalizedStorageKey &&
                revoked.credentialId == null &&
                revoked.remainingCredentials == 0
        ) {
            "Passkey credential revoke-all returned an invalid result"
        }
        cloudBackup.deletePasskeyBackup(normalizedStorageKey)
    }

    private suspend fun requireAuthenticatedEnvelope(payload: PasskeyBackupEncryptedPayload) {
        val metadata = payload.envelopeMetadata()
        val key = backupKeyProvider.backupKey(metadata)
        try {
            val plaintext = envelopeCryptography.decrypt(payload.encryptedPayload, metadata, key)
            plaintext.fill(0)
        } finally {
            key.fill(0)
        }
    }
}

class PasskeyBackupWorkflow(
    private val challengeService: PasskeyBackupChallengeService,
    private val cloudBackup: PasskeyBackupCloudStorage,
    private val backupKeyProvider: RecoverablePasskeyBackupKeyProvider =
        UnavailableRecoverablePasskeyBackupKeyProvider(),
    private val envelopeCryptography: PasskeyBackupEnvelopeCryptography =
        AesGcmPasskeyBackupEnvelopeCryptography(),
    private val relyingPartyId: String = PasskeyBackupContract.PASSKEY_RP_ID,
    private val isReleaseEnabled: Boolean = PasskeyBackupReleaseConfig.PASSKEY_BACKUP_ENABLED,
    private val registrationCompensationTimeoutMillis: Long =
        DEFAULT_REGISTRATION_COMPENSATION_TIMEOUT_MILLIS,
    private val createdAtMillisProvider: () -> Long = { System.currentTimeMillis() }
) {
    init {
        PasskeyBackupContract.requireValidRpId(relyingPartyId)
        val hasValidCompensationTimeout =
            registrationCompensationTimeoutMillis >= MIN_REGISTRATION_COMPENSATION_TIMEOUT_MILLIS &&
                registrationCompensationTimeoutMillis <= MAX_REGISTRATION_COMPENSATION_TIMEOUT_MILLIS
        require(hasValidCompensationTimeout) {
            "Passkey registration compensation timeout must be 1-30000 milliseconds"
        }
    }

    suspend fun beginRegistration(
        walletId: String,
        accountName: String,
        displayName: String
    ): PendingPasskeyBackupRegistration {
        PasskeyBackupReleaseConfig.requireEnabled(isReleaseEnabled)
        val normalizedWalletId = PasskeyBackupContract.requireWalletId(walletId)
        val selectedAccountName = GoogleDrivePasskeyBackup.requireAccountName(accountName)
        val selectedDisplayName = PasskeyBackupContract.requireDisplayName(displayName)
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

    suspend fun finishRegistrationWithEncryptedRecord(
        pending: PendingPasskeyBackupRegistration,
        credentialResponseJson: String,
        record: PasskeyBackupEncryptedPayload
    ): PasskeyBackupEncryptedPayload {
        PasskeyBackupReleaseConfig.requireEnabled(isReleaseEnabled)
        val expectedStorageKey = PasskeyBackupContract.requireStorageKey(pending.storageKey)
        val expectedWalletId = PasskeyBackupContract.requireWalletId(pending.walletId)
        val expectedAccountName = GoogleDrivePasskeyBackup.requireAccountName(pending.accountName)
        require(record.storageKey == expectedStorageKey) {
            "Encrypted passkey backup record returned a mismatched storageKey"
        }
        require(record.walletId == expectedWalletId) {
            "Encrypted passkey backup record returned a mismatched walletId"
        }
        require(record.accountName == expectedAccountName) {
            "Encrypted passkey backup record returned a mismatched Google account"
        }
        requireAuthenticatedEnvelope(record)
        val credentialId = registrationCredentialId(credentialResponseJson)

        var completionConfirmed = false
        return withRegistrationCompensation(
            expectedStorageKey,
            credentialId,
            shouldCompensate = { error ->
                completionConfirmed ||
                    error is PasskeyBackupRegistrationCompletionUncertain
            }
        ) {
            val result = challengeService.completeRegistration(
                registrationId = pending.registrationId,
                credentialResponseJson = credentialResponseJson
            )
            completionConfirmed = true
            requireMatchingStorageKey(
                expected = expectedStorageKey,
                actual = result.storageKey,
                ceremony = "registration"
            )
            cloudBackup.savePasskeyBackup(record)
            record
        }
    }

    @Deprecated(
        message = "Opaque ciphertext cannot be authenticated without its original AAD metadata; use finishRegistrationWithPlaintext or finishRegistrationWithEncryptedRecord"
    )
    @Suppress("UnusedParameter")
    suspend fun finishRegistration(
        pending: PendingPasskeyBackupRegistration,
        credentialResponseJson: String,
        encryptedPayload: ByteArray
    ): PasskeyBackupEncryptedPayload {
        PasskeyBackupReleaseConfig.requireEnabled(isReleaseEnabled)
        throw PasskeyBackupEncryptedRecordMetadataRequiredException()
    }

    suspend fun finishRegistrationWithPlaintext(
        pending: PendingPasskeyBackupRegistration,
        credentialResponseJson: String,
        plaintextBackup: ByteArray
    ): PasskeyBackupEncryptedPayload {
        PasskeyBackupReleaseConfig.requireEnabled(isReleaseEnabled)
        val expectedStorageKey = PasskeyBackupContract.requireStorageKey(pending.storageKey)
        val metadata = PasskeyBackupEnvelopeMetadata(
            storageKey = expectedStorageKey,
            walletId = pending.walletId,
            accountName = pending.accountName,
            createdAtMillis = PasskeyBackupContract.requireCreatedAtMillis(createdAtMillisProvider())
        )
        val plaintextCopy = plaintextBackup.copyOf()
        val encryptedPayload = try {
            val key = backupKeyProvider.backupKey(metadata)
            try {
                envelopeCryptography.encrypt(plaintextCopy, metadata, key)
            } finally {
                key.fill(0)
            }
        } finally {
            plaintextCopy.fill(0)
        }
        val payload = PasskeyBackupEncryptedPayload(
            storageKey = metadata.storageKey,
            walletId = metadata.walletId,
            accountName = metadata.accountName,
            createdAtMillis = metadata.createdAtMillis,
            encryptedPayload = encryptedPayload,
            schemaVersion = metadata.schemaVersion
        )
        val credentialId = registrationCredentialId(credentialResponseJson)

        var completionConfirmed = false
        return withRegistrationCompensation(
            expectedStorageKey,
            credentialId,
            shouldCompensate = { error ->
                completionConfirmed ||
                    error is PasskeyBackupRegistrationCompletionUncertain
            }
        ) {
            val result = challengeService.completeRegistration(
                registrationId = pending.registrationId,
                credentialResponseJson = credentialResponseJson
            )
            completionConfirmed = true
            requireMatchingStorageKey(
                expected = expectedStorageKey,
                actual = result.storageKey,
                ceremony = "registration"
            )
            cloudBackup.savePasskeyBackup(payload)
            payload
        }
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

    suspend fun finishRestoreWithDecryption(
        pending: PendingPasskeyBackupAssertion,
        credentialResponseJson: String
    ): ByteArray {
        val record = finishRestore(pending, credentialResponseJson)
        val metadata = record.envelopeMetadata()
        val key = backupKeyProvider.backupKey(metadata)
        return try {
            envelopeCryptography.decrypt(record.encryptedPayload, metadata, key)
        } finally {
            key.fill(0)
        }
    }

    suspend fun listCredentials(storageKey: String): PasskeyBackupCredentialListResult {
        PasskeyBackupReleaseConfig.requireEnabled(isReleaseEnabled)
        val normalizedStorageKey = PasskeyBackupContract.requireStorageKey(storageKey)
        val result = challengeService.listCredentials(normalizedStorageKey)
        requireMatchingStorageKey(
            expected = normalizedStorageKey,
            actual = result.storageKey,
            ceremony = "credential list"
        )
        return result
    }

    suspend fun revokeCredential(storageKey: String, credentialId: String): PasskeyBackupCredentialRevokeResult {
        PasskeyBackupReleaseConfig.requireEnabled(isReleaseEnabled)
        val normalizedStorageKey = PasskeyBackupContract.requireStorageKey(storageKey)
        val normalizedCredentialId = requireCredentialId(credentialId)
        val result = challengeService.revokeCredential(
            normalizedStorageKey,
            normalizedCredentialId
        )
        requireMatchingStorageKey(
            expected = normalizedStorageKey,
            actual = result.storageKey,
            ceremony = "credential revoke"
        )
        require(result.credentialId == normalizedCredentialId) {
            "Passkey credential revoke returned a mismatched credentialId"
        }
        return result
    }

    suspend fun deleteBackup(storageKey: String) {
        PasskeyBackupReleaseConfig.requireEnabled(isReleaseEnabled)
        val normalizedStorageKey = PasskeyBackupContract.requireStorageKey(storageKey)
        val revoked = challengeService.revokeAllCredentials(normalizedStorageKey)
        require(
            revoked.storageKey == normalizedStorageKey &&
                revoked.credentialId == null &&
                revoked.remainingCredentials == 0
        ) {
            "Passkey credential revoke-all returned an invalid result"
        }
        cloudBackup.deletePasskeyBackup(normalizedStorageKey)
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

    private fun registrationCredentialId(credentialResponseJson: String): String {
        val root = runCatching { JsonParser.parseString(credentialResponseJson) }.getOrNull()
        require(root != null && root.isJsonObject) {
            "Passkey registration credential response must be a JSON object"
        }
        val id = root.asJsonObject.get("id")
        require(id != null && id.isJsonPrimitive && id.asJsonPrimitive.isString) {
            "Passkey registration credential response id is required"
        }
        return requireCredentialId(id.asString)
    }

    private suspend fun requireAuthenticatedEnvelope(payload: PasskeyBackupEncryptedPayload) {
        val metadata = payload.envelopeMetadata()
        val key = backupKeyProvider.backupKey(metadata)
        try {
            val plaintext = envelopeCryptography.decrypt(payload.encryptedPayload, metadata, key)
            plaintext.fill(0)
        } finally {
            key.fill(0)
        }
    }

    private suspend fun <T> withRegistrationCompensation(
        storageKey: String,
        credentialId: String,
        shouldCompensate: (Exception) -> Boolean,
        operation: suspend () -> T
    ): T {
        try {
            return operation()
        } catch (operationError: Exception) {
            if (shouldCompensate(operationError)) {
                compensateRegistration(storageKey, credentialId, operationError)
            }
            throw operationError
        }
    }

    private suspend fun compensateRegistration(
        storageKey: String,
        credentialId: String,
        operationError: Exception
    ) {
        val compensationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val compensation = compensationScope.async {
            val result = challengeService.revokeCredential(storageKey, credentialId)
            requireMatchingStorageKey(
                expected = storageKey,
                actual = result.storageKey,
                ceremony = "registration compensation revoke"
            )
            require(result.credentialId == credentialId) {
                "Passkey registration compensation revoke returned a mismatched credentialId"
            }
        }
        try {
            withContext(NonCancellable) {
                withTimeout(registrationCompensationTimeoutMillis) {
                    compensation.await()
                }
            }
        } catch (revokeError: Exception) {
            operationError.addPasskeyRegistrationCompensationFailure(revokeError)
        } finally {
            compensation.cancel()
            compensationScope.cancel()
        }
    }
}
