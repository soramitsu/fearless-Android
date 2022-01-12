package jp.co.soramitsu.feature_wallet_api.domain

import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import kotlinx.coroutines.flow.Flow

interface AssetUseCase {

    fun currentAssetFlow(): Flow<Asset>

    suspend fun availableAssetsToSelect(): List<Asset>
}
