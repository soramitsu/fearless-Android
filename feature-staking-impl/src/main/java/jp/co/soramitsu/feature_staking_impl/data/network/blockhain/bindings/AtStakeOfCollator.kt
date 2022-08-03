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
import jp.co.soramitsu.feature_staking_api.domain.model.AtStake
import java.math.BigInteger

@UseCaseBinding
fun bindAtStakeOfCollator(scale: String, runtime: RuntimeSnapshot): AtStake {
    val type = runtime.metadata.parachainStaking().storage("AtStake").returnType()

    val dynamicInstance = type.fromHexOrNull(runtime, scale)
    requireType<Struct.Instance>(dynamicInstance)

    val list = dynamicInstance.getList("delegations")
    return AtStake(
        bond = dynamicInstance.get<BigInteger>("bond") ?: BigInteger.ZERO,
        total = dynamicInstance.get<BigInteger>("total") ?: BigInteger.ZERO,
        delegations = list.map {
            requireType<Struct.Instance>(it)
            (it.get<ByteArray>("owner") ?: incompatible()) to (it.get<BigInteger>("amount") ?: incompatible())
        }
    )
}
