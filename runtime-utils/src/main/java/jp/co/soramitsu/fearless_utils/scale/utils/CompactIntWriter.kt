package jp.co.soramitsu.fearless_utils.scale.utils

import io.emeraldpay.polkaj.scale.CompactMode
import io.emeraldpay.polkaj.scale.ScaleCodecWriter
import io.emeraldpay.polkaj.scale.ScaleWriter
import io.emeraldpay.polkaj.scale.writer.CompactULongWriter
import java.io.IOException
import java.math.BigInteger

private val LONG_WRITER = CompactULongWriter()

fun BigInteger.toUnsignedBytes(): ByteArray {
    var bytes = toByteArray()

    if (bytes.first() == 0.toByte() && bytes.size > 1) {
        bytes = bytes.drop(1).toByteArray()
    }

    return bytes
}

class CompactBigIntWriter : ScaleWriter<BigInteger> {
    @Throws(IOException::class)
    override fun write(wrt: ScaleCodecWriter, value: BigInteger) {
        val mode = CompactMode.forNumber(value)
        val data = value.toUnsignedBytes()
        var pos = data.size - 1
        if (mode != CompactMode.BIGINT) {
            LONG_WRITER.write(wrt, value.toLong())
        } else {
            wrt.directWrite((data.size - 4 shl 2) + mode.value)
            while (pos >= 0) {
                wrt.directWrite(data[pos].toInt())
                --pos
            }
        }
    }
}
