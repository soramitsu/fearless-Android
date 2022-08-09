package jp.co.soramitsu.featurestakingimpl.data.network.blockhain.bindings

import java.math.BigInteger
import jp.co.soramitsu.common.data.network.runtime.binding.UseCaseBinding
import jp.co.soramitsu.common.data.network.runtime.binding.bindNumber
import jp.co.soramitsu.common.data.network.runtime.binding.fromHexOrIncompatible
import jp.co.soramitsu.common.data.network.runtime.binding.storageReturnType
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot

@UseCaseBinding
fun bindHistoryDepth(scale: String, runtime: RuntimeSnapshot): BigInteger {
    val type = runtime.metadata.storageReturnType("Staking", "HistoryDepth")

    return bindNumber(type.fromHexOrIncompatible(scale, runtime))
}
