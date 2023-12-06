package jp.co.soramitsu.account.api.domain.interfaces

import jp.co.soramitsu.account.api.domain.model.AssetBalance
import kotlinx.coroutines.flow.Flow

interface AssetBalanceUseCase {

    suspend operator fun invoke(assetId: String): AssetBalance

    fun observe(assetId: String): Flow<AssetBalance>

}