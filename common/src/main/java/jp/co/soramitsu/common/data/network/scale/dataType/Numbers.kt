@file:Suppress("EXPERIMENTAL_API_USAGE")

package jp.co.soramitsu.common.data.network.scale.dataType

import io.emeraldpay.polkaj.scale.ScaleCodecReader
import io.emeraldpay.polkaj.scale.ScaleCodecWriter
import io.emeraldpay.polkaj.scale.reader.CompactBigIntReader
import jp.co.soramitsu.common.data.network.scale.utils.CompactBigIntWriter
import java.math.BigInteger

object byte : DataType<Byte>() {
    override fun read(reader: ScaleCodecReader): Byte {
        val readByte = reader.readByte()
        return readByte
    }

    override fun write(writer: ScaleCodecWriter, value: Byte) = writer.writeByte(value)

    override fun conformsType(value: Any?) = value is Byte
}

object uint8 : DataType<UByte>() {
    override fun read(reader: ScaleCodecReader): UByte {
        val toUByte = reader.readUByte().toUByte()
        return toUByte
    }

    override fun write(writer: ScaleCodecWriter, value: UByte) = writer.writeByte(value.toInt())

    override fun conformsType(value: Any?) = value is UByte
}

object uint16 : DataType<Int>() {
    override fun read(reader: ScaleCodecReader): Int {
        return reader.readUint16()
    }

    override fun write(writer: ScaleCodecWriter, value: Int) = writer.writeUint16(value)

    override fun conformsType(value: Any?) = value is Int
}

object uint32 : DataType<UInt>() {
    override fun read(reader: ScaleCodecReader): UInt {
        return reader.readUint32().toUInt()
    }

    override fun write(writer: ScaleCodecWriter, value: UInt) = writer.writeUint32(value.toLong())

    override fun conformsType(value: Any?) = value is UInt
}

object long : DataType<Long>() {
    override fun read(reader: ScaleCodecReader) = reader.readLong()

    override fun write(writer: ScaleCodecWriter, value: Long) {
        writer.writeLong(value)
    }

    override fun conformsType(value: Any?) = value is Long
}

open class uint(val size: Int) : DataType<BigInteger>() {
    override fun read(reader: ScaleCodecReader): BigInteger {
        val bytes = reader.readByteArray(size)

        return BigInteger(bytes.reversedArray())
    }

    override fun write(writer: ScaleCodecWriter, value: BigInteger) {
        val array = value.toByteArray()
        val padded = ByteArray(size)

        val startAt = padded.size - array.size

        array.copyInto(padded, startAt)

        writer.directWrite(padded.reversedArray(), 0, size)
    }

    override fun conformsType(value: Any?) = value is BigInteger
}

object uint128 : uint(16)

object uint64 : uint(8)

private val compactIntReader = CompactBigIntReader()
private val compactIntWriter = CompactBigIntWriter()

object compactInt : DataType<BigInteger>() {
    override fun read(reader: ScaleCodecReader): BigInteger {
        val read = compactIntReader.read(reader)
        return read
    }

    override fun write(writer: ScaleCodecWriter, value: BigInteger) {
        compactIntWriter.write(writer, value)
    }

    override fun conformsType(value: Any?) = value is BigInteger
}