package jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings

import jp.co.soramitsu.common.data.network.runtime.binding.UseCaseBinding
import jp.co.soramitsu.common.data.network.runtime.binding.cast
import jp.co.soramitsu.common.data.network.runtime.binding.incompatible
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.TypeReference
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.DictEnum
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Tuple
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.fromHex
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.Null
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.BooleanType
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.u32
import jp.co.soramitsu.feature_staking_api.domain.model.Election
import jp.co.soramitsu.feature_staking_api.domain.model.ElectionPhase

private val electionPhaseType = DictEnum(
    "ElectionPhase",
    listOf(
        DictEnum.Entry("Off", TypeReference(Null)),
        DictEnum.Entry("Signed", TypeReference(Null)),
        DictEnum.Entry(
            "Unsigned",
            TypeReference(
                Tuple(
                    "(bool, Bn)",
                    listOf(
                        TypeReference(BooleanType),
                        TypeReference(u32)
                    )
                )
            )
        )
    )
)

@UseCaseBinding
fun bindElectionFromPhase(
    scale: String,
    runtime: RuntimeSnapshot,
): Election {
    val dynamicInstance: DictEnum.Entry<Any?> = electionPhaseType.fromHex(runtime, scale).cast()

    val phase = when (dynamicInstance.name) {
        "Off" -> ElectionPhase.Off
        "Signed" -> ElectionPhase.Signed
        "Unsigned" -> {
            val (isOpen, blockNumber) = dynamicInstance.value.cast<List<*>>()

            ElectionPhase.Unsigned(
                isOpen = isOpen.cast(),
                block = bindBlockNumber(blockNumber)
            )
        }
        else -> incompatible()
    }

    return when (phase) {
        ElectionPhase.Off -> Election.CLOSED
        else -> Election.OPEN
    }
}
