package jp.co.soramitsu.tonconnect.api.model

import java.nio.ByteBuffer
import java.nio.ByteOrder

@Suppress("MagicNumber")
fun Long.toByteArray(): ByteArray {
    return ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(this).array()
}

@Suppress("MagicNumber")
fun Int.toByteArray(): ByteArray {
    return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(this).array()
}

@Suppress("MagicNumber")
enum class BridgeError(val code: Int, val message: String) {
    UNKNOWN(0, "Unknown error"),
    BAD_REQUEST(1, "Bad request"),
    UNKNOWN_APP(100, "Unknown app"),
    USER_DECLINED_TRANSACTION(300, "User declined the transaction"),
    METHOD_NOT_SUPPORTED(400, "Method not supported");

    class Exception(val error: BridgeError) : Throwable(error.message)
}
