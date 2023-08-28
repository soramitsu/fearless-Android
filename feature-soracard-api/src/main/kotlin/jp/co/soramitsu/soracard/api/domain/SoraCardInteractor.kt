package jp.co.soramitsu.soracard.api.domain

import jp.co.soramitsu.oauth.base.sdk.contract.SoraCardCommonVerification
import jp.co.soramitsu.soracard.api.presentation.models.SoraCardAvailabilityInfo
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import kotlinx.coroutines.flow.Flow

interface SoraCardInteractor {
    val soraCardChainId: String

    fun xorAssetFlow(): Flow<Asset>

    fun subscribeToSoraCardAvailabilityFlow(): Flow<SoraCardAvailabilityInfo>

    fun subscribeSoraCardStatus(): Flow<SoraCardCommonVerification>
    suspend fun checkSoraCardPending()
    fun setStatus(status: SoraCardCommonVerification)

    fun setLogout()

}
