@file:Suppress("unused", "EXPERIMENTAL_API_USAGE")

package jp.co.soramitsu.common.data.network.scale

typealias StructBuilder<SCHEMA> = (EncodableStruct<SCHEMA>) -> Unit

operator fun <S : Schema<S>> S.invoke(block: StructBuilder<S>? = null): EncodableStruct<S> {
    val struct = EncodableStruct(this)

    block?.invoke(struct)

    return struct
}

fun <S : Schema<S>> S.string() = NonNullFieldDelegate(string, this)

fun <S : Schema<S>> S.uint8() = NonNullFieldDelegate(uint8, this)

fun <S : Schema<S>> S.uint32() = NonNullFieldDelegate(uint32, this)

fun <S : Schema<S>> S.uint128() = NonNullFieldDelegate(uint128, this)

fun <S : Schema<S>, T : Schema<T>> S.schema(schema: T) =
    NonNullFieldDelegate(scalable(schema), this)

fun <S : Schema<S>> S.byte() = NonNullFieldDelegate(byte, this)

fun <S : Schema<S>> S.compactInt() = NonNullFieldDelegate(compactInt, this)

fun <S : Schema<S>> S.byteArray(length: Int? = null): NonNullFieldDelegate<S, ByteArray> {
    val type = if (length != null) byteArraySized(length) else byteArray

    return NonNullFieldDelegate(type, this)
}

fun <S : Schema<S>> S.long() = NonNullFieldDelegate(long, this)