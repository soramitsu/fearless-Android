package jp.co.soramitsu.backup.passkey

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Independently authenticated wallet identity, never inferred from a journal or Drive response. */
class PasskeyBackupExpectedWalletIdentity(
    val storageKey: String,
    val walletId: String,
    val publicIdentitySha256: String
) {
    init {
        PasskeyBackupContract.requireStorageKey(storageKey)
        PasskeyBackupContract.requireWalletId(walletId)
        PasskeyBackupGenerationFormat.requireSha256(publicIdentitySha256)
    }

    override fun toString(): String = "PasskeyBackupExpectedWalletIdentity(redacted)"
}

/** Local-only result. A production verifier must derive it from PRF unwrap, decryption and original keys. */
class PasskeyBackupLocalWalletEvidence(
    val storageKey: String,
    val walletId: String,
    val publicIdentitySha256: String,
    val decryptionVerified: Boolean,
    val originalKeySigningVerified: Boolean,
    val originalKeyExportVerified: Boolean
) {
    override fun toString(): String = "PasskeyBackupLocalWalletEvidence(redacted)"
}

/** Application-owned proof boundary with no production default or metadata-only implementation. */
fun interface PasskeyBackupGenerationLocalVerifier {
    /** Must reject missing PRF, failed unwrap/decryption, wrong identity, signing or export. */
    suspend fun verify(
        generation: PasskeyBackupGeneration,
        expectedWallet: PasskeyBackupExpectedWalletIdentity
    ): PasskeyBackupLocalWalletEvidence
}

sealed interface PasskeyBackupGenerationReconciliation {
    /** A 404 does not permit another POST, a new Drive ID or deletion of the journaled candidate. */
    data object NotFound : PasskeyBackupGenerationReconciliation

    /** Local verifier accepted exact downloaded bytes; owner-head CAS and backup completion remain separate. */
    data class LocallyVerified(
        val generationId: String,
        val bundleSha256: String,
        val publicIdentitySha256: String
    ) : PasskeyBackupGenerationReconciliation {
        override fun toString(): String = "PasskeyBackupGenerationReconciliation.LocallyVerified(redacted)"
    }
}

/** Read-only recovery from an uncertain Drive create; never retries upload or promotes a head. */
class PasskeyBackupGenerationReconciler(
    private val storage: GoogleDrivePasskeyBackupGenerationStorage,
    private val verifier: PasskeyBackupGenerationLocalVerifier
) {
    suspend fun reconcile(
        operationId: String,
        journal: PasskeyBackupGenerationJournal,
        expectedScope: PasskeyBackupJournalEntry.Scope,
        expectedWallet: PasskeyBackupExpectedWalletIdentity
    ): PasskeyBackupGenerationReconciliation {
        currentCoroutineContext().ensureActive()
        val admitted = withContext(Dispatchers.IO) {
            requireNotNull(journal.readForReconciliation(operationId, expectedScope)) { "Missing prepared backup journal entry" }
        }
        require(admitted.createAttemptRecorded) { "Backup generation has no create attempt" }
        val candidate = admitted.candidate
        requireWalletIdentity(
            PasskeyBackupGenerationFormat.decode(candidate.bytes, candidate.context, candidate.sha256),
            expectedWallet
        )
        val downloaded = storage.readCandidate(candidate.fileId, candidate.context, candidate.sha256)
            ?: return PasskeyBackupGenerationReconciliation.NotFound
        currentCoroutineContext().ensureActive()
        require(PasskeyBackupGenerationFormat.encode(downloaded).contentEquals(candidate.bytes)) {
            "Downloaded backup generation differs from journaled bytes"
        }
        requireWalletIdentity(downloaded, expectedWallet)
        val evidence = verifier.verify(downloaded, expectedWallet)
        currentCoroutineContext().ensureActive()
        require(
            evidence.storageKey == expectedWallet.storageKey && evidence.walletId == expectedWallet.walletId &&
                evidence.publicIdentitySha256 == expectedWallet.publicIdentitySha256 && evidence.decryptionVerified &&
                evidence.originalKeySigningVerified && evidence.originalKeyExportVerified
        ) {
            "Local wallet verification failed"
        }
        // Do not report success if local scope or the exact durable candidate changed during verification.
        val confirmed = withContext(Dispatchers.IO) {
            requireNotNull(journal.readForReconciliation(operationId, expectedScope)) { "Missing prepared backup journal entry" }
        }
        require(
            confirmed.createAttemptRecorded && confirmed.recordSha256 == admitted.recordSha256 &&
                confirmed.candidate.fileId == candidate.fileId && confirmed.candidate.bytes.contentEquals(candidate.bytes)
        ) {
            "Backup journal changed during verification"
        }
        storage.requireSelectedAccount()
        currentCoroutineContext().ensureActive()
        return PasskeyBackupGenerationReconciliation.LocallyVerified(
            candidate.context.generationId, candidate.sha256, evidence.publicIdentitySha256
        )
    }

    private fun requireWalletIdentity(
        generation: PasskeyBackupGeneration,
        expectedWallet: PasskeyBackupExpectedWalletIdentity
    ) {
        require(
            generation.envelope.storageKey == expectedWallet.storageKey &&
                generation.envelope.walletId == expectedWallet.walletId
        ) {
            "Backup generation wallet identity mismatch"
        }
    }
}
