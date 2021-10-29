package jp.co.soramitsu.runtime.extrinsic

import jp.co.soramitsu.common.data.network.runtime.ExtrinsicStatusResponse
import jp.co.soramitsu.common.data.network.runtime.blake2b256String
import jp.co.soramitsu.common.data.network.runtime.calls.PhaseRecord
import jp.co.soramitsu.common.data.network.runtime.calls.RpcCalls
import jp.co.soramitsu.common.utils.system
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.fearless_utils.runtime.metadata.event
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformWhile

data class BlockEvent(val module: Int, val event: Int, val number: Long?)

class ExtrinsicService(
    private val rpcCalls: RpcCalls,
    private val extrinsicBuilderFactory: ExtrinsicBuilderFactory
) {

    suspend fun submitExtrinsic(
        accountAddress: String,
        formExtrinsic: suspend ExtrinsicBuilder.() -> Unit
    ): Result<String> = runCatching {
        val extrinsicBuilder = extrinsicBuilderFactory.create(accountAddress)

        extrinsicBuilder.formExtrinsic()

        val extrinsic = extrinsicBuilder.build()

        rpcCalls.submitExtrinsic(extrinsic)
    }

    suspend fun submitAndWatchExtrinsic(
        accountAddress: String,
        formExtrinsic: suspend ExtrinsicBuilder.() -> Unit,
        snapshot: RuntimeSnapshot
    ): Pair<String, String>? {
        val extrinsicBuilder = extrinsicBuilderFactory.create(accountAddress)
        extrinsicBuilder.formExtrinsic()
        val extrinsic = extrinsicBuilder.build()
        val result = rpcCalls.submitAndWatchExtrinsic(extrinsic)
            .catch {
                emit("" to ExtrinsicStatusResponse.ExtrinsicStatusPending(""))
            }
            .map {
                Triple(
                    it.first,
                    (it.second as? ExtrinsicStatusResponse.ExtrinsicStatusFinalized)?.inBlock,
                    it.second.subscription
                )
            }
            .transformWhile { value ->
                val finish = value.second?.let { blockHash ->
                    val txHash = value.first
                    val blockResponse = rpcCalls.getBlock(blockHash)
                    val extrinsicId =
                        blockResponse.block.extrinsics.indexOfFirst { s -> s.blake2b256String() == txHash }
                            .toLong()
                    val isSuccess = isExtrinsicSuccessful(snapshot, extrinsicId, blockHash, txHash)
                    if (isSuccess) txHash to blockHash else null
                }
                emit(finish)
                val more = value.second.isNullOrEmpty() && value.first.isNotEmpty()
                more
            }.first {
                it != null
            }
        return result
    }

    private suspend fun isExtrinsicSuccessful(
        snapshot: RuntimeSnapshot,
        extrinsicId: Long,
        blockHash: String,
        txHash: String
    ): Boolean {
        val events = rpcCalls.getEventsInBlock(snapshot, blockHash)
        val blockEvents = events.map {
            BlockEvent(
                it.event.moduleIndex,
                it.event.eventIndex,
                (it.phase as? PhaseRecord.ApplyExtrinsic)?.extrinsicId?.toLong()
            )
        }
        if (blockEvents.isEmpty()) return false
        val (moduleIndexSuccess, eventIndexSuccess) = snapshot.metadata.system().event("ExtrinsicSuccess").index
        val (moduleIndexFailed, eventIndexFailed) = snapshot.metadata.system().event("ExtrinsicFailed").index
        val successEvent = blockEvents.find { event ->
            event.module == moduleIndexSuccess && event.event == eventIndexSuccess && event.number == extrinsicId
        }
        val failedEvent = blockEvents.find { event ->
            event.module == moduleIndexFailed && event.event == eventIndexFailed && event.number == extrinsicId
        }
        return when {
            successEvent != null -> {
                true
            }
            failedEvent != null -> {
                false
            }
            else -> {
                false
            }
        }
    }
}
