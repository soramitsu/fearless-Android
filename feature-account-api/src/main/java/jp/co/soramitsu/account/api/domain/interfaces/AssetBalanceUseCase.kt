package jp.co.soramitsu.account.api.domain.interfaces

import jp.co.soramitsu.account.api.domain.model.AssetBalance
import kotlinx.coroutines.flow.Flow

interface AssetBalanceUseCase {

    suspend operator fun invoke(accountMetaId: Long, chainId: String, assetId: String): AssetBalance

    fun observe(accountMetaId: Long, chainId: String, assetId: String): Flow<AssetBalance>

}
