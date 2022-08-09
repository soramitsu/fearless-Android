package jp.co.soramitsu.featurestakingimpl.data.network.blockhain.bindings

import java.math.BigInteger
import jp.co.soramitsu.common.data.network.runtime.binding.UseCaseBinding
import jp.co.soramitsu.common.data.network.runtime.binding.bindNumber
import jp.co.soramitsu.common.data.network.runtime.binding.fromHexOrIncompatible
import jp.co.soramitsu.common.data.network.runtime.binding.storageReturnType
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot

/*
Balance
 */
@UseCaseBinding
fun bindTotalIssuance(
    scale: String,
    runtime: RuntimeSnapshot
): BigInteger {
    val returnType = runtime.metadata.storageReturnType("Balances", "TotalIssuance")

    return bindNumber(returnType.fromHexOrIncompatible(scale, runtime))
}
