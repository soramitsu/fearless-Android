package jp.co.soramitsu.wallet.impl.data.network.model

import java.math.BigInteger
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.wallet.impl.domain.model.Transfer

class EvmTransfer(
    val sender: String,
    val recipient: String,
    val amount: BigInteger? = null,
    val chainAsset: Asset,
    val nonce: BigInteger? = null,
    val gasLimit: BigInteger? = null,
    val maxPriorityFeePerGas: BigInteger? = null,
    val maxFeePerGas: BigInteger? = null,
    val gasPrice: BigInteger? = null
) {
    companion object {
        fun createFromTransfer(
            transfer: Transfer,
            nonce: BigInteger? = null,
            gasLimit: BigInteger? = null,
            maxPriorityFeePerGas: BigInteger? = null,
            maxFeePerGas: BigInteger? = null,
            gasPrice: BigInteger? = null
        ): EvmTransfer {
            return EvmTransfer(
                sender = transfer.sender,
                recipient = transfer.recipient,
                amount = transfer.amountInPlanks,
                chainAsset = transfer.chainAsset,
                nonce = nonce,
                gasLimit = gasLimit,
                maxPriorityFeePerGas = maxPriorityFeePerGas,
                maxFeePerGas = maxFeePerGas,
                gasPrice = gasPrice
            )
        }
    }
}
