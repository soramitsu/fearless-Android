package jp.co.soramitsu.backup.passkey

import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class PasskeyBackupPlayIntegrityBootstrapTest {
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
    fun `invalid proof shape or missing audited project is rejected`() {
        assertTrue(runCatching { PasskeyBackupPlayIntegrityCloudProject(0) }.isFailure)
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
    fun `disabled release fails before preparing Google provider`() = runBlocking {
        val gateway = RecordingGateway()
        val requester = requester(gateway, enabled = false)

        assertTrue(runCatching { requester.warmUp() }.isFailure)
        assertTrue(runCatching { requester.requestToken(signedProofBinding()) }.isFailure)
        assertEquals(0, gateway.prepares)
        assertEquals(0, gateway.requests.size)
    }

    @Test
    fun `native request sends only exact signed-proof hash and returns sanitized transport`() = runBlocking {
        val gateway = RecordingGateway()
        val requester = requester(gateway)
        val binding = signedProofBinding()

        requester.warmUp()
        val result = requester.requestToken(binding)

        assertEquals(1, gateway.prepares)
        assertEquals(123_456_789L, gateway.projectNumber)
        assertEquals(listOf(binding.requestHash), gateway.requests)
        assertEquals(binding.requestHash, result.requestHash)
        val server = JsonParser.parseString(result.serverAttestationJson()).asJsonObject
        assertEquals(setOf("kind", "token"), server.keySet())
        assertEquals("play-integrity", server.get("kind").asString)
        assertEquals(gateway.token, server.get("token").asString)
        assertFalse(result.toString().contains(gateway.token))
        assertFalse(result.serverAttestationJson().contains(binding.requestHash))
        requester.requestToken(binding)
        assertEquals(1, gateway.prepares)
    }

    @Test
    fun `Google failure or malformed token is redacted and forces new preparation`() = runBlocking {
        val gateway = RecordingGateway()
        val requester = requester(gateway)
        gateway.failure = true
        val failed = runCatching { requester.requestToken(signedProofBinding()) }.exceptionOrNull()
        assertTrue(failed is IllegalStateException)
        assertFalse(requireNotNull(failed).toString().contains("provider-secret"))
        assertEquals(1, gateway.prepares)

        gateway.failure = false
        gateway.token = "malformed token with spaces"
        val malformed = runCatching { requester.requestToken(signedProofBinding()) }.exceptionOrNull()
        assertTrue(malformed is IllegalStateException)
        assertFalse(requireNotNull(malformed).toString().contains(gateway.token))
        assertEquals(2, gateway.prepares)

        gateway.token = "a".repeat(40)
        requester.requestToken(signedProofBinding())
        assertEquals(3, gateway.prepares)
    }

    @Test
    fun `failed preparation does not cache a provider or leak native error`() = runBlocking {
        val gateway = RecordingGateway().apply { preparationFailure = true }
        val requester = requester(gateway)
        val failed = runCatching { requester.requestToken(signedProofBinding()) }.exceptionOrNull()
        assertTrue(failed is IllegalStateException)
        assertFalse(requireNotNull(failed).toString().contains("provider-secret"))
        gateway.preparationFailure = false
        requester.requestToken(signedProofBinding())
        assertEquals(2, gateway.prepares)
    }

    private fun requester(gateway: RecordingGateway, enabled: Boolean = true) =
        PasskeyBackupPlayIntegrityBootstrapRequester(
            PasskeyBackupPlayIntegrityCloudProject(123_456_789), gateway, enabled
        )

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

    private class RecordingGateway : PasskeyBackupStandardIntegrityGateway {
        var prepares = 0
        var projectNumber = 0L
        var preparationFailure = false
        var failure = false
        var token = "a".repeat(40)
        val requests = mutableListOf<String>()

        override suspend fun prepare(cloudProjectNumber: Long): PasskeyBackupStandardIntegrityGateway.PreparedProvider {
            prepares++
            projectNumber = cloudProjectNumber
            if (preparationFailure) error("provider-secret preparation detail")
            return PasskeyBackupStandardIntegrityGateway.PreparedProvider { requestHash ->
                requests += requestHash
                if (failure) error("provider-secret request detail")
                token
            }
        }
    }
}
