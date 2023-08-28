package jp.co.soramitsu.soracard.api.domain

import java.math.BigDecimal

interface SoraCardRepository {
    suspend fun getXorEuroPrice(): BigDecimal?
}