@file:Suppress("EXPERIMENTAL_API_USAGE")

package jp.co.soramitsu.common.data.network.scale

import io.emeraldpay.polkaj.scale.ScaleCodecReader
import io.emeraldpay.polkaj.scale.ScaleCodecWriter
import io.emeraldpay.polkaj.scale.ScaleReader
import io.emeraldpay.polkaj.scale.ScaleWriter
import io.emeraldpay.polkaj.scale.reader.CompactBigIntReader
import io.emeraldpay.polkaj.scale.writer.BoolWriter
import io.emeraldpay.polkaj.scale.writer.CompactBigIntWriter
import java.math.BigInteger

sealed class DataType<T> : ScaleReader<T>, ScaleWriter<T>

object string : DataType<String>() {
    override fun read(reader: ScaleCodecReader): String? {
        return reader.readString()
    }
    override fun write(writer: ScaleCodecWriter, value: String) = writer.writeString(value)
}

object uint32 : DataType<UInt>() {
    override fun read(reader: ScaleCodecReader): UInt {
        return reader.readUint32().toUInt()
    }

    override fun write(writer: ScaleCodecWriter, value: UInt) = writer.writeUint32(value.toLong())
}

object boolean : DataType<Boolean>() {
    override fun read(reader: ScaleCodecReader): Boolean {
        return reader.readBoolean()
    }

    override fun write(writer: ScaleCodecWriter, value: Boolean) = writer.write(BoolWriter(), value)
}

object byte : DataType<Byte>() {
    override fun read(reader: ScaleCodecReader): Byte {
        val readByte = reader.readByte()
        return readByte
    }

    override fun write(writer: ScaleCodecWriter, value: Byte) = writer.writeByte(value)
}

object uint8 : DataType<UByte>() {
    override fun read(reader: ScaleCodecReader): UByte {
        val toUByte = reader.readUByte().toUByte()
        return toUByte
    }

    override fun write(writer: ScaleCodecWriter, value: UByte) = writer.writeByte(value.toInt())
}

object uint128 : DataType<BigInteger>() {
    override fun read(reader: ScaleCodecReader): BigInteger {
        val bytes = reader.readByteArray(16)

        return BigInteger(bytes.reversedArray())
    }

    override fun write(writer: ScaleCodecWriter, value: BigInteger) {
        val array = value.toByteArray()
        val padded = ByteArray(16)

        val startAt = padded.size - array.size

        array.copyInto(padded, startAt)

        writer.directWrite(padded.reversedArray(), 0, 16)
    }
}

class tuple<A, B>(
    private val a: DataType<A>,
    private val b: DataType<B>
) : DataType<Pair<A, B>>() {
    override fun read(reader: ScaleCodecReader): Pair<A, B> {
        val a = a.read(reader)
        val b = b.read(reader)

        return a to b
    }

    override fun write(writer: ScaleCodecWriter, value: Pair<A, B>) {
        a.write(writer, value.first)
        b.write(writer, value.second)
    }
}

private val compactIntReader = CompactBigIntReader()
private val compactIntWriter = CompactBigIntWriter()

object compactInt : DataType<BigInteger>() {
    override fun read(reader: ScaleCodecReader): BigInteger? {
        val read = compactIntReader.read(reader)
        return read
    }

    override fun write(writer: ScaleCodecWriter, value: BigInteger) = compactIntWriter.write(writer, value)
}

object byteArray : DataType<ByteArray>() {
    override fun read(reader: ScaleCodecReader): ByteArray? {
        val readByteArray = reader.readByteArray()
        return readByteArray
    }

    override fun write(writer: ScaleCodecWriter, value: ByteArray) {
        writer.writeByteArray(value)
    }
}

class byteArraySized(private val length: Int) : DataType<ByteArray>() {
    override fun read(reader: ScaleCodecReader): ByteArray? {
        val readByteArray = reader.readByteArray(length)
        return readByteArray
    }

    override fun write(writer: ScaleCodecWriter, value: ByteArray) = writer.directWrite(value, 0, length)
}

object long : DataType<Long>() {
    override fun read(reader: ScaleCodecReader) = reader.readLong()

    override fun write(writer: ScaleCodecWriter, value: Long) {
        writer.writeLong(value)
    }
}

@Suppress("UNCHECKED_CAST")
class optional<T>(private val dataType: DataType<T>) : DataType<T?>() {
    override fun read(reader: ScaleCodecReader): T? {
        if (dataType is boolean) {
            return when (reader.readByte().toInt()) {
                0 -> null
                1 -> false as T?
                2 -> true as T?
                else -> throw IllegalArgumentException("Not a optional boolean")
            }
        }

        val some: Boolean = reader.readBoolean()

        return if (some) dataType.read(reader) else null
    }

    override fun write(writer: ScaleCodecWriter, value: T?) {
        if (dataType is boolean) {
            writer.writeOptional(BoolWriter(), value as Boolean)
        } else {
            writer.writeOptional(dataType, value)
        }
    }
}

@Suppress("UNCHECKED_CAST")
class scalable<S : Schema<S>>(private val schema: Schema<S>) : DataType<EncodableStruct<S>>() {
    override fun read(reader: ScaleCodecReader): EncodableStruct<S> {
        return schema.read(reader)
    }

    override fun write(writer: ScaleCodecWriter, struct: EncodableStruct<S>) {
        schema.write(writer, struct)
    }
}