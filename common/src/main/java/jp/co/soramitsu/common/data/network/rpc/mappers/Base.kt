package jp.co.soramitsu.common.data.network.rpc.mappers

import com.google.gson.Gson
import jp.co.soramitsu.common.base.errors.FearlessException
import jp.co.soramitsu.fearless_utils.wsrpc.response.RpcResponse

class NullableContainer<R>(val result: R?)

interface ResponseMapper<R> {
    fun map(rpcResponse: RpcResponse, jsonMapper: Gson): R
}

abstract class NullableMapper<R> : ResponseMapper<NullableContainer<R>> {

    abstract fun mapNullable(rpcResponse: RpcResponse, jsonMapper: Gson) : R?

    override fun map(rpcResponse: RpcResponse, jsonMapper: Gson): NullableContainer<R> {
        val value = mapNullable(rpcResponse, jsonMapper)

        return NullableContainer(value)
    }
}

private const val ERROR_KEY = "ERROR_KEY"

object ErrorMapper : ResponseMapper<FearlessException> {
    override fun map(rpcResponse: RpcResponse, jsonMapper: Gson): FearlessException {
        val raw = rpcResponse.result as Map<*, *>
        val message = raw[ERROR_KEY] as? String

        return FearlessException(FearlessException.Kind.NETWORK, message)
    }
}

class NonNullMapper<R>(val nullable: ResponseMapper<NullableContainer<R>>) : ResponseMapper<R> {
    override fun map(rpcResponse: RpcResponse, jsonMapper: Gson): R {
        return nullable.map(rpcResponse, jsonMapper).result ?: throw ErrorMapper.map(rpcResponse, jsonMapper)
    }
}