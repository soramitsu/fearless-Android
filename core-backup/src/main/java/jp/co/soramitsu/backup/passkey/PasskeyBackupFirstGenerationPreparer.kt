package jp.co.soramitsu.backup.passkey

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import java.util.Base64

/** Durable encrypted candidate only. Upload, readback, owner CAS and backup completion remain separate. */
class PasskeyBackupPreparedFirstGeneration internal constructor(
    val operationId: String,
    val generationId: String,
    val bundleSha256: String,
    val driveFileId: String
) {
    override fun toString(): String = "PasskeyBackupPreparedFirstGeneration(redacted)"
}

/**
 * Joins the verified first-owner assertion to local encryption and an append-only journal. A
 * second first-generation request is rejected while an earlier candidate has an unknown outcome.
 * This candidate has no production app wiring or original-wallet exporter implementation yet.
 */
class PasskeyBackupFirstGenerationPreparer(
    private val ownerAuthentication: PasskeyBackupOwnerAuthenticationClient,
    private val ownerHead: PasskeyBackupOwnerHeadHttpClient,
    private val tokenProvider: GoogleDriveAccessTokenProvider,
    private val storage: GoogleDrivePasskeyBackupGenerationStorage,
    private val journal: PasskeyBackupGenerationJournal,
    private val builder: PasskeyBackupFirstGenerationBuilder,
    private val keyWrapper: PasskeyBackupCredentialKeyWrapper = PasskeyBackupCredentialKeyWrapper(),
    private val random: SecureRandom = SecureRandom(),
    private val isReleaseEnabled: Boolean = PasskeyBackupReleaseConfig.PASSKEY_BACKUP_ENABLED
) {
    suspend fun prepare(
        firstOwner: PasskeyBackupFirstOwnerBootstrapResult,
        selectedAccountName: String,
        expectedWallet: PasskeyBackupExpectedWalletIdentity
    ): PasskeyBackupPreparedFirstGeneration {
        PasskeyBackupReleaseConfig.requireEnabled(isReleaseEnabled)
        require(firstOwner.session.platform == ANDROID_PLATFORM && firstOwner.session.generation == 0L) {
            "First backup owner session is invalid"
        }
        require(
            firstOwner.originalWalletIdentity.storageKey == expectedWallet.storageKey &&
                firstOwner.originalWalletIdentity.walletId == expectedWallet.walletId &&
                firstOwner.originalWalletIdentity.publicIdentitySha256 == expectedWallet.publicIdentitySha256
        ) { "First backup original wallet changed after owner creation" }
        requireEmptyHead(firstOwner.emptyHead, firstOwner.session)
        val credentialId = requireCredentialId(firstOwner.credentialId)
        val selected = tokenProvider.accessToken()
        val accountName = GoogleDrivePasskeyBackup.requireMatchingAccountName(
            selectedAccountName, selected.accountName, "first generation"
        )
        require(
            firstOwner.emptyHead.storageAccountBinding == PasskeyBackupGenerationFormat.storageAccountBinding(selected.subject)
        ) { "First backup selected account changed" }
        val scope = PasskeyBackupJournalEntry.Scope(
            firstOwner.session.subject, firstOwner.session.namespace, firstOwner.emptyHead.storageAccountBinding
        )
        withContext(Dispatchers.IO) {
            require(
                journal.listForReconciliation(scope).none {
                    it.candidate.context.parentHeadRevision == 0L
                }
            ) { "First backup generation already has a journal operation" }
        }
        storage.requireSelectedAccount()
        val fileId = storage.allocateFileId()
        val operationId = randomIdentifier()
        val prfSalt = keyWrapper.newPrfSalt()
        return try {
            ownerAuthentication.withVerifiedFirstOwnerPrf(
                firstOwner.session, credentialId, prfSalt, ownerHead
            ) { session, head, localPrf ->
                requireSameFirstOwner(firstOwner, session, head)
                val freshAccount = tokenProvider.accessToken()
                require(freshAccount.subject == selected.subject) { "First backup Google account changed" }
                GoogleDrivePasskeyBackup.requireMatchingAccountName(accountName, freshAccount.accountName, "first generation")
                val generation = try {
                    builder.build(head, freshAccount, credentialId, prfSalt, localPrf, expectedWallet)
                } finally {
                    localPrf.fill(0)
                }
                require(operationId != generation.context.generationId) { "Backup operation ID collided with generation ID" }
                currentCoroutineContext().ensureActive()
                requireSameFirstOwner(firstOwner, session, ownerHead.readHead(session))
                val finalAccount = tokenProvider.accessToken()
                require(finalAccount.subject == selected.subject) { "First backup Google account changed" }
                GoogleDrivePasskeyBackup.requireMatchingAccountName(accountName, finalAccount.accountName, "first generation")
                storage.requireSelectedAccount()
                currentCoroutineContext().ensureActive()
                val candidate = storage.prepareCandidate(fileId, generation)
                val entry = withContext(Dispatchers.IO) {
                    journal.persistPreparedFirstGeneration(operationId, candidate, scope)
                }
                require(entry.candidate.sha256 == candidate.sha256 && !entry.createAttemptRecorded) {
                    "First backup journal candidate changed"
                }
                PasskeyBackupPreparedFirstGeneration(
                    operationId, generation.context.generationId, candidate.sha256, fileId
                )
            }
        } finally {
            prfSalt.fill(0)
        }
    }

    private fun requireSameFirstOwner(
        firstOwner: PasskeyBackupFirstOwnerBootstrapResult,
        session: PasskeyBackupOwnerSession,
        head: PasskeyBackupAuthenticatedHead
    ) {
        require(
            session.subject == firstOwner.session.subject && session.namespace == firstOwner.session.namespace &&
                session.generation == firstOwner.session.generation && session.platform == ANDROID_PLATFORM &&
                head.storageAccountBinding == firstOwner.emptyHead.storageAccountBinding
        ) { "Verified first backup owner changed" }
        requireEmptyHead(head, session)
    }

    private fun requireEmptyHead(head: PasskeyBackupAuthenticatedHead, session: PasskeyBackupOwnerSession) {
        require(
            head.ownerSubject == session.subject && head.backupNamespace == session.namespace &&
                head.head == null && head.previous == null
        ) { "First backup owner head is not empty" }
    }

    private fun randomIdentifier(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(
        ByteArray(IDENTIFIER_BYTES).also(random::nextBytes)
    )

    private companion object {
        const val ANDROID_PLATFORM = "android"
        const val IDENTIFIER_BYTES = 32
    }
}
