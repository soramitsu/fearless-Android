package jp.co.soramitsu.common.scale

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

class NonNullFieldDelegate<S : Schema<S>, T>(private val dataType: DataType<T>) :
    ReadOnlyProperty<Schema<S>, Field<T>> {
    private var field: Field<T>? = null

    override fun getValue(thisRef: Schema<S>, property: KProperty<*>): Field<T> {
        if (field == null) {
            field = thisRef.field(dataType)
        }

        return field!!
    }

    fun optional() = NullableFieldDelegate<S, T>(optional(dataType))
}

class NullableFieldDelegate<S : Schema<S>, T>(private val dataType: optional<T>) :
    ReadOnlyProperty<Schema<S>, Field<T?>> {
    private var field: Field<T?>? = null

    override fun getValue(thisRef: Schema<S>, property: KProperty<*>): Field<T?> {
        if (field == null) {
            field = thisRef.nullableField(dataType)
        }

        return field!!
    }
}