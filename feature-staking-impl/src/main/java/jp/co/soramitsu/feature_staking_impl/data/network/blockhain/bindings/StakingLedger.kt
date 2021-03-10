package jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings

import jp.co.soramitsu.common.data.network.runtime.binding.HelperBinding
import jp.co.soramitsu.common.data.network.runtime.binding.UseCaseBinding
import jp.co.soramitsu.common.data.network.runtime.binding.getList
import jp.co.soramitsu.common.data.network.runtime.binding.getTyped
import jp.co.soramitsu.common.data.network.runtime.binding.incompatible
import jp.co.soramitsu.common.data.network.runtime.binding.requireType
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.fromHexOrNull
import java.math.BigInteger

class StakingLedger(
    val stashId: AccountId,
    val total: BigInteger,
    val active: BigInteger,
    val unlocking: List<UnlockChunk>,
    val claimedRewards: List<BigInteger>
) {

    companion object {

        fun empty(stashId: AccountId): StakingLedger {
            return StakingLedger(
                stashId = stashId,
                total = BigInteger.ZERO,
                active = BigInteger.ZERO,
                unlocking = emptyList(),
                claimedRewards = emptyList()
            )
        }
    }
}

class UnlockChunk(val amount: BigInteger, val era: BigInteger)

@UseCaseBinding
fun bindStakingLedger(scale: String, runtime: RuntimeSnapshot): StakingLedger {
    val type = runtime.typeRegistry["StakingLedger"] ?: incompatible()
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