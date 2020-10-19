@file:Suppress("unused", "EXPERIMENTAL_API_USAGE")

package jp.co.soramitsu.common.data.network.scale

import java.math.BigInteger

typealias StructBuilder<SCHEMA> = (EncodableStruct<SCHEMA>) -> Unit

operator fun <S : Schema<S>> S.invoke(block: StructBuilder<S>? = null): EncodableStruct<S> {
    val struct = EncodableStruct(this)

    block?.invoke(struct)

    return struct
}

fun <S : Schema<S>> S.string(default: String? = null) = NonNullFieldDelegate(string, this, default)

fun <S : Schema<S>> S.uint8(default: UByte? = null) = NonNullFieldDelegate(uint8, this, default)

fun <S : Schema<S>> S.uint32(default: UInt? = null) = NonNullFieldDelegate(uint32, this, default)

fun <S : Schema<S>> S.uint128(default: BigInteger? = null) = NonNullFieldDelegate(uint128, this, default)

fun <S : Schema<S>, T : Schema<T>> S.schema(schema: T, default: EncodableStruct<T>? = null) =
    NonNullFieldDelegate(scalable(schema), this, default)

fun <S : Schema<S>> S.byte(default: Byte? = null) = NonNullFieldDelegate(byte, this, default)

fun <S : Schema<S>> S.compactInt(default: BigInteger? = null) = NonNullFieldDelegate(compactInt, this, default)

fun <S : Schema<S>> S.sizedByteArray(length: Int, default: ByteArray? = null): NonNullFieldDelegate<S, ByteArray> {
    if (default != null) {
        require(length == default.size)
    }

    return NonNullFieldDelegate(byteArraySized(length), this, default)
}

fun <S : Schema<S>, A, B> S.pair(
    first: DataType<A>,
    second: DataType<B>,
    default: Pair<A, B>? = null
) = NonNullFieldDelegate(tuple(first, second), this, default)

fun <S : Schema<S>> S.byteArray(default: ByteArray? = null): NonNullFieldDelegate<S, ByteArray> {
    return NonNullFieldDelegate(byteArray, this, default)
}

fun <S : Schema<S>> S.long(default: Long? = null) = NonNullFieldDelegate(long, this, default)