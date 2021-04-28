package jp.co.soramitsu.feature_staking_impl.data.network.blockhain.calls

import jp.co.soramitsu.common.data.network.runtime.binding.MultiAddress
import jp.co.soramitsu.common.data.network.runtime.binding.bindMultiAddress
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.feature_staking_api.domain.model.RewardDestination
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.bindRewardDestination
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
    payee: RewardDestination,
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

fun ExtrinsicBuilder.payoutStakers(era: BigInteger, validatorId: AccountId): ExtrinsicBuilder {
    return call(
        "Staking", "payout_stakers",
        mapOf(
            "validator_stash" to validatorId,
            "era" to era
        )
    )
}

fun ExtrinsicBuilder.bondMore(amount: BigInteger): ExtrinsicBuilder {
    return call(
        "Staking", "bond_extra",
        mapOf(
            "max_additional" to amount
        )
    )
}

fun ExtrinsicBuilder.unbond(amount: BigInteger): ExtrinsicBuilder {
    return call(
        "Staking", "unbond",
        mapOf(
            "value" to amount
        )
    )
}
