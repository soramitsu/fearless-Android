package jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings

import jp.co.soramitsu.common.utils.SuspendableProperty
import jp.co.soramitsu.common.utils.constant
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.metadata.module
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.bindMaximumRewardedNominators

private const val MAX_VALIDATORS_PER_NOMINATOR = 16

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

        instance = RecommendationSettingsProvider(maxNominatorRewardedPerValidator, MAX_VALIDATORS_PER_NOMINATOR)

        return instance!!
    }
}
