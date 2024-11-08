package jp.co.soramitsu.wallet.api.data

import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.coredb.model.AssetBalanceUpdateItem
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import kotlinx.coroutines.flow.Flow

abstract class BalanceLoader(val chain: Chain) {

    abstract suspend fun loadBalance(metaAccounts: Set<MetaAccount>): List<AssetBalanceUpdateItem>
    abstract fun subscribeBalance(metaAccount: MetaAccount): Flow<AssetBalanceUpdateItem>

    interface Provider {
        fun invoke(chain: Chain): BalanceLoader
    }
}

