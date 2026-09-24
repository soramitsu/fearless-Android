package jp.co.soramitsu.backup.passkey

import android.content.Context
import java.nio.file.Path

/**
 * App-private, no-backup append-only journal. Call on an IO executor; fsync and cross-process locking are synchronous.
 * Prepared data and attempts are never automatically removed, overwritten or promoted to an owner head.
 */
class PasskeyBackupGenerationJournal internal constructor(directory: Path, durability: PasskeyBackupJournalDurability) {
    private val disk = PasskeyBackupJournalDisk(directory, durability)

    /** Return only after exact ciphertext/context/ID bytes and their directory entry are durable. */
    fun persistPrepared(
        operationId: String,
        candidate: GoogleDrivePasskeyBackupGenerationStorage.Candidate,
        expectedScope: PasskeyBackupJournalEntry.Scope
    ): PasskeyBackupJournalEntry {
        expectedScope.requireMatch(candidate.context)
        val bytes = PasskeyBackupJournalRecord.encode(operationId, candidate)
        return disk.locked {
            val operations = disk.inventory()
            if (operationId in operations) {
                val existing = requireNotNull(readLocked(operationId, expectedScope))
                require(disk.read(operationId).contentEquals(bytes)) { "Conflicting backup journal operation" }
                disk.confirmPreparedDurable(operationId)
                existing
            } else {
                require(operations.size < PasskeyBackupJournalDisk.MAX_ENTRIES) { "Backup journal is full" }
                requireUniqueCandidate(operations, candidate)
                disk.create(operationId, bytes)
                PasskeyBackupJournalRecord.decode(bytes, operationId)
            }
        }
    }

    /** Missing data is not permission to allocate a new ID or recreate an unknown remote outcome. */
    fun read(operationId: String, expectedScope: PasskeyBackupJournalEntry.Scope): PasskeyBackupJournalEntry? =
        disk.locked {
        disk.inventory()
        readLocked(operationId, expectedScope)
    }

    /** A partial commit marker still proves an attempted CAS; only read-only reconciliation may use this view. */
    fun readForReconciliation(
        operationId: String,
        expectedScope: PasskeyBackupJournalEntry.Scope
    ): PasskeyBackupJournalEntry? = disk.locked {
        disk.inventory()
        if (!disk.exists(operationId)) return@locked null
        loadLocked(operationId, tolerateCommitMarkerDamage = true).also {
            expectedScope.requireMatch(it.candidate.context)
        }
    }

    /** Enumerate every prepared candidate for read-only status recovery, including a torn commit marker. */
    fun listForReconciliation(expectedScope: PasskeyBackupJournalEntry.Scope): List<PasskeyBackupJournalEntry> =
        disk.locked {
            disk.inventory().sorted().map { operation ->
                loadLocked(operation, tolerateCommitMarkerDamage = true)
            }.filter { expectedScope.matches(it.candidate.context) }
        }

    /** Validate every record before filtering by independently supplied owner/account scope. */
    fun listPending(expectedScope: PasskeyBackupJournalEntry.Scope): List<PasskeyBackupJournalEntry> = disk.locked {
        disk.inventory().sorted().map { operation ->
            loadLocked(operation)
        }.filter { expectedScope.matches(it.candidate.context) }
    }

    /**
     * Required before handing an entry to POST. Exactly one successful admission per operation, including after restart.
     * Any marker (even a partial marker after a failed write) denies another admission. Reconcile the same Drive ID.
     * This primitive does not call the network; callers must not use a mere prepared/read entry as an upload permit.
     */
    fun markCreateAttempt(
        operationId: String,
        expectedScope: PasskeyBackupJournalEntry.Scope
    ): PasskeyBackupJournalEntry = disk.locked {
        disk.inventory()
        val entry = requireNotNull(readLocked(operationId, expectedScope)) { "Missing prepared backup journal entry" }
        require(!entry.createAttemptRecorded) { "Backup journal create attempt already recorded" }
        disk.confirmPreparedDurable(operationId)
        disk.create(operationId, PasskeyBackupJournalRecord.attempt(entry), attempt = true)
        PasskeyBackupJournalEntry(entry.operationId, entry.candidate, entry.recordSha256, createAttemptRecorded = true)
    }

    /** Durable one-way CAS admission. A surviving or partial marker forbids a second commit attempt. */
    internal fun markCommitAttempt(
        operationId: String,
        expectedScope: PasskeyBackupJournalEntry.Scope
    ): PasskeyBackupJournalEntry = disk.locked {
        disk.inventory()
        val entry = requireNotNull(readLocked(operationId, expectedScope)) { "Missing prepared backup journal entry" }
        require(entry.createAttemptRecorded && !entry.commitAttemptRecorded) {
            "Backup operation is not eligible for a new commit attempt"
        }
        disk.confirmPreparedDurable(operationId)
        disk.create(operationId, PasskeyBackupJournalRecord.commitAttempt(entry), commit = true)
        PasskeyBackupJournalEntry(
            entry.operationId, entry.candidate, entry.recordSha256,
            createAttemptRecorded = true, commitAttemptRecorded = true
        )
    }

    private fun readLocked(operationId: String, scope: PasskeyBackupJournalEntry.Scope): PasskeyBackupJournalEntry? {
        if (!disk.exists(operationId)) return null
        return loadLocked(operationId).also { scope.requireMatch(it.candidate.context) }
    }

    private fun loadLocked(
        operationId: String,
        tolerateCommitMarkerDamage: Boolean = false
    ): PasskeyBackupJournalEntry {
        val attempted = disk.exists(operationId, attempt = true)
        val commitAttempted = disk.exists(operationId, commit = true)
        val entry = PasskeyBackupJournalRecord.decode(disk.read(operationId), operationId, attempted)
        if (attempted) PasskeyBackupJournalRecord.validateAttempt(disk.read(operationId, attempt = true), entry)
        if (commitAttempted) {
            require(attempted) { "Backup commit attempt without create attempt" }
            if (!tolerateCommitMarkerDamage) {
                PasskeyBackupJournalRecord.validateCommitAttempt(disk.read(operationId, commit = true), entry)
            }
        }
        return PasskeyBackupJournalEntry(
            entry.operationId, entry.candidate, entry.recordSha256,
            createAttemptRecorded = attempted, commitAttemptRecorded = commitAttempted
        )
    }

    private fun requireUniqueCandidate(
        operations: Set<String>,
        candidate: GoogleDrivePasskeyBackupGenerationStorage.Candidate
    ) {
        operations.forEach { operation ->
            val other = loadLocked(operation).candidate
            val sameFile = other.context.storageAccountBinding == candidate.context.storageAccountBinding && other.fileId == candidate.fileId
            val sameGeneration = other.context.ownerSubject == candidate.context.ownerSubject &&
                other.context.backupNamespace == candidate.context.backupNamespace && other.context.generationId == candidate.context.generationId
            require(!sameFile && !sameGeneration) { "Backup generation already has a journal operation" }
        }
    }

    companion object {
        private const val DIRECTORY_NAME = "passkey-generations-v1"

        /** Uses Android's backup-excluded internal directory; no external, cache or caller-selected production path. */
        fun forApplication(context: Context): PasskeyBackupGenerationJournal = PasskeyBackupGenerationJournal(
            context.noBackupFilesDir.toPath().resolve(DIRECTORY_NAME), AndroidBackupJournalDurability
        )
    }
}
