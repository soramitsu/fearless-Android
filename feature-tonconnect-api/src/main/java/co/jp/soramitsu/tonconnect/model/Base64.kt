package co.jp.soramitsu.tonconnect.model

import android.util.Base64
import io.ktor.util.decodeBase64Bytes

val String.base64: ByteArray
    get() = Base64.decode(this, Base64.DEFAULT)

fun String.fixedBase64(): String {
    return this.trim().replace(" ", "+")
}

fun String.base64(): ByteArray {
    return fixedBase64().decodeBase64Bytes()
}