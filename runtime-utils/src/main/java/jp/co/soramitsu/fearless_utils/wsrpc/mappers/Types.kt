package jp.co.soramitsu.fearless_utils.wsrpc.mappers

import com.google.gson.Gson
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.scale.EncodableStruct
import jp.co.soramitsu.fearless_utils.scale.Schema
import jp.co.soramitsu.fearless_utils.wsrpc.exception.RpcException
import jp.co.soramitsu.fearless_utils.wsrpc.response.RpcResponse

/**
 *  Mark that the result is always non-null and null result means that error happened
 * @throws RpcException in case of null result
 */
fun <R> NullableMapper<R>.nonNull() = NonNullMapper(this)

fun <S : Schema<S>> scale(schema: S) = ScaleMapper(schema)

fun <S : Schema<S>> scaleCollection(schema: S) = ScaleCollectionMapper(schema)

inline fun <reified T> pojo() = POJOMapper(T::class.java)

inline fun <reified T> pojoList() = POJOCollectionMapper(T::class.java)

fun string() = StringMapper

object StringMapper : NullableMapper<String>() {
    override fun mapNullable(rpcResponse: RpcResponse, jsonMapper: Gson): String? {
        return rpcResponse.result?.toString()
    }
}

class ScaleMapper<S : Schema<S>>(val schema: S) : NullableMapper<EncodableStruct<S>>() {
    override fun mapNullable(rpcResponse: RpcResponse, jsonMapper: Gson): EncodableStruct<S>? {
        val raw = rpcResponse.result as? String ?: return null

        return schema.read(raw.fromHex())
    }
}

class ScaleCollectionMapper<S : Schema<S>>(val schema: S) :
    NullableMapper<List<EncodableStruct<S>>>() {

    override fun mapNullable(
        rpcResponse: RpcResponse,
        jsonMapper: Gson
    ): List<EncodableStruct<S>>? {
        val raw = rpcResponse.result as? List<String> ?: return null

        return raw.map(schema::read)
    }
}

class POJOCollectionMapper<T>(val classRef: Class<T>) : NullableMapper<List<T>>() {
    override fun mapNullable(rpcResponse: RpcResponse, jsonMapper: Gson): List<T>? {
        val raw = rpcResponse.result as? List<*> ?: return null
        return raw.map {
            val t = jsonMapper.toJsonTree(it)
            jsonMapper.fromJson(t, classRef)
        }
    }
}

class POJOMapper<T>(val classRef: Class<T>) : NullableMapper<T>() {

    override fun mapNullable(rpcResponse: RpcResponse, jsonMapper: Gson): T? {
        return when (rpcResponse.result) {
            is Map<*, *> -> {
                val tree = jsonMapper.toJsonTree(rpcResponse.result)
                jsonMapper.fromJson(tree, classRef)
            }
            else -> rpcResponse.result as? T ?: null
        }
    }
}
