package jp.co.soramitsu.core.updater

import jp.co.soramitsu.core.model.StorageChange
import kotlinx.coroutines.flow.Flow

interface SubscriptionBuilder {

    fun subscribe(key: String): Flow<StorageChange>
}