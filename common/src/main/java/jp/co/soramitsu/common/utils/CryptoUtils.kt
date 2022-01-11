package jp.co.soramitsu.common.utils

import android.util.Base64
import jp.co.soramitsu.fearless_utils.encrypt.keypair.ECDSAUtils
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.hash.Hasher.blake2b256
import org.bouncycastle.jcajce.provider.digest.Keccak
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

fun String.hmacSHA256(secret: String): ByteArray {
    val chiper: Mac = Mac.getInstance("HmacSHA256")
    val secretKeySpec = SecretKeySpec(secret.toByteArray(), "HmacSHA256")
    chiper.init(secretKeySpec)

    return chiper.doFinal(this.toByteArray())
}

fun ByteArray.ethereumAddressFromPublicKey(): ByteArray {
    val decompressed = if (size == 64) {
        this
    } else {
        ECDSAUtils.decompressed(this)
    }

    return decompressed.keccak256().copyLast(20)
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

fun ByteArray.keccak256(): ByteArray {
    val digest = Keccak.Digest256()

    return digest.digest(this)
}

fun String.md5(): String {
    val hasher = MessageDigest.getInstance("MD5")

    return hasher.digest(encodeToByteArray()).decodeToString()
}

fun ByteArray.toBase64() = Base64.encodeToString(this, Base64.NO_WRAP)
