package jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters

import jp.co.soramitsu.common.data.network.rpc.PagedKeysRetriever
import jp.co.soramitsu.common.utils.SuspendableProperty
import jp.co.soramitsu.core.updater.SubscriptionBuilder
import jp.co.soramitsu.core.updater.Updater
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.math.BigInteger

class ElectedNominatorsUpdater(
    val runtimeProperty: SuspendableProperty<RuntimeSnapshot>,
    val pagedKeysRetriever: PagedKeysRetriever,
) : Updater {

    override suspend fun listenForUpdates(storageSubscriptionBuilder: SubscriptionBuilder): Flow<Updater.SideEffect> {

       /* return storageSubscriptionBuilder.subscribe(activeEraKey)
            .onEach {
                val era = bindActiveEra(it.value!!, runtime)

                updateElectedValidators(era)
            }.noSideAffects()*/

        return flowOf()
    }

    private fun updateElectedValidators(runtime: RuntimeSnapshot, era: BigInteger) {
        val key = runtime
    }
}