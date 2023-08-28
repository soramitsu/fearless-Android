package jp.co.soramitsu.soracard.impl.domain

import java.math.BigDecimal
import javax.inject.Inject
import jp.co.soramitsu.soracard.api.domain.SoraCardRepository
import jp.co.soramitsu.soracard.impl.data.SoraCardApi

class SoraCardRepositoryImpl @Inject constructor(
    private val soraCardApi: SoraCardApi
) : SoraCardRepository {
    private companion object {
        private const val XOR_PRICE_REQUEST_DELAY_MILLIS = 30_000
    }

    var cachedXorEuroPrice: Pair<BigDecimal?, Long> = Pair(null, 0)

    override suspend fun getXorEuroPrice(): BigDecimal? {
        val (xorEurPrice, cachedTime) = cachedXorEuroPrice

        val cacheExpired = cachedTime + XOR_PRICE_REQUEST_DELAY_MILLIS < System.currentTimeMillis()

        return if (xorEurPrice != null && cacheExpired.not()) {
            xorEurPrice
        } else {
            val soraPrice = soraCardApi.getXorEuroPrice()
            val newValue = soraPrice?.price?.toBigDecimalOrNull()
            cachedXorEuroPrice = newValue to System.currentTimeMillis()

            newValue
        }
    }
}
