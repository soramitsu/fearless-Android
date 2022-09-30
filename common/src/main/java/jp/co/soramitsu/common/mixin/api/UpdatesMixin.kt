package jp.co.soramitsu.common.mixin.api

import androidx.lifecycle.LiveData
import jp.co.soramitsu.common.model.AssetKey
import jp.co.soramitsu.fearless_utils.runtime.AccountId

interface UpdatesMixin : UpdatesProviderUi

interface UpdatesProviderUi {
    val tokenRatesUpdate: LiveData<Set<String>>

    val assetsUpdate: LiveData<Set<AssetKey>>

    val chainsUpdate: LiveData<Set<String>>

    suspend fun startUpdateToken(priceId: String)

    suspend fun startUpdateTokens(priceIds: Set<String>)

    suspend fun finishUpdateTokens(priceIds: Set<String>)

    suspend fun finishUpdateToken(priceId: String)

    suspend fun startUpdateAsset(metaId: Long, chainId: String, accountId: AccountId, assetId: String)

    suspend fun finishUpdateAsset(metaId: Long, chainId: String, accountId: AccountId, assetId: String)

    suspend fun startChainSyncUp(chainId: String)

    suspend fun startChainsSyncUp(chainIds: List<String>)

    suspend fun finishChainSyncUp(chainId: String)
}
