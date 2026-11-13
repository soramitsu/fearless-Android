package jp.co.soramitsu.staking.impl.data.network.blockhain.calls

import java.math.BigInteger
import jp.co.soramitsu.common.utils.staking
import jp.co.soramitsu.core.models.MultiAddress
import jp.co.soramitsu.core.models.bindMultiAddress
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.DictEnum
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.bytes
import jp.co.soramitsu.fearless_utils.runtime.metadata.call
import jp.co.soramitsu.fearless_utils.runtime.metadata.module
import jp.co.soramitsu.fearless_utils.runtime.metadata.moduleOrNull
import jp.co.soramitsu.runtime.assethub.AssetHubRuntimeTypes
import jp.co.soramitsu.staking.api.domain.model.RewardDestination
import jp.co.soramitsu.staking.impl.data.network.blockhain.bindings.bindRewardDestination

fun ExtrinsicBuilder.setController(controllerAddress: MultiAddress): ExtrinsicBuilder {
    return stakingCall(
        "Staking",
        "set_controller",
        mapOf(
            "controller" to bindMultiAddress(controllerAddress)
        )
    )
}

fun ExtrinsicBuilder.setController(): ExtrinsicBuilder {
    return stakingCall(
        "Staking",
        "set_controller",
        emptyMap()
    )
}

fun ExtrinsicBuilder.setControllerSora(controllerAccountId: AccountId): ExtrinsicBuilder {
    return stakingCall(
        "Staking",
        "set_controller",
        mapOf(
            "controller" to controllerAccountId
        )
    )
}

/** Current runtimes, Asset Hub staking among them, dropped the controller from `bond`. */
fun ExtrinsicBuilder.bond(
    controllerAddress: MultiAddress,
    amount: BigInteger,
    payee: RewardDestination
): ExtrinsicBuilder {
    val arguments = mutableMapOf<String, Any?>("value" to amount, "payee" to bindRewardDestination(payee))
    val names = runtime.metadata.staking().call("bond").arguments.map { it.name }.toSet()
    require(names == setOf("value", "payee") || names == setOf("controller", "value", "payee")) {
        "Unsupported staking bond arguments"
    }
    if ("controller" in names) arguments["controller"] = bindMultiAddress(controllerAddress)
    return stakingCall("Staking", "bond", arguments)
}

fun ExtrinsicBuilder.nominate(targets: List<MultiAddress>): ExtrinsicBuilder {
    return stakingCall(
        "Staking",
        "nominate",
        mapOf(
            "targets" to targets.map(::bindMultiAddress)
        )
    )
}

fun ExtrinsicBuilder.bondSora(
    controllerAddress: AccountId,
    amount: BigInteger,
    payee: RewardDestination
): ExtrinsicBuilder {
    return stakingCall(
        "Staking",
        "bond",
        mapOf(
            "controller" to controllerAddress,
            "value" to amount,
            "payee" to bindRewardDestination(payee)
        )
    )
}

fun ExtrinsicBuilder.nominateSora(targets: List<AccountId>): ExtrinsicBuilder {
    return stakingCall(
        "Staking",
        "nominate",
        mapOf(
            "targets" to targets
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

/** Paged exposures pay one page per call; [page] is null for runtimes before paged rewards. */
fun ExtrinsicBuilder.payoutStakers(era: BigInteger, validatorId: AccountId, page: BigInteger? = null): ExtrinsicBuilder {
    if (page != null) {
        require(page.signum() >= 0)
        return stakingCall(
            "Staking",
            "payout_stakers_by_page",
            mapOf("validator_stash" to validatorId, "era" to era, "page" to page)
        )
    }
    return stakingCall(
        "Staking",
        "payout_stakers",
        mapOf(
            "validator_stash" to validatorId,
            "era" to era
        )
    )
}

fun ExtrinsicBuilder.bondMore(amount: BigInteger): ExtrinsicBuilder {
    return stakingCall(
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
    return stakingCall("Staking", "chill", emptyMap())
}

fun ExtrinsicBuilder.unbond(amount: BigInteger): ExtrinsicBuilder {
    return stakingCall(
        "Staking",
        "unbond",
        mapOf(
            "value" to amount
        )
    )
}

fun ExtrinsicBuilder.withdrawUnbonded(numberOfSlashingSpans: BigInteger): ExtrinsicBuilder {
    return stakingCall(
        "Staking",
        "withdraw_unbonded",
        mapOf(
            "num_slashing_spans" to numberOfSlashingSpans
        )
    )
}

fun ExtrinsicBuilder.rebond(amount: BigInteger): ExtrinsicBuilder {
    return stakingCall(
        "Staking",
        "rebond",
        mapOf(
            "value" to amount
        )
    )
}

fun ExtrinsicBuilder.setPayee(rewardDestination: RewardDestination): ExtrinsicBuilder {
    return stakingCall(
        "Staking",
        "set_payee",
        mapOf(
            "payee" to bindRewardDestination(rewardDestination)
        )
    )
}

fun ExtrinsicBuilder.joinPool(amount: BigInteger, poolId: BigInteger): ExtrinsicBuilder {
    return stakingCall(
        "NominationPools",
        "join",
        mapOf(
            "amount" to amount,
            "pool_id" to poolId
        )
    )
}

// TODO rename to createPool when polkadot runtime upgrades to 9390
fun ExtrinsicBuilder.createPoolBouncer(amount: BigInteger, root: MultiAddress, nominator: MultiAddress, stateToggler: MultiAddress): ExtrinsicBuilder {
    return stakingCall(
        "NominationPools",
        "create",
        mapOf(
            "amount" to amount,
            "root" to bindMultiAddress(root),
            "nominator" to bindMultiAddress(nominator),
            "bouncer" to bindMultiAddress(stateToggler)
        )
    )
}

@Deprecated("Replace with createPoolBouncer when polkadot runtime upgrades to 9390", replaceWith = ReplaceWith("ExtrinsicBuilder.createPoolBouncer"))
fun ExtrinsicBuilder.createPoolStateToggler(amount: BigInteger, root: MultiAddress, nominator: MultiAddress, stateToggler: MultiAddress): ExtrinsicBuilder {
    return stakingCall(
        "NominationPools",
        "create",
        mapOf(
            "amount" to amount,
            "root" to bindMultiAddress(root),
            "nominator" to bindMultiAddress(nominator),
            "state_toggler" to bindMultiAddress(stateToggler)
        )
    )
}

fun ExtrinsicBuilder.setPoolMetadata(poolId: BigInteger, poolName: ByteArray): ExtrinsicBuilder {
    return stakingCall(
        "NominationPools",
        "set_metadata",
        mapOf(
            "pool_id" to poolId,
            "metadata" to poolName
        )
    )
}

fun ExtrinsicBuilder.nominatePool(poolId: BigInteger, validators: List<AccountId>): ExtrinsicBuilder {
    return stakingCall(
        "NominationPools",
        "nominate",
        mapOf(
            "pool_id" to poolId,
            "validators" to validators
        )
    )
}

fun ExtrinsicBuilder.claimPayout(): ExtrinsicBuilder {
    return stakingCall("NominationPools", "claim_payout", emptyMap())
}

/** Choose the member format from this exact builder runtime before the single submission. */
fun ExtrinsicBuilder.withdrawUnbondedFromPool(
    memberAccountId: AccountId,
    memberAddress: MultiAddress,
    numSlashingSpans: BigInteger = BigInteger.ZERO
): ExtrinsicBuilder = stakingCall(
    "NominationPools",
    "withdraw_unbonded",
    mapOf(
        "member_account" to poolMemberAccount("withdraw_unbonded", memberAccountId, memberAddress),
        "num_slashing_spans" to numSlashingSpans
    )
)

/** Runtime encoding compatibility must never retry a failed or cancelled network submission. */
fun ExtrinsicBuilder.unbondFromPool(
    memberAccountId: AccountId,
    memberAddress: MultiAddress,
    unbondingPoints: BigInteger
): ExtrinsicBuilder = stakingCall(
    "NominationPools",
    "unbond",
    mapOf(
        "member_account" to poolMemberAccount("unbond", memberAccountId, memberAddress),
        "unbonding_points" to unbondingPoints
    )
)

private fun ExtrinsicBuilder.poolMemberAccount(
    callName: String,
    accountId: AccountId,
    multiAddress: MultiAddress
): Any {
    val memberType = requireNotNull(
        runtime.metadata.module("NominationPools").call(callName)
            .arguments.single { it.name == "member_account" }.type
    ) { "Pool member account type is unresolved" }
    require(memberType.isFullyResolved) { "Pool member account type is unresolved" }
    val boundAddress = bindMultiAddress(multiAddress)
    val member = when {
        memberType.isValidInstance(boundAddress) -> boundAddress
        memberType.isValidInstance(accountId) -> accountId
        else -> error("Unsupported pool member account type")
    }
    // Complete public SCALE validation before adding the call; no RPC or signing fallback.
    memberType.bytes(runtime, member)
    return member
}

fun ExtrinsicBuilder.bondExtra(amount: BigInteger): ExtrinsicBuilder {
    return stakingCall(
        "NominationPools",
        "bond_extra",
        mapOf(
            "extra" to DictEnum.Entry("FreeBalance", amount)
        )
    )
}

fun ExtrinsicBuilder.updateRolesStateToggler(
    poolId: BigInteger,
    root: DictEnum.Entry<AccountId?>,
    nominator: DictEnum.Entry<AccountId?>,
    stateToggler: DictEnum.Entry<AccountId?>
): ExtrinsicBuilder {
    return stakingCall(
        "NominationPools",
        "update_roles",
        mapOf(
            "pool_id" to poolId,
            "new_root" to root,
            "new_nominator" to nominator,
            "new_state_toggler" to stateToggler
        )
    )
}

fun ExtrinsicBuilder.updateRolesBouncer(
    poolId: BigInteger,
    root: DictEnum.Entry<AccountId?>,
    nominator: DictEnum.Entry<AccountId?>,
    stateToggler: DictEnum.Entry<AccountId?>
): ExtrinsicBuilder {
    return stakingCall(
        "NominationPools",
        "update_roles",
        mapOf(
            "pool_id" to poolId,
            "new_root" to root,
            "new_nominator" to nominator,
            "new_bouncer" to stateToggler
        )
    )
}

/**
 * Asset Hub staking runtimes carry `StakingRcClient`. Their staking and pool transactions must sign over the
 * complete context: genesis, spec and transaction versions, mortality, nonce, metadata mode and the native fee.
 */
private fun ExtrinsicBuilder.stakingCall(
    moduleName: String,
    callName: String,
    arguments: Map<String, Any?>
): ExtrinsicBuilder {
    if (runtime.metadata.moduleOrNull("StakingRcClient") != null) {
        AssetHubRuntimeTypes.requireSigningExtensions(runtime.metadata.extrinsic.signedExtensions)
    }
    return call(moduleName, callName, arguments)
}
