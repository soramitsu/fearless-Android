package jp.co.soramitsu.common.utils

import android.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

fun String.hmacSHA256(secret: String): ByteArray {
    val chiper: Mac = Mac.getInstance("HmacSHA256")
    val secretKeySpec = SecretKeySpec(secret.toByteArray(), "HmacSHA256")
    chiper.init(secretKeySpec)

    return chiper.doFinal(this.toByteArray())
}

fun ByteArray.toBase64() = Base64.encodeToString(this, Base64.NO_WRAP)
