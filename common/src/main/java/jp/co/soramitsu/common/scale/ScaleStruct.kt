package jp.co.soramitsu.common.scale


class Field<T>(val dataType: DataType<T>)

@Suppress("UNCHECKED_CAST", "unused")
class EncodableStruct<S : Schema<S>>(schema: Schema<S>) {
    internal val fieldsWithValues: MutableMap<Field<*>, Any?> = mutableMapOf()

    val fields = schema.fields

    operator fun <T> set(field: Field<T>, value: T) {
        fieldsWithValues[field] = value as Any?
    }

    operator fun <T> get(field: Field<T>): T {
        val value = fieldsWithValues[field]

        if (value == null) {
            if (field.dataType is optional<*>) {
                return null as T
            } else {
                throw IllegalArgumentException("123")
            }
        } else {
            return value as T
        }
    }
}

