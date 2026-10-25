package jp.co.soramitsu.fearless_utils.wsrpc.mappers

import com.google.gson.Gson
import jp.co.soramitsu.fearless_utils.wsrpc.exception.RpcException
import jp.co.soramitsu.fearless_utils.wsrpc.response.RpcResponse

class NullableContainer<R>(val result: R?)

interface ResponseMapper<R> {

    fun map(rpcResponse: RpcResponse, jsonMapper: Gson): R
}

abstract class NullableMapper<R> : ResponseMapper<NullableContainer<R>> {

    protected abstract fun mapNullable(rpcResponse: RpcResponse, jsonMapper: Gson): R?

    override fun map(rpcResponse: RpcResponse, jsonMapper: Gson): NullableContainer<R> {
        val value = mapNullable(rpcResponse, jsonMapper)

        return NullableContainer(value)
    }
}

object ErrorMapper : ResponseMapper<RpcException> {

    override fun map(rpcResponse: RpcResponse, jsonMapper: Gson): RpcException {
        val error = rpcResponse.error?.message

        return RpcException(error)
    }
}

class NonNullMapper<R>(
    private val nullable: ResponseMapper<NullableContainer<R>>
) : ResponseMapper<R> {

    override fun map(rpcResponse: RpcResponse, jsonMapper: Gson): R {
        return nullable.map(rpcResponse, jsonMapper).result ?: throw ErrorMapper.map(rpcResponse, jsonMapper)
    }
}
