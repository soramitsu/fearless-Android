package jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings

import jp.co.soramitsu.common.data.network.runtime.binding.HelperBinding
import jp.co.soramitsu.common.data.network.runtime.binding.UseCaseBinding
import jp.co.soramitsu.common.data.network.runtime.binding.bindNumber
import jp.co.soramitsu.common.data.network.runtime.binding.getTyped
import jp.co.soramitsu.common.data.network.runtime.binding.incompatible
import jp.co.soramitsu.common.data.network.runtime.binding.storageReturnType
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.fromHexOrNull
import jp.co.soramitsu.feature_staking_api.domain.model.EraIndex
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

    return bindEraIndex(decoded.getTyped("index"))
}

/*
EraIndex
 */
@UseCaseBinding
fun bindCurrentEra(
    scale: String,
    runtime: RuntimeSnapshot
): BigInteger {
    val returnType = runtime.metadata.storageReturnType("Staking", "CurrentEra")

    return bindEraIndex(returnType.fromHexOrNull(runtime, scale))
}

@HelperBinding
fun bindEraIndex(dynamicInstance: Any?): EraIndex = bindNumber(dynamicInstance)
