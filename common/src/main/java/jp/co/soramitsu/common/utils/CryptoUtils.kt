package jp.co.soramitsu.common.utils

import android.util.Base64
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.hash.Hasher.blake2b256
import org.bouncycastle.jcajce.provider.digest.SHA3
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

fun String.hmacSHA256(secret: String): ByteArray {
    val chiper: Mac = Mac.getInstance("HmacSHA256")
    val secretKeySpec = SecretKeySpec(secret.toByteArray(), "HmacSHA256")
    chiper.init(secretKeySpec)

    return chiper.doFinal(this.toByteArray())
}

fun ByteArray.ethereumAddress(): String {
    return copyOf(newSize = 20).sha3().toHexString(withPrefix = true)
}

fun ByteArray.substrateAccountId(): ByteArray {
    return if (size > 32) {
        this.blake2b256()
    } else {
        this
    }
}

fun String.sha3(): ByteArray = encodeToByteArray().sha3()

fun ByteArray.sha3(): ByteArray {
    val digest = SHA3.Digest256()

    return digest.digest(this)
}

fun String.md5(): String {
    val hasher = MessageDigest.getInstance("MD5")

    return hasher.digest(encodeToByteArray()).decodeToString()
}

fun ByteArray.toBase64() = Base64.encodeToString(this, Base64.NO_WRAP)
