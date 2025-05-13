package jp.co.soramitsu.account.api.domain.interfaces

import jp.co.soramitsu.account.api.domain.model.NomisScoreData
import kotlinx.coroutines.flow.Flow

interface NomisScoreInteractor {
    fun observeNomisScores(): Flow<List<NomisScoreData>>

    fun observeCurrentAccountScore(): Flow<NomisScoreData?>
    fun observeAccountScore(metaId: Long): Flow<NomisScoreData?>

    var nomisMultichainScoreEnabled: Boolean
    fun observeNomisMultichainScoreEnabled(): Flow<Boolean>

    suspend fun getNomisScore(address: String): NomisScoreData?
    fun getNomisScoreFromMemoryCache(address: String): NomisScoreData?
}