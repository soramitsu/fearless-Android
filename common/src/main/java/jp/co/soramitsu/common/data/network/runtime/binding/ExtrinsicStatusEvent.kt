package jp.co.soramitsu.common.data.network.runtime.binding

import jp.co.soramitsu.common.utils.index
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.fromHex
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.GenericEvent
import jp.co.soramitsu.fearless_utils.runtime.metadata.event
import jp.co.soramitsu.fearless_utils.runtime.metadata.module
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage

enum class ExtrinsicStatusEvent {
    SUCCESS, FAILURE
}

@HelperBinding
fun bindExtrinsicStatus(
    dynamicInstance: GenericEvent.Instance,
    runtime: RuntimeSnapshot
): ExtrinsicStatusEvent {
    val systemModule = runtime.metadata.module("System")

    return when (dynamicInstance.index) {
        systemModule.event("ExtrinsicFailed").index -> ExtrinsicStatusEvent.FAILURE
        systemModule.event("ExtrinsicSuccess").index -> ExtrinsicStatusEvent.SUCCESS
        else -> incompatible()
    }
}

@UseCaseBinding
fun bindExtrinsicStatusEventRecords(
    scale: String,
    runtime: RuntimeSnapshot
): List<EventRecord<ExtrinsicStatusEvent>> {
    val returnType = runtime.metadata.module("System").storage("Events").type.value ?: incompatible()

    val dynamicInstance = returnType.fromHex(runtime, scale)
    requireType<List<*>>(dynamicInstance)

    return dynamicInstance.mapNotNull { dynamicEventRecord ->
        bindOrNull {
            bindEventRecord(dynamicEventRecord) { bindExtrinsicStatus(it, runtime) }
        }
    }
}
