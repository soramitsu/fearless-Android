package jp.co.soramitsu.common.data.network.rpc

import com.google.gson.Gson
import jp.co.soramitsu.common.data.network.scale.EncodableStruct
import jp.co.soramitsu.common.data.network.scale.Schema
import jp.co.soramitsu.fearless_utils.wsrpc.response.RpcResponse

class Mapped<R>(val result: R)

interface ResponseMapper<R> {
    fun map(rpcResponse: RpcResponse, jsonMapper: Gson): R
}

fun <S : Schema<S>> scale(schema: S) = ScaleMapper(schema)

class ScaleMapper<S : Schema<S>>(val schema: S) : ResponseMapper<EncodableStruct<S>?> {
    override fun map(rpcResponse: RpcResponse, jsonMapper: Gson): EncodableStruct<S>? {
        val raw = rpcResponse.result as? String ?: return null

        return schema.read(raw)
    }
}

fun <S : Schema<S>> scaleCollection(schema: S) = ScaleCollectionMapper(schema)

class ScaleCollectionMapper<S : Schema<S>>(val schema: S) : ResponseMapper<List<EncodableStruct<S>>?> {
    override fun map(rpcResponse: RpcResponse, jsonMapper: Gson): List<EncodableStruct<S>>? {
        val raw = rpcResponse.result as? List<String> ?: return null

        return raw.map { schema.read(it) }
    }
}

fun string() = StringMapper()

class StringMapper : ResponseMapper<String> {
    override fun map(rpcResponse: RpcResponse, jsonMapper: Gson): String {
        return rpcResponse.result as String
    }
}

inline fun <reified T> pojo() = POJOMapper(T::class.java)

class POJOMapper<T>(val classRef: Class<T>) : ResponseMapper<T> {
    override fun map(rpcResponse: RpcResponse, jsonMapper: Gson): T {
        val raw = rpcResponse.result as Map<String, Any?>

        val tree = jsonMapper.toJsonTree(raw)

        return jsonMapper.fromJson(tree, classRef)
    }
}