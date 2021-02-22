package jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings

import jp.co.soramitsu.common.utils.SuspendableProperty
import jp.co.soramitsu.common.utils.constant
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.metadata.module
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.bindMaximumRewardedNominators

class RecommendationSettingsProviderFactory(
    val runtimeProperty: SuspendableProperty<RuntimeSnapshot>
) {

    private var instance: RecommendationSettingsProvider? = null

    @Synchronized
    suspend fun get(): RecommendationSettingsProvider {
        if (instance != null) return instance!!

        val runtime = runtimeProperty.get()

        val constant = runtime.metadata.module("Staking").constant("MaxNominatorRewardedPerValidator")
        val maxNominatorRewardedPerValidator = bindMaximumRewardedNominators(constant, runtime)

        instance = RecommendationSettingsProvider(maxNominatorRewardedPerValidator)

        return instance!!
    }
}