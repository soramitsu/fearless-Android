package jp.co.soramitsu.core_db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import jp.co.soramitsu.core_db.model.Extrinsic
import jp.co.soramitsu.core_db.model.Reward
import jp.co.soramitsu.core_db.model.Transaction
import jp.co.soramitsu.core_db.model.Transfer
import jp.co.soramitsu.core_db.model.relations.OperationsRelation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

@Dao
abstract class OperationsDao {
//    @Query("SELECT * FROM transaction WHERE ")
//    abstract fun observeTransactions(
//
//    )

//    @Query(
//        "INSERT INTO transfer(id, amount, senderAddress, recipientAddress, fee, block, extrinsicId) VALUES (0, 749034981000, \"14VU9vkhYu8FCp7FmQUgBt9rMAY7KazBZ1UxYzrBS8gHrePu\", \"1MpyrAwWTB7etchEuXq8GNZBiKfxHYDLHwS382mujaPzPWw\", 30800000, \"3570180\", \"3570180-2\");"
//    )
//    abstract fun insertTransfer()
//
//    @Query(
//        "INSERT INTO tran(id, timestamp, address, rewardId, extrinsicId, transferId) VALUES (0, \"1626094703\", \"1MpyrAwWTB7etchEuXq8GNZBiKfxHYDLHwS382mujaPzPWw\", null, null, 0);"
//    )
//    abstract fun insertTran()
//
//    fun insertTrans(){
//        insertTransfer()
//        insertTran()
//    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertTran(transaction: Transaction)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertTransfer(transfer: Transfer)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertReward(reward: Reward)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(extrinsic: Extrinsic)

    @Query("SELECT * FROM transfer WHERE id = :transactionId")
    abstract fun observeTransfers(transactionId: Int): Flow<List<Transfer>>

    @Query("SELECT * FROM tran")
    abstract fun observeTransactions(): Flow<List<OperationsRelation>>
//    @Query("SELECT * FROM tran")
//    abstract suspend fun observeOperations(): Flow<List<Transaction>>
//
//    @Query("SELECT * FROM reward")
//    abstract suspend fun observeReward(): Flow<List<Reward>>
//
//    @Query("SELECT * FROM transfer")
//    abstract suspend fun observeTransfer(): Flow<List<Transfer>>
//
//    @Query("SELECT * FROM extrinsic")
//    abstract suspend fun observeExtrinsic(): Flow<List<Extrinsic>>

//    suspend fun resultFlow() = combine(
//        observeOperations(),
//        observeReward(),
//        observeTransfer(),
//        observeExtrinsic()
//    ) { operations, reward, transfer, extrinsic ->
//         Operation(operations, transfer, reward, extrinsic)
//    }
}
