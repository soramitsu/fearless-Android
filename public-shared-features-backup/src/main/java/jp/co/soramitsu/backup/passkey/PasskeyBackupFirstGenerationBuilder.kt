package jp.co.soramitsu.backup.passkey

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.security.SecureRandom
import java.util.Base64

/** App-owned source of the complete original wallet inventory; never supply a metadata-only projection. */
fun interface PasskeyBackupOriginalWalletExporter {
    /** Return canonical portable plaintext after local authorization and coverage checks. Caller erases it. */
    suspend fun exportCompleteWalletInventory(expectedWallet: PasskeyBackupExpectedWalletIdentity): ByteArray
}

/**
 * Creates the first encrypted generation only after an authenticated empty owner head and a
 * server-verified assertion have supplied local PRF output. It performs an independent decrypt
 * and original-key check before returning bytes eligible for durable journaling. No upload occurs.
 */
class PasskeyBackupFirstGenerationBuilder(
    private val exporter: PasskeyBackupOriginalWalletExporter,
    private val walletVerifier: PasskeyBackupPlaintextWalletVerifier,
    private val envelopeCryptography: PasskeyBackupEnvelopeCryptography = AesGcmPasskeyBackupEnvelopeCryptography(),
    private val keyWrapper: PasskeyBackupCredentialKeyWrapper = PasskeyBackupCredentialKeyWrapper(),
    private val random: SecureRandom = SecureRandom(),
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val isReleaseEnabled: Boolean = PasskeyBackupReleaseConfig.PASSKEY_BACKUP_ENABLED
) {
    suspend fun build(
        emptyHead: PasskeyBackupAuthenticatedHead,
        selectedAccount: GoogleDriveAccountAccess,
        credentialId: String,
        prfSalt: ByteArray,
        localPrfOutput: ByteArray,
        expectedWallet: PasskeyBackupExpectedWalletIdentity
    ): PasskeyBackupGeneration {
        PasskeyBackupReleaseConfig.requireEnabled(isReleaseEnabled)
        require(emptyHead.head == null && emptyHead.previous == null) { "First backup owner head is not empty" }
        require(
            emptyHead.storageAccountBinding == PasskeyBackupGenerationFormat.storageAccountBinding(selectedAccount.subject)
        ) { "First backup selected account differs from owner head" }
        val normalizedCredentialId = requireCredentialId(credentialId)
        PasskeyBackupContract.requirePrfSalt(prfSalt)
        require(localPrfOutput.size == BACKUP_KEY_BYTES) { "Passkey provider returned an invalid PRF result" }
        val salt = prfSalt.copyOf()
        val prf = localPrfOutput.copyOf()
        try {
            currentCoroutineContext().ensureActive()
            val plaintext = exporter.exportCompleteWalletInventory(expectedWallet)
            try {
                return sealVerifiedInventory(
                    plaintext, emptyHead, selectedAccount, normalizedCredentialId, salt, prf, expectedWallet
                )
            } finally {
                plaintext.fill(0)
            }
        } finally {
            salt.fill(0)
            prf.fill(0)
        }
    }

    private suspend fun sealVerifiedInventory(
        plaintext: ByteArray,
        emptyHead: PasskeyBackupAuthenticatedHead,
        selectedAccount: GoogleDriveAccountAccess,
        credentialId: String,
        salt: ByteArray,
        prf: ByteArray,
        expectedWallet: PasskeyBackupExpectedWalletIdentity
    ): PasskeyBackupGeneration {
        require(plaintext.isNotEmpty()) { "Original wallet inventory is empty" }
        requireWalletEvidence(walletVerifier.verify(plaintext, expectedWallet), expectedWallet)
        currentCoroutineContext().ensureActive()
        val metadata = PasskeyBackupEnvelopeMetadata(
            expectedWallet.storageKey, expectedWallet.walletId, selectedAccount.accountName,
            PasskeyBackupContract.requireCreatedAtMillis(nowMillis())
        )
        val key = ByteArray(BACKUP_KEY_BYTES).also(random::nextBytes)
        try {
            val encrypted = envelopeCryptography.encrypt(plaintext, metadata, key)
            val envelope = try {
                PasskeyBackupEncryptedPayload(
                    metadata.storageKey, metadata.walletId, metadata.accountName,
                    metadata.createdAtMillis, encrypted, metadata.schemaVersion
                )
            } finally {
                encrypted.fill(0)
            }
            val context = PasskeyBackupGeneration.Context(
                emptyHead.ownerSubject, emptyHead.backupNamespace, randomIdentifier(),
                parentHeadRevision = 0L, parentHeadSha256 = null, keyEpoch = 1L,
                storageAccountBinding = emptyHead.storageAccountBinding
            )
            val wrapperContext = PasskeyBackupKeyWrapperContext(
                emptyHead.ownerSubject, credentialId, context.keyEpoch, metadata
            )
            val wrapper = keyWrapper.wrap(key, prf, salt, wrapperContext)
            val generation = PasskeyBackupGeneration(context, envelope, listOf(wrapper))
            // A same-process round trip catches local encoding, wrapping, and decryption errors.
            requireWalletEvidence(
                PasskeyBackupGenerationCryptographicVerifier(
                    walletVerifier, envelopeCryptography, keyWrapper
                ).verify(generation, credentialId, prf, expectedWallet),
                expectedWallet
            )
            currentCoroutineContext().ensureActive()
            return generation
        } finally {
            key.fill(0)
        }
    }

    private fun requireWalletEvidence(
        evidence: PasskeyBackupLocalWalletEvidence,
        expectedWallet: PasskeyBackupExpectedWalletIdentity
    ) {
        require(
            evidence.storageKey == expectedWallet.storageKey && evidence.walletId == expectedWallet.walletId &&
                evidence.publicIdentitySha256 == expectedWallet.publicIdentitySha256 &&
                evidence.decryptionVerified && evidence.originalKeySigningVerified && evidence.originalKeyExportVerified
        ) { "First backup original wallet verification failed" }
    }

    private fun randomIdentifier(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(
        ByteArray(BACKUP_KEY_BYTES).also(random::nextBytes)
    )

    private companion object {
        const val BACKUP_KEY_BYTES = 32
    }
}
