package jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.updaters

import jp.co.soramitsu.common.utils.SuspendableProperty
import jp.co.soramitsu.common.utils.fromHex
import jp.co.soramitsu.core_api.data.network.Updater
import jp.co.soramitsu.fearless_utils.runtime.Module
import jp.co.soramitsu.fearless_utils.runtime.Service
import jp.co.soramitsu.fearless_utils.runtime.StorageUtils
import jp.co.soramitsu.fearless_utils.runtime.storageKey
import jp.co.soramitsu.fearless_utils.scale.dataType.boolean
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.storage.SubscribeStorageRequest
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.storage.storageChange
import jp.co.soramitsu.fearless_utils.wsrpc.subscription.response.SubscriptionChange
import jp.co.soramitsu.fearless_utils.wsrpc.subscriptionFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

object TripleRefCountService : Service<Unit>(Module.System, "UpgradedToTripleRefCount") {
    override fun storageKey(storageArgs: Unit): String {
        return StorageUtils.createStorageKey(this, null)
    }
}

private const val DEFAULT_TRIPLE_REF_COUNT = false

private val upgradedToTripleRefCountRequest = SubscribeStorageRequest(TripleRefCountService.storageKey())

private fun SubscriptionChange.tripleRefCountChange(): Boolean {
    val storageChange = storageChange()

    val raw = storageChange.getSingleChange()

    return raw?.let(boolean::fromHex) ?: DEFAULT_TRIPLE_REF_COUNT
}

class AccountInfoSchemaUpdater(
    private val tripleRefCountProperty: SuspendableProperty<Boolean>,
    private val socketService: SocketService
) : Updater {

    override suspend fun listenForUpdates() {
        tripleRefCountProperty.invalidate()

        socketService.subscriptionFlow(upgradedToTripleRefCountRequest)
            .map { it.tripleRefCountChange() }
            .onEach(tripleRefCountProperty::set)
            .collect()
    }
}