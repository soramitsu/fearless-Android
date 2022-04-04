package jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings

import jp.co.soramitsu.common.data.network.runtime.binding.UseCaseBinding
import jp.co.soramitsu.common.data.network.runtime.binding.getList
import jp.co.soramitsu.common.data.network.runtime.binding.incompatible
import jp.co.soramitsu.common.data.network.runtime.binding.requireType
import jp.co.soramitsu.common.data.network.runtime.binding.returnType
import jp.co.soramitsu.common.utils.parachainStaking
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.fromHexOrNull
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.feature_staking_api.domain.model.Delegation
import jp.co.soramitsu.feature_staking_api.domain.model.DelegatorState
import jp.co.soramitsu.feature_staking_api.domain.model.DelegatorStateStatus

@UseCaseBinding
fun bindDelegatorState(scale: String, runtime: RuntimeSnapshot): DelegatorState {
    val type = runtime.metadata.parachainStaking().storage("DelegatorState").returnType()

    val dynamicInstance = type.fromHexOrNull(runtime, scale)
    requireType<Struct.Instance>(dynamicInstance)

    return DelegatorState(
        id = dynamicInstance.get<String>("id") ?: incompatible(),
        delegations = dynamicInstance.getList("delegations").map { it as Delegation },
        total = dynamicInstance["total"] ?: incompatible(),
        status = DelegatorStateStatus.from(dynamicInstance["status"])
    )
}
