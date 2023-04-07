package jp.co.soramitsu.wallet.api.presentation

import androidx.navigation.NavBackStackEntry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.coroutines.flow.Flow

interface WalletRouter {

    fun backWithResult(vararg results: Pair<String, Any?>)

    fun popOutOfSend()

    fun back()

    fun setChainSelectorPayload(chainId: ChainId?)

    fun openSelectChainAsset(chainId: ChainId)

    fun <T> observeResult(key: String): Flow<T>

    fun getCurrentBackStackEntryFlow(): Flow<NavBackStackEntry>

    companion object {
        const val KEY_CHAIN_ID = "chain_id"
        const val KEY_ASSET_ID = "asset_id"
    }
}
