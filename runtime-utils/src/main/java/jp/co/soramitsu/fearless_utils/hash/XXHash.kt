package jp.co.soramitsu.fearless_utils.hash

import net.jpountz.xxhash.XXHash64
import java.nio.ByteBuffer
import java.nio.ByteOrder

class XXHash(
    hashLengthBits: Int,
    private val xxHash64: XXHash64
) {
    init {
        require(hashLengthBits % 64 == 0)
    }

    val hashLengthBytes = hashLengthBits / 8

    private val timesToRepeat = hashLengthBits / 64

    fun hash(byteArray: ByteArray): ByteArray {
        val buffer = ByteBuffer.allocate(hashLengthBytes)
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        (0 until timesToRepeat).map {
            xxHash64.hash(byteArray, seed = it.toLong())
        }.onEach(buffer::putLong)

        return buffer.array()
    }
}
