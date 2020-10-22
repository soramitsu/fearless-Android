package jp.co.soramitsu.common.data.network.scale.dataType

import io.emeraldpay.polkaj.scale.ScaleCodecReader
import io.emeraldpay.polkaj.scale.ScaleCodecWriter
import io.emeraldpay.polkaj.scale.writer.BoolWriter

object string : DataType<String>() {
    override fun read(reader: ScaleCodecReader): String {
        return reader.readString()
    }
    override fun write(writer: ScaleCodecWriter, value: String) = writer.writeString(value)
}

object boolean : DataType<Boolean>() {
    override fun read(reader: ScaleCodecReader): Boolean {
        return reader.readBoolean()
    }

    override fun write(writer: ScaleCodecWriter, value: Boolean) = writer.write(BoolWriter(), value)
}

object byteArray : DataType<ByteArray>() {
    override fun read(reader: ScaleCodecReader): ByteArray {
        val readByteArray = reader.readByteArray()
        return readByteArray
    }

    override fun write(writer: ScaleCodecWriter, value: ByteArray) {
        writer.writeByteArray(value)
    }
}

class byteArraySized(private val length: Int) : DataType<ByteArray>() {
    override fun read(reader: ScaleCodecReader): ByteArray {
        val readByteArray = reader.readByteArray(length)
        return readByteArray
    }

    override fun write(writer: ScaleCodecWriter, value: ByteArray) = writer.directWrite(value, 0, length)
}