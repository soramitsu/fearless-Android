package jp.co.soramitsu.feature_staking_impl.di

import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import jp.co.soramitsu.common.data.network.HttpExceptionHandler
import jp.co.soramitsu.common.data.network.NetworkApiCreator
import jp.co.soramitsu.common.data.network.rpc.BulkRetriever
import jp.co.soramitsu.common.data.network.runtime.calls.SubstrateCalls
import jp.co.soramitsu.common.di.scope.FeatureScope
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.SuspendableProperty
import jp.co.soramitsu.core.storage.StorageCache
import jp.co.soramitsu.core_db.dao.AccountStakingDao
import jp.co.soramitsu.core_db.dao.StakingRewardDao
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_staking_api.domain.api.IdentityRepository
import jp.co.soramitsu.feature_staking_api.domain.api.StakingRepository
import jp.co.soramitsu.feature_staking_impl.data.network.subscan.StakingApi
import jp.co.soramitsu.feature_staking_impl.data.network.subscan.SubscanValidatorSetFetcher
import jp.co.soramitsu.feature_staking_impl.data.repository.IdentityRepositoryImpl
import jp.co.soramitsu.feature_staking_impl.data.repository.PayoutRepository
import jp.co.soramitsu.feature_staking_impl.data.repository.StakingConstantsRepository
import jp.co.soramitsu.feature_staking_impl.data.repository.StakingRepositoryImpl
import jp.co.soramitsu.feature_staking_impl.data.repository.StakingRewardsRepository
import jp.co.soramitsu.feature_staking_impl.data.repository.SubscanPagedSynchronizer
import jp.co.soramitsu.feature_staking_impl.data.repository.datasource.StakingStoriesDataSource
import jp.co.soramitsu.feature_staking_impl.data.repository.datasource.StakingStoriesDataSourceImpl
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.domain.payout.PayoutInteractor
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.ValidatorRecommendatorFactory
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.RecommendationSettingsProviderFactory
import jp.co.soramitsu.feature_staking_impl.domain.rewards.RewardCalculatorFactory
import jp.co.soramitsu.feature_staking_impl.domain.setup.MaxFeeEstimator
import jp.co.soramitsu.feature_staking_impl.presentation.common.SetupStakingSharedState
import jp.co.soramitsu.feature_staking_impl.presentation.common.fee.FeeLoaderMixin
import jp.co.soramitsu.feature_staking_impl.presentation.common.fee.FeeLoaderProvider
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletConstants
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.runtime.extrinsic.ExtrinsicBuilderFactory
import jp.co.soramitsu.runtime.extrinsic.ExtrinsicService
import jp.co.soramitsu.runtime.extrinsic.FeeEstimator

@Module
class StakingFeatureModule {

    @Provides
    @FeatureScope
    fun provideStakingStoriesDataSource(): StakingStoriesDataSource = StakingStoriesDataSourceImpl()

    @Provides
    @FeatureScope
    fun provideStakingRepository(
        storageCache: StorageCache,
        runtimeProperty: SuspendableProperty<RuntimeSnapshot>,
        bulkRetriever: BulkRetriever,
        accountStakingDao: AccountStakingDao,
        stakingStoriesDataSource: StakingStoriesDataSource
    ): StakingRepository = StakingRepositoryImpl(storageCache, runtimeProperty, accountStakingDao, bulkRetriever, stakingStoriesDataSource)

    @Provides
    @FeatureScope
    fun provideIdentityRepository(
        runtimeProperty: SuspendableProperty<RuntimeSnapshot>,
        bulkRetriever: BulkRetriever
    ): IdentityRepository = IdentityRepositoryImpl(runtimeProperty, bulkRetriever)

    @Provides
    @FeatureScope
    fun provideStakingInteractor(
        walletRepository: WalletRepository,
        accountRepository: AccountRepository,
        stakingRepository: StakingRepository,
        stakingRewardsRepository: StakingRewardsRepository,
        stakingConstantsRepository: StakingConstantsRepository,
        extrinsicBuilderFactory: ExtrinsicBuilderFactory,
        walletConstants: WalletConstants,
        identityRepository: IdentityRepository,
        payoutRepository: PayoutRepository,
        substrateCalls: SubstrateCalls
    ) = StakingInteractor(
        walletRepository,
        accountRepository,
        stakingRepository,
        stakingRewardsRepository,
        stakingConstantsRepository,
        substrateCalls,
        identityRepository,
        walletConstants,
        payoutRepository,
        extrinsicBuilderFactory
    )

    @Provides
    @FeatureScope
    fun provideRewardCalculatorFactory(
        repository: StakingRepository
    ) = RewardCalculatorFactory(repository)

    @Provides
    @FeatureScope
    fun provideValidatorRecommendatorFactory(
        stakingRepository: StakingRepository,
        identityRepository: IdentityRepository,
        rewardCalculatorFactory: RewardCalculatorFactory
    ) = ValidatorRecommendatorFactory(stakingRepository, identityRepository, rewardCalculatorFactory)

    @Provides
    @FeatureScope
    fun provideStakingConstantsRepository(
        runtimeProperty: SuspendableProperty<RuntimeSnapshot>
    ) = StakingConstantsRepository(runtimeProperty)

    @Provides
    @FeatureScope
    fun provideRecommendationSettingsProviderFactory(
        stakingConstantsRepository: StakingConstantsRepository
    ) = RecommendationSettingsProviderFactory(stakingConstantsRepository)

    @Provides
    @FeatureScope
    fun provideMaxFeeEstimator(
        substrateCalls: SubstrateCalls,
        extrinsicBuilderFactory: ExtrinsicBuilderFactory
    ) = MaxFeeEstimator(substrateCalls, extrinsicBuilderFactory)

    @Provides
    @FeatureScope
    fun provideSetupStakingSharedState() = SetupStakingSharedState()

    @Provides
    fun provideFeeLoaderMixin(
        stakingInteractor: StakingInteractor,
        resourceManager: ResourceManager
    ): FeeLoaderMixin.Presentation = FeeLoaderProvider(stakingInteractor, resourceManager)

    @Provides
    @FeatureScope
    fun provideStakingRewardsApi(networkApiCreator: NetworkApiCreator): StakingApi {
        return networkApiCreator.create(StakingApi::class.java)
    }

    @Provides
    @FeatureScope
    fun provideSubscanPagedSynchronizer(httpExceptionHandler: HttpExceptionHandler): SubscanPagedSynchronizer {
        return SubscanPagedSynchronizer(httpExceptionHandler)
    }

    @Provides
    @FeatureScope
    fun provideStakingRewardsRepository(
        stakingApi: StakingApi,
        stakingRewardDao: StakingRewardDao,
        subscanPagedSynchronizer: SubscanPagedSynchronizer,
    ): StakingRewardsRepository {
        return StakingRewardsRepository(
            stakingApi,
            stakingRewardDao,
            subscanPagedSynchronizer
        )
    }

    @Provides
    @FeatureScope
    fun provideValidatorSetFetcher(
        gson: Gson,
        stakingApi: StakingApi,
        subscanPagedSynchronizer: SubscanPagedSynchronizer,
    ): SubscanValidatorSetFetcher {
        return SubscanValidatorSetFetcher(
            gson,
            stakingApi,
            subscanPagedSynchronizer
        )
    }

    @Provides
    @FeatureScope
    fun providePayoutRepository(
        stakingRepository: StakingRepository,
        validatorSetFetcher: SubscanValidatorSetFetcher,
        runtimeProperty: SuspendableProperty<RuntimeSnapshot>,
        bulkRetriever: BulkRetriever,
        storageCache: StorageCache,
    ): PayoutRepository {
        return PayoutRepository(stakingRepository, bulkRetriever, runtimeProperty, validatorSetFetcher, storageCache)
    }

    @Provides
    @FeatureScope
    fun providePayoutInteractor(
        feeEstimator: FeeEstimator,
        extrinsicService: ExtrinsicService
    ) = PayoutInteractor(feeEstimator, extrinsicService)
}
