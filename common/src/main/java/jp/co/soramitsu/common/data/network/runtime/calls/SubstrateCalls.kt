package jp.co.soramitsu.common.data.network.runtime.calls

import jp.co.soramitsu.common.data.network.runtime.model.ActiveEraInfo
import jp.co.soramitsu.fearless_utils.runtime.Module
import jp.co.soramitsu.fearless_utils.runtime.storageKey
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.executeAsync
import jp.co.soramitsu.fearless_utils.wsrpc.mappers.nonNull
import jp.co.soramitsu.fearless_utils.wsrpc.mappers.pojo
import jp.co.soramitsu.fearless_utils.wsrpc.mappers.scale
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.chain.RuntimeVersion
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.chain.RuntimeVersionRequest
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.storage.GetStorageRequest
import jp.co.soramitsu.feature_account_api.domain.model.Account
import java.math.BigInteger

@Suppress("EXPERIMENTAL_API_USAGE")
class SubstrateCalls(
    val socketService: SocketService
) {

    suspend fun getNonce(account: Account): BigInteger {
        val nonceRequest = NextAccountIndexRequest(account.address)

        val response = socketService.executeAsync(nonceRequest)
        val doubleResult = response.result as Double

        return doubleResult.toInt().toBigInteger()
    }

    suspend fun getActiveEra(): Long {
        val key = Module.Staking.ActiveEra.storageKey()
        val request = GetStorageRequest(listOf(key))

        val struct = socketService.executeAsync(request, mapper = scale(ActiveEraInfo).nonNull())

        return struct[ActiveEraInfo.index].toLong()
    }

    suspend fun getRuntimeVersion(): RuntimeVersion {
        val request = RuntimeVersionRequest()

        return socketService.executeAsync(request, mapper = pojo<RuntimeVersion>().nonNull())
    }
}