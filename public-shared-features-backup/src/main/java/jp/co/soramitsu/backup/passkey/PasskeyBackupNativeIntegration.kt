package jp.co.soramitsu.backup.passkey

import android.app.Activity
import android.os.Build
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.PublicKeyCredential
import com.google.gson.JsonObject
import com.google.gson.JsonParser

interface AndroidPasskeyCredentialManagerGateway {
    suspend fun createCredential(requestJson: String): String

    suspend fun getCredential(requestJson: String): String
}

class SystemAndroidPasskeyCredentialManagerGateway(
    private val activity: Activity,
    private val credentialManager: CredentialManager
) : AndroidPasskeyCredentialManagerGateway {
    override suspend fun createCredential(requestJson: String): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            throw UnsupportedOperationException("Passkey registration requires Android 9 or newer")
        }
        val response = credentialManager.createCredential(
            activity,
            androidx.credentials.CreatePublicKeyCredentialRequest(requestJson)
        )
        require(response is CreatePublicKeyCredentialResponse) {
            "Credential Manager returned an unexpected registration response type"
        }
        return response.registrationResponseJson
    }

    override suspend fun getCredential(requestJson: String): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            throw UnsupportedOperationException("Passkey authentication requires Android 9 or newer")
        }
        val credential = credentialManager.getCredential(
            activity,
            GetCredentialRequest(listOf(androidx.credentials.GetPublicKeyCredentialOption(requestJson)))
        ).credential
        require(credential is PublicKeyCredential) {
            "Credential Manager returned an unexpected assertion credential type"
        }
        return credential.authenticationResponseJson
    }
}

interface PasskeyBackupCeremonyExecutor {
    suspend fun performRegistration(pending: PendingPasskeyBackupRegistration): PasskeyBackupNativeCeremonyResult
    suspend fun performAssertion(pending: PendingPasskeyBackupAssertion): PasskeyBackupNativeCeremonyResult
}

/** Only [serverCredentialJson] may cross the challenge-service boundary. */
class PasskeyBackupNativeCeremonyResult private constructor(
    val serverCredentialJson: String,
    @Transient private var localPrfOutput: ByteArray?
) : AutoCloseable {
    val hasLocalPrfOutput: Boolean get() = localPrfOutput != null

    fun <T> withLocalPrfOutput(block: (ByteArray?) -> T): T {
        val copy = localPrfOutput?.copyOf()
        return try {
            block(copy)
        } finally {
            copy?.fill(0)
        }
    }

    override fun close() {
        localPrfOutput?.fill(0)
        localPrfOutput = null
    }

    override fun toString(): String = "PasskeyBackupNativeCeremonyResult(redacted)"

    companion object {
        private const val MAX_RESPONSE_BYTES = 128 * 1024
        private const val PRF_OUTPUT_BYTES = 32

        fun registration(responseJson: String): PasskeyBackupNativeCeremonyResult =
            fromCredentialManager(responseJson, registration = true)

        fun assertion(responseJson: String): PasskeyBackupNativeCeremonyResult =
            fromCredentialManager(responseJson, registration = false)

        private fun fromCredentialManager(
            responseJson: String,
            registration: Boolean
        ): PasskeyBackupNativeCeremonyResult {
            require(
                responseJson.isNotEmpty() &&
                    responseJson == responseJson.trim() &&
                    responseJson.toByteArray().size <= MAX_RESPONSE_BYTES
            ) { "Credential Manager returned an invalid passkey response" }
            val raw = runCatching { JsonParser.parseString(responseJson).asJsonObject }.getOrNull()
            require(raw != null && raw.size() > 0) {
                "Credential Manager returned malformed passkey response JSON"
            }

            // Rebuild from a public-field allowlist. WebAuthn's toJSON() can include
            // clientExtensionResults.prf.results.first, which is wallet key material.
            val public = JsonObject().apply {
                addProperty("id", raw.requiredString("id"))
                addProperty("rawId", raw.requiredString("rawId"))
                addProperty("type", raw.requiredString("type"))
            }
            val rawResponse = raw.requiredObject("response")
            val publicResponse = JsonObject().apply {
                addProperty("clientDataJSON", rawResponse.requiredString("clientDataJSON"))
                if (registration) {
                    addProperty("attestationObject", rawResponse.requiredString("attestationObject"))
                } else {
                    addProperty("authenticatorData", rawResponse.requiredString("authenticatorData"))
                    addProperty("signature", rawResponse.requiredString("signature"))
                    addProperty("userHandle", rawResponse.requiredString("userHandle"))
                }
            }
            public.add("response", publicResponse)

            val (publicExtensions, localPrf) = parseExtensions(raw)
            public.add("clientExtensionResults", publicExtensions)
            return PasskeyBackupNativeCeremonyResult(public.toString(), localPrf)
        }

        private fun parseExtensions(raw: JsonObject): Pair<JsonObject, ByteArray?> {
            val rawExtensions = raw.get("clientExtensionResults")?.let {
                require(it.isJsonObject) { "Credential Manager returned invalid passkey extensions" }
                it.asJsonObject
            }
            require(rawExtensions == null || rawExtensions.keySet().all { it == "prf" || it == "credProps" }) {
                "Credential Manager returned unsupported passkey extensions"
            }
            val publicExtensions = JsonObject()
            rawExtensions?.get("credProps")?.let { properties ->
                require(properties.isJsonObject) {
                    "Credential Manager returned invalid public credential properties"
                }
                val rawProperties = properties.asJsonObject
                require(
                    rawProperties.size() == 1 &&
                        rawProperties.get("rk")?.asJsonPrimitive?.isBoolean == true
                ) {
                    "Credential Manager returned invalid public credential properties"
                }
                val publicProperties = JsonObject().apply {
                    addProperty("rk", rawProperties.get("rk").asBoolean)
                }
                publicExtensions.add("credProps", publicProperties)
            }
            val localPrf = rawExtensions?.get("prf")?.let { extension ->
                require(extension.isJsonObject) { "Credential Manager returned invalid PRF extension" }
                val prf = extension.asJsonObject
                val enabled = prf.get("enabled")
                require(enabled == null || enabled.isJsonPrimitive && enabled.asJsonPrimitive.isBoolean) {
                    "Credential Manager returned invalid PRF availability"
                }
                val results = prf.get("results") ?: return@let null
                require(results.isJsonObject) { "Credential Manager returned invalid PRF results" }
                val result = results.asJsonObject
                require(result.size() == 1 && result.has("first")) {
                    "Credential Manager returned unexpected PRF results"
                }
                val output = PasskeyBackupContract.decodeBase64Url(
                    result.requiredString("first"), "local PRF output"
                )
                require(output.size == PRF_OUTPUT_BYTES && enabled?.asBoolean != false) {
                    "Credential Manager returned invalid PRF output"
                }
                output
            }
            return publicExtensions to localPrf
        }

        private fun JsonObject.requiredObject(name: String): JsonObject {
            val value = get(name)
            require(value != null && value.isJsonObject) {
                "Credential Manager returned an invalid passkey response"
            }
            return value.asJsonObject
        }

        private fun JsonObject.requiredString(name: String): String {
            val value = get(name)
            require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isString && value.asString.isNotEmpty()) {
                "Credential Manager returned an invalid passkey response"
            }
            return value.asString
        }
    }
}

class CredentialManagerPasskeyBackupCeremonyExecutor(
    private val gateway: AndroidPasskeyCredentialManagerGateway,
    private val isReleaseEnabled: Boolean = PasskeyBackupReleaseConfig.PASSKEY_BACKUP_ENABLED
) : PasskeyBackupCeremonyExecutor {
    constructor(
        activity: Activity,
        credentialManager: CredentialManager = CredentialManager.create(activity),
        isReleaseEnabled: Boolean = PasskeyBackupReleaseConfig.PASSKEY_BACKUP_ENABLED
    ) : this(
        gateway = SystemAndroidPasskeyCredentialManagerGateway(activity, credentialManager),
        isReleaseEnabled = isReleaseEnabled
    )

    override suspend fun performRegistration(
        pending: PendingPasskeyBackupRegistration
    ): PasskeyBackupNativeCeremonyResult {
        PasskeyBackupReleaseConfig.requireEnabled(isReleaseEnabled)
        return PasskeyBackupNativeCeremonyResult.registration(gateway.createCredential(pending.requestJson))
    }

    override suspend fun performAssertion(pending: PendingPasskeyBackupAssertion): PasskeyBackupNativeCeremonyResult {
        PasskeyBackupReleaseConfig.requireEnabled(isReleaseEnabled)
        return PasskeyBackupNativeCeremonyResult.assertion(
            gateway.getCredential(pending.requestJson)
        )
    }
}

class PasskeyBackupClient(
    private val workflow: PasskeyBackupWorkflow,
    private val ceremonyExecutor: PasskeyBackupCeremonyExecutor,
    private val isReleaseEnabled: Boolean = PasskeyBackupReleaseConfig.PASSKEY_BACKUP_ENABLED
) {
    suspend fun registerBackup(
        walletId: String,
        accountName: String,
        displayName: String,
        plaintextBackup: ByteArray
    ): PasskeyBackupEncryptedPayload {
        PasskeyBackupReleaseConfig.requireEnabled(isReleaseEnabled)
        val pending = workflow.beginRegistration(walletId, accountName, displayName)
        return ceremonyExecutor.performRegistration(pending).use { result ->
            workflow.finishRegistrationWithPlaintext(
                pending, result.serverCredentialJson, plaintextBackup
            )
        }
    }

    suspend fun restoreBackup(storageKey: String): ByteArray {
        PasskeyBackupReleaseConfig.requireEnabled(isReleaseEnabled)
        val pending = workflow.beginRestore(storageKey)
        return ceremonyExecutor.performAssertion(pending).use { result ->
            workflow.finishRestoreWithDecryption(pending, result.serverCredentialJson)
        }
    }

    suspend fun listCredentials(storageKey: String): PasskeyBackupCredentialListResult {
        PasskeyBackupReleaseConfig.requireEnabled(isReleaseEnabled)
        return workflow.listCredentials(storageKey)
    }

    suspend fun revokeCredential(storageKey: String, credentialId: String): PasskeyBackupCredentialRevokeResult {
        PasskeyBackupReleaseConfig.requireEnabled(isReleaseEnabled)
        return workflow.revokeCredential(storageKey, credentialId)
    }

    suspend fun deleteBackup(storageKey: String) {
        PasskeyBackupReleaseConfig.requireEnabled(isReleaseEnabled)
        workflow.deleteBackup(storageKey)
    }
}

object PasskeyBackupComposition {
    fun create(
        activity: Activity,
        challengeService: PasskeyBackupChallengeService,
        cloudStorage: PasskeyBackupCloudStorage,
        backupKeyProvider: RecoverablePasskeyBackupKeyProvider =
            UnavailableRecoverablePasskeyBackupKeyProvider(),
        credentialManager: CredentialManager = CredentialManager.create(activity),
        envelopeCryptography: PasskeyBackupEnvelopeCryptography =
            AesGcmPasskeyBackupEnvelopeCryptography(),
        isReleaseEnabled: Boolean = PasskeyBackupReleaseConfig.PASSKEY_BACKUP_ENABLED
    ): PasskeyBackupClient {
        val workflow = PasskeyBackupWorkflow(
            challengeService = challengeService,
            cloudBackup = cloudStorage,
            backupKeyProvider = backupKeyProvider,
            envelopeCryptography = envelopeCryptography,
            isReleaseEnabled = isReleaseEnabled
        )
        val ceremonyExecutor = CredentialManagerPasskeyBackupCeremonyExecutor(
            activity = activity,
            credentialManager = credentialManager,
            isReleaseEnabled = isReleaseEnabled
        )
        return PasskeyBackupClient(workflow, ceremonyExecutor, isReleaseEnabled)
    }
}
