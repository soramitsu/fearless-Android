package jp.co.soramitsu.account.impl.domain

import java.util.concurrent.ConcurrentHashMap
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.interfaces.NomisScoreInteractor
import jp.co.soramitsu.account.api.domain.model.NomisScoreData
import jp.co.soramitsu.account.impl.data.mappers.toDomain
import jp.co.soramitsu.common.data.network.nomis.NomisApi
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.utils.flowOf
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class NomisScoreInteractorImpl(
    private val accountRepository: AccountRepository,
    private val preferences: Preferences,
    private val nomisApi: NomisApi,
    private val coroutineContext: CoroutineContext = Dispatchers.Default
)
    : NomisScoreInteractor {

    private val scoresCache = ConcurrentHashMap<String, NomisScoreData>()

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeNomisScores(): Flow<List<NomisScoreData>> {
        return observeNomisMultichainScoreEnabled().flatMapLatest {
            if(it) {
                accountRepository.observeNomisScores()
            } else {
                flowOf(emptyList())
            }
        }.flowOn(coroutineContext)
    }

    override fun observeCurrentAccountScore(): Flow<NomisScoreData?> {
        return observeNomisMultichainScoreEnabled().flatMapLatest {
            if (it) {
                accountRepository.selectedMetaAccountFlow().flatMapLatest { metaAccount ->
                    accountRepository.observeNomisScore(metaAccount.id)
                }
            } else {
                flowOf(null)
            }
        }
            .flowOn(coroutineContext)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeAccountScore(metaId: Long): Flow<NomisScoreData?> {
        return flowOf {
            accountRepository.getMetaAccount(metaId)
        }.flatMapLatest {
            accountRepository.observeNomisScore(it.id)
        }
    }

    override fun observeNomisMultichainScoreEnabled(): Flow<Boolean> {
        return preferences.booleanFlow("nomis_multichain_score_enabled", true)
    }

    override var nomisMultichainScoreEnabled: Boolean
        get() = preferences.getBoolean("nomis_multichain_score_enabled", true)
        set(value) {
            preferences.putBoolean("nomis_multichain_score_enabled", value)
        }

    override suspend fun getNomisScore(address: String): NomisScoreData?  {
        return scoresCache.getOrPut(address) {
            withContext(coroutineContext) {runCatching { nomisApi.getNomisScore(address) }.getOrNull()?.toDomain() }?: return null
        }
    }

    override fun getNomisScoreFromMemoryCache(address: String): NomisScoreData? {
        return scoresCache[address]
    }
}