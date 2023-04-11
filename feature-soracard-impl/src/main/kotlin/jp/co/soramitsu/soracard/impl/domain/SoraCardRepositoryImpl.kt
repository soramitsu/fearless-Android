package jp.co.soramitsu.soracard.impl.domain

import jp.co.soramitsu.coredb.dao.SoraCardDao
import jp.co.soramitsu.coredb.model.SoraCardInfoLocal
import jp.co.soramitsu.soracard.api.domain.SoraCardRepository
import jp.co.soramitsu.soracard.api.presentation.models.SoraCardInfo
import jp.co.soramitsu.soracard.impl.data.SoraCardApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal

class SoraCardRepositoryImpl(
    private val soraCardDao: SoraCardDao,
    private val soraCardApi: SoraCardApi
) : SoraCardRepository {
    private companion object {
        const val SORA_CARD_ID = "soraCardId"
        private const val XOR_PRICE_REQUEST_DELAY_MILLIS = 30 * 1000
    }

    var cachedXorEuroPrice: Pair<BigDecimal?, Long> = Pair(null, 0)

    override fun subscribeSoraCardInfo(): Flow<SoraCardInfo?> {
        return soraCardDao.observeSoraCardInfo(SORA_CARD_ID).map {
            it?.let {
                SoraCardInfoMapper.map(it)
            }
        }
    }

    override suspend fun getSoraCardInfo(): SoraCardInfo? {
        return soraCardDao.getSoraCardInfo(SORA_CARD_ID)?.let {
            SoraCardInfoMapper.map(it)
        }
    }

    override suspend fun updateSoraCardKycStatus(kycStatus: String) {
        soraCardDao.updateKycStatus(SORA_CARD_ID, kycStatus)
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

    override suspend fun getXorEuroPrice(): BigDecimal? {
        val (xorEurPrice, cachedTime) = cachedXorEuroPrice

        val cacheIsGood = xorEurPrice != null && cachedTime + XOR_PRICE_REQUEST_DELAY_MILLIS > System.currentTimeMillis()

        return if (cacheIsGood) {
            xorEurPrice
        } else {
            val soraPrice = soraCardApi.getXorEuroPrice()
            val newValue = soraPrice?.price?.toBigDecimalOrNull()
            cachedXorEuroPrice = newValue to System.currentTimeMillis()

            newValue
        }
    }
}
