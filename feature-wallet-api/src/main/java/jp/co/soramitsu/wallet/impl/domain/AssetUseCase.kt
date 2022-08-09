package jp.co.soramitsu.wallet.impl.domain

import jp.co.soramitsu.wallet.impl.domain.model.Asset
import kotlinx.coroutines.flow.Flow

interface AssetUseCase {

    fun currentAssetFlow(): Flow<Asset>

    suspend fun availableAssetsToSelect(): List<Asset>
}
