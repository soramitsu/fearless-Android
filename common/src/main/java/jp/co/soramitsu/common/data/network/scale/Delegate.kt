package jp.co.soramitsu.common.data.network.scale

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

class NonNullFieldDelegate<S : Schema<S>, T>(
    private val dataType: DataType<T>,
    private val schema: S
) : ReadOnlyProperty<Schema<S>, Field<T>> {

    private var field: Field<T> = schema.field(dataType)

    override fun getValue(thisRef: Schema<S>, property: KProperty<*>) = field

    fun optional(): NullableFieldDelegate<S, T> {
        schema.fields.remove(field)

        return NullableFieldDelegate(optional(dataType), schema)
    }
}

class NullableFieldDelegate<S : Schema<S>, T>(private val dataType: optional<T>, schema: S) :
    ReadOnlyProperty<Schema<S>, Field<T?>> {

    private var field: Field<T?> = schema.nullableField(dataType)

    override fun getValue(thisRef: Schema<S>, property: KProperty<*>) = field
}