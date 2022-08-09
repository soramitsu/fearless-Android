package jp.co.soramitsu.featurewalletapi.domain

import jp.co.soramitsu.featurewalletapi.domain.model.Asset
import kotlinx.coroutines.flow.Flow

interface AssetUseCase {

    fun currentAssetFlow(): Flow<Asset>

    suspend fun availableAssetsToSelect(): List<Asset>
}
