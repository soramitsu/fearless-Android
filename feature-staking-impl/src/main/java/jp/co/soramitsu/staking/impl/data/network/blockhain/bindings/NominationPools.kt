package jp.co.soramitsu.staking.impl.data.network.blockhain.bindings

import jp.co.soramitsu.common.data.network.runtime.binding.UseCaseBinding
import jp.co.soramitsu.common.data.network.runtime.binding.bindNumber
import jp.co.soramitsu.common.data.network.runtime.binding.bindString
import jp.co.soramitsu.common.data.network.runtime.binding.fromHexOrIncompatible
import jp.co.soramitsu.common.data.network.runtime.binding.getTyped
import jp.co.soramitsu.common.data.network.runtime.binding.incompatible
import jp.co.soramitsu.common.data.network.runtime.binding.requireType
import jp.co.soramitsu.common.data.network.runtime.binding.storageReturnType
import jp.co.soramitsu.common.utils.nominationPools
import jp.co.soramitsu.common.utils.second
import jp.co.soramitsu.core.runtime.storage.returnType
import jp.co.soramitsu.shared_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.shared_utils.runtime.definitions.types.composite.DictEnum
import jp.co.soramitsu.shared_utils.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.shared_utils.runtime.definitions.types.fromHexOrNull
import jp.co.soramitsu.shared_utils.runtime.metadata.storage
import jp.co.soramitsu.staking.impl.data.model.BondedPool
import jp.co.soramitsu.staking.impl.data.model.BondedPoolState
import jp.co.soramitsu.staking.impl.data.model.PoolMember
import jp.co.soramitsu.staking.impl.data.model.PoolRewards
import jp.co.soramitsu.staking.impl.data.model.PoolUnbonding
import java.math.BigInteger

@UseCaseBinding
fun bindMinJoinBond(
    scale: String,
    runtime: RuntimeSnapshot
): BigInteger {
    val returnType = runtime.metadata.storageReturnType("NominationPools", "MinJoinBond")

    return bindNumber(returnType.fromHexOrIncompatible(scale, runtime))
}

@UseCaseBinding
fun bindMinCreateBond(
    scale: String,
    runtime: RuntimeSnapshot
): BigInteger {
    val returnType = runtime.metadata.storageReturnType("NominationPools", "MinCreateBond")

    return bindNumber(returnType.fromHexOrIncompatible(scale, runtime))
}

@UseCaseBinding
fun bindMaxPools(
    scale: String?,
    runtime: RuntimeSnapshot
): BigInteger? {
    scale ?: return null
    val returnType = runtime.metadata.storageReturnType("NominationPools", "MaxPools")

    return bindNumber(returnType.fromHexOrIncompatible(scale, runtime))
}

@UseCaseBinding
fun bindMaxPoolMembers(
    scale: String?,
    runtime: RuntimeSnapshot
): BigInteger? {
    scale ?: return null
    val returnType = runtime.metadata.storageReturnType("NominationPools", "MaxPoolMembersPerPool")

    return bindNumber(returnType.fromHexOrIncompatible(scale, runtime))
}

@UseCaseBinding
fun bindMaxMembersInPool(
    scale: String?,
    runtime: RuntimeSnapshot
): BigInteger? {
    scale ?: return null
    val returnType = runtime.metadata.storageReturnType("NominationPools", "MaxPoolMembers")

    return bindNumber(returnType.fromHexOrIncompatible(scale, runtime))
}

@UseCaseBinding
fun bindExistingPools(
    scale: String?,
    runtime: RuntimeSnapshot
): BigInteger {
    scale ?: return BigInteger.ZERO
    val returnType = runtime.metadata.storageReturnType("NominationPools", "CounterForBondedPools")

    return bindNumber(returnType.fromHexOrIncompatible(scale, runtime))
}

@UseCaseBinding
fun bindLastPoolId(
    scale: String?,
    runtime: RuntimeSnapshot
): BigInteger {
    scale ?: return BigInteger.ZERO
    val returnType = runtime.metadata.storageReturnType("NominationPools", "LastPoolId")

    return bindNumber(returnType.fromHexOrIncompatible(scale, runtime))
}

@UseCaseBinding
fun bindPoolsCount(
    scale: String?,
    runtime: RuntimeSnapshot
): BigInteger {
    scale ?: return BigInteger.ZERO
    val returnType = runtime.metadata.storageReturnType("NominationPools", "CounterForBondedPools")

    return bindNumber(returnType.fromHexOrIncompatible(scale, runtime))
}

@UseCaseBinding
fun bindBondedPool(
    scale: String?,
    runtime: RuntimeSnapshot
): BondedPool? {
    scale ?: return null
    val returnType = runtime.metadata.storageReturnType("NominationPools", "BondedPools")
    val decoded = returnType.fromHexOrNull(runtime, scale) as? Struct.Instance ?: incompatible()

    val points = bindNumber(decoded.getTyped("points"))
    val state = BondedPoolState.from(decoded.getTyped<DictEnum.Entry<*>>("state").name)
    val memberCounter = bindNumber(decoded.getTyped("memberCounter"))
    val roles = decoded.getTyped<Struct.Instance>("roles")
    val depositor = roles.get<ByteArray>("depositor") ?: error("Cannot bind BondedPool.depositor")
    val root = roles.get<ByteArray>("root")
    val nominator = roles.get<ByteArray>("nominator")
    val stateToggler = roles.get<ByteArray>("stateToggler") ?: roles.get<ByteArray>("bouncer")

    return BondedPool(points, state, memberCounter, depositor, root, nominator, stateToggler)
}

@UseCaseBinding
fun bindBondedPoolsMetadata(
    scale: String,
    runtime: RuntimeSnapshot
): String {
    val returnType = runtime.metadata.storageReturnType("NominationPools", "Metadata")
    val decoded = returnType.fromHexOrNull(runtime, scale)
    return bindString(decoded)
}

@UseCaseBinding
fun bindPoolMember(scale: String?, runtime: RuntimeSnapshot): PoolMember? {
    scale ?: return null
    val type = runtime.metadata.nominationPools().storage("PoolMembers").returnType()

    val dynamicInstance = type.fromHexOrNull(runtime, scale)
    requireType<Struct.Instance>(dynamicInstance)

    val poolId = dynamicInstance.get<BigInteger>("poolId") ?: incompatible()
    val points = dynamicInstance.get<BigInteger>("points") ?: incompatible() // bonded
    val lastRecordedRewardCounter = dynamicInstance.get<BigInteger>("lastRecordedRewardCounter") ?: incompatible()

    val unbondings = dynamicInstance.get<List<List<BigInteger>>>("unbondingEras")?.map {
        PoolUnbonding(it.first(), it.second()) // era to amount
    } ?: emptyList()

    return PoolMember(
        poolId,
        points,
        lastRecordedRewardCounter,
        unbondings
    )
}

@UseCaseBinding
fun bindRewardPool(
    scale: String?,
    runtime: RuntimeSnapshot
): PoolRewards? {
    scale ?: return null
    val returnType = runtime.metadata.storageReturnType("NominationPools", "RewardPools")
    val decoded = returnType.fromHexOrNull(runtime, scale) as? Struct.Instance ?: incompatible()

    val lastRecordedRewardCounter = bindNumber(decoded.getTyped("lastRecordedRewardCounter"))
    val lastRecordedTotalPayouts = bindNumber(decoded.getTyped("lastRecordedTotalPayouts"))
    val totalRewardsClaimed = bindNumber(decoded.getTyped("totalRewardsClaimed"))

    return PoolRewards(lastRecordedRewardCounter, lastRecordedTotalPayouts, totalRewardsClaimed)
}
