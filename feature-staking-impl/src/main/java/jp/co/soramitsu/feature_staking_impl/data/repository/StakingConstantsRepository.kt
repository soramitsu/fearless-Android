package jp.co.soramitsu.feature_staking_impl.data.repository

import jp.co.soramitsu.common.utils.SuspendableProperty
import jp.co.soramitsu.common.utils.constant
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.metadata.module
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.bindMaximumRewardedNominators

class StakingConstantsRepository(
    val runtimeProperty: SuspendableProperty<RuntimeSnapshot>,
) {

    suspend fun maxRewardedNominatorPerValidatorPrefs(): Int {
        val runtime = runtimeProperty.get()

        val constant = runtime.metadata.module("Staking").constant("MaxNominatorRewardedPerValidator")

        return bindMaximumRewardedNominators(constant, runtime).toInt()
    }
}
