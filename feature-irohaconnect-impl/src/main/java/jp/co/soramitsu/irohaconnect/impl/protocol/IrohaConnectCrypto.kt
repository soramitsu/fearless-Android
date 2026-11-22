// Digest, key and tag sizes of the primitives Iroha Connect fixes.
// Infix bit operations keep explicit grouping.
@file:Suppress("MagicNumber", "UnnecessaryParentheses")

package jp.co.soramitsu.irohaconnect.impl.protocol

import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.digests.Blake2bDigest
import org.bouncycastle.crypto.digests.Blake3Digest
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.modes.ChaCha20Poly1305
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.security.SecureRandom

/** The primitives Iroha Connect v1 uses, on BouncyCastle's lightweight API. */
internal object IrohaConnectCrypto {
    const val KEY_BYTES = 32
    const val SIGNATURE_BYTES = 64
    private const val AEAD_TAG_BITS = 128
    private const val CRC64_XZ_POLY = -0x3693a86a2878f0beL // 0xC96C5795D7870F42
    private val CRC64_TABLE = LongArray(256) { index ->
        var crc = index.toLong()
        repeat(8) { crc = if (crc and 1L != 0L) (crc ushr 1) xor CRC64_XZ_POLY else crc ushr 1 }
        crc
    }

    private val random = SecureRandom()

    fun randomBytes(size: Int): ByteArray = ByteArray(size).also(random::nextBytes)

    fun blake2b256(data: ByteArray): ByteArray {
        val digest = Blake2bDigest(256)
        digest.update(data, 0, data.size)
        return ByteArray(32).also { digest.doFinal(it, 0) }
    }

    /** Iroha's marked hash: Blake2b-256 with the lowest bit of the last byte set. */
    fun irohaHash(data: ByteArray): ByteArray =
        blake2b256(data).also { it[it.size - 1] = (it[it.size - 1].toInt() or 1).toByte() }

    /** BLAKE3-256. */
    fun blake3(data: ByteArray): ByteArray {
        val digest = Blake3Digest()
        digest.update(data, 0, data.size)
        return ByteArray(32).also { digest.doFinal(it, 0) }
    }

    fun sha256(data: ByteArray): ByteArray {
        val digest = SHA256Digest()
        digest.update(data, 0, data.size)
        return ByteArray(32).also { digest.doFinal(it, 0) }
    }

    fun hkdfSha256(
        inputKey: ByteArray,
        salt: ByteArray,
        info: ByteArray,
        length: Int
    ): ByteArray {
        val generator = HKDFBytesGenerator(SHA256Digest())
        generator.init(HKDFParameters(inputKey, salt, info))
        return ByteArray(length).also { generator.generateBytes(it, 0, length) }
    }

    fun x25519PublicKey(privateKey: ByteArray): ByteArray {
        require(privateKey.size == KEY_BYTES) { "X25519 private key must be 32 bytes" }
        return X25519PrivateKeyParameters(privateKey, 0).generatePublicKey().encoded
    }

    fun x25519KeyPair(): Pair<ByteArray, ByteArray> {
        val privateKey = X25519PrivateKeyParameters(random)
        return privateKey.encoded to privateKey.generatePublicKey().encoded
    }

    /** The raw X25519 shared secret; refuses low-order peers whose secret is all zeroes. */
    fun x25519SharedSecret(privateKey: ByteArray, peerPublicKey: ByteArray): ByteArray {
        require(privateKey.size == KEY_BYTES && peerPublicKey.size == KEY_BYTES) { "X25519 keys must be 32 bytes" }
        val agreement = X25519Agreement()
        agreement.init(X25519PrivateKeyParameters(privateKey, 0))
        val shared = ByteArray(agreement.agreementSize)
        try {
            agreement.calculateAgreement(X25519PublicKeyParameters(peerPublicKey, 0), shared, 0)
        } catch (error: IllegalStateException) {
            throw IllegalArgumentException("X25519 key agreement failed", error)
        }
        require(shared.any { it.toInt() != 0 }) { "X25519 key agreement failed" }
        return shared
    }

    fun chaCha20Poly1305Seal(
        key: ByteArray,
        nonce: ByteArray,
        aad: ByteArray,
        plaintext: ByteArray
    ): ByteArray {
        val cipher = ChaCha20Poly1305()
        cipher.init(true, AEADParameters(KeyParameter(key), AEAD_TAG_BITS, nonce, aad))
        val output = ByteArray(cipher.getOutputSize(plaintext.size))
        val written = cipher.processBytes(plaintext, 0, plaintext.size, output, 0)
        cipher.doFinal(output, written)
        return output
    }

    /** Returns null when the ciphertext does not authenticate. */
    fun chaCha20Poly1305Open(
        key: ByteArray,
        nonce: ByteArray,
        aad: ByteArray,
        sealed: ByteArray
    ): ByteArray? {
        val cipher = ChaCha20Poly1305()
        cipher.init(false, AEADParameters(KeyParameter(key), AEAD_TAG_BITS, nonce, aad))
        val output = ByteArray(cipher.getOutputSize(sealed.size))
        return try {
            val written = cipher.processBytes(sealed, 0, sealed.size, output, 0)
            val total = written + cipher.doFinal(output, written)
            output.copyOf(total)
        } catch (_: Exception) {
            output.fill(0)
            null
        }
    }

    fun ed25519PublicKey(privateKeySeed: ByteArray): ByteArray {
        require(privateKeySeed.size == KEY_BYTES) { "Ed25519 private key seed must be 32 bytes" }
        return Ed25519PrivateKeyParameters(privateKeySeed, 0).generatePublicKey().encoded
    }

    /** Signs and self-verifies, so a faulty signer can never hand a dApp an invalid signature. */
    fun ed25519Sign(privateKeySeed: ByteArray, message: ByteArray): ByteArray {
        require(privateKeySeed.size == KEY_BYTES) { "Ed25519 private key seed must be 32 bytes" }
        val privateKey = Ed25519PrivateKeyParameters(privateKeySeed, 0)
        val signer = Ed25519Signer()
        signer.init(true, privateKey)
        signer.update(message, 0, message.size)
        val signature = signer.generateSignature()
        check(ed25519Verify(privateKey.generatePublicKey().encoded, message, signature)) { "Ed25519 self-verification failed" }
        return signature
    }

    fun ed25519Verify(
        publicKey: ByteArray,
        message: ByteArray,
        signature: ByteArray
    ): Boolean {
        if (publicKey.size != KEY_BYTES || signature.size != SIGNATURE_BYTES) return false
        return runCatching {
            val verifier = Ed25519Signer()
            verifier.init(false, Ed25519PublicKeyParameters(publicKey, 0))
            verifier.update(message, 0, message.size)
            verifier.verifySignature(signature)
        }.getOrDefault(false)
    }

    /** CRC-64/XZ (ECMA-182, reflected), the checksum in every Norito NRT0 header. */
    fun crc64Xz(bytes: ByteArray): Long {
        var crc = -1L
        bytes.forEach { byte ->
            crc = CRC64_TABLE[((crc xor byte.toLong()) and 0xff).toInt()] xor (crc ushr 8)
        }
        return crc.inv()
    }
}
