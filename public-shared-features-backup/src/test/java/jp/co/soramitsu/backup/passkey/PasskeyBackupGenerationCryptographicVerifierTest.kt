package jp.co.soramitsu.backup.passkey

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class PasskeyBackupGenerationCryptographicVerifierTest {
    private val owner = "owner:${identifier(1)}"
    private val namespace = "backup:${identifier(2)}"
    private val credential = identifier(3)
    private val wallet = PasskeyBackupExpectedWalletIdentity(
        "wallet-1234", "wallet-001", PasskeyBackupGenerationFormat.sha256("original-public-identity".toByteArray())
    )
    private val metadata = PasskeyBackupEnvelopeMetadata(
        wallet.storageKey, wallet.walletId, "selected@example.com", 1_767_225_600_000L
    )
    private val plaintext = "synthetic-original-wallet-material".toByteArray()
    private val backupKey = ByteArray(32) { 0x46 }
    private val prfOutput = ByteArray(32) { 0x47 }

    @Test
    fun `verified credential unwraps decrypts and requires original key evidence`() = runBlocking {
        val generation = generation()
        var observedPlaintext: ByteArray? = null
        val verifier = PasskeyBackupGenerationCryptographicVerifier(
            PasskeyBackupPlaintextWalletVerifier { decrypted, expected ->
                observedPlaintext = decrypted
                assertArrayEquals(plaintext, decrypted)
                evidence(expected)
            }
        )
        val callerPrf = prfOutput.copyOf()

        val result = verifier.verify(generation, credential, callerPrf, wallet)

        assertEquals(wallet.publicIdentitySha256, result.publicIdentitySha256)
        assertTrue(requireNotNull(observedPlaintext).all { it == 0.toByte() })
        assertArrayEquals(prfOutput, callerPrf)
        assertFalse(result.toString().contains(wallet.storageKey))
    }

    @Test
    fun `wrong credential PRF wallet and tampered ciphertext cannot reach wallet verifier`() = runBlocking {
        val generation = generation()
        var verificationCalls = 0
        val verifier = PasskeyBackupGenerationCryptographicVerifier(
            PasskeyBackupPlaintextWalletVerifier { _, expected ->
                verificationCalls++
                evidence(expected)
            }
        )
        assertFailure { verifier.verify(generation, identifier(4), prfOutput, wallet) }
        assertFailure { verifier.verify(generation, credential, ByteArray(32) { 0x48 }, wallet) }
        assertFailure {
            verifier.verify(
                generation, credential, prfOutput,
                PasskeyBackupExpectedWalletIdentity("wallet-9999", wallet.walletId, wallet.publicIdentitySha256)
            )
        }
        val ciphertext = generation.envelope.encryptedPayload.also {
            it[it.lastIndex] = (it.last().toInt() xor 1).toByte()
        }
        val tampered = PasskeyBackupGeneration(
            generation.context,
            PasskeyBackupEncryptedPayload(
                metadata.storageKey, metadata.walletId, metadata.accountName,
                metadata.createdAtMillis, ciphertext
            ),
            generation.wrappers
        )
        assertFailure { verifier.verify(tampered, credential, prfOutput, wallet) }
        assertEquals(0, verificationCalls)
    }

    @Test
    fun `missing signing export decryption or exact identity evidence fails closed`() = runBlocking {
        val generation = generation()
        val invalid = listOf(
            PasskeyBackupLocalWalletEvidence(
                wallet.storageKey, wallet.walletId, wallet.publicIdentitySha256,
                false, true, true
            ),
            PasskeyBackupLocalWalletEvidence(
                wallet.storageKey, wallet.walletId, wallet.publicIdentitySha256,
                true, false, true
            ),
            PasskeyBackupLocalWalletEvidence(
                wallet.storageKey, wallet.walletId, wallet.publicIdentitySha256,
                true, true, false
            ),
            PasskeyBackupLocalWalletEvidence(
                wallet.storageKey, wallet.walletId, "a".repeat(64),
                true, true, true
            ),
            PasskeyBackupLocalWalletEvidence(
                "wallet-9999", wallet.walletId, wallet.publicIdentitySha256,
                true, true, true
            )
        )
        invalid.forEach { reported ->
            var observedPlaintext: ByteArray? = null
            val verifier = PasskeyBackupGenerationCryptographicVerifier(
                PasskeyBackupPlaintextWalletVerifier { decrypted, _ ->
                    observedPlaintext = decrypted
                    reported
                }
            )
            assertFailure { verifier.verify(generation, credential, prfOutput, wallet) }
            assertTrue(requireNotNull(observedPlaintext).all { it == 0.toByte() })
        }
    }

    @Test
    fun `wallet verifier failure still clears decrypted material`() = runBlocking {
        var observedPlaintext: ByteArray? = null
        val verifier = PasskeyBackupGenerationCryptographicVerifier(
            PasskeyBackupPlaintextWalletVerifier { decrypted, _ ->
                observedPlaintext = decrypted
                error("synthetic wallet verification failure")
            }
        )
        assertFailure { verifier.verify(generation(), credential, prfOutput, wallet) }
        assertTrue(requireNotNull(observedPlaintext).all { it == 0.toByte() })
    }

    private fun generation(): PasskeyBackupGeneration {
        val context = PasskeyBackupGeneration.Context(
            owner, namespace, identifier(5), 0, null, 1,
            PasskeyBackupGenerationFormat.storageAccountBinding("google-subject-123")
        )
        val encrypted = AesGcmPasskeyBackupEnvelopeCryptography().encrypt(plaintext, metadata, backupKey)
        val envelope = PasskeyBackupEncryptedPayload(
            metadata.storageKey, metadata.walletId, metadata.accountName,
            metadata.createdAtMillis, encrypted
        )
        val wrapperContext = PasskeyBackupKeyWrapperContext(owner, credential, context.keyEpoch, metadata)
        val keyWrapper = PasskeyBackupCredentialKeyWrapper()
        val wrapper = keyWrapper.wrap(backupKey, prfOutput, keyWrapper.newPrfSalt(), wrapperContext)
        return PasskeyBackupGeneration(context, envelope, listOf(wrapper))
    }

    private fun evidence(expected: PasskeyBackupExpectedWalletIdentity) = PasskeyBackupLocalWalletEvidence(
        expected.storageKey, expected.walletId, expected.publicIdentitySha256,
        decryptionVerified = true, originalKeySigningVerified = true, originalKeyExportVerified = true
    )

    private suspend fun assertFailure(block: suspend () -> Unit) {
        assertTrue(runCatching { block() }.isFailure)
    }

    private companion object {
        fun identifier(value: Int): String = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(ByteArray(32) { value.toByte() })
    }
}
