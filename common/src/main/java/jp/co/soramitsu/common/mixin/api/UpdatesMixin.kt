package jp.co.soramitsu.common.mixin.api

import androidx.lifecycle.LiveData
import jp.co.soramitsu.common.model.AssetKey
import jp.co.soramitsu.fearless_utils.runtime.AccountId

interface UpdatesMixin : UpdatesProviderUi

interface UpdatesProviderUi {
    val tokenRatesUpdate: LiveData<Set<String>>

    val assetsUpdate: LiveData<Set<AssetKey>>

    val chainsUpdate: LiveData<Set<String>>

    suspend fun startUpdateToken(assetId: String)

    suspend fun startUpdateTokens(assetIds: List<String>)

    suspend fun finishUpdateTokens(assetIds: List<String>)

    suspend fun finishUpdateToken(assetId: String)

    suspend fun startUpdateAsset(metaId: Long, chainId: String, accountId: AccountId, assetId: String)

    suspend fun finishUpdateAsset(metaId: Long, chainId: String, accountId: AccountId, assetId: String)

    suspend fun startChainSyncUp(chainId: String)

    suspend fun startChainsSyncUp(chainIds: List<String>)

    suspend fun finishChainSyncUp(chainId: String)
}
