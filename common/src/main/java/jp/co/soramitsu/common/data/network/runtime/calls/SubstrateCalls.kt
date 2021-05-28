package jp.co.soramitsu.common.data.network.runtime.calls

import jp.co.soramitsu.common.data.network.runtime.model.FeeResponse
import jp.co.soramitsu.common.data.network.runtime.model.SignedBlock
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.executeAsync
import jp.co.soramitsu.fearless_utils.wsrpc.mappers.nonNull
import jp.co.soramitsu.fearless_utils.wsrpc.mappers.pojo
import jp.co.soramitsu.fearless_utils.wsrpc.request.DeliveryType
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.author.SubmitExtrinsicRequest
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.chain.RuntimeVersion
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.chain.RuntimeVersionRequest
import java.math.BigInteger

@Suppress("EXPERIMENTAL_API_USAGE")
class SubstrateCalls(
    private val socketService: SocketService,
) {

    suspend fun getExtrinsicFee(extrinsic: String): BigInteger {
        val request = FeeCalculationRequest(extrinsic)

        val feeResponse = socketService.executeAsync(request, mapper = pojo<FeeResponse>().nonNull())

        return feeResponse.partialFee
    }

    suspend fun submitExtrinsic(extrinsic: String): String {
        val request = SubmitExtrinsicRequest(extrinsic)

        return socketService.executeAsync(
            request,
            mapper = pojo<String>().nonNull(),
            deliveryType = DeliveryType.AT_MOST_ONCE
        )
    }

    suspend fun getNonce(accountAddress: String): BigInteger {
        val nonceRequest = NextAccountIndexRequest(accountAddress)

        val response = socketService.executeAsync(nonceRequest)
        val doubleResult = response.result as Double

        return doubleResult.toInt().toBigInteger()
    }

    suspend fun getRuntimeVersion(): RuntimeVersion {
        val request = RuntimeVersionRequest()

        return socketService.executeAsync(request, mapper = pojo<RuntimeVersion>().nonNull())
    }

    /**
     * Retrieves the block with given hash
     * If hash is null, than the latest block is returned
     */
    suspend fun getBlock(hash: String? = null): SignedBlock {
        val blockRequest = GetBlockRequest(hash)

        return socketService.executeAsync(blockRequest, mapper = pojo<SignedBlock>().nonNull())
    }
}
