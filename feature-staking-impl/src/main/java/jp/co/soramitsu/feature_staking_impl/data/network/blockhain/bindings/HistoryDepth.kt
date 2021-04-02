package jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings

import jp.co.soramitsu.common.data.network.runtime.binding.UseCaseBinding
import jp.co.soramitsu.common.data.network.runtime.binding.fromHexOrIncompatible
import jp.co.soramitsu.common.data.network.runtime.binding.storageReturnType
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import java.math.BigInteger

@UseCaseBinding
fun bindHistoryDepth(scale: String, runtime: RuntimeSnapshot): BigInteger {
    val type = runtime.metadata.storageReturnType("Staking", "HistoryDepth")

    return bindNumber(type.fromHexOrIncompatible(scale, runtime))
}
