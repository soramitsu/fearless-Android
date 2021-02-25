package jp.co.soramitsu.feature_staking_impl.di

import dagger.Module
import dagger.Provides
import jp.co.soramitsu.common.data.network.rpc.BulkRetriever
import jp.co.soramitsu.common.di.scope.FeatureScope
import jp.co.soramitsu.common.utils.SuspendableProperty
import jp.co.soramitsu.core.storage.StorageCache
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_staking_api.domain.api.StakingRepository
import jp.co.soramitsu.feature_staking_impl.data.repository.StakingRepositoryImpl
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.ValidatorRecommendatorFactory
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.RecommendationSettingsProviderFactory
import jp.co.soramitsu.feature_staking_impl.domain.rewards.RewardCalculatorFactory
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository

@Module
class StakingFeatureModule {

    @Provides
    @FeatureScope
    fun provideStakingRepository(
        storageCache: StorageCache,
        runtimeProperty: SuspendableProperty<RuntimeSnapshot>,
        bulkRetriever: BulkRetriever
    ): StakingRepository = StakingRepositoryImpl(storageCache, runtimeProperty, bulkRetriever)

    @Provides
    @FeatureScope
    fun provideStakingInteractor(
        walletRepository: WalletRepository,
        accountRepository: AccountRepository
    ) = StakingInteractor(walletRepository, accountRepository)

    @Provides
    @FeatureScope
    fun provideRewardCalculatorFactory(
        repository: StakingRepository
    ) = RewardCalculatorFactory(repository)

    @Provides
    @FeatureScope
    fun provideValidatorRecommendatorFactory(
        repository: StakingRepository,
        rewardCalculatorFactory: RewardCalculatorFactory
    ) = ValidatorRecommendatorFactory(repository, rewardCalculatorFactory)

    @Provides
    @FeatureScope
    fun provideRecommendationSettingsProviderFactory(
        runtimeProperty: SuspendableProperty<RuntimeSnapshot>
    ) = RecommendationSettingsProviderFactory(runtimeProperty)
}