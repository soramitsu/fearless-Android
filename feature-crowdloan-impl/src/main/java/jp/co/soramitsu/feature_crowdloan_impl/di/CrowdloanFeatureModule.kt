package jp.co.soramitsu.feature_crowdloan_impl.di

import dagger.Module
import dagger.Provides
import jp.co.soramitsu.common.di.scope.FeatureScope
import jp.co.soramitsu.feature_crowdloan_api.data.repository.CrowdloanRepository
import jp.co.soramitsu.feature_crowdloan_impl.data.repository.CrowdloanRepositoryImpl

@Module
class CrowdloanFeatureModule {

    @Provides
    @FeatureScope
    fun crowdloanRepository(): CrowdloanRepository = CrowdloanRepositoryImpl()
}
