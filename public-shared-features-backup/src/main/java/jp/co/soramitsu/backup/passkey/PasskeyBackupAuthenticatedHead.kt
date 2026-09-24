package jp.co.soramitsu.backup.passkey

/** Authenticated owner-session metadata, never authority supplied by Drive or the local journal. */
class PasskeyBackupHeadDescriptor(
    val headRevision: Long,
    val parentHeadRevision: Long,
    val parentHeadSha256: String?,
    val generationId: String,
    val bundleSha256: String,
    val keyEpoch: Long,
    val driveFileId: String,
    val storageAccountBinding: String
) {
    init {
        require(headRevision > 0 && headRevision - 1 == parentHeadRevision) { "Invalid backup head revision" }
        PasskeyBackupGenerationFormat.requireSha256(bundleSha256)
        GoogleDriveGenerationResponse.requireFileId(driveFileId)
        // The FPBKGEN1 context validator owns generation, parent, epoch and account rules.
        expectedContext(PLACEHOLDER_OWNER, PLACEHOLDER_NAMESPACE)
    }

    fun expectedContext(ownerSubject: String, backupNamespace: String) = PasskeyBackupGeneration.Context(
        ownerSubject, backupNamespace, generationId, parentHeadRevision, parentHeadSha256,
        keyEpoch, storageAccountBinding
    )

    override fun toString(): String = "PasskeyBackupHeadDescriptor(redacted)"

    private companion object {
        const val PLACEHOLDER_OWNER = "owner:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        const val PLACEHOLDER_NAMESPACE = "backup:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
    }
}

/** Construct only from a fresh authenticated owner-session response and independently expected scope. */
class PasskeyBackupAuthenticatedHead(
    val ownerSubject: String,
    val backupNamespace: String,
    val head: PasskeyBackupHeadDescriptor?,
    val previous: PasskeyBackupHeadDescriptor?,
    expectedOwnerSubject: String,
    expectedBackupNamespace: String,
    expectedStorageAccountBinding: String
) {
    val storageAccountBinding: String = expectedStorageAccountBinding
    init {
        require(ownerSubject == expectedOwnerSubject && backupNamespace == expectedBackupNamespace) {
            "Backup head owner mismatch"
        }
        PasskeyBackupGeneration.Context(
            ownerSubject, backupNamespace, PLACEHOLDER_GENERATION, 0, null, 1,
            storageAccountBinding
        )
        require(head == null || head.storageAccountBinding == storageAccountBinding) {
            "Backup head storage account mismatch"
        }
        require(previous == null || previous.storageAccountBinding == storageAccountBinding) {
            "Previous backup head storage account mismatch"
        }
        when {
            head == null -> require(previous == null) { "Previous backup without current head" }
            previous == null -> require(head.headRevision == 1L) { "Missing previous backup head" }
            else -> require(
                head.headRevision > 1 && previous.headRevision == head.parentHeadRevision &&
                    head.parentHeadSha256 == previous.bundleSha256 && previous.keyEpoch <= head.keyEpoch &&
                    previous.generationId != head.generationId && previous.driveFileId != head.driveFileId
            ) { "Backup head history mismatch" }
        }
    }

    fun currentReadParameters(): ReadParameters {
        val current = requireNotNull(head) { "No committed backup head" }
        return ReadParameters(
            current.driveFileId, current.expectedContext(ownerSubject, backupNamespace), current.bundleSha256
        )
    }

    override fun toString(): String = "PasskeyBackupAuthenticatedHead(redacted)"

    class ReadParameters(
        val fileId: String,
        val context: PasskeyBackupGeneration.Context,
        val sha256: String
    ) {
        override fun toString(): String = "PasskeyBackupAuthenticatedHead.ReadParameters(redacted)"
    }

    private companion object {
        const val PLACEHOLDER_GENERATION = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
    }
}
