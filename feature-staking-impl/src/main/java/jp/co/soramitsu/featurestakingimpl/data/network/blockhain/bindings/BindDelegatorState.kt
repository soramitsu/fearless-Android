package jp.co.soramitsu.featurestakingimpl.data.network.blockhain.bindings

import jp.co.soramitsu.common.data.network.runtime.binding.UseCaseBinding
import jp.co.soramitsu.common.data.network.runtime.binding.getList
import jp.co.soramitsu.common.data.network.runtime.binding.getTyped
import jp.co.soramitsu.common.data.network.runtime.binding.incompatible
import jp.co.soramitsu.common.data.network.runtime.binding.requireType
import jp.co.soramitsu.common.data.network.runtime.binding.returnType
import jp.co.soramitsu.common.utils.parachainStaking
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.DictEnum
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.fromHexOrNull
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.featurestakingapi.domain.model.Delegation
import jp.co.soramitsu.featurestakingapi.domain.model.DelegatorState
import jp.co.soramitsu.featurestakingapi.domain.model.DelegatorStateStatus

@UseCaseBinding
fun bindDelegatorState(scale: String, runtime: RuntimeSnapshot): DelegatorState {
    val type = runtime.metadata.parachainStaking().storage("DelegatorState").returnType()

    val dynamicInstance = type.fromHexOrNull(runtime, scale)
    requireType<Struct.Instance>(dynamicInstance)

    val delegations = (dynamicInstance.getList("delegations")).map { it as Struct.Instance }
        .map { Delegation(it["owner"] ?: incompatible(), it["amount"] ?: incompatible()) }

    val status = DelegatorStateStatus.from(dynamicInstance.getTyped<DictEnum.Entry<*>>("status").name)

    return DelegatorState(
        id = dynamicInstance.get<AccountId>("id") ?: incompatible(),
        delegations = delegations,
        total = dynamicInstance["total"] ?: incompatible(),
        status = status
    )
}
