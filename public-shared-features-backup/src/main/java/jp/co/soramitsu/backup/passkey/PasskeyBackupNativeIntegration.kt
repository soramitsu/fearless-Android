package jp.co.soramitsu.backup.passkey

import android.app.Activity
import android.os.Build
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.PublicKeyCredential
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
    suspend fun performRegistration(pending: PendingPasskeyBackupRegistration): String
    suspend fun performAssertion(pending: PendingPasskeyBackupAssertion): String
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

    override suspend fun performRegistration(pending: PendingPasskeyBackupRegistration): String {
        PasskeyBackupReleaseConfig.requireEnabled(isReleaseEnabled)
        return requireCredentialResponseJson(gateway.createCredential(pending.requestJson))
    }

    override suspend fun performAssertion(pending: PendingPasskeyBackupAssertion): String {
        PasskeyBackupReleaseConfig.requireEnabled(isReleaseEnabled)
        return requireCredentialResponseJson(
            gateway.getCredential(pending.requestJson)
        )
    }

    private fun requireCredentialResponseJson(value: String): String {
        require(value.isNotEmpty() && value == value.trim() && value.toByteArray().size <= MAX_RESPONSE_BYTES) {
            "Credential Manager returned an invalid passkey response"
        }
        val root = runCatching { JsonParser.parseString(value) }.getOrNull()
        require(root != null && root.isJsonObject && root.asJsonObject.size() > 0) {
            "Credential Manager returned malformed passkey response JSON"
        }
        return value
    }

    private companion object {
        const val MAX_RESPONSE_BYTES = 128 * 1024
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
        val responseJson = ceremonyExecutor.performRegistration(pending)
        return workflow.finishRegistrationWithPlaintext(pending, responseJson, plaintextBackup)
    }

    suspend fun restoreBackup(storageKey: String): ByteArray {
        PasskeyBackupReleaseConfig.requireEnabled(isReleaseEnabled)
        val pending = workflow.beginRestore(storageKey)
        val responseJson = ceremonyExecutor.performAssertion(pending)
        return workflow.finishRestoreWithDecryption(pending, responseJson)
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
