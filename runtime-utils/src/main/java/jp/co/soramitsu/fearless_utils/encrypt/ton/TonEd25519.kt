package jp.co.soramitsu.fearless_utils.encrypt.ton

import jp.co.soramitsu.fearless_utils.encrypt.Ed25519Crypto
import org.bouncycastle.crypto.digests.SHA512Digest
import org.bouncycastle.math.ec.rfc7748.X25519
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom
import java.util.zip.CRC32

/**
 * TON's Ed25519 key operations (ton-kotlin's Ed25519 object and PrivateKeyEd25519 class) on BouncyCastle.
 *
 * A TON private key is a 32-byte RFC 8032 seed, so keys and signatures equal the ton-kotlin ones byte for byte.
 * [verify] is a correct strict verifier; ton-kotlin 0.3.1 rejected about half of the valid signatures.
 */
object TonEd25519 {

    const val KEY_SIZE_BYTES = 32

    private const val TL_ID_SIZE = 4
    private const val PRIVATE_KEY_SCHEMA = "pk.ed25519 key:int256 = PrivateKey"
    private const val MONTGOMERY_SIGN_BIT = 255

    // TL constructor id of pk.ed25519: CRC-32 of its schema, stored little-endian before a boxed key.
    private val privateKeyTlId: Int = CRC32().run {
        update(PRIVATE_KEY_SCHEMA.encodeToByteArray())
        value.toInt()
    }

    // BigInteger.TWO needs Android 13; minSdk is 26.
    private val TWO: BigInteger = BigInteger.valueOf(2)
    private val fieldPrime: BigInteger = BigInteger.ONE.shiftLeft(255).subtract(BigInteger.valueOf(19))
    private val edwardsD: BigInteger = BigInteger.valueOf(-121665).mod(fieldPrime)
        .multiply(BigInteger.valueOf(121666).modInverse(fieldPrime))
        .mod(fieldPrime)

    /** A fresh random private key. */
    fun generatePrivateKey(random: SecureRandom = SecureRandom()): ByteArray =
        ByteArray(KEY_SIZE_BYTES).also(random::nextBytes)

    /**
     * ton-kotlin's `PrivateKeyEd25519.of`: a bare 32-byte key, or one boxed with its 4-byte TL constructor id.
     */
    fun privateKeyOf(bytes: ByteArray): ByteArray = when (bytes.size) {
        KEY_SIZE_BYTES -> bytes.copyOf()
        KEY_SIZE_BYTES + TL_ID_SIZE -> {
            val id = ByteBuffer.wrap(bytes, 0, TL_ID_SIZE).order(ByteOrder.LITTLE_ENDIAN).int
            require(id == privateKeyTlId) { "Invalid TL id for pk.ed25519" }
            bytes.copyOfRange(TL_ID_SIZE, bytes.size)
        }
        else -> throw IllegalArgumentException("Invalid key size: ${bytes.size}")
    }

    /** The 32-byte public key of a 32-byte private key. */
    fun publicKey(privateKey: ByteArray): ByteArray = Ed25519Crypto.publicKey(privateKey)

    /** The 64-byte Ed25519 signature of [message]. TON signs the 32-byte hash of a cell. */
    fun sign(privateKey: ByteArray, message: ByteArray): ByteArray = Ed25519Crypto.sign(privateKey, message)

    /** Strict RFC 8032 verification. */
    fun verify(publicKey: ByteArray, message: ByteArray, signature: ByteArray): Boolean =
        Ed25519Crypto.verify(message, signature, publicKey)

    /**
     * ton-kotlin's `Ed25519.sharedKey`: X25519 of the private key's clamped SHA-512 scalar with the Montgomery form
     * of the other side's Ed25519 public key. Used by TON encrypted comments.
     */
    fun sharedKey(privateKey: ByteArray, publicKey: ByteArray): ByteArray {
        require(privateKey.size == KEY_SIZE_BYTES) { "ed25519: bad length: ${privateKey.size}" }
        val theirs = montgomeryU(publicKey.copyOf(KEY_SIZE_BYTES))
        val sha512 = SHA512Digest()
        sha512.update(privateKey, 0, privateKey.size)
        val digest = ByteArray(sha512.digestSize)
        sha512.doFinal(digest, 0)
        val scalar = digest.copyOf(X25519.SCALAR_SIZE)
        try {
            val shared = ByteArray(X25519.POINT_SIZE)
            check(X25519.calculateAgreement(scalar, 0, theirs, 0, shared, 0)) { "bad input point: low order point" }
            return shared
        } finally {
            digest.fill(0)
            scalar.fill(0)
        }
    }

    // u = (1 + y) / (1 - y) for the Edwards y in the key, after the on-curve check curve25519-kotlin makes.
    // Public data only, so plain BigInteger arithmetic is fine here.
    private fun montgomeryU(edwardsPublicKey: ByteArray): ByteArray {
        val y = BigInteger(1, edwardsPublicKey.reversedArray()).clearBit(MONTGOMERY_SIGN_BIT).mod(fieldPrime)
        val ySquared = y.multiply(y).mod(fieldPrime)
        val numerator = ySquared.subtract(BigInteger.ONE).mod(fieldPrime)
        val denominator = edwardsD.multiply(ySquared).add(BigInteger.ONE).mod(fieldPrime)
        val xSquared = numerator.multiply(invert(denominator)).mod(fieldPrime)
        require(xSquared.signum() == 0 || isQuadraticResidue(xSquared)) { "Invalid Y coordinate" }

        val u = BigInteger.ONE.add(y).multiply(invert(BigInteger.ONE.subtract(y).mod(fieldPrime))).mod(fieldPrime)
        return littleEndian(u)
    }

    // Fermat inversion, which like the field code being matched maps 0 to 0.
    private fun invert(value: BigInteger): BigInteger = value.modPow(fieldPrime.subtract(TWO), fieldPrime)

    private fun isQuadraticResidue(value: BigInteger): Boolean =
        value.modPow(fieldPrime.subtract(BigInteger.ONE).shiftRight(1), fieldPrime) == BigInteger.ONE

    private fun littleEndian(value: BigInteger): ByteArray {
        val bigEndian = value.toByteArray()
        val out = ByteArray(X25519.POINT_SIZE)
        for (index in out.indices) {
            val source = bigEndian.size - 1 - index
            if (source >= 0) out[index] = bigEndian[source]
        }
        return out
    }
}
