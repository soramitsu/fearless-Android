package jp.co.soramitsu.common.data.network.runtime.calls

import jp.co.soramitsu.common.data.network.runtime.binding.BlockNumber
import jp.co.soramitsu.common.data.network.runtime.model.FeeResponse
import jp.co.soramitsu.common.data.network.runtime.model.SignedBlock
import jp.co.soramitsu.common.data.network.runtime.model.SignedBlock.Block.Header
import jp.co.soramitsu.common.utils.SuspendableProperty
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
class RpcCalls(
    private val socketProperty: SuspendableProperty<SocketService>,
) {

    suspend fun getExtrinsicFee(extrinsic: String): BigInteger {
        val request = FeeCalculationRequest(extrinsic)

        val feeResponse = socketProperty.get().executeAsync(request, mapper = pojo<FeeResponse>().nonNull())

        return feeResponse.partialFee
    }

    suspend fun submitExtrinsic(extrinsic: String): String {
        val request = SubmitExtrinsicRequest(extrinsic)

        return socketProperty.get().executeAsync(
            request,
            mapper = pojo<String>().nonNull(),
            deliveryType = DeliveryType.AT_MOST_ONCE
        )
    }

    suspend fun getNonce(accountAddress: String): BigInteger {
        val nonceRequest = NextAccountIndexRequest(accountAddress)

        val response = socketProperty.get().executeAsync(nonceRequest)
        val doubleResult = response.result as Double

        return doubleResult.toInt().toBigInteger()
    }

    suspend fun getRuntimeVersion(): RuntimeVersion {
        val request = RuntimeVersionRequest()

        return socketProperty.get().executeAsync(request, mapper = pojo<RuntimeVersion>().nonNull())
    }

    /**
     * Retrieves the block with given hash
     * If hash is null, than the latest block is returned
     */
    suspend fun getBlock(hash: String? = null): SignedBlock {
        val blockRequest = GetBlockRequest(hash)

        return socketProperty.get().executeAsync(blockRequest, mapper = pojo<SignedBlock>().nonNull())
    }

    /**
     * Get hash of the last finalized block in the canon chain
     */
    suspend fun getFinalizedHead(): String {
        return socketProperty.get().executeAsync(GetFinalizedHeadRequest, mapper = pojo<String>().nonNull())
    }

    /**
     * Retrieves the header for a specific block
     *
     * @param hash - hash of the block. If null - then the  best pending header is returned
     */
    suspend fun getBlockHeader(hash: String? = null): Header {
        return socketProperty.get().executeAsync(GetHeaderRequest(hash), mapper = pojo<Header>().nonNull())
    }

    /**
     * Retrieves the hash of a specific block
     *
     *  @param blockNumber - if null, then the  best block hash is returned
     */
    suspend fun getBlockHash(blockNumber: BlockNumber? = null): String {
        return socketProperty.get().executeAsync(GetBlockHashRequest(blockNumber), mapper = pojo<String>().nonNull())
    }
}
