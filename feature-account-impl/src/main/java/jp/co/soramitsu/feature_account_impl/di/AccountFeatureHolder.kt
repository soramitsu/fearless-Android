package jp.co.soramitsu.feature_account_impl.di

import jp.co.soramitsu.common.di.FeatureApiHolder
import jp.co.soramitsu.common.di.FeatureContainer
import jp.co.soramitsu.common.di.scope.ApplicationScope
import javax.inject.Inject

@ApplicationScope
class AccountFeatureHolder @Inject constructor(
    featureContainer: FeatureContainer
) : FeatureApiHolder(featureContainer) {

    override fun initializeDependencies(): Any {
        val accountFeatureDependencies = DaggerAccountFeatureComponent_AccountFeatureDependenciesComponent.builder()
            .commonApi(commonApi())
            .build()
        return DaggerAccountFeatureComponent.builder()
            .accountFeatureDependencies(accountFeatureDependencies)
            .build()
    }
}