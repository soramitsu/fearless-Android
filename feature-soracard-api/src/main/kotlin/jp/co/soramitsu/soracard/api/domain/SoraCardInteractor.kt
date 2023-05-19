package jp.co.soramitsu.soracard.api.domain

import jp.co.soramitsu.soracard.api.presentation.models.SoraCardInfo
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

interface SoraCardInteractor {
    val soraCardChainId: String

    suspend fun xorAssetFlow(): Flow<Asset>

    fun subscribeSoraCardInfo(): Flow<SoraCardInfo?>

    suspend fun getSoraCardInfo(): SoraCardInfo?

    suspend fun updateSoraCardKycStatus(kycStatus: String)

    suspend fun getXorEuroPrice(priceId: String? = null): BigDecimal?

    suspend fun updateSoraCardInfo(
        accessToken: String,
        refreshToken: String,
        accessTokenExpirationTime: Long,
        kycStatus: String
    )
}
