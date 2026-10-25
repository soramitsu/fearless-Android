package jp.co.soramitsu.fearless_utils.scale

import jp.co.soramitsu.fearless_utils.scale.dataType.DataType
import jp.co.soramitsu.fearless_utils.scale.dataType.optional
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

class NonNullFieldDelegate<S : Schema<S>, T>(
    private val dataType: DataType<T>,
    private val schema: S,
    default: T? = null
) : ReadOnlyProperty<Schema<S>, Field<T>> {

    private var field: Field<T> = schema.field(dataType, default)

    override fun getValue(thisRef: Schema<S>, property: KProperty<*>) = field

    fun optional(): NullableFieldDelegate<S, T> {
        schema.fields.remove(field)

        return NullableFieldDelegate(optional(dataType), schema, field.defaultValue)
    }
}

class NullableFieldDelegate<S : Schema<S>, T>(
    dataType: optional<T>,
    schema: S,
    default: T? = null
) :
    ReadOnlyProperty<Schema<S>, Field<T?>> {

    private var field: Field<T?> = schema.nullableField(dataType, default)

    override fun getValue(thisRef: Schema<S>, property: KProperty<*>) = field
}
