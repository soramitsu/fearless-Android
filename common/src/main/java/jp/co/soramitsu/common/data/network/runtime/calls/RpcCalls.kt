package jp.co.soramitsu.common.data.network.runtime.calls

import jp.co.soramitsu.common.data.network.runtime.ExtrinsicStatusResponse
import jp.co.soramitsu.common.data.network.runtime.binding.BlockNumber
import jp.co.soramitsu.common.data.network.runtime.blake2b256String
import jp.co.soramitsu.common.data.network.runtime.model.FeeResponse
import jp.co.soramitsu.common.data.network.runtime.model.SignedBlock
import jp.co.soramitsu.common.data.network.runtime.model.SignedBlock.Block.Header
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.DictEnum
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.fromHex
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.GenericEvent
import jp.co.soramitsu.fearless_utils.runtime.metadata.module
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.executeAsync
import jp.co.soramitsu.fearless_utils.wsrpc.mappers.nonNull
import jp.co.soramitsu.fearless_utils.wsrpc.mappers.pojo
import jp.co.soramitsu.fearless_utils.wsrpc.request.DeliveryType
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.author.SubmitAndWatchExtrinsicRequest
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.author.SubmitExtrinsicRequest
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.chain.RuntimeVersion
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.chain.RuntimeVersionRequest
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.storage.GetStorageRequest
import jp.co.soramitsu.fearless_utils.wsrpc.subscription.response.SubscriptionChange
import jp.co.soramitsu.fearless_utils.wsrpc.subscriptionFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.math.BigInteger

data class EventRecord(val phase: PhaseRecord, val event: InnerEventRecord)

data class InnerEventRecord(val moduleIndex: Int, val eventIndex: Int, var list: List<Any?>? = null)

sealed class PhaseRecord {

    class ApplyExtrinsic(val extrinsicId: BigInteger) : PhaseRecord()

    object Finalization : PhaseRecord()

    object Initialization : PhaseRecord()
}

@Suppress("EXPERIMENTAL_API_USAGE")
class RpcCalls(
    private val socketService: SocketService,
) {

    companion object {
        private const val FINALIZED = "finalized"
        private const val FINALITY_TIMEOUT = "finalityTimeout"
    }

    suspend fun getEventsInBlock(
        runtime: RuntimeSnapshot,
        blockHash: String
    ): List<EventRecord> {
        val storageKey = runtime.metadata.module("System").storage("Events").storageKey()
        return runCatching {
            socketService.executeAsync(
                request = GetStorageRequest(listOf(storageKey, blockHash)),
                mapper = pojo<String>().nonNull(),
            )
                .let { storage ->
                    val eventType =
                        runtime.metadata.module("System").storage("Events").type.value!!
                    val eventsRaw = eventType.fromHex(runtime, storage)
                    if (eventsRaw is List<*>) {
                        val eventRecordList = eventsRaw.filterIsInstance<Struct.Instance>().map {
                            val phase = it.get<DictEnum.Entry<*>>("phase")
                            val phaseValue = when (phase?.name) {
                                "ApplyExtrinsic" -> PhaseRecord.ApplyExtrinsic(phase.value as BigInteger)
                                "Finalization" -> PhaseRecord.Finalization
                                "Initialization" -> PhaseRecord.Initialization
                                else -> null
                            }
                            val innerEvent = it.get<GenericEvent.Instance>("event")
                            EventRecord(
                                phaseValue!!,
                                InnerEventRecord(
                                    innerEvent!!.module.index.toInt(),
                                    innerEvent.event.index.second,
                                    innerEvent.arguments
                                )
                            )
                        }
                        eventRecordList
                    } else emptyList()
                }
        }.getOrElse {
            emptyList()
        }
    }

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

    fun submitAndWatchExtrinsic(extrinsic: String): Flow<Pair<String, ExtrinsicStatusResponse>> {
        val request = SubmitAndWatchExtrinsicRequest(extrinsic)
        val extHash = extrinsic.blake2b256String()
        return socketService.subscriptionFlow(
            request,
            "author_unwatchExtrinsic"
        ).map {
            mapSubscription(extHash, it)
        }
    }

    private fun mapSubscription(
        hash: String,
        response: SubscriptionChange
    ): Pair<String, ExtrinsicStatusResponse> {
        val s = response.subscriptionId
        val result = response.params.result
        val statusResponse: ExtrinsicStatusResponse = when {
            (result as? Map<String, *>)?.containsKey(FINALIZED)
                ?: false -> ExtrinsicStatusResponse.ExtrinsicStatusFinalized(
                s,
                (result as? Map<String, *>)?.getValue(FINALIZED) as String
            )
            (result as? Map<String, *>)?.containsKey(FINALITY_TIMEOUT)
                ?: false -> ExtrinsicStatusResponse.ExtrinsicStatusFinalized(
                s,
                (result as? Map<String, *>)?.getValue(FINALITY_TIMEOUT) as String
            )
            else -> ExtrinsicStatusResponse.ExtrinsicStatusPending(s)
        }
        return hash to statusResponse
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

    /**
     * Get hash of the last finalized block in the canon chain
     */
    suspend fun getFinalizedHead(): String {
        return socketService.executeAsync(GetFinalizedHeadRequest, mapper = pojo<String>().nonNull())
    }

    /**
     * Retrieves the header for a specific block
     *
     * @param hash - hash of the block. If null - then the  best pending header is returned
     */
    suspend fun getBlockHeader(hash: String? = null): Header {
        return socketService.executeAsync(GetHeaderRequest(hash), mapper = pojo<Header>().nonNull())
    }

    /**
     * Retrieves the hash of a specific block
     *
     *  @param blockNumber - if null, then the  best block hash is returned
     */
    suspend fun getBlockHash(blockNumber: BlockNumber? = null): String {
        return socketService.executeAsync(GetBlockHashRequest(blockNumber), mapper = pojo<String>().nonNull())
    }
}
