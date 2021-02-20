package jp.co.soramitsu.feature_staking_impl.di

import dagger.Module
import dagger.Provides
import jp.co.soramitsu.common.di.scope.FeatureScope
import jp.co.soramitsu.common.utils.SuspendableProperty
import jp.co.soramitsu.core.storage.StorageCache
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.feature_staking_impl.data.repository.StakingRepositoryImpl
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractorImpl
import jp.co.soramitsu.feature_staking_impl.domain.rewards.RewardCalculatorFactory

@Module
class StakingFeatureModule {

    @Provides
    @FeatureScope
    fun provideStakingRepository() = StakingRepositoryImpl()

    @Provides
    @FeatureScope
    fun provideStakingInteractor() = StakingInteractorImpl()

    @Provides
    @FeatureScope
    fun provideRewardCalculatorFactory(
        storageCache: StorageCache,
        runtimeProperty: SuspendableProperty<RuntimeSnapshot>
    ) = RewardCalculatorFactory(storageCache, runtimeProperty)
}