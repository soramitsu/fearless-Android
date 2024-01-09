package jp.co.soramitsu.coredb.model

import androidx.room.Entity
import java.math.BigInteger

@Entity(
    tableName = "operations",
    primaryKeys = ["id", "address", "chainId", "chainAssetId"]
)
data class OperationLocal(
    val id: String,
    val address: String,
    val chainId: String,
    val chainAssetId: String,
    val time: Long,
    val status: Status,
    val source: Source,
    val operationType: Type,
    val module: String? = null,
    val call: String? = null,
    val amount: BigInteger? = null,
    val sender: String? = null,
    val receiver: String? = null,
    val hash: String? = null,
    val fee: BigInteger? = null,
    val isReward: Boolean? = null,
    val era: Int? = null,
    val validator: String? = null,
    val liquidityFee: BigInteger? = null,
    val market: String? = null,
    val targetAssetId: String? = null,
    val targetAmount: BigInteger? = null
) {
    enum class Type {
        EXTRINSIC, TRANSFER, REWARD, SWAP
    }

    enum class Source {
        BLOCKCHAIN, SUBQUERY, APP
    }

    enum class Status {
        PENDING, COMPLETED, FAILED
    }

    companion object {

        fun manualTransfer(
            hash: String,
            address: String,
            chainId: String,
            chainAssetId: String,
            amount: BigInteger,
            senderAddress: String,
            receiverAddress: String,
            fee: BigInteger?,
            status: Status,
            source: Source
        ) = OperationLocal(
            id = hash,
            hash = hash,
            address = address,
            chainId = chainId,
            chainAssetId = chainAssetId,
            time = System.currentTimeMillis(),
            amount = amount,
            sender = senderAddress,
            receiver = receiverAddress,
            fee = fee,
            status = status,
            source = source,
            operationType = Type.TRANSFER
        )
    }
}
