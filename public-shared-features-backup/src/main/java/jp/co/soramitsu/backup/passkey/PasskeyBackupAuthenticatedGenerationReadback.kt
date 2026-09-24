package jp.co.soramitsu.backup.passkey

import com.google.gson.JsonParser
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Metadata from an authenticated Fearless owner session. Google account access alone must never
 * produce this value. The authority must fetch the current head rather than replay a local journal.
 */
class PasskeyBackupRecoveryHeadSnapshot(
    val credentialId: String,
    val expectedWallet: PasskeyBackupExpectedWalletIdentity,
    val authenticatedHead: PasskeyBackupAuthenticatedHead
) {
    init {
        requireCredentialId(credentialId)
        authenticatedHead.currentReadParameters()
    }

    override fun toString(): String = "PasskeyBackupRecoveryHeadSnapshot(redacted)"
}

/** The service has consumed exactly this assertion and verified its signature and owner binding. */
class PasskeyBackupVerifiedAssertionHead(
    val assertionId: String,
    val credentialId: String,
    val snapshot: PasskeyBackupRecoveryHeadSnapshot
) {
    init {
        require(ASSERTION_ID_PATTERN.matches(assertionId)) { "Invalid verified assertion ID" }
        require(credentialId == snapshot.credentialId) { "Verified assertion credential mismatch" }
    }

    override fun toString(): String = "PasskeyBackupVerifiedAssertionHead(redacted)"

    private companion object {
        val ASSERTION_ID_PATTERN = Regex("^[A-Za-z0-9._:-]{8,128}$")
    }
}

/**
 * No production implementation is supplied until owner-session bootstrap, one-use assertion
 * verification and current-head reads are deployed and reviewed. Each method must enforce the
 * caller's live owner session; [verifyAssertionAndReadHead] must consume a credential-directed
 * challenge and return the current head only after verifying its exact signed assertion.
 */
interface PasskeyBackupRecoveryAuthority {
    suspend fun readAuthorizedHead(storageKey: String, credentialId: String): PasskeyBackupRecoveryHeadSnapshot

    suspend fun assertionChallenge(storageKey: String, credentialId: String): PasskeyBackupAssertionChallenge

    suspend fun verifyAssertionAndReadHead(
        assertionId: String,
        serverCredentialJson: String
    ): PasskeyBackupVerifiedAssertionHead
}

/** Local readback evidence only. It cannot install keys, promote a head or mark recovery complete. */
class PasskeyBackupAuthenticatedReadbackEvidence internal constructor(
    val generationId: String,
    val bundleSha256: String,
    val publicIdentitySha256: String
) {
    override fun toString(): String = "PasskeyBackupAuthenticatedReadbackEvidence(redacted)"
}

/**
 * Read the exact owner head, obtain its credential's PRF salt, then require a new server-verified
 * assertion before local unwrap, decryption and original-key signing/export checks. This remains
 * unwired and release-disabled until the real authority and wallet verifier are available.
 */
class PasskeyBackupAuthenticatedGenerationReadback(
    private val authority: PasskeyBackupRecoveryAuthority,
    private val storage: GoogleDrivePasskeyBackupGenerationStorage,
    private val ceremonyExecutor: PasskeyBackupCeremonyExecutor,
    private val verifier: PasskeyBackupGenerationCryptographicVerifier,
    private val isReleaseEnabled: Boolean = PasskeyBackupReleaseConfig.PASSKEY_BACKUP_ENABLED
) {
    suspend fun verify(storageKey: String, credentialId: String): PasskeyBackupAuthenticatedReadbackEvidence {
        PasskeyBackupReleaseConfig.requireEnabled(isReleaseEnabled)
        val expectedStorageKey = PasskeyBackupContract.requireStorageKey(storageKey)
        val expectedCredentialId = requireCredentialId(credentialId)
        currentCoroutineContext().ensureActive()
        val before = authority.readAuthorizedHead(expectedStorageKey, expectedCredentialId)
        requireExpectedScope(before, expectedStorageKey, expectedCredentialId)
        val generation = requireNotNull(storage.readCurrentHead(before.authenticatedHead)) {
            "Committed backup generation is unavailable"
        }
        val wrapper = requireNotNull(
            generation.wrappers.singleOrNull { it.context.credentialId == expectedCredentialId }
        ) { "Committed backup has no wrapper for the authenticated credential" }
        val salt = wrapper.prfSalt
        val pending = try {
            val challenge = authority.assertionChallenge(expectedStorageKey, expectedCredentialId)
            require(challenge.storageKey == expectedStorageKey && challenge.credentialId == expectedCredentialId) {
                "Recovery assertion challenge scope mismatch"
            }
            PendingPasskeyBackupAssertion(
                challenge.assertionId,
                expectedStorageKey,
                PasskeyBackupContract.assertionOptionsJsonWithPrf(
                    challenge.challenge, expectedCredentialId, salt
                )
            )
        } finally {
            salt.fill(0)
        }
        return ceremonyExecutor.performAssertion(pending).use { result ->
            result.withRequiredLocalPrfOutput { prf ->
                val responseCredential = JsonParser.parseString(result.serverCredentialJson)
                    .asJsonObject.get("id")?.asString
                require(responseCredential == expectedCredentialId) { "Recovery assertion credential mismatch" }
                val after = authority.verifyAssertionAndReadHead(
                    pending.assertionId, result.serverCredentialJson
                )
                require(after.assertionId == pending.assertionId && after.credentialId == expectedCredentialId) {
                    "Verified recovery assertion mismatch"
                }
                requireSameHead(before, after.snapshot, expectedStorageKey, expectedCredentialId)
                val evidence = verifier.verify(generation, expectedCredentialId, prf, before.expectedWallet)
                storage.requireSelectedAccount()
                currentCoroutineContext().ensureActive()
                val latest = authority.readAuthorizedHead(expectedStorageKey, expectedCredentialId)
                requireSameHead(before, latest, expectedStorageKey, expectedCredentialId)
                storage.requireSelectedAccount()
                currentCoroutineContext().ensureActive()
                val read = before.authenticatedHead.currentReadParameters()
                PasskeyBackupAuthenticatedReadbackEvidence(
                    read.context.generationId, read.sha256, evidence.publicIdentitySha256
                )
            }
        }
    }

    private fun requireExpectedScope(
        snapshot: PasskeyBackupRecoveryHeadSnapshot,
        storageKey: String,
        credentialId: String
    ) {
        require(snapshot.credentialId == credentialId && snapshot.expectedWallet.storageKey == storageKey) {
            "Recovery owner scope mismatch"
        }
    }

    private fun requireSameHead(
        expected: PasskeyBackupRecoveryHeadSnapshot,
        actual: PasskeyBackupRecoveryHeadSnapshot,
        storageKey: String,
        credentialId: String
    ) {
        requireExpectedScope(actual, storageKey, credentialId)
        val oldWallet = expected.expectedWallet
        val newWallet = actual.expectedWallet
        val oldHead = expected.authenticatedHead.currentReadParameters()
        val newHead = actual.authenticatedHead.currentReadParameters()
        require(
            oldWallet.walletId == newWallet.walletId &&
                oldWallet.publicIdentitySha256 == newWallet.publicIdentitySha256 &&
                oldHead.fileId == newHead.fileId && oldHead.context == newHead.context &&
                oldHead.sha256 == newHead.sha256
        ) { "Committed recovery head or wallet identity changed during readback" }
    }
}
