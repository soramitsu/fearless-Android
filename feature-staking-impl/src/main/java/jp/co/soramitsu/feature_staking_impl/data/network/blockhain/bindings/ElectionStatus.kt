package jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings

import jp.co.soramitsu.common.data.network.runtime.binding.UseCaseBinding
import jp.co.soramitsu.common.data.network.runtime.binding.cast
import jp.co.soramitsu.common.data.network.runtime.binding.incompatible
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.DictEnum
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.fromHex
import jp.co.soramitsu.feature_staking_api.domain.model.ElectionStatus

@UseCaseBinding
fun bindElectionStatus(
    scale: String,
    runtime: RuntimeSnapshot,
): ElectionStatus {
    val type = runtime.typeRegistry["ElectionStatus"] ?: incompatible()

    val dynamicInstance: DictEnum.Entry<Any?> = type.fromHex(runtime, scale).cast()

    return when (dynamicInstance.name) {
        "Close" -> ElectionStatus.Close
        "Open" -> ElectionStatus.Open(
            block = bindBlockNumber(dynamicInstance.value)
        )
        else -> incompatible()
    }
}
