package jp.co.soramitsu.common.scale

import io.emeraldpay.polkaj.scale.ScaleCodecReader
import io.emeraldpay.polkaj.scale.ScaleCodecWriter
import io.emeraldpay.polkaj.scale.ScaleReader
import io.emeraldpay.polkaj.scale.ScaleWriter

@Suppress("UNCHECKED_CAST")
abstract class Schema<S : Schema<S>> : ScaleReader<EncodableStruct<S>>,
    ScaleWriter<EncodableStruct<S>> {
    companion object;

    internal val fields: MutableList<Field<*>> = mutableListOf()

    fun <T> field(dataType: DataType<T>): Field<T> {
        val field = Field(dataType)

        fields += field

        return field
    }

    fun <T> nullableField(dataType: optional<T>): Field<T?> {
        val field = Field(dataType)

        fields += field

        return field
    }

    override fun read(reader: ScaleCodecReader): EncodableStruct<S> {
        val struct = EncodableStruct(this)

        for (field in fields) {
            struct[field as Field<Any?>] = field.dataType.read(reader)
        }

        return struct
    }

    override fun write(writer: ScaleCodecWriter, struct: EncodableStruct<S>) {
        for (field in fields) {
            val value = struct.fieldsWithValues[field]

            val type = field.dataType as DataType<Any?>

            type.write(writer, value)
        }
    }
}