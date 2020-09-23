@file:Suppress("EXPERIMENTAL_API_USAGE")

package jp.co.soramitsu.common.data.network.scale

import io.emeraldpay.polkaj.scale.ScaleCodecReader
import io.emeraldpay.polkaj.scale.ScaleCodecWriter
import io.emeraldpay.polkaj.scale.ScaleReader
import io.emeraldpay.polkaj.scale.ScaleWriter
import io.emeraldpay.polkaj.scale.writer.BoolWriter
import java.math.BigInteger

sealed class DataType<T> : ScaleReader<T>, ScaleWriter<T>

object string : DataType<String>() {
    override fun read(reader: ScaleCodecReader) = reader.readString()
    override fun write(writer: ScaleCodecWriter, value: String) = writer.writeString(value)
}

object uint32 : DataType<UInt>() {
    override fun read(reader: ScaleCodecReader): UInt {
        return reader.readUint32().toUInt()
    }

    override fun write(writer: ScaleCodecWriter, value: UInt) = writer.writeUint32(value.toLong())
}

object boolean : DataType<Boolean>() {
    override fun read(reader: ScaleCodecReader) = reader.readBoolean()

    override fun write(writer: ScaleCodecWriter, value: Boolean) = writer.write(BoolWriter(), value)
}

object byte : DataType<Byte>() {
    override fun read(reader: ScaleCodecReader) = reader.readByte()

    override fun write(writer: ScaleCodecWriter, value: Byte) = writer.writeByte(value)
}

object uint8 : DataType<UByte>() {
    override fun read(reader: ScaleCodecReader): UByte {
        return reader.readUByte().toUByte()
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

object compactInt : DataType<Int>() {
    override fun read(reader: ScaleCodecReader) = reader.readCompactInt()

    override fun write(writer: ScaleCodecWriter, value: Int) = writer.writeCompact(value)
}

object byteArray : DataType<ByteArray>() {
    override fun read(reader: ScaleCodecReader) = reader.readByteArray()

    override fun write(writer: ScaleCodecWriter, value: ByteArray) = writer.writeByteArray(value)
}

class byteArraySized(private val length: Int) : DataType<ByteArray>() {
    override fun read(reader: ScaleCodecReader) = reader.readByteArray(length)

    override fun write(writer: ScaleCodecWriter, value: ByteArray) = writer.writeByteArray(value)
}

object long : DataType<Long>() {
    override fun read(reader: ScaleCodecReader) = reader.readLong()

    override fun write(writer: ScaleCodecWriter, value: Long) = writer.writeLong(value)
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
        val struct = EncodableStruct(schema)

        for (field in schema.fields) {
            struct[field as Field<Any?>] = field.dataType.read(reader)
        }

        return struct
    }

    override fun write(writer: ScaleCodecWriter, struct: EncodableStruct<S>) {
        for (field in schema.fields) {
            val value = struct.fieldsWithValues[field]

            val type = field.dataType as DataType<Any?>

            type.write(writer, value)
        }
    }
}