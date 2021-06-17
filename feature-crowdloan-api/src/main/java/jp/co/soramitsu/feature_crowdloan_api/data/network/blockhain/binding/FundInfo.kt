package jp.co.soramitsu.feature_crowdloan_api.data.network.blockhain.binding

import jp.co.soramitsu.common.data.network.runtime.binding.bindAccountId
import jp.co.soramitsu.common.data.network.runtime.binding.bindNumber
import jp.co.soramitsu.common.data.network.runtime.binding.cast
import jp.co.soramitsu.common.data.network.runtime.binding.fromHexOrIncompatible
import jp.co.soramitsu.common.data.network.runtime.binding.storageReturnType
import jp.co.soramitsu.common.utils.Modules
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Struct
import java.math.BigInteger

class FundInfo(
    val depositor: AccountId,
    val deposit: BigInteger,
    val raised: BigInteger,
    val lastSlot: BigInteger,
    val firstSlot: BigInteger,
    val end: BigInteger,
    val cap: BigInteger,
    val verifier: Any?,
    val trieIndex: TrieIndex
)

fun bindFundInfo(scale: String, runtime: RuntimeSnapshot): FundInfo {
    val type = runtime.metadata.storageReturnType(Modules.CROWDLOAN, "Funds")

    val dynamicInstance = type.fromHexOrIncompatible(scale, runtime)
        .cast<Struct.Instance>()

    return FundInfo(
        depositor = bindAccountId(dynamicInstance["depositor"]),
        deposit = bindNumber(dynamicInstance["deposit"]),
        raised = bindNumber(dynamicInstance["raised"]),
        end = bindNumber(dynamicInstance["end"]),
        cap = bindNumber(dynamicInstance["cap"]),
        firstSlot = bindNumber(dynamicInstance["firstPeriod"] ?: dynamicInstance["firstSlot"]),
        lastSlot = bindNumber(dynamicInstance["lastPeriod"] ?: dynamicInstance["lastSlot"]),
        verifier = dynamicInstance["verifier"],
        trieIndex = bindTrieIndex(dynamicInstance["trieIndex"])
    )
}
