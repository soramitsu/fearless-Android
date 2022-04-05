package jp.co.soramitsu.feature_account_api.domain.interfaces

import jp.co.soramitsu.common.model.AssetKey
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.coroutines.flow.Flow

interface AssetNotNeedAccountUseCase {
    /**
     * Mark asset without account - as correct, no need of account
     * used in lists sort
     */
    suspend fun markNotNeed(chainId: ChainId, metaId: Long, symbol: String)

    /**
     * Get assets without account
     */
    fun getAssetsMarkedNotNeedFlow(metaId: Long): Flow<List<AssetKey>>
}
