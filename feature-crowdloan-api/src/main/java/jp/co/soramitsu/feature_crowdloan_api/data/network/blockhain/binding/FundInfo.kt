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
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.u32
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.toByteArray
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
    val trieIndex: TrieIndex,
    val paraId: ParaId,
    val bidderAccountId: AccountId
)

fun bindFundInfo(scale: String, runtime: RuntimeSnapshot, paraId: ParaId): FundInfo {
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
        trieIndex = bindTrieIndex(dynamicInstance["trieIndex"]),
        bidderAccountId = createBidderAccountId(runtime, paraId),
        paraId = paraId
    )
}

private val ADDRESS_PADDING = ByteArray(32)
private val ADDRESS_PREFIX = "modlpy/cfund".encodeToByteArray()

private fun createBidderAccountId(runtime: RuntimeSnapshot, paraId: ParaId): AccountId {
    val fullKey = ADDRESS_PREFIX + u32.toByteArray(runtime, paraId) + ADDRESS_PADDING

    return fullKey.copyOfRange(0, 32)
}
