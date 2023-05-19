package jp.co.soramitsu.staking.impl.data.network.blockhain.bindings

import jp.co.soramitsu.common.data.network.runtime.binding.UseCaseBinding
import jp.co.soramitsu.common.data.network.runtime.binding.getTyped
import jp.co.soramitsu.common.data.network.runtime.binding.incompatible
import jp.co.soramitsu.common.data.network.runtime.binding.requireType
import jp.co.soramitsu.common.data.network.runtime.binding.returnType
import jp.co.soramitsu.common.utils.parachainStaking
import jp.co.soramitsu.shared_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.shared_utils.runtime.definitions.types.composite.DictEnum
import jp.co.soramitsu.shared_utils.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.shared_utils.runtime.definitions.types.fromHexOrNull
import jp.co.soramitsu.shared_utils.runtime.metadata.storage
import jp.co.soramitsu.staking.api.domain.model.CandidateCapacity
import jp.co.soramitsu.staking.api.domain.model.CandidateInfo
import jp.co.soramitsu.staking.api.domain.model.CandidateInfoStatus
import java.math.BigInteger

@UseCaseBinding
fun bindCandidateInfo(scale: String, runtime: RuntimeSnapshot): CandidateInfo {
    val type = runtime.metadata.parachainStaking().storage("CandidateInfo").returnType()

    val dynamicInstance = type.fromHexOrNull(runtime, scale)
    requireType<Struct.Instance>(dynamicInstance)

    val topCapacity = CandidateCapacity.from(dynamicInstance.getTyped<DictEnum.Entry<*>>("topCapacity").name)
    val bottomCapacity = CandidateCapacity.from(dynamicInstance.getTyped<DictEnum.Entry<*>>("bottomCapacity").name)
    val status = dynamicInstance.getTyped<DictEnum.Entry<*>>("status")
    val parsedStatus = if (status.name == "Leaving") {
        val leavingRound = status.value as? BigInteger
        CandidateInfoStatus.LEAVING(leavingRound?.toLong())
    } else {
        CandidateInfoStatus.from(status.name)
    }

    return CandidateInfo(
        bond = dynamicInstance["bond"] ?: incompatible(),
        delegationCount = dynamicInstance["delegationCount"] ?: incompatible(),
        totalCounted = dynamicInstance["totalCounted"] ?: incompatible(),
        lowestTopDelegationAmount = dynamicInstance["lowestTopDelegationAmount"] ?: incompatible(),
        highestBottomDelegationAmount = dynamicInstance["highestBottomDelegationAmount"] ?: incompatible(),
        lowestBottomDelegationAmount = dynamicInstance["lowestBottomDelegationAmount"] ?: incompatible(),
        topCapacity = topCapacity,
        bottomCapacity = bottomCapacity,
        request = dynamicInstance["request"],
        status = parsedStatus
    )
}
