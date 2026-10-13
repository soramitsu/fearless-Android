package jp.co.soramitsu.backup.passkey

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Application-owned check of restored original keys. The plaintext must not be retained or logged. */
fun interface PasskeyBackupPlaintextWalletVerifier {
    /** Derive identities from the original keys, then verify signing and export before returning success. */
    suspend fun verify(
        plaintextBackup: ByteArray,
        expectedWallet: PasskeyBackupExpectedWalletIdentity
    ): PasskeyBackupLocalWalletEvidence
}

/**
 * Local cryptographic half of generation verification. A caller must first authenticate the owner,
 * obtain an exact committed head, and complete a server-verified credential-directed PRF ceremony.
 * This does not install a wallet, promote a head, or declare a backup complete.
 */
class PasskeyBackupGenerationCryptographicVerifier(
    private val walletVerifier: PasskeyBackupPlaintextWalletVerifier,
    private val envelopeCryptography: PasskeyBackupEnvelopeCryptography = AesGcmPasskeyBackupEnvelopeCryptography(),
    private val keyWrapper: PasskeyBackupCredentialKeyWrapper = PasskeyBackupCredentialKeyWrapper()
) {
    suspend fun verify(
        generation: PasskeyBackupGeneration,
        credentialId: String,
        localPrfOutput: ByteArray,
        expectedWallet: PasskeyBackupExpectedWalletIdentity
    ): PasskeyBackupLocalWalletEvidence {
        currentCoroutineContext().ensureActive()
        val envelope = generation.envelope
        require(envelope.storageKey == expectedWallet.storageKey && envelope.walletId == expectedWallet.walletId) {
            "Backup generation wallet identity mismatch"
        }
        val metadata = envelope.envelopeMetadata()
        val wrapperContext = PasskeyBackupKeyWrapperContext(
            generation.context.ownerSubject, credentialId, generation.context.keyEpoch, metadata
        )
        val record = requireNotNull(generation.wrappers.singleOrNull { it.context == wrapperContext }) {
            "Backup generation has no wrapper for the verified credential"
        }
        val prf = localPrfOutput.copyOf()
        return try {
            verifyWithPrf(envelope, metadata, record, wrapperContext, prf, expectedWallet)
        } finally {
            prf.fill(0)
        }
    }

    private suspend fun verifyWithPrf(
        envelope: PasskeyBackupEncryptedPayload,
        metadata: PasskeyBackupEnvelopeMetadata,
        record: PasskeyBackupCredentialKeyWrapperRecord,
        context: PasskeyBackupKeyWrapperContext,
        prf: ByteArray,
        expectedWallet: PasskeyBackupExpectedWalletIdentity
    ): PasskeyBackupLocalWalletEvidence {
        val backupKey = keyWrapper.unwrap(record, prf, context)
        return try {
            verifyWithKey(envelope, metadata, backupKey, expectedWallet)
        } finally {
            backupKey.fill(0)
        }
    }

    private suspend fun verifyWithKey(
        envelope: PasskeyBackupEncryptedPayload,
        metadata: PasskeyBackupEnvelopeMetadata,
        backupKey: ByteArray,
        expectedWallet: PasskeyBackupExpectedWalletIdentity
    ): PasskeyBackupLocalWalletEvidence {
        val plaintext = envelopeCryptography.decrypt(envelope.encryptedPayload, metadata, backupKey)
        return try {
            currentCoroutineContext().ensureActive()
            val evidence = walletVerifier.verify(plaintext, expectedWallet)
            currentCoroutineContext().ensureActive()
            require(
                evidence.storageKey == expectedWallet.storageKey &&
                    evidence.walletId == expectedWallet.walletId &&
                    evidence.publicIdentitySha256 == expectedWallet.publicIdentitySha256 &&
                    evidence.decryptionVerified && evidence.originalKeySigningVerified &&
                    evidence.originalKeyExportVerified
            ) { "Local wallet verification failed" }
            evidence
        } finally {
            plaintext.fill(0)
        }
    }
}
