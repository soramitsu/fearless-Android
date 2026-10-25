package jp.co.soramitsu.backup.passkey

import java.util.Collections

/** Immutable encrypted candidate, not a server head or proof of successful local recovery. */
class PasskeyBackupGeneration(
    val context: Context,
    val envelope: PasskeyBackupEncryptedPayload,
    wrappers: List<PasskeyBackupCredentialKeyWrapperRecord>
) {
    val wrappers: List<PasskeyBackupCredentialKeyWrapperRecord> = Collections.unmodifiableList(
        wrappers.sortedBy { it.context.credentialId }
    )

    init {
        require(this.wrappers.size in 1..MAX_CREDENTIALS) { "Invalid generation wrapper count" }
        require(this.wrappers.map { it.context.credentialId }.distinct().size == this.wrappers.size) {
            "Duplicate generation credential"
        }
        val metadata = envelope.envelopeMetadata()
        val matchingContexts = this.wrappers.all {
            it.context.ownerSubject == context.ownerSubject && it.context.keyEpoch == context.keyEpoch &&
                it.context.envelopeMetadata == metadata
        }
        require(matchingContexts) { "Generation wrapper context mismatch" }
    }

    override fun toString(): String = "PasskeyBackupGeneration(redacted)"

    /** Supplied by an authenticated owner/head operation; construction alone does not authenticate it. */
    data class Context(
        val ownerSubject: String,
        val backupNamespace: String,
        val generationId: String,
        val parentHeadRevision: Long,
        val parentHeadSha256: String?,
        val keyEpoch: Long,
        val storageAccountBinding: String
    ) {
        init {
            PasskeyBackupGenerationFormat.requireIdentifier(ownerSubject, "owner:")
            PasskeyBackupGenerationFormat.requireIdentifier(backupNamespace, "backup:")
            PasskeyBackupGenerationFormat.requireIdentifier(generationId)
            require(parentHeadRevision >= 0 && keyEpoch >= 1) { "Invalid generation revision or epoch" }
            val genesis = parentHeadRevision == 0L
            require(genesis == (parentHeadSha256 == null)) { "Invalid generation parent" }
            parentHeadSha256?.let(PasskeyBackupGenerationFormat::requireSha256)
            PasskeyBackupGenerationFormat.requireSha256(storageAccountBinding)
        }

        override fun toString(): String = "PasskeyBackupGeneration.Context(redacted)"
    }

    companion object {
        const val MAX_CREDENTIALS = 32
    }
}
