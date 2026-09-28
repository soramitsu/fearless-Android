package jp.co.soramitsu.backup.passkey

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class PasskeyBackupExistingHeadCandidateVerifierTest {
    @Test
    fun `one-use native PRF stays local until assertion verification then decrypts original keys`() = runBlocking {
        val fixture = Fixture()
        val evidence = fixture.verifier().verify(GenerationFixture.generation(), fixture.wallet)
        assertEquals(fixture.wallet.publicIdentitySha256, evidence.publicIdentitySha256)
        assertEquals(1, fixture.authority.completions)
        assertEquals(2, fixture.authority.headReads)
        assertEquals(1, fixture.walletChecks)
        assertFalse(requireNotNull(fixture.ceremony.lastResult).hasLocalPrfOutput)
        assertFalse(fixture.authority.serverCredentialJson.contains("prf", ignoreCase = true))
        assertFalse(fixture.authority.serverCredentialJson.contains(fixture.prfEncoded))
        assertFalse(evidence.toString().contains(fixture.wallet.walletId))
    }

    @Test
    fun `missing PRF and server rejection never decrypt or prove original keys`() = runBlocking {
        val noPrf = Fixture()
        noPrf.ceremony.includePrf = false
        assertTrue(runCatching { noPrf.verifier().verify(GenerationFixture.generation(), noPrf.wallet) }.isFailure)
        assertEquals(0, noPrf.authority.completions)
        assertEquals(0, noPrf.walletChecks)

        val rejected = Fixture()
        rejected.authority.rejectAssertion = true
        assertTrue(runCatching { rejected.verifier().verify(GenerationFixture.generation(), rejected.wallet) }.isFailure)
        assertEquals(1, rejected.authority.completions)
        assertEquals(0, rejected.walletChecks)
        assertFalse(requireNotNull(rejected.ceremony.lastResult).hasLocalPrfOutput)
    }

    @Test
    fun `owner head or wallet change after native ceremony denies local proof`() = runBlocking {
        val changedHead = Fixture()
        changedHead.authority.changeHeadOnCompletion = true
        assertTrue(runCatching { changedHead.verifier().verify(GenerationFixture.generation(), changedHead.wallet) }.isFailure)
        assertEquals(0, changedHead.walletChecks)

        val changedWallet = Fixture()
        changedWallet.authority.changeWalletAfterProof = true
        assertTrue(
            runCatching { changedWallet.verifier().verify(GenerationFixture.generation(), changedWallet.wallet) }.isFailure
        )
        assertEquals(1, changedWallet.walletChecks)
    }

    private class Fixture {
        val context = GenerationFixture.context
        val credentialId = GenerationFixture.json["credentialId"].asString
        val wallet = PasskeyBackupExpectedWalletIdentity("wallet-1234", "wallet-001", "b".repeat(64))
        val head = PasskeyBackupAuthenticatedHead(
            context.ownerSubject, context.backupNamespace,
            PasskeyBackupHeadDescriptor(
                6, 5, "a".repeat(64), identifier(5), "a".repeat(64), 7,
                "old-drive-current", context.storageAccountBinding
            ),
            PasskeyBackupHeadDescriptor(
                5, 4, "c".repeat(64), identifier(4), "a".repeat(64), 7,
                "old-drive-previous", context.storageAccountBinding
            ),
            context.ownerSubject, context.backupNamespace, context.storageAccountBinding
        )
        val prfEncoded = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32) { 0x66 })
        var walletChecks = 0
        val authority = Authority(this)
        val ceremony = Ceremony(this)

        fun verifier(enabled: Boolean = true) = PasskeyBackupExistingHeadCandidateVerifier(
            credentialId, head, authority, ceremony,
            PasskeyBackupGenerationCryptographicVerifier(
                PasskeyBackupPlaintextWalletVerifier { plaintext, expected ->
                    walletChecks++
                    assertEquals("cross-platform-passkey-backup", plaintext.toString(Charsets.UTF_8))
                    PasskeyBackupLocalWalletEvidence(
                        expected.storageKey, expected.walletId, expected.publicIdentitySha256,
                        decryptionVerified = true, originalKeySigningVerified = true, originalKeyExportVerified = true
                    )
                }
            ),
            isReleaseEnabled = enabled
        )

        fun snapshot(
            head: PasskeyBackupAuthenticatedHead = this.head,
            wallet: PasskeyBackupExpectedWalletIdentity = this.wallet
        ): PasskeyBackupRecoveryHeadSnapshot {
            return PasskeyBackupRecoveryHeadSnapshot(credentialId, wallet, head)
        }
    }

    private class Authority(private val fixture: Fixture) : PasskeyBackupRecoveryAuthority {
        var headReads = 0
        var completions = 0
        var rejectAssertion = false
        var changeHeadOnCompletion = false
        var changeWalletAfterProof = false
        var serverCredentialJson = ""

        override suspend fun readAuthorizedHead(
            storageKey: String,
            credentialId: String
        ): PasskeyBackupRecoveryHeadSnapshot {
            headReads++
            return if (changeWalletAfterProof && headReads == 2) {
                fixture.snapshot(wallet = PasskeyBackupExpectedWalletIdentity(storageKey, "other-wallet", fixture.wallet.publicIdentitySha256))
            } else {
                fixture.snapshot()
            }
        }

        override suspend fun assertionChallenge(
            storageKey: String,
            credentialId: String
        ): PasskeyBackupAssertionChallenge {
            return PasskeyBackupAssertionChallenge(
                "assertion-1234", ByteArray(32) { it.toByte() }, storageKey, credentialId = credentialId
            )
        }

        override suspend fun verifyAssertionAndReadHead(
            assertionId: String,
            serverCredentialJson: String
        ): PasskeyBackupVerifiedAssertionHead {
            completions++
            this.serverCredentialJson = serverCredentialJson
            if (rejectAssertion) error("Invalid signed assertion")
            val snapshot = if (changeHeadOnCompletion) {
                val altered = PasskeyBackupAuthenticatedHead(
                    fixture.context.ownerSubject, fixture.context.backupNamespace,
                    PasskeyBackupHeadDescriptor(
                        6, 5, "a".repeat(64), identifier(8), "e".repeat(64),
                        7, "rival-drive", fixture.context.storageAccountBinding
                    ),
                    fixture.head.previous,
                    fixture.context.ownerSubject, fixture.context.backupNamespace, fixture.context.storageAccountBinding
                )
                fixture.snapshot(head = altered)
            } else {
                fixture.snapshot()
            }
            return PasskeyBackupVerifiedAssertionHead(assertionId, fixture.credentialId, snapshot)
        }
    }

    private class Ceremony(private val fixture: Fixture) : PasskeyBackupCeremonyExecutor {
        var includePrf = true
        var lastResult: PasskeyBackupNativeCeremonyResult? = null

        override suspend fun performRegistration(
            pending: PendingPasskeyBackupRegistration
        ): PasskeyBackupNativeCeremonyResult = error("Unexpected registration")

        override suspend fun performAssertion(
            pending: PendingPasskeyBackupAssertion
        ): PasskeyBackupNativeCeremonyResult {
            val extension = if (includePrf) {
                "{\"prf\":{\"results\":{\"first\":\"${fixture.prfEncoded}\"}}}"
            } else {
                "{}"
            }
            return PasskeyBackupNativeCeremonyResult.assertion(
                """{"id":"${fixture.credentialId}","rawId":"${fixture.credentialId}","type":"public-key","response":{"clientDataJSON":"AQ","authenticatorData":"AQ","signature":"AQ","userHandle":null},"clientExtensionResults":$extension}""",
                pending.requestJson
            ).also { lastResult = it }
        }
    }

    private companion object {
        fun identifier(value: Int): String = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(ByteArray(32) { value.toByte() })
    }
}
