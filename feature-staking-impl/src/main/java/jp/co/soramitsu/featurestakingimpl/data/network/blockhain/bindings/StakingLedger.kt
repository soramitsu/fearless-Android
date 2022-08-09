package jp.co.soramitsu.featurestakingimpl.data.network.blockhain.bindings

import jp.co.soramitsu.common.data.network.runtime.binding.HelperBinding
import jp.co.soramitsu.common.data.network.runtime.binding.UseCaseBinding
import jp.co.soramitsu.common.data.network.runtime.binding.getList
import jp.co.soramitsu.common.data.network.runtime.binding.getTyped
import jp.co.soramitsu.common.data.network.runtime.binding.incompatible
import jp.co.soramitsu.common.data.network.runtime.binding.requireType
import jp.co.soramitsu.common.data.network.runtime.binding.returnType
import jp.co.soramitsu.common.utils.staking
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.fromHexOrNull
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.featurestakingapi.domain.model.StakingLedger
import jp.co.soramitsu.featurestakingapi.domain.model.UnlockChunk

@UseCaseBinding
fun bindStakingLedger(scale: String, runtime: RuntimeSnapshot): StakingLedger {
    val type = runtime.metadata.staking().storage("Ledger").returnType()
    val dynamicInstance = type.fromHexOrNull(runtime, scale) ?: incompatible()
    requireType<Struct.Instance>(dynamicInstance)

    return StakingLedger(
        stashId = dynamicInstance.getTyped("stash"),
        total = dynamicInstance.getTyped("total"),
        active = dynamicInstance.getTyped("active"),
        unlocking = dynamicInstance.getList("unlocking").map(::bindUnlockChunk),
        claimedRewards = dynamicInstance.getList("claimedRewards").map(::bindEraIndex)
    )
}

@HelperBinding
fun bindUnlockChunk(dynamicInstance: Any?): UnlockChunk {
    requireType<Struct.Instance>(dynamicInstance)

    return UnlockChunk(
        amount = dynamicInstance.getTyped("value"),
        era = dynamicInstance.getTyped("era")
    )
}
