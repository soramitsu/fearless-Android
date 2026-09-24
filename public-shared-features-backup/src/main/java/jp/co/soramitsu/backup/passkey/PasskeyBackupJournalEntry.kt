package jp.co.soramitsu.backup.passkey

/** A local recovery hint, never proof of current owner authorization or a committed remote head. */
class PasskeyBackupJournalEntry internal constructor(
    val operationId: String,
    val candidate: GoogleDrivePasskeyBackupGenerationStorage.Candidate,
    val recordSha256: String,
    val createAttemptRecorded: Boolean,
    val commitAttemptRecorded: Boolean = false
) {
    override fun toString(): String = "PasskeyBackupJournalEntry(redacted)"

    /** Values must be supplied independently by the authenticated owner and selected verified Google account. */
    data class Scope(val ownerSubject: String, val backupNamespace: String, val storageAccountBinding: String) {
        init {
            PasskeyBackupGenerationFormat.requireIdentifier(ownerSubject, "owner:")
            PasskeyBackupGenerationFormat.requireIdentifier(backupNamespace, "backup:")
            PasskeyBackupGenerationFormat.requireSha256(storageAccountBinding)
        }

        internal fun requireMatch(context: PasskeyBackupGeneration.Context) {
            require(matches(context)) { "Backup journal owner scope mismatch" }
        }

        internal fun matches(context: PasskeyBackupGeneration.Context): Boolean =
            ownerSubject == context.ownerSubject && backupNamespace == context.backupNamespace &&
                storageAccountBinding == context.storageAccountBinding

        override fun toString(): String = "PasskeyBackupJournalEntry.Scope(redacted)"
    }
}
