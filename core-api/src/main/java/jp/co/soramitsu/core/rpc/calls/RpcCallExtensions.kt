package jp.co.soramitsu.core.rpc.calls

import com.google.gson.Gson
import jp.co.soramitsu.core.rpc.RpcCalls
import jp.co.soramitsu.core.rpc.RuntimeCall
import jp.co.soramitsu.core.rpc.SignedBlock
import jp.co.soramitsu.core.rpc.normalizeRpcBigInteger
import jp.co.soramitsu.core.runtime.models.requests.ExistentialDepositRequest
import jp.co.soramitsu.core.runtime.models.requests.GetBlockRequest
import jp.co.soramitsu.core.runtime.models.requests.LiquidityProxyQuoteRequest
import jp.co.soramitsu.core.runtime.models.requests.RuntimeCallRequest
import jp.co.soramitsu.core.runtime.models.responses.QuoteResponse
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.wsrpc.exception.RpcException
import jp.co.soramitsu.fearless_utils.wsrpc.executeAsync
import jp.co.soramitsu.fearless_utils.wsrpc.mappers.ResponseMapper
import jp.co.soramitsu.fearless_utils.wsrpc.mappers.nonNull
import jp.co.soramitsu.fearless_utils.wsrpc.mappers.pojo
import jp.co.soramitsu.fearless_utils.wsrpc.request.DeliveryType
import jp.co.soramitsu.fearless_utils.wsrpc.response.RpcError
import jp.co.soramitsu.fearless_utils.wsrpc.response.RpcResponse
import java.math.BigInteger

// The RPC helpers 4.2.0 resolved from shared-features core 1.1.1.44-FLW (core/rpc/calls at 7508bc83):
// same methods, parameters and delivery types. A failed call throws or returns a failure, never a default value.

suspend fun RpcCalls.getExistentialDeposit(
    chainId: String,
    assetIdentifier: Pair<String, Any>
): BigInteger {
    return socketFor(chainId).executeAsync(
        ExistentialDepositRequest(mapOf(assetIdentifier)),
        mapper = ExistentialDepositMapper
    )
}

suspend fun RpcCalls.getBlock(
    chainId: String,
    blockHash: String
): SignedBlock {
    return socketFor(chainId).executeAsync(
        GetBlockRequest(blockHash),
        mapper = pojo<SignedBlock>().nonNull()
    )
}

suspend fun RpcCalls.executeRuntimeCall(
    chainId: String,
    call: RuntimeCall<*>
): Result<ByteArray> {
    val request = RuntimeCallRequest(call.path, call.args.toHexString(withPrefix = true))

    return runCatching {
        socketFor(chainId).executeAsync(request, deliveryType = DeliveryType.AT_MOST_ONCE)
    }.mapCatching(::mapRuntimeCallResponse)
}

suspend fun RpcCalls.liquidityProxyQuote(
    chainId: String,
    inputAssetId: String,
    outputAssetId: String,
    amount: BigInteger,
    swapVariant: String,
    selectedSourceTypes: List<String>,
    filterMode: String,
    dexId: Int
): QuoteResponse? {
    val request = LiquidityProxyQuoteRequest(
        dexId = dexId,
        inputAssetId = inputAssetId,
        outputAssetId = outputAssetId,
        amount = amount,
        swapVariant = swapVariant,
        selectedSourceTypes = selectedSourceTypes,
        filterMode = filterMode
    )

    return socketFor(chainId).executeAsync(request, mapper = LiquidityProxyQuoteMapper)
}

internal object LiquidityProxyQuoteMapper : ResponseMapper<QuoteResponse?> {
    override fun map(rpcResponse: RpcResponse, jsonMapper: Gson): QuoteResponse? {
        rpcResponse.error?.let { throw it.toRpcException() }
        return pojo<QuoteResponse>().map(rpcResponse, jsonMapper).result
    }
}

internal object ExistentialDepositMapper : ResponseMapper<BigInteger> {
    // orml tokens RPC answers NumberOrHex: a big-endian hex of any length ("0xb320334"), or a plain number.
    override fun map(rpcResponse: RpcResponse, jsonMapper: Gson): BigInteger {
        rpcResponse.error?.let { throw it.toRpcException() }

        return normalizeRpcBigInteger(rpcResponse.result, "existential deposit")
    }
}

internal fun mapRuntimeCallResponse(response: RpcResponse): ByteArray {
    response.error?.let { throw it.toRpcException() }

    val resultHex = response.result as? String
        ?: throw IllegalArgumentException("state_call result must be a hex string")

    return resultHex.fromHex()
}

private fun RpcError.toRpcException() = RpcException("Rpc error. code: $code, message: $message")
