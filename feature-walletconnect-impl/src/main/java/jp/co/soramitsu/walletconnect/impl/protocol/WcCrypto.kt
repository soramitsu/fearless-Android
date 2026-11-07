package jp.co.soramitsu.walletconnect.impl.protocol

import org.bouncycastle.crypto.InvalidCipherTextException
import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.modes.ChaCha20Poly1305
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import java.security.SecureRandom

/** A WalletConnect message could not be sealed or opened. Carries no key or message material. */
internal class WcCryptoException(message: String, cause: Throwable? = null) : Exception(message, cause)

internal fun cryptoFailure(message: String, cause: Throwable? = null): Nothing = throw WcCryptoException(message, cause)

/** An X25519 key pair. The private key never leaves the engine's encrypted state. */
internal class WcKeyPair(val privateKey: ByteArray, val publicKey: ByteArray)

/**
 * A parsed envelope: type 0 is sealed with the topic's key, type 1 also carries the sender's
 * public key so a receiver that has none for the topic can derive the key.
 */
internal class WcEnvelope(val type: Int, val senderPublicKey: ByteArray?, val iv: ByteArray, val sealed: ByteArray)

/**
 * The WalletConnect v2 cryptography: X25519 key agreement, HKDF-SHA256 key derivation,
 * ChaCha20-Poly1305 envelopes and SHA-256 topics, all on BouncyCastle's lightweight API.
 */
internal object WcCrypto {
    const val KEY_LENGTH = 32
    const val IV_LENGTH = 12
    const val TYPE_0 = 0
    const val TYPE_1 = 1
    private const val TAG_BITS = 128
    private const val TAG_LENGTH = TAG_BITS / Byte.SIZE_BITS

    private val random = SecureRandom()

    fun randomBytes(size: Int): ByteArray = ByteArray(size).also(random::nextBytes)

    fun generateKeyPair(): WcKeyPair {
        val privateKey = X25519PrivateKeyParameters(random)
        return WcKeyPair(privateKey.encoded, privateKey.generatePublicKey().encoded)
    }

    fun publicKeyOf(privateKey: ByteArray): ByteArray {
        require(privateKey.size == KEY_LENGTH) { "Invalid private key" }
        return X25519PrivateKeyParameters(privateKey, 0).generatePublicKey().encoded
    }

    /** The symmetric key two peers share: HKDF-SHA256 (no salt, no info) over their X25519 secret. */
    fun deriveSymKey(privateKey: ByteArray, peerPublicKey: ByteArray): ByteArray {
        if (privateKey.size != KEY_LENGTH || peerPublicKey.size != KEY_LENGTH) cryptoFailure("Invalid key length")
        val agreement = X25519Agreement()
        agreement.init(X25519PrivateKeyParameters(privateKey, 0))
        val shared = ByteArray(agreement.agreementSize)
        try {
            agreement.calculateAgreement(X25519PublicKeyParameters(peerPublicKey, 0), shared, 0)
        } catch (error: IllegalStateException) {
            // BouncyCastle refuses low-order points, whose shared secret is all zeros.
            cryptoFailure("Key agreement failed", error)
        }
        if (shared.all { it.toInt() == 0 }) cryptoFailure("Key agreement failed")
        val hkdf = HKDFBytesGenerator(SHA256Digest())
        hkdf.init(HKDFParameters(shared, null, null))
        val symKey = ByteArray(KEY_LENGTH)
        hkdf.generateBytes(symKey, 0, KEY_LENGTH)
        shared.fill(0)
        return symKey
    }

    fun sha256(data: ByteArray): ByteArray {
        val digest = SHA256Digest()
        digest.update(data, 0, data.size)
        return ByteArray(digest.digestSize).also { digest.doFinal(it, 0) }
    }

    /** A topic is the hex SHA-256 of the key that encrypts it. */
    fun topicOf(symKey: ByteArray): String = WcEncoding.hex(sha256(symKey))

    /** The hex SHA-256 of a message's UTF-8 text, as Verify attestations name messages. */
    fun hashMessage(message: String): String = WcEncoding.hex(sha256(message.toByteArray(Charsets.UTF_8)))

    fun seal(
        symKey: ByteArray,
        iv: ByteArray,
        plaintext: ByteArray
    ): ByteArray {
        val cipher = cipher(forEncryption = true, symKey, iv)
        val out = ByteArray(cipher.getOutputSize(plaintext.size))
        val written = cipher.processBytes(plaintext, 0, plaintext.size, out, 0)
        cipher.doFinal(out, written)
        return out
    }

    fun open(
        symKey: ByteArray,
        iv: ByteArray,
        sealed: ByteArray
    ): ByteArray {
        if (sealed.size < TAG_LENGTH) cryptoFailure("Envelope too short")
        val cipher = cipher(forEncryption = false, symKey, iv)
        val out = ByteArray(cipher.getOutputSize(sealed.size))
        return try {
            val written = cipher.processBytes(sealed, 0, sealed.size, out, 0)
            val total = written + cipher.doFinal(out, written)
            out.copyOf(total)
        } catch (error: InvalidCipherTextException) {
            cryptoFailure("Envelope authentication failed", error)
        }
    }

    /** A type 0 envelope: `0x00 || iv || ciphertext+tag`, Base64. */
    fun encodeType0(
        symKey: ByteArray,
        message: String,
        iv: ByteArray = randomBytes(IV_LENGTH)
    ): String {
        val sealed = seal(symKey, iv, message.toByteArray(Charsets.UTF_8))
        return WcEncoding.base64(byteArrayOf(TYPE_0.toByte()) + iv + sealed)
    }

    /**
     * A type 1 envelope: `0x01 || senderPublicKey || iv || ciphertext+tag`, sealed with the key
     * [sender] shares with [receiverPublicKey].
     */
    fun encodeType1(
        sender: WcKeyPair,
        receiverPublicKey: ByteArray,
        message: String,
        iv: ByteArray = randomBytes(IV_LENGTH)
    ): String {
        val symKey = deriveSymKey(sender.privateKey, receiverPublicKey)
        val sealed = seal(symKey, iv, message.toByteArray(Charsets.UTF_8))
        symKey.fill(0)
        return WcEncoding.base64(byteArrayOf(TYPE_1.toByte()) + sender.publicKey + iv + sealed)
    }

    fun parseEnvelope(encoded: String): WcEnvelope {
        val bytes = try {
            WcEncoding.unbase64(encoded)
        } catch (error: IllegalArgumentException) {
            cryptoFailure("Envelope is not Base64", error)
        }
        val type = bytes.firstOrNull()?.toInt() ?: cryptoFailure("Empty envelope")
        // Type 1 carries the sender's public key between the type byte and the nonce.
        val ivStart = when (type) {
            TYPE_0 -> 1
            TYPE_1 -> 1 + KEY_LENGTH
            else -> cryptoFailure("Unsupported envelope type")
        }
        if (bytes.size < ivStart + IV_LENGTH + TAG_LENGTH) cryptoFailure("Envelope too short")
        return WcEnvelope(
            type = type,
            senderPublicKey = bytes.copyOfRange(1, ivStart).takeIf { type == TYPE_1 },
            iv = bytes.copyOfRange(ivStart, ivStart + IV_LENGTH),
            sealed = bytes.copyOfRange(ivStart + IV_LENGTH, bytes.size)
        )
    }

    fun decodeType0(symKey: ByteArray, envelope: WcEnvelope): String {
        if (envelope.type != TYPE_0) cryptoFailure("Not a type 0 envelope")
        return String(open(symKey, envelope.iv, envelope.sealed), Charsets.UTF_8)
    }

    fun decodeType1(receiverPrivateKey: ByteArray, envelope: WcEnvelope): String {
        val senderPublicKey = envelope.senderPublicKey?.takeIf {
            envelope.type == TYPE_1
        } ?: cryptoFailure("Not a type 1 envelope")
        val symKey = deriveSymKey(receiverPrivateKey, senderPublicKey)
        return try {
            String(open(symKey, envelope.iv, envelope.sealed), Charsets.UTF_8)
        } finally {
            symKey.fill(0)
        }
    }

    private fun cipher(
        forEncryption: Boolean,
        symKey: ByteArray,
        iv: ByteArray
    ): ChaCha20Poly1305 {
        if (symKey.size != KEY_LENGTH || iv.size != IV_LENGTH) cryptoFailure("Invalid key or nonce length")
        return ChaCha20Poly1305().apply { init(forEncryption, AEADParameters(KeyParameter(symKey), TAG_BITS, iv)) }
    }
}
