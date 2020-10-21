package jp.co.soramitsu.common.data.network.scale

import io.emeraldpay.polkaj.scale.ScaleCodecReader
import io.emeraldpay.polkaj.scale.ScaleCodecWriter
import io.emeraldpay.polkaj.scale.ScaleReader
import io.emeraldpay.polkaj.scale.ScaleWriter
import jp.co.soramitsu.common.data.network.scale.dataType.DataType
import jp.co.soramitsu.common.data.network.scale.dataType.optional
import jp.co.soramitsu.common.utils.requirePrefix
import org.bouncycastle.util.encoders.Hex
import java.io.ByteArrayOutputStream
import java.lang.IndexOutOfBoundsException

@Suppress("UNCHECKED_CAST")
abstract class Schema<S : Schema<S>> : ScaleReader<EncodableStruct<S>>,
    ScaleWriter<EncodableStruct<S>> {
    companion object;

    internal val fields: MutableList<Field<*>> = mutableListOf()

    fun <T> field(dataType: DataType<T>, default: T?): Field<T> {
        val field = Field(dataType, default)

        fields += field

        return field
    }

    fun <T> nullableField(dataType: optional<T>, default: T?): Field<T?> {
        val field = Field(dataType, default)

        fields += field

        return field
    }

    fun readOrNull(source: String): EncodableStruct<S>? {
        return try {
            read(source)
        } catch (e: IndexOutOfBoundsException) {
            return null
        }
    }

    fun read(source: String): EncodableStruct<S> {
        val withoutPrefix = source.removePrefix("0x")

        val bytes = Hex.decode(withoutPrefix)

        val reader = ScaleCodecReader(bytes)

        return read(reader)
    }

    override fun read(reader: ScaleCodecReader): EncodableStruct<S> {
        val struct = EncodableStruct(this)

        for (field in fields) {
            val value = field.dataType.read(reader)
            struct[field as Field<Any?>] = value
        }

        return struct
    }

    fun toByteArray(struct: EncodableStruct<S>): ByteArray {
        val outputStream = ByteArrayOutputStream()

        val writer = ScaleCodecWriter(outputStream)

        write(writer, struct)

        return outputStream.toByteArray()
    }

    fun toHexString(struct: EncodableStruct<S>): String = Hex.toHexString(toByteArray(struct)).requirePrefix("0x")

    override fun write(writer: ScaleCodecWriter, struct: EncodableStruct<S>) {
        for (field in fields) {
            val value = struct.fieldsWithValues[field]

            val type = field.dataType as DataType<Any?>

            type.write(writer, value)
        }
    }
}