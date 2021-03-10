package jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.updaters

import jp.co.soramitsu.common.utils.fromHex
import jp.co.soramitsu.core.model.StorageChange
import jp.co.soramitsu.core.updater.GlobalScopeUpdater
import jp.co.soramitsu.core.updater.SubscriptionBuilder
import jp.co.soramitsu.core.updater.Updater
import jp.co.soramitsu.fearless_utils.runtime.Module
import jp.co.soramitsu.fearless_utils.runtime.Service
import jp.co.soramitsu.fearless_utils.runtime.StorageUtils
import jp.co.soramitsu.fearless_utils.runtime.storageKey
import jp.co.soramitsu.fearless_utils.scale.dataType.boolean
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.account.AccountInfoFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

object DualRefCountService : Service<Unit>(Module.System, "UpgradedToDualRefCount") {
    override fun storageKey(storageArgs: Unit): String {
        return StorageUtils.createStorageKey(this, null)
    }
}

private const val DEFAULT_DUAL_REF_COUNT = false

private fun StorageChange.dualRefCountChange(): Boolean {
    return value?.let(boolean::fromHex) ?: DEFAULT_DUAL_REF_COUNT
}

class AccountInfoSchemaUpdater(
    private val accountInfoFactory: AccountInfoFactory
) : GlobalScopeUpdater {

    override suspend fun listenForUpdates(storageSubscriptionBuilder: SubscriptionBuilder): Flow<Updater.SideEffect> {
        accountInfoFactory.isUpgradedToDualRefCount.invalidate()

        return storageSubscriptionBuilder.subscribe(DualRefCountService.storageKey())
            .map { it.dualRefCountChange() }
            .onEach(accountInfoFactory.isUpgradedToDualRefCount::set)
            .noSideAffects()
    }
}