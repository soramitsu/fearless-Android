package jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings

import jp.co.soramitsu.common.data.network.runtime.binding.UseCaseBinding
import jp.co.soramitsu.common.data.network.runtime.binding.bindNumber
import jp.co.soramitsu.common.data.network.runtime.binding.fromHexOrIncompatible
import jp.co.soramitsu.common.data.network.runtime.binding.incompatible
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.Type
import java.math.BigInteger

@UseCaseBinding
fun bindTotalValidatorEraReward(scale: String?, runtime: RuntimeSnapshot, type: Type<*>): BigInteger {
    val result = scale?.let { bindNumber(type.fromHexOrIncompatible(it, runtime)) }
    return result ?: incompatible()
}
