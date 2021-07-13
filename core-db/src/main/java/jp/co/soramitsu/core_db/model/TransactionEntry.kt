package jp.co.soramitsu.core_db.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import java.math.BigDecimal

//TODO should rename after deleting subscan from transactions -> rename to TransactionLocal
@Entity(
    tableName = "tran",
    foreignKeys = [
        ForeignKey(
            entity = Transfer::class,
            parentColumns = ["id"],
            childColumns = ["transferId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Extrinsic::class,
            parentColumns = ["id"],
            childColumns = ["extrinsicId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Reward::class,
            parentColumns = ["id"],
            childColumns = ["rewardId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
class Transaction(
    @PrimaryKey val id: String,
    val timestamp: String,
    val address: String,
    val rewardId: Int?,
    val extrinsicId: Int?,
    val transferId: Int?
)

@Entity(
    tableName = "transfer"
)
class Transfer(
    @PrimaryKey val id: Int,
    val amount: BigDecimal,
    val senderAddress: String,
    val recipientAddress: String,
    val fee: BigDecimal,
    val block: String,
    val extrinsicId: String?
)

@Entity(tableName = "reward")
class Reward(
    @PrimaryKey val id: Int,
    val isReward: Boolean,
    val era: Int?,
    val validator: String?
)

@Entity(tableName = "extrinsic")
class Extrinsic(
    @PrimaryKey val id: Int,
    val hash: String,
    val module: String,
    val call: String,
    val success: Boolean
)
