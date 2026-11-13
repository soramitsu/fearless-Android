package jp.co.soramitsu.fearless_utils.encrypt

import org.bouncycastle.math.ec.rfc8032.Ed25519
import java.math.BigInteger

/**
 * RFC 8032 Ed25519 on 32-byte seeds, implemented with BouncyCastle.
 *
 * Ed25519 signing is deterministic: the same seed and message give the same 64 bytes in every
 * RFC 8032 implementation. That keeps signatures byte-identical to the EdDSA-Java provider and the
 * ton-kotlin curve this object replaces. Verification is strict: it rejects S >= L
 * (CVE-2020-36843) and keys or R values that do not decode to canonical curve points.
 */
object Ed25519Crypto {

    const val SEED_SIZE = 32
    const val PUBLIC_KEY_SIZE = 32
    const val SIGNATURE_SIZE = 64

    private const val SCALAR_OFFSET = 32

    // L, the order of the Ed25519 base point.
    private val GROUP_ORDER = BigInteger("1000000000000000000000000000000014def9dea2f79cd65812631a5cf5d3ed", 16)

    /** The 32-byte public key A of [seed]. */
    fun publicKey(seed: ByteArray): ByteArray {
        requireSeed(seed)
        val publicKey = ByteArray(PUBLIC_KEY_SIZE)
        Ed25519.generatePublicKey(seed, 0, publicKey, 0)
        return publicKey
    }

    /** The 64-byte RFC 8032 signature R || S of [message] under [seed]. */
    fun sign(seed: ByteArray, message: ByteArray): ByteArray {
        requireSeed(seed)
        val signature = ByteArray(SIGNATURE_SIZE)
        Ed25519.sign(seed, 0, message, 0, message.size, signature, 0)
        return signature
    }

    /** True only for a canonical RFC 8032 signature of [message] by [publicKey]. */
    fun verify(
        message: ByteArray,
        signature: ByteArray,
        publicKey: ByteArray
    ): Boolean {
        if (signature.size != SIGNATURE_SIZE || publicKey.size != PUBLIC_KEY_SIZE) return false
        if (!hasCanonicalScalar(signature)) return false

        return Ed25519.verify(signature, 0, publicKey, 0, message, 0, message.size)
    }

    /** S, the last 32 bytes of the signature read little-endian, must be below L. */
    fun hasCanonicalScalar(signature: ByteArray): Boolean {
        if (signature.size != SIGNATURE_SIZE) return false
        val scalar = BigInteger(1, signature.copyOfRange(SCALAR_OFFSET, SIGNATURE_SIZE).reversedArray())
        return scalar < GROUP_ORDER
    }

    private fun requireSeed(seed: ByteArray) {
        require(seed.size == SEED_SIZE) { "Ed25519 seed must be $SEED_SIZE bytes" }
    }
}
