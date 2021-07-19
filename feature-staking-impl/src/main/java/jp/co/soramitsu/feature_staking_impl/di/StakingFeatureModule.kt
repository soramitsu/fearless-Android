package jp.co.soramitsu.feature_staking_impl.di

import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.data.memory.ComputationalCache
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
import jp.co.soramitsu.core_db.dao.StakingTotalRewardDao
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
import jp.co.soramitsu.feature_staking_impl.data.repository.datasource.StakingRewardsDataSource
import jp.co.soramitsu.feature_staking_impl.data.repository.datasource.StakingRewardsSubscanDataSourceImpl
import jp.co.soramitsu.feature_staking_impl.data.repository.datasource.StakingStoriesDataSource
import jp.co.soramitsu.feature_staking_impl.data.repository.datasource.StakingStoriesDataSourceImpl
import jp.co.soramitsu.feature_staking_impl.data.repository.datasource.SubqueryStakingRewardsDataSource
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.domain.alerts.AlertsInteractor
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
import jp.co.soramitsu.feature_staking_impl.domain.validators.current.search.SearchCustomValidatorsInteractor
import jp.co.soramitsu.feature_staking_impl.presentation.common.SetupStakingSharedState
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

const val SUBSCAN_REWARD_SOURCE = "SUBSCAN_REWARD_SOURCE"
const val SUBQUERY_REWARD_SOURCE = "SUBQUERY_REWARD_SOURCE"

@Module
class StakingFeatureModule {

    @Provides
    @FeatureScope
    fun provideStakingStoriesDataSource(): StakingStoriesDataSource = StakingStoriesDataSourceImpl()

    @Provides
    @Named(SUBQUERY_REWARD_SOURCE)
    @FeatureScope
    fun provideStakingRewardsSubqueryDataSource(
        stakingApi: StakingApi,
        stakingTotalRewardDao: StakingTotalRewardDao,
    ): StakingRewardsDataSource = SubqueryStakingRewardsDataSource(
        stakingApi = stakingApi,
        stakingTotalRewardDao = stakingTotalRewardDao
    )

    @Provides
    @Named(SUBSCAN_REWARD_SOURCE)
    @FeatureScope
    fun provideStakingRewardsSubscanDataSource(
        stakingRewardDao: StakingRewardDao,
        subscanPagedSynchronizer: SubscanPagedSynchronizer,
        stakingApi: StakingApi,
    ): StakingRewardsDataSource = StakingRewardsSubscanDataSourceImpl(
        stakingRewardDao = stakingRewardDao,
        subscanPagedSynchronizer = subscanPagedSynchronizer,
        stakingApi = stakingApi,
    )

    @Provides
    @FeatureScope
    fun provideStakingRepository(
        storageCache: StorageCache,
        accountRepository: AccountRepository,
        runtimeProperty: SuspendableProperty<RuntimeSnapshot>,
        bulkRetriever: BulkRetriever,
        accountStakingDao: AccountStakingDao,
        @Named(LOCAL_STORAGE_SOURCE) localStorageSource: StorageDataSource,
        @Named(REMOTE_STORAGE_SOURCE) remoteStorageSource: StorageDataSource,
        stakingStoriesDataSource: StakingStoriesDataSource,
        walletConstants: WalletConstants,
    ): StakingRepository = StakingRepositoryImpl(
        storageCache = storageCache,
        accountRepository = accountRepository,
        runtimeProperty = runtimeProperty,
        accountStakingDao = accountStakingDao,
        bulkRetriever = bulkRetriever,
        remoteStorage = remoteStorageSource,
        localStorage = localStorageSource,
        stakingStoriesDataSource = stakingStoriesDataSource,
        walletConstants = walletConstants
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
    fun provideAlertsInteractor(
        stakingRepository: StakingRepository,
        stakingConstantsRepository: StakingConstantsRepository,
        walletRepository: WalletRepository,
    ) = AlertsInteractor(
        stakingRepository, stakingConstantsRepository, walletRepository
    )

    @Provides
    @FeatureScope
    fun provideRewardCalculatorFactory(
        repository: StakingRepository,
    ) = RewardCalculatorFactory(repository)

    @Provides
    @FeatureScope
    fun provideValidatorRecommendatorFactory(
        validatorProvider: ValidatorProvider,
        computationalCache: ComputationalCache
    ) = ValidatorRecommendatorFactory(validatorProvider, computationalCache)

    @Provides
    @FeatureScope
    fun provideValidatorProvider(
        stakingRepository: StakingRepository,
        identityRepository: IdentityRepository,
        rewardCalculatorFactory: RewardCalculatorFactory,
        accountRepository: AccountRepository
    ) = ValidatorProvider(stakingRepository, identityRepository, accountRepository, rewardCalculatorFactory)

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
    fun provideRewardDestinationChooserMixin(
        resourceManager: ResourceManager,
        appLinksProvider: AppLinksProvider,
        stakingInteractor: StakingInteractor,
        iconGenerator: AddressIconGenerator,
        accountDisplayUseCase: AddressDisplayUseCase,
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
        @Named(SUBSCAN_REWARD_SOURCE) subscanStakingRewardsDataSource: StakingRewardsDataSource,
        @Named(SUBQUERY_REWARD_SOURCE) subqueryStakingRewardsDataSource: StakingRewardsDataSource,
    ): StakingRewardsRepository {
        return StakingRewardsRepository(
            subscanStakingRewardsDataSource,
            subqueryStakingRewardsDataSource
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
        stakingRepository: StakingRepository
    ) = UnbondInteractor(feeEstimator, extrinsicService, stakingRepository)

    @Provides
    @FeatureScope
    fun provideRedeemInteractor(
        feeEstimator: FeeEstimator,
        extrinsicService: ExtrinsicService,
        stakingRepository: StakingRepository,
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
        extrinsicService: ExtrinsicService,
    ) = ControllerInteractor(feeEstimator, extrinsicService)

    @Provides
    @FeatureScope
    fun provideCurrentValidatorsInteractor(
        stakingRepository: StakingRepository,
        stakingConstantsRepository: StakingConstantsRepository,
        validatorProvider: ValidatorProvider,
    ) = CurrentValidatorsInteractor(
        stakingRepository, stakingConstantsRepository, validatorProvider
    )

    @Provides
    @FeatureScope
    fun provideChangeRewardDestinationInteractor(
        feeEstimator: FeeEstimator,
        extrinsicService: ExtrinsicService,
    ) = ChangeRewardDestinationInteractor(feeEstimator, extrinsicService)

    @Provides
    @FeatureScope
    fun provideSearchCustomValidatorsInteractor(
        validatorProvider: ValidatorProvider,
        accountRepository: AccountRepository
    ) = SearchCustomValidatorsInteractor(validatorProvider, accountRepository)
}
