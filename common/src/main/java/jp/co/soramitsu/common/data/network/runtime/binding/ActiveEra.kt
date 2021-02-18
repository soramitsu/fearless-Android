package jp.co.soramitsu.common.data.network.runtime.binding

import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.fromHex
import java.math.BigInteger

@UseCaseBinding
fun bindActiveEra(
    scale: String,
    runtime: RuntimeSnapshot
) : BigInteger {
    val returnType = runtime.metadata.storageReturnType("Staking", "ActiveEra")
    val decoded = returnType.fromHex(runtime, scale)

    return decoded as? BigInteger ?: incompatible()
}