package jp.co.soramitsu.soracard.api.domain

import jp.co.soramitsu.oauth.base.sdk.contract.SoraCardCommonVerification
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface SoraCardInteractor {
    val soraCardChainId: String

    fun xorAssetFlow(): Flow<Asset>

    val basicStatus: StateFlow<SoraCardBasicStatus>

    suspend fun initialize()

    suspend fun setStatus(status: SoraCardCommonVerification)
    suspend fun setLogout()
}
