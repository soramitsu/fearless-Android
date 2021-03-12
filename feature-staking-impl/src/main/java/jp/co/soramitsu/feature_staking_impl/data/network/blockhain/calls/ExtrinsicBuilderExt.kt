package jp.co.soramitsu.feature_staking_impl.data.network.blockhain.calls

import jp.co.soramitsu.common.data.network.runtime.binding.MultiAddress
import jp.co.soramitsu.common.data.network.runtime.binding.bindMultiAddress
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.bindRewardDestination
import jp.co.soramitsu.feature_staking_impl.domain.model.RewardDestination
import java.math.BigInteger

fun ExtrinsicBuilder.setController(controllerAddress: MultiAddress): ExtrinsicBuilder {
    return call(
        "Staking", "set_controller",
        mapOf(
            "controller" to bindMultiAddress(controllerAddress)
        )
    )
}

fun ExtrinsicBuilder.bond(
    controllerAddress: MultiAddress,
    amount: BigInteger,
    payee: RewardDestination
): ExtrinsicBuilder {
    return call(
        "Staking", "bond",
        mapOf(
            "controller" to bindMultiAddress(controllerAddress),
            "value" to amount,
            "payee" to bindRewardDestination(payee)
        )
    )
}

fun ExtrinsicBuilder.nominate(targets: List<MultiAddress>): ExtrinsicBuilder {
    return call(
        "Staking", "nominate",
        mapOf(
            "targets" to targets.map(::bindMultiAddress)
        )
    )
}
