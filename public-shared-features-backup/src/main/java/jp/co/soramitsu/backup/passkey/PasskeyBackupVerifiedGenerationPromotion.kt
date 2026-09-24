package jp.co.soramitsu.backup.passkey

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Neither pending result grants another upload or permits deleting the previous generation. */
sealed interface PasskeyBackupGenerationPromotionResult {
    data object AwaitingDriveReadback : PasskeyBackupGenerationPromotionResult
    data object AwaitingOwnerOperation : PasskeyBackupGenerationPromotionResult

    /** Exact committed owner head and local original-key proof; wallet installation remains separate. */
    class CurrentVerifiedHead internal constructor(
        val generationId: String,
        val bundleSha256: String,
        val publicIdentitySha256: String
    ) : PasskeyBackupGenerationPromotionResult {
        override fun toString(): String = "PasskeyBackupGenerationPromotionResult.CurrentVerifiedHead(redacted)"
    }
}

/** Bind each fresh PRF ceremony and local proof to this invocation's authenticated owner head. */
fun interface PasskeyBackupGenerationLocalVerifierFactory {
    fun forHead(head: PasskeyBackupAuthenticatedHead): PasskeyBackupGenerationLocalVerifier
}

/**
 * Disabled promotion candidate. Its journaled one-way commit marker is written before network dispatch.
 * An unknown CAS outcome is reconciled only by the same operation ID and fresh authenticated head.
 * It never deletes the former Drive generation or reports a backup complete to wallet UI.
 */
class PasskeyBackupVerifiedGenerationPromotion(
    private val ownerHead: PasskeyBackupOwnerHeadHttpClient,
    private val ownerGeneration: PasskeyBackupOwnerGenerationHttpClient,
    private val storage: GoogleDrivePasskeyBackupGenerationStorage,
    private val journal: PasskeyBackupGenerationJournal,
    private val localVerifierFactory: PasskeyBackupGenerationLocalVerifierFactory,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val isReleaseEnabled: Boolean = PasskeyBackupReleaseConfig.PASSKEY_BACKUP_ENABLED
) {
    @Suppress("ReturnCount") // Pending and committed states leave through distinct fail-closed exits.
    suspend fun promote(
        session: PasskeyBackupOwnerSession,
        operationId: String,
        expectedWallet: PasskeyBackupExpectedWalletIdentity
    ): PasskeyBackupGenerationPromotionResult {
        PasskeyBackupReleaseConfig.requireEnabled(isReleaseEnabled)
        currentCoroutineContext().ensureActive()
        val initialHead = ownerHead.readHead(session)
        val scope = PasskeyBackupJournalEntry.Scope(
            initialHead.ownerSubject, initialHead.backupNamespace, initialHead.storageAccountBinding
        )
        var entry = readEntry(operationId, scope)
        require(entry.candidate.context.keyEpoch >= initialHead.head?.keyEpoch ?: 1L) {
            "Backup generation key epoch regressed"
        }

        if (!entry.createAttemptRecorded) {
            requireExtendsHead(entry, initialHead)
            storage.createCandidate(operationId, journal, scope)
            entry = readEntry(operationId, scope)
        }
        val operation = PasskeyBackupGenerationOperationReference.fromJournal(entry, initialHead)
        val priorStatus = ownerGeneration.operationStatus(session, operation)
        if (entry.commitAttemptRecorded && priorStatus == PasskeyBackupGenerationOperationStatus.Absent) {
            return awaitOwnerOperation(session, operation, scope)
        }

        val local = PasskeyBackupGenerationReconciler(storage, localVerifierFactory.forHead(initialHead))
            .reconcile(operationId, journal, scope, expectedWallet)
        if (local == PasskeyBackupGenerationReconciliation.NotFound) {
            return PasskeyBackupGenerationPromotionResult.AwaitingDriveReadback
        }
        val verified = local as PasskeyBackupGenerationReconciliation.LocallyVerified
        var latestHead = ownerHead.readHead(session)
        requireSameScope(initialHead, latestHead)

        if (priorStatus is PasskeyBackupGenerationOperationStatus.Committed) {
            return confirmCurrent(operationId, scope, operation, verified, latestHead, session)
        }
        if (entry.commitAttemptRecorded) return awaitOwnerOperation(session, operation, scope)
        requireExtendsHead(entry, latestHead)
        val metadata = PasskeyBackupGenerationMetadata(
            operationId, entry.candidate.context, entry.candidate.sha256, entry.candidate.fileId, latestHead
        )
        // A status query is linearizable on the owner authority; a committed race must not receive a second CAS.
        when (ownerGeneration.operationStatus(session, operation)) {
            is PasskeyBackupGenerationOperationStatus.Committed ->
                return confirmCurrent(operationId, scope, operation, verified, ownerHead.readHead(session), session)
            PasskeyBackupGenerationOperationStatus.Absent -> Unit
        }
        val grant = ownerGeneration.grant(session, metadata)
        latestHead = ownerHead.readHead(session)
        requireSameHead(initialHead, latestHead)
        storage.requireSelectedAccount()
        currentCoroutineContext().ensureActive()
        requireFreshSession(session)
        withContext(Dispatchers.IO) { journal.markCommitAttempt(operationId, scope) }
        try {
            ownerGeneration.commit(session, metadata, grant)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // The marker forbids another commit. Status, not an HTTP exception, determines the outcome.
        }
        return when (ownerGeneration.operationStatus(session, operation)) {
            is PasskeyBackupGenerationOperationStatus.Committed -> {
                val current = ownerHead.readHead(session)
                confirmCurrent(operationId, scope, operation, verified, current, session)
            }
            PasskeyBackupGenerationOperationStatus.Absent -> awaitOwnerOperation(session, operation, scope)
        }
    }

    private suspend fun awaitOwnerOperation(
        session: PasskeyBackupOwnerSession,
        operation: PasskeyBackupGenerationOperationReference,
        scope: PasskeyBackupJournalEntry.Scope
    ): PasskeyBackupGenerationPromotionResult {
        val latest = ownerHead.readHead(session)
        requireSameScope(scope, latest)
        require(latest.head?.let(operation::matchesDescriptor) != true) {
            "Owner operation status conflicts with authenticated head"
        }
        storage.requireSelectedAccount()
        currentCoroutineContext().ensureActive()
        requireFreshSession(session)
        return PasskeyBackupGenerationPromotionResult.AwaitingOwnerOperation
    }

    private suspend fun confirmCurrent(
        operationId: String,
        scope: PasskeyBackupJournalEntry.Scope,
        operation: PasskeyBackupGenerationOperationReference,
        verified: PasskeyBackupGenerationReconciliation.LocallyVerified,
        observedHead: PasskeyBackupAuthenticatedHead,
        session: PasskeyBackupOwnerSession
    ): PasskeyBackupGenerationPromotionResult {
        requireSameScope(scope, observedHead)
        val head = requireNotNull(observedHead.head) { "Committed backup head is unavailable" }
        operation.requireDescriptor(head)
        val fresh = ownerHead.readHead(session)
        requireSameHead(observedHead, fresh)
        val currentEntry = readEntry(operationId, scope)
        operation.requireDescriptor(requireNotNull(fresh.head))
        require(
            currentEntry.createAttemptRecorded &&
                currentEntry.candidate.context.generationId == verified.generationId &&
                currentEntry.candidate.sha256 == verified.bundleSha256
        ) { "Verified backup journal changed" }
        val committed = requireNotNull(storage.readCurrentHead(fresh)) {
            "Committed Drive generation is unavailable after owner CAS"
        }
        require(PasskeyBackupGenerationFormat.encode(committed).contentEquals(currentEntry.candidate.bytes)) {
            "Committed Drive generation differs from locally verified bytes"
        }
        storage.requireSelectedAccount()
        val finalHead = ownerHead.readHead(session)
        requireSameHead(fresh, finalHead)
        currentCoroutineContext().ensureActive()
        requireFreshSession(session)
        return PasskeyBackupGenerationPromotionResult.CurrentVerifiedHead(
            verified.generationId, verified.bundleSha256, verified.publicIdentitySha256
        )
    }

    private suspend fun readEntry(
        operationId: String,
        scope: PasskeyBackupJournalEntry.Scope
    ): PasskeyBackupJournalEntry = withContext(Dispatchers.IO) {
        requireNotNull(journal.readForReconciliation(operationId, scope)) { "Missing prepared backup journal entry" }
    }

    private fun requireExtendsHead(entry: PasskeyBackupJournalEntry, head: PasskeyBackupAuthenticatedHead) {
        PasskeyBackupGenerationMetadata(
            entry.operationId, entry.candidate.context, entry.candidate.sha256, entry.candidate.fileId, head
        )
        require(entry.candidate.context.keyEpoch >= head.head?.keyEpoch ?: 1L) {
            "Backup generation key epoch regressed"
        }
    }

    private fun requireSameScope(expected: PasskeyBackupAuthenticatedHead, actual: PasskeyBackupAuthenticatedHead) {
        requireSameScope(
            PasskeyBackupJournalEntry.Scope(
                expected.ownerSubject, expected.backupNamespace, expected.storageAccountBinding
            ),
            actual
        )
    }

    private fun requireSameScope(expected: PasskeyBackupJournalEntry.Scope, actual: PasskeyBackupAuthenticatedHead) {
        require(
            expected.ownerSubject == actual.ownerSubject &&
                expected.backupNamespace == actual.backupNamespace &&
                expected.storageAccountBinding == actual.storageAccountBinding
        ) { "Authenticated backup owner or selected account changed" }
    }

    private fun requireSameHead(expected: PasskeyBackupAuthenticatedHead, actual: PasskeyBackupAuthenticatedHead) {
        requireSameScope(expected, actual)
        require(sameDescriptor(expected.head, actual.head) && sameDescriptor(expected.previous, actual.previous)) {
            "Authenticated backup head changed during promotion"
        }
    }

    private fun sameDescriptor(expected: PasskeyBackupHeadDescriptor?, actual: PasskeyBackupHeadDescriptor?): Boolean =
        if (expected == null || actual == null) {
            expected == null && actual == null
        } else {
            expected.headRevision == actual.headRevision &&
                    expected.parentHeadRevision == actual.parentHeadRevision &&
                    expected.parentHeadSha256 == actual.parentHeadSha256 &&
                    expected.generationId == actual.generationId &&
                    expected.bundleSha256 == actual.bundleSha256 &&
                    expected.keyEpoch == actual.keyEpoch &&
                    expected.driveFileId == actual.driveFileId &&
                    expected.storageAccountBinding == actual.storageAccountBinding
        }

    private fun requireFreshSession(session: PasskeyBackupOwnerSession) {
        val millis = nowMillis()
        require(millis >= 0) { "Invalid owner session clock" }
        val seconds = millis / MILLIS_PER_SECOND
        require(
            session.platform == "android" && session.expiresAt > seconds &&
                session.expiresAt <= seconds + MAX_SESSION_AHEAD_SECONDS
        ) { "Owner session expired during backup promotion" }
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
        const val MAX_SESSION_AHEAD_SECONDS = 660L
    }
}
