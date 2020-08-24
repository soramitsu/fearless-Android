package jp.co.soramitsu.app.di.main

import jp.co.soramitsu.app.navigation.Navigator
import jp.co.soramitsu.common.di.FeatureApiHolder
import jp.co.soramitsu.common.di.FeatureContainer
import jp.co.soramitsu.common.di.scope.ApplicationScope
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import javax.inject.Inject

@ApplicationScope
class MainFeatureHolder @Inject constructor(
    private val navigator: Navigator,
    featureContainer: FeatureContainer
) : FeatureApiHolder(featureContainer) {

    override fun initializeDependencies(): Any {
        val mainFeatureDependencies = DaggerMainComponent_MainFeatureDependenciesComponent.builder()
            .accountFeatureApi(getFeature(AccountFeatureApi::class.java))
            .build()
        return DaggerMainComponent.factory()
            .create(navigator, mainFeatureDependencies)
    }
}