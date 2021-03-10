package jp.co.soramitsu.feature_account_api.domain.updaters

import jp.co.soramitsu.core.updater.SideEffectScope
import jp.co.soramitsu.core.updater.SubscriptionBuilder
import jp.co.soramitsu.core.updater.Updater
import jp.co.soramitsu.feature_account_api.domain.model.Account
import kotlinx.coroutines.flow.Flow

interface AccountUpdater : SideEffectScope {

    suspend fun listenAccountUpdates(
        accountSubscriptionBuilder: SubscriptionBuilder,
        account: Account
    ): Flow<Updater.SideEffect>
}