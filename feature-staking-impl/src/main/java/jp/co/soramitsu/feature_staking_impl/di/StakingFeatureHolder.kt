package jp.co.soramitsu.feature_staking_impl.di

import jp.co.soramitsu.common.di.FeatureApiHolder
import jp.co.soramitsu.common.di.FeatureContainer
import jp.co.soramitsu.common.di.scope.ApplicationScope
import jp.co.soramitsu.core_db.di.DbApi
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_wallet_api.di.WalletFeatureApi
import jp.co.soramitsu.runtime.di.RuntimeApi
import javax.inject.Inject

@ApplicationScope
class StakingFeatureHolder @Inject constructor(
    featureContainer: FeatureContainer,
    private val router: StakingRouter
) : FeatureApiHolder(featureContainer) {

    override fun initializeDependencies(): Any {
        val dependencies = DaggerStakingFeatureComponent_StakingFeatureDependenciesComponent.builder()
            .commonApi(commonApi())
            .runtimeApi(getFeature(RuntimeApi::class.java))
            .dbApi(getFeature(DbApi::class.java))
            .walletFeatureApi(getFeature(WalletFeatureApi::class.java))
            .accountFeatureApi(getFeature(AccountFeatureApi::class.java))
            .build()

        return DaggerStakingFeatureComponent.factory()
            .create(router, dependencies)
    }
}