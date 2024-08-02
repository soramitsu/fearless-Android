package jp.co.soramitsu.coredb.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import jp.co.soramitsu.coredb.model.NomisWalletScoreLocal
import kotlinx.coroutines.flow.Flow

@Dao
interface NomisScoresDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(scores: List<NomisWalletScoreLocal>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(scores: NomisWalletScoreLocal)

    @Query("SELECT * FROM nomis_wallet_score")
    suspend fun getScores(): List<NomisWalletScoreLocal>

    @Query("SELECT * FROM nomis_wallet_score")
    fun observeScores(): Flow<List<NomisWalletScoreLocal>>

    @Query("SELECT * FROM nomis_wallet_score WHERE metaId = :metaId")
    fun observeScore(metaId: Long): Flow<NomisWalletScoreLocal>
}