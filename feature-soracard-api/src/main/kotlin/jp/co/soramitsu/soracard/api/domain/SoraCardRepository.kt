package jp.co.soramitsu.soracard.api.domain

import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.core.models.Asset

interface SoraCardRepository {
    suspend fun getXorEuroPrice(): BigDecimal?

    suspend fun getStakedFarmedAmountOfAsset(address: String, asset: Asset): BigInteger

    suspend fun getXorPooledAmount(address: String, asset: Asset): BigDecimal
}