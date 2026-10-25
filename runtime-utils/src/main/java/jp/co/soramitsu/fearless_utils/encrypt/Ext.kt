package jp.co.soramitsu.fearless_utils.encrypt

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

fun ByteArray.hmacSHA256(secret: ByteArray) = hmac(secret, "HmacSHA256")
fun ByteArray.hmacSHA512(secret: ByteArray) = hmac(secret, "HmacSHA512")

private fun ByteArray.hmac(secret: ByteArray, shaAlgorithm: String): ByteArray {
    val chiper: Mac = Mac.getInstance(shaAlgorithm)
    val secretKeySpec = SecretKeySpec(secret, shaAlgorithm)
    chiper.init(secretKeySpec)

    return chiper.doFinal(this)
}
