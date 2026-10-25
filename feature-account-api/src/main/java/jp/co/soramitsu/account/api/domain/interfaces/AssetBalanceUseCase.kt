package jp.co.soramitsu.account.api.domain.interfaces

import jp.co.soramitsu.account.api.domain.model.AssetBalance
import kotlinx.coroutines.flow.Flow

interface AssetBalanceUseCase {
    /** Historical read bound to the requested account and the currency captured by the reader. */
    suspend operator fun invoke(accountMetaId: Long, chainId: String, assetId: String): AssetBalance

    /** Null follows the current account; a fixed ID follows that account's updates and deletion. */
    fun observe(accountMetaId: Long?, chainId: String, assetId: String): Flow<AssetBalance>
}
