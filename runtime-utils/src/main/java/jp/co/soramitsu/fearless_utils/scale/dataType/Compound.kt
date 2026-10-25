@file:Suppress("ClassName", "UNCHECKED_CAST")

package jp.co.soramitsu.fearless_utils.scale.dataType

import io.emeraldpay.polkaj.scale.ScaleCodecReader
import io.emeraldpay.polkaj.scale.ScaleCodecWriter
import io.emeraldpay.polkaj.scale.writer.BoolWriter
import jp.co.soramitsu.fearless_utils.scale.EncodableStruct
import jp.co.soramitsu.fearless_utils.scale.Schema

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

    override fun conformsType(value: Any?) =
        value is Pair<*, *> && a.conformsType(value.first) && b.conformsType(value.second)
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

    override fun conformsType(value: Any?) = value == null || dataType.conformsType(value)
}

class list<T>(private val dataType: DataType<T>) : DataType<List<T>>() {
    override fun read(reader: ScaleCodecReader): List<T> {
        val size = compactInt.read(reader)
        val result = mutableListOf<T>()

        repeat(size.toInt()) {
            val element = dataType.read(reader)
            result.add(element)
        }

        return result
    }

    override fun write(writer: ScaleCodecWriter, value: List<T>) {
        val size = value.size.toBigInteger()
        compactInt.write(writer, size)

        value.forEach {
            dataType.write(writer, it)
        }
    }

    override fun conformsType(value: Any?): Boolean {
        return value is List<*> && value.all(dataType::conformsType)
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

    override fun conformsType(value: Any?): Boolean {
        return value is EncodableStruct<*> && value.schema == schema
    }
}

class EnumType<E : Enum<E>>(private val enumClass: Class<E>) : DataType<E>() {
    override fun conformsType(value: Any?): Boolean {
        return value?.javaClass == enumClass
    }

    override fun read(reader: ScaleCodecReader): E {
        val index = reader.readByte()

        return enumClass.enumConstants[index.toInt()]
    }

    override fun write(writer: ScaleCodecWriter, value: E) {
        writer.writeByte(value.ordinal)
    }
}

class CollectionEnumType(
    val values: List<String>
) : DataType<String>() {

    override fun conformsType(value: Any?): Boolean {
        return value is String
    }

    override fun read(reader: ScaleCodecReader): String {
        val index = reader.readByte()

        return values[index.toInt()]
    }

    override fun write(writer: ScaleCodecWriter, value: String) {
        val index = values.indexOf(value)

        if (index == -1) {
            throw java.lang.IllegalArgumentException("No $value in $values")
        }

        writer.writeByte(index)
    }
}

class union(val dataTypes: Array<out DataType<*>>) : DataType<Any?>() {
    override fun read(reader: ScaleCodecReader): Any? {
        val typeIndex = reader.readByte()
        val type = dataTypes[typeIndex.toInt()]

        return type.read(reader)
    }

    override fun write(writer: ScaleCodecWriter, value: Any?) {
        val typeIndex = dataTypes.indexOfFirst { it.conformsType(value) }

        if (typeIndex == -1) {
            throw java.lang.IllegalArgumentException(
                "Unknown type ${value?.javaClass} for this enum. Supported: ${
                dataTypes.joinToString(
                    ", "
                )
                }"
            )
        }

        val type = dataTypes[typeIndex] as DataType<Any?>

        writer.write(uint8, typeIndex.toUByte())
        writer.write(type, value)
    }

    override fun conformsType(value: Any?): Boolean {
        return dataTypes.any { it.conformsType(value) }
    }
}
