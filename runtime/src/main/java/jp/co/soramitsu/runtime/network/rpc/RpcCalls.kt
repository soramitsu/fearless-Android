package jp.co.soramitsu.runtime.network.rpc

import java.math.BigInteger
import jp.co.soramitsu.common.data.network.runtime.ExtrinsicStatusResponse
import jp.co.soramitsu.common.data.network.runtime.binding.BlockNumber
import jp.co.soramitsu.common.data.network.runtime.blake2b256String
import jp.co.soramitsu.common.data.network.runtime.calls.ExistentialDepositRequest
import jp.co.soramitsu.common.data.network.runtime.calls.FeeCalculationRequest
import jp.co.soramitsu.common.data.network.runtime.calls.GetBlockHashRequest
import jp.co.soramitsu.common.data.network.runtime.calls.GetBlockRequest
import jp.co.soramitsu.common.data.network.runtime.calls.GetFinalizedHeadRequest
import jp.co.soramitsu.common.data.network.runtime.calls.GetHeaderRequest
import jp.co.soramitsu.common.data.network.runtime.calls.NextAccountIndexRequest
import jp.co.soramitsu.common.data.network.runtime.model.BrokenSubstrateHex
import jp.co.soramitsu.common.data.network.runtime.model.FeeResponse
import jp.co.soramitsu.common.data.network.runtime.model.SignedBlock
import jp.co.soramitsu.common.data.network.runtime.model.SignedBlock.Block.Header
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.DictEnum
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.fromHex
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.GenericEvent
import jp.co.soramitsu.fearless_utils.runtime.metadata.module
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
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
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.getRuntime
import jp.co.soramitsu.runtime.network.RuntimeCall
import jp.co.soramitsu.runtime.network.toRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// TODO: Move - OK

data class EventRecord(val phase: PhaseRecord, val event: InnerEventRecord)

data class InnerEventRecord(val moduleIndex: Int, val eventIndex: Int, var list: List<Any?>? = null)

sealed class PhaseRecord {

    class ApplyExtrinsic(val extrinsicId: BigInteger) : PhaseRecord()

    object Finalization : PhaseRecord()

    object Initialization : PhaseRecord()
}

@Suppress("EXPERIMENTAL_API_USAGE")
class RpcCalls(
    private val chainRegistry: ChainRegistry
) {

    companion object {
        private const val FINALIZED = "finalized"
        private const val FINALITY_TIMEOUT = "finalityTimeout"
    }

    suspend fun getEventsInBlock(
        chainId: ChainId,
        blockHash: String
    ): List<EventRecord> {
        val runtime = chainRegistry.getRuntime(chainId)
        val storageKey = runtime.metadata.module("System").storage("Events").storageKey()
        return runCatching {
            socketFor(chainId).executeAsync(
                request = GetStorageRequest(listOf(storageKey, blockHash)),
                mapper = pojo<String>().nonNull()
            )
                .let { storage ->
                    val eventType = runtime.metadata.module("System").storage("Events").type.value ?: return@let emptyList()
                    val eventsRaw = eventType.fromHex(runtime, storage)
                    if (eventsRaw is List<*>) {
                        val eventRecordList = eventsRaw.filterIsInstance<Struct.Instance>().mapNotNull {
                            val phase = it.get<DictEnum.Entry<*>>("phase")
                            val phaseValue = when (phase?.name) {
                                "ApplyExtrinsic" -> PhaseRecord.ApplyExtrinsic(phase.value as BigInteger)
                                "Finalization" -> PhaseRecord.Finalization
                                "Initialization" -> PhaseRecord.Initialization
                                else -> null
                            }
                            val innerEvent = it.get<GenericEvent.Instance>("event")
                            if (phaseValue == null || innerEvent == null) {
                                null
                            } else {
                                EventRecord(
                                    phaseValue,
                                    InnerEventRecord(
                                        innerEvent.module.index.toInt(),
                                        innerEvent.event.index.second,
                                        innerEvent.arguments
                                    )
                                )
                            }
                        }
                        eventRecordList
                    } else {
                        emptyList()
                    }
                }
        }.getOrElse {
            emptyList()
        }
    }

    suspend fun getExtrinsicFee(chainId: ChainId, extrinsic: String): BigInteger {
        val request = FeeCalculationRequest(extrinsic)

        val feeResponse = socketFor(chainId).executeAsync(request, mapper = pojo<FeeResponse>().nonNull())

        return feeResponse.inclusionFee.sum
    }

    suspend fun submitExtrinsic(chainId: ChainId, extrinsic: String): String {
        val request = SubmitExtrinsicRequest(extrinsic)

        return socketFor(chainId).executeAsync(
            request,
            mapper = pojo<String>().nonNull(),
            deliveryType = DeliveryType.AT_MOST_ONCE
        )
    }

    suspend fun executeRuntimeCall(chainId: ChainId, call: RuntimeCall<*>): Result<ByteArray> {
        val request = call.toRequest()
        val result = kotlin.runCatching {
            socketFor(chainId).executeAsync(
                request,
                deliveryType = DeliveryType.AT_MOST_ONCE
            )
        }
        return result.mapCatching { response ->
            response.error?.let {
                throw RpcException(it)
            } ?: (response.result as String).fromHex()
        }
    }

    suspend fun getNonce(chainId: ChainId, accountAddress: String): BigInteger {
        val nonceRequest = NextAccountIndexRequest(accountAddress)

        val response = socketFor(chainId).executeAsync(nonceRequest)
        val doubleResult = response.result as Double

        return doubleResult.toInt().toBigInteger()
    }

    suspend fun getRuntimeVersion(chainId: ChainId): RuntimeVersion {
        val request = RuntimeVersionRequest()

        return socketFor(chainId).executeAsync(request, mapper = pojo<RuntimeVersion>().nonNull())
    }

    /**
     * Retrieves the block with given hash
     * If hash is null, than the latest block is returned
     */
    suspend fun getBlock(chainId: ChainId, hash: String? = null): SignedBlock {
        val blockRequest = GetBlockRequest(hash)

        return socketFor(chainId).executeAsync(blockRequest, mapper = pojo<SignedBlock>().nonNull())
    }

    /**
     * Get hash of the last finalized block in the canon chain
     */
    suspend fun getFinalizedHead(chainId: ChainId): String {
        return socketFor(chainId).executeAsync(GetFinalizedHeadRequest, mapper = pojo<String>().nonNull())
    }

    /**
     * Retrieves the header for a specific block
     *
     * @param hash - hash of the block. If null - then the  best pending header is returned
     */
    suspend fun getBlockHeader(chainId: ChainId, hash: String? = null): Header {
        return socketFor(chainId).executeAsync(GetHeaderRequest(hash), mapper = pojo<Header>().nonNull())
    }

    fun submitAndWatchExtrinsic(chainId: ChainId, extrinsic: String): Flow<Pair<String, ExtrinsicStatusResponse>> {
        val request = SubmitAndWatchExtrinsicRequest(extrinsic)
        val extHash = extrinsic.blake2b256String()
        return socketFor(chainId).subscriptionFlow(
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
        val subscriptionId = response.subscriptionId
        val result = response.params.result
        val statusResponse: ExtrinsicStatusResponse = when {
            (result as? Map<String, *>)?.containsKey(FINALIZED)
                ?: false -> ExtrinsicStatusResponse.ExtrinsicStatusFinalized(
                subscriptionId,
                (result as? Map<String, *>)?.getValue(FINALIZED) as String
            )
            (result as? Map<String, *>)?.containsKey(FINALITY_TIMEOUT)
                ?: false -> ExtrinsicStatusResponse.ExtrinsicStatusFinalized(
                subscriptionId,
                (result as? Map<String, *>)?.getValue(FINALITY_TIMEOUT) as String
            )
            else -> ExtrinsicStatusResponse.ExtrinsicStatusPending(subscriptionId)
        }
        return hash to statusResponse
    }

    /**
     * Retrieves the hash of a specific block
     *
     *  @param blockNumber - if null, then the  best block hash is returned
     */
    suspend fun getBlockHash(chainId: ChainId, blockNumber: BlockNumber? = null): String {
        return socketFor(chainId).executeAsync(GetBlockHashRequest(blockNumber), mapper = pojo<String>().nonNull())
    }

    private fun socketFor(chainId: ChainId) = chainRegistry.getConnection(chainId).socketService

    suspend fun getExistentialDeposit(chainId: ChainId, assetIdentifier: Pair<String, Any>): BigInteger {
        val request = ExistentialDepositRequest(mapOf(assetIdentifier))
        val resultInHex = socketFor(chainId).executeAsync(request, mapper = pojo<String>().nonNull())

        return BrokenSubstrateHex(resultInHex).decodeBigInt()
    }
}
