package jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings

import jp.co.soramitsu.common.data.network.runtime.binding.UseCaseBinding
import jp.co.soramitsu.common.data.network.runtime.binding.getOfType
import jp.co.soramitsu.common.data.network.runtime.binding.incompatible
import jp.co.soramitsu.common.data.network.runtime.binding.storageReturnType
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.fromHexOrNull
import java.math.BigInteger

/*
"ActiveEraInfo": {
  "type": "struct",
  "type_mapping": [
    [
      "index",
      "EraIndex"
    ],
    [
      "start",
      "Option<Moment>"
    ]
  ]
}
 */
@UseCaseBinding
fun bindActiveEra(
    scale: String,
    runtime: RuntimeSnapshot
): BigInteger {
    val returnType = runtime.metadata.storageReturnType("Staking", "ActiveEra")
    val decoded = returnType.fromHexOrNull(runtime, scale) as? Struct.Instance ?: incompatible()

    return bindEra(decoded.getOfType("index"))
}

/*
EraIndex
 */
@UseCaseBinding
fun bindCurrentEraIndex(
    scale: String,
    runtime: RuntimeSnapshot
): BigInteger {
    val returnType = runtime.metadata.storageReturnType("Staking", "CurrentEra")

    return bindEra(returnType.fromHexOrNull(runtime, scale))
}

fun bindEra(dynamicInstance: Any?) = dynamicInstance as? BigInteger ?: incompatible()