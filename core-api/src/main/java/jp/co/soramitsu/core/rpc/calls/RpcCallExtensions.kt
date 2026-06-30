package jp.co.soramitsu.core.rpc.calls

import jp.co.soramitsu.core.rpc.LiquidityProxyQuote
import jp.co.soramitsu.core.rpc.RpcCalls
import jp.co.soramitsu.core.rpc.RuntimeCall
import jp.co.soramitsu.core.rpc.SignedBlock
import java.math.BigInteger

private const val PUBLIC_RPC_UNAVAILABLE =
    "Substrate RPC calls are unavailable in the public compatibility layer"

suspend fun RpcCalls.getExistentialDeposit(
    chainId: String,
    assetIdentifier: Pair<String, Any>
): BigInteger {
    throw UnsupportedOperationException(PUBLIC_RPC_UNAVAILABLE)
}

suspend fun RpcCalls.getBlock(
    chainId: String,
    blockHash: String
): SignedBlock {
    throw UnsupportedOperationException(PUBLIC_RPC_UNAVAILABLE)
}

suspend fun RpcCalls.executeRuntimeCall(
    chainId: String,
    call: RuntimeCall<*>
): Result<ByteArray> = Result.failure(UnsupportedOperationException(PUBLIC_RPC_UNAVAILABLE))

suspend fun RpcCalls.liquidityProxyQuote(
    chainId: String,
    inputAssetId: String,
    outputAssetId: String,
    amount: BigInteger,
    swapVariant: String,
    selectedSourceTypes: List<String>,
    filterMode: String,
    dexId: Int
): LiquidityProxyQuote? {
    throw UnsupportedOperationException(PUBLIC_RPC_UNAVAILABLE)
}
