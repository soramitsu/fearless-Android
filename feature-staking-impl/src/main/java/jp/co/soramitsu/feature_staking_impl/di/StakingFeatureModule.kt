package jp.co.soramitsu.feature_staking_impl.di

import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.data.network.AppLinksProvider
import jp.co.soramitsu.common.data.network.HttpExceptionHandler
import jp.co.soramitsu.common.data.network.NetworkApiCreator
import jp.co.soramitsu.common.data.network.rpc.BulkRetriever
import jp.co.soramitsu.common.di.scope.FeatureScope
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.SuspendableProperty
import jp.co.soramitsu.core.storage.StorageCache
import jp.co.soramitsu.core_db.dao.AccountStakingDao
import jp.co.soramitsu.core_db.dao.StakingRewardDao
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.presenatation.account.AddressDisplayUseCase
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
import jp.co.soramitsu.feature_staking_impl.domain.setup.SetupStakingInteractor
import jp.co.soramitsu.feature_staking_impl.domain.staking.bond.BondMoreInteractor
import jp.co.soramitsu.feature_staking_impl.domain.staking.controller.ControllerInteractor
import jp.co.soramitsu.feature_staking_impl.domain.staking.rebond.RebondInteractor
import jp.co.soramitsu.feature_staking_impl.domain.staking.redeem.RedeemInteractor
import jp.co.soramitsu.feature_staking_impl.domain.staking.rewardDestination.ChangeRewardDestinationInteractor
import jp.co.soramitsu.feature_staking_impl.domain.staking.unbond.UnbondInteractor
import jp.co.soramitsu.feature_staking_impl.domain.validators.ValidatorProvider
import jp.co.soramitsu.feature_staking_impl.domain.validators.current.CurrentValidatorsInteractor
import jp.co.soramitsu.feature_staking_impl.presentation.common.SetupStakingSharedState
import jp.co.soramitsu.feature_staking_impl.presentation.common.fee.FeeLoaderMixin
import jp.co.soramitsu.feature_staking_impl.presentation.common.fee.FeeLoaderProvider
import jp.co.soramitsu.feature_staking_impl.presentation.common.rewardDestination.RewardDestinationMixin
import jp.co.soramitsu.feature_staking_impl.presentation.common.rewardDestination.RewardDestinationProvider
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletConstants
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.runtime.di.LOCAL_STORAGE_SOURCE
import jp.co.soramitsu.runtime.di.REMOTE_STORAGE_SOURCE
import jp.co.soramitsu.runtime.extrinsic.ExtrinsicService
import jp.co.soramitsu.runtime.extrinsic.FeeEstimator
import jp.co.soramitsu.runtime.storage.source.StorageDataSource
import javax.inject.Named

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
        @Named(LOCAL_STORAGE_SOURCE) localStorageSource: StorageDataSource,
        @Named(REMOTE_STORAGE_SOURCE) remoteStorageSource: StorageDataSource,
        stakingStoriesDataSource: StakingStoriesDataSource,
    ): StakingRepository = StakingRepositoryImpl(
        storageCache = storageCache,
        runtimeProperty = runtimeProperty,
        accountStakingDao = accountStakingDao,
        bulkRetriever = bulkRetriever,
        remoteStorage = remoteStorageSource,
        localStorage = localStorageSource,
        stakingStoriesDataSource = stakingStoriesDataSource
    )

    @Provides
    @FeatureScope
    fun provideIdentityRepository(
        runtimeProperty: SuspendableProperty<RuntimeSnapshot>,
        bulkRetriever: BulkRetriever,
    ): IdentityRepository = IdentityRepositoryImpl(runtimeProperty, bulkRetriever)

    @Provides
    @FeatureScope
    fun provideStakingInteractor(
        walletRepository: WalletRepository,
        accountRepository: AccountRepository,
        stakingRepository: StakingRepository,
        stakingRewardsRepository: StakingRewardsRepository,
        stakingConstantsRepository: StakingConstantsRepository,
        walletConstants: WalletConstants,
        identityRepository: IdentityRepository,
        payoutRepository: PayoutRepository,
    ) = StakingInteractor(
        walletRepository,
        accountRepository,
        stakingRepository,
        stakingRewardsRepository,
        stakingConstantsRepository,
        identityRepository,
        walletConstants,
        payoutRepository
    )

    @Provides
    @FeatureScope
    fun provideRewardCalculatorFactory(
        repository: StakingRepository,
    ) = RewardCalculatorFactory(repository)

    @Provides
    @FeatureScope
    fun provideValidatorRecommendatorFactory(
        validatorProvider: ValidatorProvider
    ) = ValidatorRecommendatorFactory(validatorProvider)

    @Provides
    @FeatureScope
    fun provideValidatorProvider(
        stakingRepository: StakingRepository,
        identityRepository: IdentityRepository,
        rewardCalculatorFactory: RewardCalculatorFactory,
    ) = ValidatorProvider(stakingRepository, identityRepository, rewardCalculatorFactory)

    @Provides
    @FeatureScope
    fun provideStakingConstantsRepository(
        runtimeProperty: SuspendableProperty<RuntimeSnapshot>,
    ) = StakingConstantsRepository(runtimeProperty)

    @Provides
    @FeatureScope
    fun provideRecommendationSettingsProviderFactory(
        stakingConstantsRepository: StakingConstantsRepository,
    ) = RecommendationSettingsProviderFactory(stakingConstantsRepository)

    @Provides
    @FeatureScope
    fun provideSetupStakingInteractor(
        feeEstimator: FeeEstimator,
        extrinsicService: ExtrinsicService,
    ) = SetupStakingInteractor(feeEstimator, extrinsicService)

    @Provides
    @FeatureScope
    fun provideSetupStakingSharedState() = SetupStakingSharedState()

    @Provides
    fun provideFeeLoaderMixin(
        stakingInteractor: StakingInteractor,
        resourceManager: ResourceManager,
    ): FeeLoaderMixin.Presentation = FeeLoaderProvider(stakingInteractor, resourceManager)

    @Provides
    fun provideRewardDestinationChooserMixin(
        resourceManager: ResourceManager,
        appLinksProvider: AppLinksProvider,
        stakingInteractor: StakingInteractor,
        iconGenerator: AddressIconGenerator,
        accountDisplayUseCase: AddressDisplayUseCase
    ): RewardDestinationMixin.Presentation = RewardDestinationProvider(
        resourceManager, stakingInteractor, iconGenerator, appLinksProvider, accountDisplayUseCase
    )

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
        extrinsicService: ExtrinsicService,
    ) = PayoutInteractor(feeEstimator, extrinsicService)

    @Provides
    @FeatureScope
    fun provideBondMoreInteractor(
        feeEstimator: FeeEstimator,
        extrinsicService: ExtrinsicService,
    ) = BondMoreInteractor(feeEstimator, extrinsicService)

    @Provides
    @FeatureScope
    fun provideUnbondInteractor(
        feeEstimator: FeeEstimator,
        extrinsicService: ExtrinsicService,
    ) = UnbondInteractor(feeEstimator, extrinsicService)

    @Provides
    @FeatureScope
    fun provideRedeemInteractor(
        feeEstimator: FeeEstimator,
        extrinsicService: ExtrinsicService,
        stakingRepository: StakingRepository
    ) = RedeemInteractor(feeEstimator, extrinsicService, stakingRepository)

    @Provides
    @FeatureScope
    fun provideRebondInteractor(
        feeEstimator: FeeEstimator,
        extrinsicService: ExtrinsicService,
    ) = RebondInteractor(feeEstimator, extrinsicService)

    @Provides
    @FeatureScope
    fun provideControllerInteractor(
        feeEstimator: FeeEstimator,
        extrinsicService: ExtrinsicService
    ) = ControllerInteractor(feeEstimator, extrinsicService)

    @Provides
    @FeatureScope
    fun provideCurrentValidatorsInteractor(
        stakingRepository: StakingRepository,
        stakingConstantsRepository: StakingConstantsRepository,
        validatorProvider: ValidatorProvider
    ) = CurrentValidatorsInteractor(
        stakingRepository, stakingConstantsRepository, validatorProvider
    )

    @Provides
    @FeatureScope
    fun provideChangeRewardDestinationInteractor(
        feeEstimator: FeeEstimator,
        extrinsicService: ExtrinsicService
    ) = ChangeRewardDestinationInteractor(feeEstimator, extrinsicService)
}
