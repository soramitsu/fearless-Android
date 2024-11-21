package co.jp.soramitsu.tonconnect.model

import java.nio.ByteBuffer
import java.nio.ByteOrder

fun Long.toByteArray(): ByteArray {
    return ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(this).array()
}

fun Int.toByteArray(): ByteArray {
    return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(this).array()
}
