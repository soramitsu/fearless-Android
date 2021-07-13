package jp.co.soramitsu.core_db.model.relations

import androidx.room.Embedded
import androidx.room.Relation
import jp.co.soramitsu.core_db.model.Extrinsic
import jp.co.soramitsu.core_db.model.Reward
import jp.co.soramitsu.core_db.model.Transaction
import jp.co.soramitsu.core_db.model.Transfer

data class OperationsRelation(
    @Embedded val transaction: Transaction,
    @Relation(
        parentColumn = "transferId",
        entityColumn = "id"
    )
    val transfer: Transfer?,
    @Relation(
        parentColumn = "rewardId",
        entityColumn = "id"
    )
    val reward: Reward?,
    @Relation(
        parentColumn = "extrinsicId",
        entityColumn = "id"
    )
    val extrinsic: Extrinsic?
)
