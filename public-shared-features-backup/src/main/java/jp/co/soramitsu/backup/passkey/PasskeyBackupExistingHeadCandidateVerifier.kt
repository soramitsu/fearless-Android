package jp.co.soramitsu.backup.passkey

import com.google.gson.JsonParser
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Local candidate proof for an existing owner head. The authority must consume and verify the
 * credential-directed public assertion before the PRF result is released to the local decryptor.
 * A new owner's empty-head enrollment needs a separate verified assertion authority API.
 */
class PasskeyBackupExistingHeadCandidateVerifier(
    private val credentialId: String,
    private val expectedHead: PasskeyBackupAuthenticatedHead,
    private val authority: PasskeyBackupRecoveryAuthority,
    private val ceremonyExecutor: PasskeyBackupCeremonyExecutor,
    private val cryptographicVerifier: PasskeyBackupGenerationCryptographicVerifier,
    private val isReleaseEnabled: Boolean = PasskeyBackupReleaseConfig.PASSKEY_BACKUP_ENABLED
) : PasskeyBackupGenerationLocalVerifier {
    init {
        requireCredentialId(credentialId)
        expectedHead.currentReadParameters()
    }

    override suspend fun verify(
        generation: PasskeyBackupGeneration,
        expectedWallet: PasskeyBackupExpectedWalletIdentity
    ): PasskeyBackupLocalWalletEvidence {
        PasskeyBackupReleaseConfig.requireEnabled(isReleaseEnabled)
        require(
            generation.context.ownerSubject == expectedHead.ownerSubject &&
                generation.context.backupNamespace == expectedHead.backupNamespace &&
                generation.context.storageAccountBinding == expectedHead.storageAccountBinding
        ) { "Candidate owner scope mismatch" }
        val before = authority.readAuthorizedHead(expectedWallet.storageKey, credentialId)
        requireSnapshot(before, expectedWallet)
        val wrapper = requireNotNull(generation.wrappers.singleOrNull { it.context.credentialId == credentialId }) {
            "Candidate has no wrapper for the verified credential"
        }
        val salt = wrapper.prfSalt
        val pending = try {
            val challenge = authority.assertionChallenge(expectedWallet.storageKey, credentialId)
            require(challenge.storageKey == expectedWallet.storageKey && challenge.credentialId == credentialId) {
                "Candidate assertion challenge scope mismatch"
            }
            PendingPasskeyBackupAssertion(
                challenge.assertionId,
                expectedWallet.storageKey,
                PasskeyBackupContract.assertionOptionsJsonWithPrf(challenge.challenge, credentialId, salt)
            )
        } finally {
            salt.fill(0)
        }
        return ceremonyExecutor.performAssertion(pending).use { result ->
            val responseCredential = JsonParser.parseString(result.serverCredentialJson)
                .asJsonObject.get("id")?.asString
            require(responseCredential == credentialId && result.hasLocalPrfOutput) {
                "Candidate assertion credential or PRF result mismatch"
            }
            val verified = authority.verifyAssertionAndReadHead(pending.assertionId, result.serverCredentialJson)
            require(verified.assertionId == pending.assertionId && verified.credentialId == credentialId) {
                "Candidate assertion was not verified"
            }
            requireSnapshot(verified.snapshot, expectedWallet)
            result.withRequiredLocalPrfOutput { prf ->
                val evidence = cryptographicVerifier.verify(generation, credentialId, prf, expectedWallet)
                currentCoroutineContext().ensureActive()
                requireSnapshot(authority.readAuthorizedHead(expectedWallet.storageKey, credentialId), expectedWallet)
                evidence
            }
        }
    }

    private fun requireSnapshot(
        snapshot: PasskeyBackupRecoveryHeadSnapshot,
        expectedWallet: PasskeyBackupExpectedWalletIdentity
    ) {
        require(
            snapshot.credentialId == credentialId &&
                snapshot.expectedWallet.storageKey == expectedWallet.storageKey &&
                snapshot.expectedWallet.walletId == expectedWallet.walletId &&
                snapshot.expectedWallet.publicIdentitySha256 == expectedWallet.publicIdentitySha256
        ) { "Candidate wallet identity changed" }
        val actual = snapshot.authenticatedHead
        val before = expectedHead.currentReadParameters()
        val after = actual.currentReadParameters()
        require(
            actual.ownerSubject == expectedHead.ownerSubject &&
                actual.backupNamespace == expectedHead.backupNamespace &&
                actual.storageAccountBinding == expectedHead.storageAccountBinding &&
                before.fileId == after.fileId && before.context == after.context && before.sha256 == after.sha256
        ) { "Candidate owner head changed" }
    }
}
