@file:Suppress("unused")

package jp.co.soramitsu.common.data.network.scale

typealias StructBuilder<SCHEMA> = (EncodableStruct<SCHEMA>) -> Unit

operator fun <S : Schema<S>> S.invoke(block: StructBuilder<S>? = null): EncodableStruct<S> {
    val struct = EncodableStruct(this)

    block?.invoke(struct)

    return struct
}

fun <S : Schema<S>> S.string() = NonNullFieldDelegate<S, String>(string)

@ExperimentalUnsignedTypes
fun <S : Schema<S>> S.uint32() = NonNullFieldDelegate<S, UInt>(uint32)

fun <S: Schema<S>, T : Schema<T>> S.struct(schema: T) =
    NonNullFieldDelegate<S, EncodableStruct<T>>(scalable(schema))

fun <S : Schema<S>> S.byte() = NonNullFieldDelegate<S, Byte>(byte)

fun <S : Schema<S>> S.compactInt() = NonNullFieldDelegate<S, Int>(compactInt)

fun <S : Schema<S>> S.byteArray(length: Int? = null) : NonNullFieldDelegate<S, ByteArray> {
    val type = if (length != null) byteArraySized(length) else byteArray

    return NonNullFieldDelegate(type)
}

fun <S : Schema<S>> S.long() = NonNullFieldDelegate<S, Long>(long)

