@file:Suppress("unused", "EXPERIMENTAL_API_USAGE")

package jp.co.soramitsu.common.data.network.scale

import jp.co.soramitsu.common.data.network.scale.dataType.DataType
import jp.co.soramitsu.common.data.network.scale.dataType.byteArraySized
import jp.co.soramitsu.common.data.network.scale.dataType.list
import jp.co.soramitsu.common.data.network.scale.dataType.scalable
import jp.co.soramitsu.common.data.network.scale.dataType.tuple
import jp.co.soramitsu.common.data.network.scale.dataType.long
import jp.co.soramitsu.common.data.network.scale.dataType.byteArray
import jp.co.soramitsu.common.data.network.scale.dataType.string
import jp.co.soramitsu.common.data.network.scale.dataType.uint32
import jp.co.soramitsu.common.data.network.scale.dataType.uint64
import jp.co.soramitsu.common.data.network.scale.dataType.uint8
import jp.co.soramitsu.common.data.network.scale.dataType.uint128
import jp.co.soramitsu.common.data.network.scale.dataType.byte
import jp.co.soramitsu.common.data.network.scale.dataType.compactInt
import jp.co.soramitsu.common.data.network.scale.dataType.union
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

fun <S : Schema<S>> S.uint64(default: BigInteger? = null) = NonNullFieldDelegate(uint64, this, default)

fun <S : Schema<S>, T : Schema<T>> S.schema(schema: T, default: EncodableStruct<T>? = null) =
    NonNullFieldDelegate(scalable(schema), this, default)

fun <S : Schema<S>, T, D : DataType<T>> S.vector(
    type: D,
    default: List<T>? = null
) = NonNullFieldDelegate(list(type), this, default)

fun <S : Schema<S>, T : Schema<T>> S.vector(
    schema: T,
    default: List<EncodableStruct<T>>? = null
) = NonNullFieldDelegate(list(scalable(schema)), this, default)

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

fun <S : Schema<S>> S.enum(vararg types: DataType<*>, default: Any? = null) = NonNullFieldDelegate(union(types), this, default)