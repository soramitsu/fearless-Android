package jp.co.soramitsu.common.data.network.scale

import jp.co.soramitsu.common.data.network.scale.dataType.DataType
import jp.co.soramitsu.common.data.network.scale.dataType.optional

class Field<T>(val dataType: DataType<T>, val defaultValue: T? = null)

@Suppress("UNCHECKED_CAST", "unused")
class EncodableStruct<S : Schema<S>>(val schema: Schema<S>) {
    internal val fieldsWithValues: MutableMap<Field<*>, Any?> = mutableMapOf()

    private val fields = schema.fields

    init {
        setDefaultValues()
    }

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

    private fun setDefaultValues() {
        fields.filter { it.defaultValue != null }
            .forEach { fieldsWithValues[it] = it.defaultValue }
    }
}