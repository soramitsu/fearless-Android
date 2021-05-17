package jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings

import jp.co.soramitsu.common.data.network.runtime.binding.HelperBinding
import jp.co.soramitsu.common.data.network.runtime.binding.UseCaseBinding
import jp.co.soramitsu.common.data.network.runtime.binding.getTyped
import jp.co.soramitsu.common.data.network.runtime.binding.incompatible
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.fromHexOrNull
import jp.co.soramitsu.feature_staking_api.domain.model.ValidatorPrefs
import java.math.BigDecimal
import java.math.BigInteger

private const val PERBILL_MANTISSA_SIZE = 9

@HelperBinding
fun bindPerbill(value: BigInteger): BigDecimal {
    return value.toBigDecimal(scale = PERBILL_MANTISSA_SIZE)
}

@UseCaseBinding
fun bindValidatorPrefs(scale: String, runtime: RuntimeSnapshot): ValidatorPrefs {
    val type = runtime.typeRegistry["ValidatorPrefs"] ?: incompatible()
    val decoded = type.fromHexOrNull(runtime, scale) as? Struct.Instance ?: incompatible()

    return ValidatorPrefs(
        commission = bindPerbill(decoded.getTyped("commission")),
        blocked = decoded.getTyped("blocked")
    )
}
