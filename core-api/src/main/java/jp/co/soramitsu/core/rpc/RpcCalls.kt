package jp.co.soramitsu.core.rpc

import com.google.gson.Gson
import jp.co.soramitsu.core.models.IChain
import jp.co.soramitsu.core.runtime.IChainRegistry
import jp.co.soramitsu.core.utils.toLongExact
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAddress
import jp.co.soramitsu.fearless_utils.wsrpc.executeAsync
import jp.co.soramitsu.fearless_utils.wsrpc.mappers.ResponseMapper
import jp.co.soramitsu.fearless_utils.wsrpc.mappers.nonNull
import jp.co.soramitsu.fearless_utils.wsrpc.mappers.pojo
import jp.co.soramitsu.fearless_utils.wsrpc.mappers.string
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.RuntimeRequest
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.author.SubmitAndWatchExtrinsicRequest
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.author.SubmitExtrinsicRequest
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.chain.RuntimeVersion
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.chain.RuntimeVersionRequest
import jp.co.soramitsu.fearless_utils.wsrpc.response.RpcResponse
import jp.co.soramitsu.fearless_utils.wsrpc.subscription.response.SubscriptionChange
import jp.co.soramitsu.fearless_utils.wsrpc.subscriptionFlow
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.math.BigInteger

class RpcCalls(
    private val chainRegistry: IChainRegistry
) {
    companion object {
        const val FINALIZED = "finalized"
        const val IN_BLOCK = "inBlock"
        const val DEFAULT_ASSETS_PAGE_SIZE = 100
    }

    suspend fun getRuntime(chainId: String): RuntimeSnapshot {
        return chainRegistry.getRuntime(chainId)
    }

    suspend fun getRuntimeVersion(chainId: String): RuntimeVersion {
        return chainRegistry.getConnection(chainId).socketService.executeAsync(
            RuntimeVersionRequest(),
            mapper = pojo<RuntimeVersion>().nonNull()
        )
    }

    suspend fun getAccountNonce(chain: IChain, accountId: AccountId): BigInteger {
        return chainRegistry.getConnection(chain.id).socketService.executeAsync(
            AccountNextIndexRequest(accountId.toAddress(chain.addressPrefix.toShort())),
            mapper = RpcBigIntegerMapper("account nonce")
        )
    }

    suspend fun getBlockHash(chainId: String, blockNumber: BigInteger): String {
        return chainRegistry.getConnection(chainId).socketService.executeAsync(
            ChainGetBlockHashRequest(blockNumber.toLongExact()),
            mapper = BlockHashMapper
        )
    }

    suspend fun estimateExtrinsicFee(chainId: String, extrinsic: String): BigInteger {
        return chainRegistry.getConnection(chainId).socketService.executeAsync(
            PaymentQueryInfoRequest(extrinsic),
            mapper = PaymentInfoFeeMapper
        )
    }

    suspend fun submitExtrinsic(chainId: String, extrinsic: String): String {
        return chainRegistry.getConnection(chainId).socketService.executeAsync(
            SubmitExtrinsicRequest(extrinsic),
            mapper = string().nonNull()
        )
    }

    fun extrinsicStatusFlow(chainId: String, extrinsic: String): Flow<SubscriptionChange> {
        return chainRegistry.getConnection(chainId).socketService.subscriptionFlow(
            SubmitAndWatchExtrinsicRequest(extrinsic)
        )
    }
}

private class AccountNextIndexRequest(accountAddress: String) : RuntimeRequest(
    method = "system_accountNextIndex",
    params = listOf(accountAddress)
)

private class PaymentQueryInfoRequest(extrinsic: String) : RuntimeRequest(
    method = "payment_queryInfo",
    params = listOf(extrinsic)
)

private class ChainGetBlockHashRequest(blockNumber: Long) : RuntimeRequest(
    method = "chain_getBlockHash",
    params = listOf(blockNumber)
)

private class RpcBigIntegerMapper(
    private val fieldName: String
) : ResponseMapper<BigInteger> {
    override fun map(rpcResponse: RpcResponse, jsonMapper: Gson): BigInteger {
        return normalizeRpcBigInteger(rpcResponse.result, fieldName)
    }
}

private object PaymentInfoFeeMapper : ResponseMapper<BigInteger> {
    override fun map(rpcResponse: RpcResponse, jsonMapper: Gson): BigInteger {
        return normalizePaymentPartialFee(rpcResponse.result)
    }
}

private object BlockHashMapper : ResponseMapper<String> {
    override fun map(rpcResponse: RpcResponse, jsonMapper: Gson): String {
        return normalizeRpcBlockHash(rpcResponse.result, "block hash")
    }
}

internal fun normalizePaymentPartialFee(result: Any?): BigInteger {
    val map = result as? Map<*, *> ?: throw IllegalArgumentException("Payment info result must be an object")
    return normalizeRpcBigInteger(map["partialFee"], "partialFee")
}

internal fun normalizeAuthorStatusBlockHash(result: Any?): String? {
    val map = result as? Map<*, *> ?: return null
    val blockHash = map[RpcCalls.FINALIZED] ?: map[RpcCalls.IN_BLOCK] ?: return null
    return (blockHash as? String)
        ?.takeIf { HEX_HASH_REGEX.matches(it) }
}

internal fun normalizeRpcBlockHash(value: Any?, fieldName: String): String {
    val hash = value as? String ?: throw IllegalArgumentException("$fieldName must be a 32-byte hex hash")
    require(HEX_HASH_REGEX.matches(hash)) { "$fieldName must be a 32-byte hex hash" }
    return hash
}

internal fun normalizeRpcBigInteger(value: Any?, fieldName: String): BigInteger {
    val parsed = when (value) {
        is BigInteger -> value
        is BigDecimal -> value.toBigIntegerExact()
        is Byte,
        is Short,
        is Int,
        is Long -> BigInteger.valueOf((value as Number).toLong())
        is Float,
        is Double -> BigDecimal.valueOf((value as Number).toDouble()).toBigIntegerExact()
        is String -> value.toBigIntegerFlexible(fieldName)
        else -> throw IllegalArgumentException("$fieldName must be an unsigned integer")
    }

    require(parsed.signum() >= 0) { "$fieldName must be an unsigned integer" }

    return parsed
}

private fun String.toBigIntegerFlexible(fieldName: String): BigInteger {
    val normalized = trim()
    require(normalized.isNotEmpty()) { "$fieldName must be an unsigned integer" }

    return if (normalized.startsWith("0x", ignoreCase = true)) {
        BigInteger(normalized.removePrefix("0x").removePrefix("0X"), 16)
    } else {
        BigInteger(normalized, 10)
    }
}

private val HEX_HASH_REGEX = Regex("^0x[0-9a-fA-F]{64}$")

interface RuntimeCall<T> {
    val path: String
    val args: ByteArray

    fun parseResult(resultBytes: ByteArray): T

    interface NominationPoolsApi<T> : RuntimeCall<T> {
        class PendingRewards(accountId: AccountId) : NominationPoolsApi<BigInteger> {
            override val path = "NominationPoolsApi_pending_rewards"
            override val args: ByteArray = accountId

            override fun parseResult(resultBytes: ByteArray): BigInteger {
                return BigInteger(1, resultBytes)
            }
        }
    }
}

data class SignedBlock(
    val block: Block
) {
    data class Block(
        val extrinsics: List<String>
    )
}

data class LiquidityProxyQuote(
    val amount: BigInteger?
)
