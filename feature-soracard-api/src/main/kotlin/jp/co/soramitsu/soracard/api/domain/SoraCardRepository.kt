package jp.co.soramitsu.soracard.api.domain

import jp.co.soramitsu.soracard.api.presentation.models.SoraCardInfo
import kotlinx.coroutines.flow.Flow

interface SoraCardRepository {

    fun subscribeSoraCardInfo(): Flow<SoraCardInfo?>

    suspend fun getSoraCardInfo(): SoraCardInfo?

    suspend fun updateSoraCardKycStatus(kycStatus: String)

    suspend fun updateSoraCardInfo(
        accessToken: String,
        refreshToken: String,
        accessTokenExpirationTime: Long,
        kycStatus: String
    )
}
