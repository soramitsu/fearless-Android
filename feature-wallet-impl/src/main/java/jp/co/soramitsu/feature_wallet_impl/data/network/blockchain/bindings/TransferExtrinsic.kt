package jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.bindings

import jp.co.soramitsu.common.data.network.runtime.binding.bindMultiAddressId
import jp.co.soramitsu.common.data.network.runtime.binding.bindNumber
import jp.co.soramitsu.common.data.network.runtime.binding.cast
import jp.co.soramitsu.common.data.network.runtime.binding.fromHexOrIncompatible
import jp.co.soramitsu.common.data.network.runtime.binding.incompatible
import jp.co.soramitsu.common.utils.balances
import jp.co.soramitsu.common.utils.extrinsicHash
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.Extrinsic
import jp.co.soramitsu.fearless_utils.runtime.metadata.call
import java.math.BigInteger

class TransferExtrinsic(
    val senderId: ByteArray,
    val recipientId: ByteArray,
    val amountInPlanks: BigInteger,
    val index: Pair<Int, Int>,
    val hash: String,
)

private val TRANSFER_CALL_NAMES = listOf("transfer", "transfer_keep_alive")

fun notTransfer(): Nothing = throw IllegalArgumentException("Extrinsic is not a transfer extrinsic")

fun bindTransferExtrinsic(scale: String, runtime: RuntimeSnapshot): TransferExtrinsic {
    val extrinsicInstance = Extrinsic.fromHexOrIncompatible(scale, runtime)
    val call = extrinsicInstance.call

    val transferModule = runtime.metadata.balances()
    val transferCalls = TRANSFER_CALL_NAMES.map(transferModule::call)

    val isTransferCall = transferCalls.any { it.index == call.function.index }

    if (!isTransferCall) throw notTransfer()

    val senderId = bindMultiAddressId(extrinsicInstance.signature!!.accountIdentifier.cast()) ?: incompatible()
    val recipientId = bindMultiAddressId(call.arguments["dest"].cast()) ?: incompatible()

    return TransferExtrinsic(
        senderId = senderId,
        recipientId = recipientId,
        amountInPlanks = bindNumber(call.arguments["value"]),
        index = call.function.index,
        hash = scale.extrinsicHash()
    )
}
