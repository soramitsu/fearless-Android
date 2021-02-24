package jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings

import jp.co.soramitsu.common.data.network.runtime.binding.HelperBinding
import jp.co.soramitsu.common.data.network.runtime.binding.UseCaseBinding
import jp.co.soramitsu.common.data.network.runtime.binding.incompatible
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.fromByteArrayOrNull
import jp.co.soramitsu.fearless_utils.runtime.metadata.Constant
import java.math.BigInteger

/*
SlashDeferDuration = EraIndex
 */
@UseCaseBinding
fun bindSlashDeferDuration(
    constant: Constant,
    runtime: RuntimeSnapshot
): BigInteger = bindNumberConstant(constant, runtime)

@UseCaseBinding
fun bindMaximumRewardedNominators(
    constant: Constant,
    runtime: RuntimeSnapshot
): BigInteger = bindNumberConstant(constant, runtime)

@HelperBinding
fun bindNumberConstant(
    constant: Constant,
    runtime: RuntimeSnapshot
): BigInteger {
    val decoded = constant.type?.fromByteArrayOrNull(runtime, constant.value) ?: incompatible()

    return decoded as? BigInteger ?: incompatible()
}