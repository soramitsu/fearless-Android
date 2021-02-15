package jp.co.soramitsu.feature_staking_impl.di

import dagger.Module
import dagger.Provides
import jp.co.soramitsu.common.di.scope.FeatureScope
import jp.co.soramitsu.feature_staking_impl.data.repository.StakingRepositoryImpl
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractorImpl

@Module
class StakingFeatureModule {

    @Provides
    @FeatureScope
    fun provideStakingRepository() = StakingRepositoryImpl()

    @Provides
    @FeatureScope
    fun provideStakingInteractor() = StakingInteractorImpl()
}