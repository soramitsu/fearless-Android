package jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings

import jp.co.soramitsu.common.data.network.runtime.binding.UseCaseBinding
import jp.co.soramitsu.common.data.network.runtime.binding.getOfType
import jp.co.soramitsu.common.data.network.runtime.binding.incompatible
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.Type
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.fromHexOrNull
import java.math.BigInteger

class SlashingSpan(
    val lastNonZeroSlash: BigInteger // era index
)

@UseCaseBinding
fun bindSlashingSpans(
    scale: String,
    runtime: RuntimeSnapshot,
    returnType: Type<*>
): SlashingSpan {
    val decoded = returnType.fromHexOrNull(runtime, scale) as? Struct.Instance ?: incompatible()

    return SlashingSpan(
        lastNonZeroSlash = bindEra(decoded.getOfType("lastNonzeroSlash"))
    )
}