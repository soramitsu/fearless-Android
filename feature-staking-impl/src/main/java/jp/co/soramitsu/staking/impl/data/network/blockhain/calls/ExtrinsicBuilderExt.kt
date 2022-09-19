package jp.co.soramitsu.staking.impl.data.network.blockhain.calls

import java.math.BigInteger
import jp.co.soramitsu.common.data.network.runtime.binding.MultiAddress
import jp.co.soramitsu.common.data.network.runtime.binding.bindMultiAddress
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.DictEnum
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.staking.api.domain.model.RewardDestination
import jp.co.soramitsu.staking.impl.data.network.blockhain.bindings.bindRewardDestination

fun ExtrinsicBuilder.setController(controllerAddress: MultiAddress): ExtrinsicBuilder {
    return call(
        "Staking",
        "set_controller",
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
        "Staking",
        "bond",
        mapOf(
            "controller" to bindMultiAddress(controllerAddress),
            "value" to amount,
            "payee" to bindRewardDestination(payee)
        )
    )
}

fun ExtrinsicBuilder.nominate(targets: List<MultiAddress>): ExtrinsicBuilder {
    return call(
        "Staking",
        "nominate",
        mapOf(
            "targets" to targets.map(::bindMultiAddress)
        )
    )
}

fun ExtrinsicBuilder.delegate(
    candidateId: AccountId,
    amountInPlanks: BigInteger,
    candidateDelegationCount: BigInteger,
    delegationCount: BigInteger
): ExtrinsicBuilder {
    return call(
        "ParachainStaking",
        "delegate",
        mapOf(
            "candidate" to candidateId,
            "amount" to amountInPlanks,
            "candidate_delegation_count" to candidateDelegationCount,
            "delegation_count" to delegationCount
        )
    )
}

fun ExtrinsicBuilder.payoutStakers(era: BigInteger, validatorId: AccountId): ExtrinsicBuilder {
    return call(
        "Staking",
        "payout_stakers",
        mapOf(
            "validator_stash" to validatorId,
            "era" to era
        )
    )
}

fun ExtrinsicBuilder.bondMore(amount: BigInteger): ExtrinsicBuilder {
    return call(
        "Staking",
        "bond_extra",
        mapOf(
            "max_additional" to amount
        )
    )
}

fun ExtrinsicBuilder.parachainCandidateBondMore(amount: BigInteger): ExtrinsicBuilder {
    return call(
        "ParachainStaking",
        "candidate_bond_more",
        mapOf(
            "more" to amount
        )
    )
}

fun ExtrinsicBuilder.parachainScheduleCandidateBondLess(amount: BigInteger): ExtrinsicBuilder {
    return call(
        "ParachainStaking",
        "schedule_candidate_bond_less",
        mapOf(
            "less" to amount
        )
    )
}

fun ExtrinsicBuilder.parachainDelegatorBondMore(candidate: String, amount: BigInteger): ExtrinsicBuilder {
    return call(
        "ParachainStaking",
        "delegator_bond_more",
        mapOf(
            "candidate" to candidate.fromHex(),
            "more" to amount
        )
    )
}

fun ExtrinsicBuilder.parachainScheduleDelegatorBondLess(candidate: String, amount: BigInteger): ExtrinsicBuilder {
    return call(
        "ParachainStaking",
        "schedule_delegator_bond_less",
        mapOf(
            "candidate" to candidate.fromHex(),
            "less" to amount
        )
    )
}

fun ExtrinsicBuilder.parachainScheduleRevokeDelegation(collator: String): ExtrinsicBuilder {
    return call(
        "ParachainStaking",
        "schedule_revoke_delegation",
        mapOf(
            "collator" to collator.fromHex()
        )
    )
}

fun ExtrinsicBuilder.parachainExecuteDelegationRequest(
    candidateId: AccountId,
    delegatorId: AccountId
): ExtrinsicBuilder {
    return call(
        "ParachainStaking",
        "execute_delegation_request",
        mapOf(
            "delegator" to delegatorId,
            "candidate" to candidateId
        )
    )
}

fun ExtrinsicBuilder.parachainCancelDelegationRequest(candidate: String): ExtrinsicBuilder {
    return call(
        "ParachainStaking",
        "cancel_delegation_request",
        mapOf(
            "candidate" to candidate.fromHex()
        )
    )
}

fun ExtrinsicBuilder.chill(): ExtrinsicBuilder {
    return call("Staking", "chill", emptyMap())
}

fun ExtrinsicBuilder.unbond(amount: BigInteger): ExtrinsicBuilder {
    return call(
        "Staking",
        "unbond",
        mapOf(
            "value" to amount
        )
    )
}

fun ExtrinsicBuilder.withdrawUnbonded(numberOfSlashingSpans: BigInteger): ExtrinsicBuilder {
    return call(
        "Staking",
        "withdraw_unbonded",
        mapOf(
            "num_slashing_spans" to numberOfSlashingSpans
        )
    )
}

fun ExtrinsicBuilder.rebond(amount: BigInteger): ExtrinsicBuilder {
    return call(
        "Staking",
        "rebond",
        mapOf(
            "value" to amount
        )
    )
}

fun ExtrinsicBuilder.setPayee(rewardDestination: RewardDestination): ExtrinsicBuilder {
    return call(
        "Staking",
        "set_payee",
        mapOf(
            "payee" to bindRewardDestination(rewardDestination)
        )
    )
}

fun ExtrinsicBuilder.joinPool(amount: BigInteger, poolId: BigInteger): ExtrinsicBuilder {
    return call(
        "NominationPools",
        "join",
        mapOf(
            "amount" to amount,
            "pool_id" to poolId
        )
    )
}

fun ExtrinsicBuilder.createPool(amount: BigInteger, root: AccountId, nominator: AccountId, stateToggler: AccountId): ExtrinsicBuilder {
    return call(
        "NominationPools",
        "create",
        mapOf(
            "amount" to amount,
            "root" to root,
            "nominator" to nominator,
            "state_toggler" to stateToggler
        )
    )
}

fun ExtrinsicBuilder.setPoolMetadata(poolId: BigInteger, poolName: ByteArray): ExtrinsicBuilder {
    return call(
        "NominationPools",
        "set_metadata",
        mapOf(
            "pool_id" to poolId,
            "metadata" to poolName
        )
    )
}

fun ExtrinsicBuilder.nominatePool(poolId: BigInteger, validators: List<AccountId>): ExtrinsicBuilder {
    return call(
        "NominationPools",
        "nominate",
        mapOf(
            "pool_id" to poolId,
            "validators" to validators
        )
    )
}

fun ExtrinsicBuilder.claimPayout(): ExtrinsicBuilder {
    return call("NominationPools", "claim_payout", emptyMap())
}

fun ExtrinsicBuilder.withdrawUnbondedFromPool(memberAccountId: MultiAddress, numSlashingSpans: BigInteger = BigInteger.ZERO): ExtrinsicBuilder {
    return call(
        "NominationPools",
        "withdraw_unbonded",
        mapOf(
            "member_account" to bindMultiAddress(memberAccountId),
            "num_slashing_spans" to numSlashingSpans
        )
    )
}

fun ExtrinsicBuilder.unbondFromPool(memberAccountId: MultiAddress, unbondingPoints: BigInteger): ExtrinsicBuilder {
    return call(
        "NominationPools",
        "unbond",
        mapOf(
            "member_account" to bindMultiAddress(memberAccountId),
            "unbonding_points" to unbondingPoints
        )
    )
}

fun ExtrinsicBuilder.bondExtra(amount: BigInteger): ExtrinsicBuilder {
    return call(
        "NominationPools",
        "bond_extra",
        mapOf(
            "extra" to DictEnum.Entry("FreeBalance", amount)
        )
    )
}
