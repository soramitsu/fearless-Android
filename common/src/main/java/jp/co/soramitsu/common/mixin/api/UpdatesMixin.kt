package jp.co.soramitsu.common.mixin.api

import androidx.lifecycle.LiveData
import jp.co.soramitsu.common.model.AssetKey
import jp.co.soramitsu.fearless_utils.runtime.AccountId

interface UpdatesMixin : UpdatesProviderUi

interface UpdatesProviderUi {
    val tokenRates: LiveData<Set<String>>

    val assets: LiveData<Set<AssetKey>>

    val chains: LiveData<Set<String>>

    suspend fun startUpdateToken(symbol: String)

    suspend fun startUpdateTokens(symbols: List<String>)

    suspend fun finishUpdateToken(symbol: String)

    suspend fun startUpdateAsset(metaId: Long, chainId: String, accountId: AccountId, tokenSymbol: String)

    suspend fun finishUpdateAsset(metaId: Long, chainId: String, accountId: AccountId, tokenSymbol: String)

    suspend fun startChainSyncUp(chainId: String)

    suspend fun startChainsSyncUp(chainIds: List<String>)

    suspend fun finishChainSyncUp(chainId: String)
}
