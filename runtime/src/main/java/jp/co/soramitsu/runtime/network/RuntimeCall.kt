package jp.co.soramitsu.runtime.network

import io.emeraldpay.polkaj.scale.ScaleCodecReader
import java.math.BigInteger
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.fearless_utils.scale.dataType.uint128
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.RuntimeRequest

interface RuntimeCall<T> {

    val path: String
    val args: ByteArray
    fun parseResult(resultBytes: ByteArray): T

    interface NominationPoolsApi<T> : RuntimeCall<T> {

        class PendingRewards(accountId: AccountId) : NominationPoolsApi<BigInteger> {
            override val path: String = "NominationPoolsApi_pending_rewards"
            override val args: ByteArray = accountId

            override fun parseResult(resultBytes: ByteArray): BigInteger {
                val reader = ScaleCodecReader(resultBytes)
                return uint128.read(reader)
            }
        }
    }
}

class RuntimeCallRequest(runtimeCallMethod: String, args: String) : RuntimeRequest(
    method = "state_call",
    params = listOf(runtimeCallMethod, args)
)

fun RuntimeCall<*>.toRequest(): RuntimeCallRequest = RuntimeCallRequest(path, args.toHexString(withPrefix = true))
