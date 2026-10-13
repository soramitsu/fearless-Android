package jp.co.soramitsu.backup.passkey

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class PasskeyBackupFirstGenerationBuilderTest {
    private val owner = "owner:${identifier(1)}"
    private val namespace = "backup:${identifier(2)}"
    private val credential = identifier(3)
    private val account = GoogleDriveAccountAccess("google-subject-123", "selected@example.com", "test-token")
    private val wallet = PasskeyBackupExpectedWalletIdentity(
        "wallet-1234", "wallet-0001", PasskeyBackupGenerationFormat.sha256("public-inventory".toByteArray())
    )
    private val head = PasskeyBackupAuthenticatedHead(
        owner, namespace, null, null, owner, namespace,
        PasskeyBackupGenerationFormat.storageAccountBinding(account.subject)
    )
    private val salt = ByteArray(32) { 7 }
    private val prf = ByteArray(32) { 8 }

    @Test
    fun `first generation encrypts complete source and verifies decrypted original keys before return`() = runBlocking {
        val exported = "synthetic complete wallet inventory with standalone EVM and native TON".toByteArray()
        var verifications = 0
        val verifier = PasskeyBackupPlaintextWalletVerifier { plaintext, expected ->
            assertArrayEquals(
                "synthetic complete wallet inventory with standalone EVM and native TON".toByteArray(),
                plaintext
            )
            verifications++
            evidence(expected)
        }
        val generation = builder(exported, verifier).build(head, account, credential, salt, prf, wallet)
        val encoded = PasskeyBackupGenerationFormat.encode(generation)

        assertEquals(2, verifications)
        assertTrue(exported.all { it == 0.toByte() })
        assertTrue(generation.context.parentHeadRevision == 0L && generation.context.parentHeadSha256 == null)
        assertEquals(1L, generation.context.keyEpoch)
        assertEquals(head.storageAccountBinding, generation.context.storageAccountBinding)
        assertEquals(credential, generation.wrappers.single().context.credentialId)
        assertFalse(encoded.toString(Charsets.UTF_8).contains("synthetic complete wallet inventory"))
        assertFalse(encoded.toString(Charsets.UTF_8).contains(Base64.getUrlEncoder().withoutPadding().encodeToString(prf)))
        assertArrayEquals(salt, generation.wrappers.single().prfSalt)
        assertTrue(salt.any { it != 0.toByte() } && prf.any { it != 0.toByte() })
        assertEquals("PasskeyBackupGeneration(redacted)", generation.toString())
        val decoded = PasskeyBackupGenerationFormat.decode(
            encoded, generation.context, PasskeyBackupGenerationFormat.sha256(encoded)
        )
        assertEquals(generation.context, decoded.context)
    }

    @Test
    fun `missing original key signing or export evidence erases plaintext and prevents generation`() = runBlocking {
        listOf(
            PasskeyBackupLocalWalletEvidence(wallet.storageKey, wallet.walletId, wallet.publicIdentitySha256, true, false, true),
            PasskeyBackupLocalWalletEvidence(wallet.storageKey, wallet.walletId, wallet.publicIdentitySha256, true, true, false),
            PasskeyBackupLocalWalletEvidence(wallet.storageKey, wallet.walletId, "b".repeat(64), true, true, true)
        ).forEach { rejected ->
            val exported = "unverified original key material".toByteArray()
            var verifications = 0
            val verifier = PasskeyBackupPlaintextWalletVerifier { _, _ ->
                verifications++
                rejected
            }
            assertTrue(runCatching { builder(exported, verifier).build(head, account, credential, salt, prf, wallet) }.isFailure)
            assertEquals(1, verifications)
            assertTrue(exported.all { it == 0.toByte() })
        }
    }

    @Test
    fun `wrong account missing PRF and disabled release fail before original wallet export`() = runBlocking {
        var exports = 0
        val exporter = PasskeyBackupOriginalWalletExporter {
            exports++
            "secret".toByteArray()
        }
        val verifier = PasskeyBackupPlaintextWalletVerifier { _, expected -> evidence(expected) }
        fun builder(enabled: Boolean) = PasskeyBackupFirstGenerationBuilder(
            exporter, verifier, nowMillis = { 1_767_225_600_000L }, isReleaseEnabled = enabled
        )
        val other = GoogleDriveAccountAccess("other-google-subject", account.accountName, "test-token")
        assertTrue(runCatching { builder(true).build(head, other, credential, salt, prf, wallet) }.isFailure)
        assertTrue(runCatching { builder(true).build(head, account, credential, salt, byteArrayOf(1), wallet) }.isFailure)
        assertTrue(runCatching { builder(false).build(head, account, credential, salt, prf, wallet) }.isFailure)
        assertEquals(0, exports)
    }

    private fun builder(exported: ByteArray, verifier: PasskeyBackupPlaintextWalletVerifier) =
        PasskeyBackupFirstGenerationBuilder(
            PasskeyBackupOriginalWalletExporter { exported }, verifier,
            nowMillis = { 1_767_225_600_000L }, isReleaseEnabled = true
        )

    private fun evidence(expected: PasskeyBackupExpectedWalletIdentity) = PasskeyBackupLocalWalletEvidence(
        expected.storageKey, expected.walletId, expected.publicIdentitySha256,
        decryptionVerified = true, originalKeySigningVerified = true, originalKeyExportVerified = true
    )

    private companion object {
        fun identifier(value: Int): String = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(ByteArray(32) { value.toByte() })
    }
}
