package jp.co.soramitsu.backup.passkey

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
import java.security.SecureRandom
import java.util.Base64

private const val MAX_FLAT_RESPONSE_BYTES = 4 * 1024
private const val MAX_FLAT_FIELDS = 16
private const val MAX_SAFE_JS_INTEGER = 9_007_199_254_740_991L
private const val OPAQUE_BYTES = 32

/** A newly verified owner with an authenticated empty head, not a completed wallet backup. */
class PasskeyBackupFirstOwnerBootstrapResult internal constructor(
    val session: PasskeyBackupOwnerSession,
    val credentialId: String,
    val emptyHead: PasskeyBackupAuthenticatedHead
) {
    override fun toString(): String = "PasskeyBackupFirstOwnerBootstrapResult(redacted)"
}

/**
 * Unwired first-owner candidate. The application must supply an original-key authorizer; there
 * is no production default. Unknown bootstrap completion must be recovered by owner authentication,
 * never by automatically registering another owner or replacing a wallet from Google identity.
 */
class PasskeyBackupFirstOwnerBootstrapClient(
    private val walletAuthorizer: PasskeyBackupFirstOwnerWalletAuthorizer,
    private val tokenProvider: GoogleDriveAccessTokenProvider,
    private val ceremonyExecutor: PasskeyBackupCeremonyExecutor,
    private val integrityRequester: PasskeyBackupPlayIntegrityBootstrapRequester,
    private val ownerHead: PasskeyBackupOwnerHeadHttpClient,
    private val transport: GoogleDriveHttpTransport = OkHttpGoogleDriveHttpTransport(OkHttpClient()),
    baseUrl: String = PasskeyBackupReleaseConfig.CHALLENGE_SERVICE_BASE_URL,
    private val random: SecureRandom = SecureRandom(),
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val isReleaseEnabled: Boolean = PasskeyBackupReleaseConfig.PASSKEY_BACKUP_ENABLED
) {
    private val normalizedBaseUrl = normalizeBaseUrl(baseUrl)

    suspend fun bootstrap(
        expectedWallet: PasskeyBackupExpectedWalletIdentity,
        selectedAccountName: String,
        displayName: String
    ): PasskeyBackupFirstOwnerBootstrapResult {
        PasskeyBackupReleaseConfig.requireEnabled(isReleaseEnabled)
        val local = walletAuthorizer.authorizeAndVerifyOriginalKeys(expectedWallet)
        requireWalletEvidence(local.originalWalletEvidence, expectedWallet)
        currentCoroutineContext().ensureActive()
        val selected = tokenProvider.accessToken()
        val accountName = GoogleDrivePasskeyBackup.requireMatchingAccountName(
            selectedAccountName, selected.accountName, "bootstrap"
        )
        val challenge = beginChallenge()
        val salt = ByteArray(PRF_SALT_BYTES).also(random::nextBytes)
        val pending = try {
            PendingPasskeyBackupRegistration(
                challenge.ceremonyId, expectedWallet.storageKey, expectedWallet.walletId, accountName,
                PasskeyBackupContract.registrationOptionsJsonWithPrf(
                    challenge.challenge, challenge.userHandle, accountName, displayName, salt
                )
            )
        } finally {
            salt.fill(0)
        }
        return ceremonyExecutor.performRegistration(pending).use { result ->
            require(result.hasLocalPrfOutput) { "Passkey provider did not return registration PRF output" }
            val credential = JsonParser.parseString(result.serverCredentialJson).asJsonObject
            val credentialId = requireCredentialId(credential.get("id").asString)
            val message = PasskeyBackupFirstOwnerProof.message(challenge, result.serverCredentialJson)
            val proof = walletAuthorizer.signOriginalWalletMessage(message, expectedWallet)
            requireWalletEvidence(proof.originalWalletEvidence, expectedWallet)
            require(
                proof.scheme == local.scheme &&
                    proof.normalizedPublicKey.contentEquals(local.normalizedPublicKey)
            ) { "First-owner original signing key changed" }
            val binding = PasskeyBackupBootstrapWalletProofBinding.fromSignedWalletProof(
                message, proof.scheme, proof.normalizedPublicKey, proof.signature
            )
            requireFresh(challenge.expiresAt, CHALLENGE_MAX_AHEAD_SECONDS)
            requireSelectedAccount(selected.subject, accountName)
            val attestation = integrityRequester.requestToken(binding)
            require(attestation.requestHash == binding.requestHash) { "Bootstrap app attestation changed" }
            requireSelectedAccount(selected.subject, accountName)
            requireFresh(challenge.expiresAt, CHALLENGE_MAX_AHEAD_SECONDS)
            currentCoroutineContext().ensureActive()
            val session = completeChallenge(challenge, credential, proof, attestation)
            requireSelectedAccount(selected.subject, accountName)
            val head = ownerHead.readHead(session)
            require(
                head.ownerSubject == challenge.subject && head.backupNamespace == challenge.namespace &&
                    head.head == null && head.previous == null
            ) { "First-owner authenticated head is not empty" }
            requireSelectedAccount(selected.subject, accountName)
            currentCoroutineContext().ensureActive()
            PasskeyBackupFirstOwnerBootstrapResult(session, credentialId, head)
        }
    }

    private suspend fun beginChallenge(): PasskeyBackupFirstOwnerChallenge {
        val response = post(
            CHALLENGE_PATH,
            JsonObject().apply {
                addProperty("schemaVersion", PasskeyBackupContract.SCHEMA_VERSION)
                addProperty("platform", ANDROID_PLATFORM)
            },
            oneShot = false
        )
        require(response.keySet() == CHALLENGE_FIELDS) { "Invalid first-owner challenge shape" }
        require(response.string("kind") == "bootstrap" && response.string("platform") == ANDROID_PLATFORM) {
            "Wrong first-owner challenge kind or platform"
        }
        PasskeyBackupContract.requireValidRpId(response.string("rpId"))
        val challenge = PasskeyBackupFirstOwnerChallenge(
            ceremonyId = response.identifier("ceremonyId", "ceremony."),
            challenge = response.base64("challenge", CHALLENGE_BYTES),
            subject = response.identifier("subject", "owner:"),
            namespace = response.identifier("namespace", "backup:"),
            userHandle = response.base64("userHandle", CHALLENGE_BYTES),
            expiresAt = response.long("expiresAt")
        )
        requireFresh(challenge.expiresAt, CHALLENGE_MAX_AHEAD_SECONDS)
        return challenge
    }

    private suspend fun completeChallenge(
        challenge: PasskeyBackupFirstOwnerChallenge,
        credential: JsonObject,
        proof: PasskeyBackupFirstOwnerSignedProof,
        attestation: PasskeyBackupPlayIntegrityBootstrapToken
    ): PasskeyBackupOwnerSession {
        val response = post(
            COMPLETE_PATH,
            JsonObject().apply {
                addProperty("schemaVersion", PasskeyBackupContract.SCHEMA_VERSION)
                addProperty("ceremonyId", challenge.ceremonyId)
                add("credential", credential)
                add("walletProof", proof.serverJson())
                add("appAttestation", JsonParser.parseString(attestation.serverAttestationJson()))
            },
            oneShot = true
        )
        require(response.keySet() == SESSION_FIELDS) { "Invalid first-owner session shape" }
        require(response.string("platform") == ANDROID_PLATFORM && response.long("generation") == 0L) {
            "Wrong first-owner session platform or generation"
        }
        val expiresAt = response.long("expiresAt")
        requireFresh(expiresAt, SESSION_MAX_AHEAD_SECONDS)
        val subject = response.identifier("subject", "owner:")
        val namespace = response.identifier("namespace", "backup:")
        require(subject == challenge.subject && namespace == challenge.namespace) {
            "First-owner session changed owner identity"
        }
        return PasskeyBackupOwnerSession(
            response.identifier("sessionToken", "session."), subject, namespace,
            0L, ANDROID_PLATFORM, expiresAt
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
                headers = mapOf("Content-Type" to "application/json; charset=utf-8", "Cache-Control" to "no-store"),
                body = body.toString().toByteArray(Charsets.UTF_8),
                isOneShot = oneShot,
                maxResponseBytes = MAX_RESPONSE_BYTES
            )
        )
        currentCoroutineContext().ensureActive()
        require(response.code == HTTP_OK) { "First-owner request failed with HTTP ${response.code}" }
        return parseFlatResponse(response.body)
    }

    private suspend fun requireSelectedAccount(subject: String, accountName: String) {
        val current = tokenProvider.accessToken()
        require(current.subject == subject) { "Google Drive selected account changed" }
        GoogleDrivePasskeyBackup.requireMatchingAccountName(accountName, current.accountName, "bootstrap")
        currentCoroutineContext().ensureActive()
    }

    private fun requireWalletEvidence(
        evidence: PasskeyBackupLocalWalletEvidence,
        expectedWallet: PasskeyBackupExpectedWalletIdentity
    ) {
        require(
            evidence.storageKey == expectedWallet.storageKey && evidence.walletId == expectedWallet.walletId &&
                evidence.publicIdentitySha256 == expectedWallet.publicIdentitySha256 &&
                evidence.decryptionVerified && evidence.originalKeySigningVerified && evidence.originalKeyExportVerified
        ) { "First-owner original wallet authorization failed" }
    }

    private fun requireFresh(expiresAt: Long, maximumAhead: Long) {
        val millis = nowMillis()
        require(millis >= 0L) { "Invalid first-owner clock" }
        val now = millis / MILLIS_PER_SECOND
        require(expiresAt > now && expiresAt <= now + maximumAhead) {
            "First-owner ceremony or session expired"
        }
    }

    private companion object {
        const val CHALLENGE_PATH = "/api/passkey-backup/v1/owner/bootstrap/challenge"
        const val COMPLETE_PATH = "/api/passkey-backup/v1/owner/bootstrap/complete"
        const val ANDROID_PLATFORM = "android"
        const val HTTP_OK = 200
        const val MAX_RESPONSE_BYTES = MAX_FLAT_RESPONSE_BYTES
        const val PRF_SALT_BYTES = 32
        const val CHALLENGE_BYTES = 32
        const val MILLIS_PER_SECOND = 1_000L
        const val CHALLENGE_MAX_AHEAD_SECONDS = 300L
        const val SESSION_MAX_AHEAD_SECONDS = 660L
        val CHALLENGE_FIELDS = setOf(
            "ceremonyId", "kind", "challenge", "rpId", "platform", "subject", "namespace", "userHandle", "expiresAt"
        )
        val SESSION_FIELDS = setOf("sessionToken", "subject", "namespace", "generation", "platform", "expiresAt")
    }
}

private fun parseFlatResponse(bytes: ByteArray): JsonObject {
    require(bytes.isNotEmpty() && bytes.size <= MAX_FLAT_RESPONSE_BYTES) {
        "First-owner response size is invalid"
    }
    val decoded = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes)).toString()
    JsonReader(StringReader(decoded)).use { reader ->
        reader.isLenient = false
        scanFlatObject(reader)
        require(reader.peek() == JsonToken.END_DOCUMENT) { "First-owner response has trailing data" }
    }
    return JsonParser.parseString(decoded).asJsonObject
}

private fun scanFlatObject(reader: JsonReader) {
    require(reader.peek() == JsonToken.BEGIN_OBJECT) { "First-owner response must be an object" }
    reader.beginObject()
    val keys = mutableSetOf<String>()
    while (reader.hasNext()) {
        require(keys.add(reader.nextName()) && keys.size <= MAX_FLAT_FIELDS) {
            "Duplicate first-owner response field"
        }
        when (reader.peek()) {
            JsonToken.STRING, JsonToken.NUMBER -> reader.nextString()
            else -> error("First-owner response has an invalid field type")
        }
    }
    reader.endObject()
}

private fun JsonObject.string(name: String): String {
    val value = get(name)
    require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isString) {
        "Invalid first-owner response string"
    }
    return value.asString
}

private fun JsonObject.long(name: String): Long {
    val value = get(name)
    require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isNumber) {
        "Invalid first-owner response integer"
    }
    return value.asString.takeIf { Regex("^(0|[1-9][0-9]*)$").matches(it) }
        ?.toLongOrNull()?.takeIf { it <= MAX_SAFE_JS_INTEGER }
        ?: throw IllegalArgumentException("Invalid first-owner response integer")
}

private fun JsonObject.base64(name: String, bytes: Int): ByteArray {
    val value = string(name)
    require(Regex("^[A-Za-z0-9_-]{43}$").matches(value)) { "Invalid first-owner base64url field" }
    val decoded = Base64.getUrlDecoder().decode(value)
    require(decoded.size == bytes && Base64.getUrlEncoder().withoutPadding().encodeToString(decoded) == value) {
        "Invalid first-owner base64url field"
    }
    return decoded
}

private fun JsonObject.identifier(name: String, prefix: String): String {
    val value = string(name)
    require(value.startsWith(prefix)) { "Invalid first-owner identifier" }
    JsonObject().apply { addProperty("identifier", value.removePrefix(prefix)) }.base64("identifier", OPAQUE_BYTES)
    return value
}
