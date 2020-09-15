package jp.co.soramitsu.common.data.network.scale

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

        return if (value == null) {
            if (field.dataType is optional<*>) {
                null as T
            } else {
                throw IllegalArgumentException("Non nullable value is not set")
            }
        } else {
            value as T
        }
    }
}