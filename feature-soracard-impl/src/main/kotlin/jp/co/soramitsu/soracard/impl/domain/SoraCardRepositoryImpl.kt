package jp.co.soramitsu.soracard.impl.domain

import jp.co.soramitsu.coredb.dao.SoraCardDao
import jp.co.soramitsu.coredb.model.SoraCardInfoLocal
import jp.co.soramitsu.soracard.api.domain.SoraCardRepository
import jp.co.soramitsu.soracard.api.presentation.models.SoraCardInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SoraCardRepositoryImpl(
    private val soraCardDao: SoraCardDao
) : SoraCardRepository {
    private companion object {
        const val SORA_CARD_ID = "soraCardId"
    }

    override fun subscribeSoraCardInfo(): Flow<SoraCardInfo?> {
        return soraCardDao.getSoraCardInfo(SORA_CARD_ID).map {
            it?.let {
                SoraCardInfoMapper.map(it)
            }
        }
    }

    override suspend fun updateSoraCardInfo(
        accessToken: String,
        refreshToken: String,
        accessTokenExpirationTime: Long,
        kycStatus: String
    ) {
        soraCardDao.insert(
            SoraCardInfoLocal(
                id = SORA_CARD_ID,
                accessToken = accessToken,
                refreshToken = refreshToken,
                accessTokenExpirationTime = accessTokenExpirationTime,
                kycStatus = kycStatus
            )
        )
    }
}
