package jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.updaters

import jp.co.soramitsu.core.updater.SubscriptionBuilder
import jp.co.soramitsu.core.updater.Updater
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.model.Account
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.withContext

abstract class AccountUpdater(
    private val accountRepository: AccountRepository,
    protected val sS58Encoder: SS58Encoder
) : Updater {

    abstract suspend fun listenForUpdates(
        storageSubscriptionBuilder: SubscriptionBuilder,
        account: Account
    ): Flow<Updater.SideEffect>

    override suspend fun listenForUpdates(
        storageSubscriptionBuilder: SubscriptionBuilder
    ): Flow<Updater.SideEffect> {
        val networkType = accountRepository.getSelectedNode().networkType

        return accountRepository.selectedAccountFlow()
            .filter { it.network.type == networkType }
            .distinctUntilChanged { old, new -> old.address == new.address }
            .flatMapLatest { listenForUpdates(storageSubscriptionBuilder, it) }
    }

    protected suspend fun getAccountId(address: String) = withContext(Dispatchers.Default) {
        sS58Encoder.decode(address)
    }
}