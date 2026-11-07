package jp.co.soramitsu.backup.passkey

import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class PasskeyBackupBootstrapAttestationTest {
    // Shared public synthetic fixture: the owner authority verifies both real signatures and
    // independently derives requestHash from the exact same signed wallet message and key bytes.
    private val vectors by lazy {
        val stream = requireNotNull(javaClass.getResourceAsStream("/bootstrap-play-integrity-vectors.json"))
        stream.use { JsonParser.parseReader(it.reader()).asJsonObject }
    }

    @Test
    fun `Ed25519 request hash matches server-verified wallet proof vector`() {
        val binding = signedProofBinding()

        assertEquals(proofVector("ed25519").get("requestHash").asString, binding.requestHash)
        assertFalse(binding.toString().contains(binding.requestHash))
    }

    @Test
    fun `secp256k1 request hash matches server-verified wallet proof vector`() {
        val vector = proofVector("secp256k1")
        val uncompressedKey = decode(vector.get("publicKey").asString)
        assertEquals(65, uncompressedKey.size)
        assertEquals(4, uncompressedKey.first().toInt())
        val normalizedKey = byteArrayOf(if (uncompressedKey.last().toInt() and 1 == 0) 2 else 3) +
            uncompressedKey.copyOfRange(1, 33)
        val binding = PasskeyBackupBootstrapWalletProofBinding.fromSignedWalletProof(
            walletMessage(), PasskeyBackupBootstrapWalletProofBinding.Scheme.SECP256K1,
            normalizedKey, decode(vector.get("signature").asString)
        )

        assertEquals(vector.get("requestHash").asString, binding.requestHash)
    }

    @Test
    fun `changing any signed proof input changes the request hash`() {
        val original = signedProofBinding().requestHash
        val changedMessage = walletMessage().also { it[it.lastIndex] = 5 }
        val changedSignature = signature().also { it[0] = 6 }
        val changedKey = publicKey().also { it[0] = 7 }

        assertTrue(signedProofBinding(message = changedMessage).requestHash != original)
        assertTrue(signedProofBinding(signature = changedSignature).requestHash != original)
        assertTrue(signedProofBinding(key = changedKey).requestHash != original)
        assertTrue(
            PasskeyBackupBootstrapWalletProofBinding.fromSignedWalletProof(
                walletMessage(), PasskeyBackupBootstrapWalletProofBinding.Scheme.SECP256K1,
                byteArrayOf(2) + ByteArray(32) { 0x44 }, signature()
            ).requestHash != original
        )
    }

    @Test
    fun `invalid proof shape is rejected`() {
        assertTrue(runCatching { signedProofBinding(message = "wallet".toByteArray()) }.isFailure)
        assertTrue(runCatching { signedProofBinding(signature = ByteArray(63)) }.isFailure)
        assertTrue(runCatching { signedProofBinding(key = ByteArray(33)) }.isFailure)
        assertTrue(
            runCatching {
                PasskeyBackupBootstrapWalletProofBinding.fromSignedWalletProof(
                    walletMessage(), PasskeyBackupBootstrapWalletProofBinding.Scheme.SECP256K1,
                    byteArrayOf(4) + ByteArray(32), signature()
                )
            }.isFailure
        )
    }

    @Test
    fun `no open attestation exists yet, so bootstrap attestation always fails closed`() = runBlocking {
        val failure = runCatching {
            UnavailablePasskeyBackupBootstrapAttestationProvider().attest(signedProofBinding())
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertFalse(PasskeyBackupReleaseConfig.PASSKEY_BACKUP_ENABLED)
    }

    @Test
    fun `attestation sent to the owner authority has exactly kind and token`() {
        val binding = signedProofBinding()
        val attestation = PasskeyBackupBootstrapAttestation("open-attestation", "a".repeat(40), binding.requestHash)

        val json = JsonParser.parseString(attestation.serverAttestationJson()).asJsonObject

        assertEquals(setOf("kind", "token"), json.keySet())
        assertEquals("open-attestation", json.get("kind").asString)
        assertFalse(attestation.toString().contains("a".repeat(40)))
    }

    @Test
    fun `malformed attestations are refused`() {
        val hash = signedProofBinding().requestHash
        listOf(
            Triple("Play Integrity", "a".repeat(40), hash),
            Triple("open", "short", hash),
            Triple("open", "a".repeat(39) + " ", hash),
            Triple("open", "a".repeat(40), "not-a-hash")
        ).forEach { (kind, token, requestHash) ->
            assertTrue(runCatching { PasskeyBackupBootstrapAttestation(kind, token, requestHash) }.isFailure)
        }
    }

    private fun signedProofBinding(
        message: ByteArray = walletMessage(),
        key: ByteArray = publicKey(),
        signature: ByteArray = signature()
    ) = PasskeyBackupBootstrapWalletProofBinding.fromSignedWalletProof(
        message, PasskeyBackupBootstrapWalletProofBinding.Scheme.ED25519, key, signature
    )

    private fun proofVector(scheme: String) = vectors.getAsJsonArray("proofs")
        .map { it.asJsonObject }
        .single { it.get("scheme").asString == scheme }

    private fun walletMessage() = decode(vectors.get("walletMessage").asString)
    private fun publicKey() = decode(proofVector("ed25519").get("publicKey").asString)
    private fun signature() = decode(proofVector("ed25519").get("signature").asString)
    private fun decode(value: String) = Base64.getUrlDecoder().decode(value)
}
