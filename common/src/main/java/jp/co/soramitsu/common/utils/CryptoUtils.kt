package jp.co.soramitsu.common.utils

import android.util.Base64
import jp.co.soramitsu.fearless_utils.encrypt.keypair.ECDSAUtils
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.hash.Hasher.blake2b256
import org.bouncycastle.jcajce.provider.digest.Keccak
import java.security.MessageDigest

fun ByteArray.ethereumAddressFromPublicKey(): ByteArray {
    val decompressed = if (size == 64) {
        this
    } else {
        ECDSAUtils.decompressed(this)
    }

    return decompressed.keccak256().copyLast(20)
}

/**
 * Validates the complete SEC1 compressed-point encoding, not only its length
 * and prefix. SQLite does not constrain wallet public-key BLOB contents, and
 * secp256k1 decoding rejects some shape-valid x coordinates.
 *
 * Only malformed point encodings are converted to `false`. Provider/linkage
 * failures still escape so migration callers continue treating them as global
 * cryptography outages instead of record-local recovery.
 */
fun ByteArray.isValidEthereumCompressedPublicKey(): Boolean {
    if (
        size != COMPRESSED_SECP256K1_PUBLIC_KEY_BYTES ||
        (this[0] != COMPRESSED_EVEN_PREFIX &&
            this[0] != COMPRESSED_ODD_PREFIX)
    ) {
        return false
    }

    return try {
        ECDSAUtils.decompressed(this).size == DECOMPRESSED_SECP256K1_PUBLIC_KEY_BYTES
    } catch (_: IllegalArgumentException) {
        false
    }
}

fun ByteArray.ethereumAddressToHex() = toHexString(withPrefix = true)

fun ByteArray.substrateAccountId(): ByteArray {
    return if (size > 32) {
        this.blake2b256()
    } else {
        this
    }
}

fun ByteArray.copyLast(n: Int) = copyOfRange(fromIndex = size - n, size)

private const val COMPRESSED_SECP256K1_PUBLIC_KEY_BYTES = 33
private const val DECOMPRESSED_SECP256K1_PUBLIC_KEY_BYTES = 64
private const val COMPRESSED_EVEN_PREFIX: Byte = 0x02
private const val COMPRESSED_ODD_PREFIX: Byte = 0x03

fun ByteArray.keccak256(): ByteArray {
    val digest = Keccak.Digest256()

    return digest.digest(this)
}

fun String.md5(): String {
    val hasher = MessageDigest.getInstance("MD5")

    return hasher.digest(encodeToByteArray()).decodeToString()
}

fun ByteArray.toBase64() = Base64.encodeToString(this, Base64.NO_WRAP)
