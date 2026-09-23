package jp.co.soramitsu.backup.passkey

/** Starts a PRF ceremony only after the server binds one credential to the assertion challenge. */
internal class PasskeyBackupCredentialDirectedAssertion(
    private val challengeService: PasskeyBackupChallengeService,
    private val relyingPartyId: String,
    private val isReleaseEnabled: Boolean
) {
    suspend fun begin(
        storageKey: String,
        credentialId: String,
        prfSalt: ByteArray
    ): PendingPasskeyBackupAssertion {
        PasskeyBackupReleaseConfig.requireEnabled(isReleaseEnabled)
        val normalizedStorageKey = PasskeyBackupContract.requireStorageKey(storageKey)
        val normalizedCredentialId = requireCredentialId(credentialId)
        PasskeyBackupContract.requirePrfSalt(prfSalt)
        val challenge = challengeService.assertionChallenge(normalizedStorageKey, normalizedCredentialId)
        val challengeStorageKey = PasskeyBackupContract.requireStorageKey(challenge.storageKey)
        require(challengeStorageKey == normalizedStorageKey) {
            "Passkey credential-directed assertion challenge returned a mismatched storageKey"
        }
        require(challenge.credentialId == normalizedCredentialId) {
            "Passkey assertion challenge returned a mismatched credentialId"
        }
        return PendingPasskeyBackupAssertion(
            assertionId = challenge.assertionId,
            storageKey = challengeStorageKey,
            requestJson = PasskeyBackupContract.assertionOptionsJsonWithPrf(
                challenge = challenge.challenge,
                credentialId = normalizedCredentialId,
                prfSalt = prfSalt,
                rpId = relyingPartyId
            )
        )
    }
}
