package jp.co.soramitsu.feature_staking_impl.di

import dagger.Module
import dagger.Provides
import javax.inject.Named
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.data.memory.ComputationalCache
import jp.co.soramitsu.common.data.network.AppLinksProvider
import jp.co.soramitsu.common.data.network.NetworkApiCreator
import jp.co.soramitsu.common.data.network.rpc.BulkRetriever
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.di.scope.FeatureScope
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core.storage.StorageCache
import jp.co.soramitsu.core_db.dao.AccountStakingDao
import jp.co.soramitsu.core_db.dao.StakingTotalRewardDao
import jp.co.soramitsu.feature_account_api.data.extrinsic.ExtrinsicService
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.presentation.account.AddressDisplayUseCase
import jp.co.soramitsu.feature_staking_api.domain.api.IdentityRepository
import jp.co.soramitsu.feature_staking_api.domain.api.StakingRepository
import jp.co.soramitsu.feature_staking_impl.data.StakingSharedState
import jp.co.soramitsu.feature_staking_impl.data.network.subquery.StakingApi
import jp.co.soramitsu.feature_staking_impl.data.network.subquery.SubQueryValidatorSetFetcher
import jp.co.soramitsu.feature_staking_impl.data.repository.IdentityRepositoryImpl
import jp.co.soramitsu.feature_staking_impl.data.repository.PayoutRepository
import jp.co.soramitsu.feature_staking_impl.data.repository.StakingConstantsRepository
import jp.co.soramitsu.feature_staking_impl.data.repository.StakingRepositoryImpl
import jp.co.soramitsu.feature_staking_impl.data.repository.StakingRewardsRepository
import jp.co.soramitsu.feature_staking_impl.data.repository.datasource.StakingRewardsDataSource
import jp.co.soramitsu.feature_staking_impl.data.repository.datasource.StakingStoriesDataSource
import jp.co.soramitsu.feature_staking_impl.data.repository.datasource.StakingStoriesDataSourceImpl
import jp.co.soramitsu.feature_staking_impl.data.repository.datasource.SubqueryStakingRewardsDataSource
import jp.co.soramitsu.feature_staking_impl.domain.EraTimeCalculatorFactory
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.domain.alerts.AlertsInteractor
import jp.co.soramitsu.feature_staking_impl.domain.payout.PayoutInteractor
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.CollatorRecommendatorFactory
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
import jp.co.soramitsu.feature_staking_impl.domain.validators.CollatorProvider
import jp.co.soramitsu.feature_staking_impl.domain.validators.ValidatorProvider
import jp.co.soramitsu.feature_staking_impl.domain.validators.current.CurrentValidatorsInteractor
import jp.co.soramitsu.feature_staking_impl.domain.validators.current.search.SearchCustomValidatorsInteractor
import jp.co.soramitsu.feature_staking_impl.presentation.common.SetupStakingProcess
import jp.co.soramitsu.feature_staking_impl.presentation.common.SetupStakingSharedState
import jp.co.soramitsu.feature_staking_impl.presentation.common.rewardDestination.RewardDestinationMixin
import jp.co.soramitsu.feature_staking_impl.presentation.common.rewardDestination.RewardDestinationProvider
import jp.co.soramitsu.feature_staking_impl.scenarios.StakingParachainScenarioInteractor
import jp.co.soramitsu.feature_staking_impl.scenarios.StakingParachainScenarioRepository
import jp.co.soramitsu.feature_staking_impl.scenarios.StakingRelayChainScenarioInteractor
import jp.co.soramitsu.feature_staking_impl.scenarios.StakingRelayChainScenarioRepository
import jp.co.soramitsu.feature_staking_impl.scenarios.StakingScenarioInteractor
import jp.co.soramitsu.feature_wallet_api.domain.AssetUseCase
import jp.co.soramitsu.feature_wallet_api.domain.TokenUseCase
import jp.co.soramitsu.feature_wallet_api.domain.implementations.AssetUseCaseImpl
import jp.co.soramitsu.feature_wallet_api.domain.implementations.TokenUseCaseImpl
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.TokenRepository
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletConstants
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.presentation.mixin.assetSelector.AssetSelectorFactory
import jp.co.soramitsu.feature_wallet_api.presentation.mixin.assetSelector.AssetSelectorMixin
import jp.co.soramitsu.feature_wallet_api.presentation.mixin.fee.FeeLoaderMixin
import jp.co.soramitsu.feature_wallet_api.presentation.mixin.fee.FeeLoaderProvider
import jp.co.soramitsu.runtime.di.LOCAL_STORAGE_SOURCE
import jp.co.soramitsu.runtime.di.REMOTE_STORAGE_SOURCE
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.repository.ChainStateRepository
import jp.co.soramitsu.runtime.storage.source.StorageDataSource

@Module
class StakingFeatureModule {

    @Provides
    @FeatureScope
    fun provideAssetUseCase(
        walletRepository: WalletRepository,
        accountRepository: AccountRepository,
        sharedState: StakingSharedState,
    ): AssetUseCase = AssetUseCaseImpl(
        walletRepository,
        accountRepository,
        sharedState
    )

    @Provides
    fun provideAssetSelectorMixinFactory(
        assetUseCase: AssetUseCase,
        singleAssetSharedState: StakingSharedState,
        resourceManager: ResourceManager
    ): AssetSelectorMixin.Presentation.Factory = AssetSelectorFactory(
        assetUseCase,
        singleAssetSharedState,
        resourceManager
    )

    @Provides
    @FeatureScope
    fun provideTokenUseCase(
        tokenRepository: TokenRepository,
        sharedState: StakingSharedState,
    ): TokenUseCase = TokenUseCaseImpl(
        tokenRepository,
        sharedState
    )

    @Provides
    @FeatureScope
    fun provideFeeLoaderMixin(
        resourceManager: ResourceManager,
        tokenUseCase: TokenUseCase,
    ): FeeLoaderMixin.Presentation = FeeLoaderProvider(
        resourceManager,
        tokenUseCase
    )

    @Provides
    @FeatureScope
    fun provideStakingSharedState(
        chainRegistry: ChainRegistry,
        preferences: Preferences
    ) = StakingSharedState(chainRegistry, preferences)

    @Provides
    @FeatureScope
    fun provideStakingStoriesDataSource(): StakingStoriesDataSource = StakingStoriesDataSourceImpl()

    @Provides
    @FeatureScope
    fun provideStakingRewardsSubqueryDataSource(
        stakingApi: StakingApi,
        stakingTotalRewardDao: StakingTotalRewardDao,
        chainRegistry: ChainRegistry
    ): StakingRewardsDataSource = SubqueryStakingRewardsDataSource(
        stakingApi = stakingApi,
        stakingTotalRewardDao = stakingTotalRewardDao,
        chainRegistry = chainRegistry
    )

    @Provides
    @FeatureScope
    fun provideParachainStakingRepository(
        @Named(REMOTE_STORAGE_SOURCE) remoteStorageSource: StorageDataSource,
        @Named(LOCAL_STORAGE_SOURCE) localStorageSource: StorageDataSource
    ) = StakingParachainScenarioRepository(remoteStorageSource, localStorageSource)

    @Provides
    @FeatureScope
    fun provideRelayChainStakingRepository(
        @Named(REMOTE_STORAGE_SOURCE) remoteStorageSource: StorageDataSource,
        @Named(LOCAL_STORAGE_SOURCE) localStorageSource: StorageDataSource,
        chainRegistry: ChainRegistry,
        walletConstants: WalletConstants,
        accountStakingDao: AccountStakingDao
    ) = StakingRelayChainScenarioRepository(
        remoteStorageSource,
        localStorageSource,
        chainRegistry,
        walletConstants,
        accountStakingDao
    )

    @Provides
    @FeatureScope
    fun provideStakingRepository(
        @Named(LOCAL_STORAGE_SOURCE) localStorageSource: StorageDataSource,
        stakingStoriesDataSource: StakingStoriesDataSource,
    ): StakingRepository = StakingRepositoryImpl(
        localStorage = localStorageSource,
        stakingStoriesDataSource = stakingStoriesDataSource,
    )

    @Provides
    @FeatureScope
    fun provideIdentityRepository(
        bulkRetriever: BulkRetriever,
        chainRegistry: ChainRegistry,
    ): IdentityRepository = IdentityRepositoryImpl(
        bulkRetriever,
        chainRegistry
    )

    @Provides
    @FeatureScope
    fun provideStakingInteractor(
        walletRepository: WalletRepository,
        accountRepository: AccountRepository,
        stakingRepository: StakingRepository,
        stakingRewardsRepository: StakingRewardsRepository,
        stakingSharedState: StakingSharedState,
        assetUseCase: AssetUseCase,
        chainStateRepository: ChainStateRepository
    ) = StakingInteractor(
        walletRepository,
        accountRepository,
        stakingRepository,
        stakingRewardsRepository,
        stakingSharedState,
        assetUseCase,
        chainStateRepository
    )

    @Provides
    @FeatureScope
    fun provideParachainScenarioInteractor(
        interactor: StakingInteractor,
        accountRepository: AccountRepository,
        stakingConstantsRepository: StakingConstantsRepository,
        stakingParachainScenarioRepository: StakingParachainScenarioRepository,
        identityRepository: IdentityRepository
    ): StakingParachainScenarioInteractor {
        return StakingParachainScenarioInteractor(
            interactor,
            accountRepository,
            stakingConstantsRepository,
            stakingParachainScenarioRepository,
            identityRepository
        )
    }

    @Provides
    @FeatureScope
    fun provideRelayChainScenarioInteractor(
        interactor: StakingInteractor,
        accountRepository: AccountRepository,
        stakingConstantsRepository: StakingConstantsRepository,
        walletRepository: WalletRepository,
        stakingRewardsRepository: StakingRewardsRepository,
        factory: EraTimeCalculatorFactory,
        stakingSharedState: StakingSharedState,
        stakingRelayChainScenarioRepository: StakingRelayChainScenarioRepository,
        identityRepository: IdentityRepository,
        payoutRepository: PayoutRepository
    ): StakingRelayChainScenarioInteractor {
        return StakingRelayChainScenarioInteractor(
            interactor,
            accountRepository,
            walletRepository,
            stakingConstantsRepository,
            stakingRelayChainScenarioRepository,
            stakingRewardsRepository,
            factory,
            stakingSharedState,
            identityRepository,
            payoutRepository
        )
    }

    @Provides
    @FeatureScope
    fun provideEraTimeCalculatorFactory(
        stakingRelayChainScenarioRepository: StakingRelayChainScenarioRepository
    ) = EraTimeCalculatorFactory(stakingRelayChainScenarioRepository)

    @Provides
    @FeatureScope
    fun provideAlertsInteractor(
        stakingRelayChainScenarioRepository: StakingRelayChainScenarioRepository,
        stakingConstantsRepository: StakingConstantsRepository,
        sharedState: StakingSharedState,
        walletRepository: WalletRepository,
        accountRepository: AccountRepository,
    ) = AlertsInteractor(
        stakingRelayChainScenarioRepository,
        stakingConstantsRepository,
        sharedState,
        walletRepository,
        accountRepository
    )

    @Provides
    @FeatureScope
    fun provideRewardCalculatorFactory(
        stakingRelayChainScenarioRepository: StakingRelayChainScenarioRepository,
        repository: StakingRepository,
        sharedState: StakingSharedState
    ) = RewardCalculatorFactory(stakingRelayChainScenarioRepository, repository, sharedState)

    @Provides
    @FeatureScope
    fun provideValidatorRecommendatorFactory(
        validatorProvider: ValidatorProvider,
        computationalCache: ComputationalCache,
        sharedState: StakingSharedState,
    ) = ValidatorRecommendatorFactory(validatorProvider, sharedState, computationalCache)

    @Provides
    @FeatureScope
    fun provideCollatorRecommendatorFactory(
        collatorProvider: CollatorProvider,
        computationalCache: ComputationalCache,
        sharedState: StakingSharedState,
    ) = CollatorRecommendatorFactory(collatorProvider, sharedState, computationalCache)

    @Provides
    @FeatureScope
    fun provideValidatorProvider(
        stakingRelayChainScenarioRepository: StakingRelayChainScenarioRepository,
        identityRepository: IdentityRepository,
        rewardCalculatorFactory: RewardCalculatorFactory,
        stakingConstantsRepository: StakingConstantsRepository,
    ) = ValidatorProvider(
        stakingRelayChainScenarioRepository,
        identityRepository,
        rewardCalculatorFactory,
        stakingConstantsRepository
    )

    @Provides
    @FeatureScope
    fun provideCollatorProvider(
        stakingParachainScenarioRepository: StakingParachainScenarioRepository,
        identityRepository: IdentityRepository,
        rewardCalculatorFactory: RewardCalculatorFactory,
        stakingConstantsRepository: StakingConstantsRepository,
    ) = CollatorProvider(
        stakingParachainScenarioRepository,
        identityRepository,
        rewardCalculatorFactory,
        stakingConstantsRepository
    )

    @Provides
    @FeatureScope
    fun provideStakingConstantsRepository(
        chainRegistry: ChainRegistry,
    ) = StakingConstantsRepository(chainRegistry)

    @Provides
    @FeatureScope
    fun provideRecommendationSettingsProviderFactory(
        stakingConstantsRepository: StakingConstantsRepository,
        computationalCache: ComputationalCache,
        sharedState: StakingSharedState,
    ) = RecommendationSettingsProviderFactory(
        computationalCache,
        stakingConstantsRepository,
        sharedState
    )

    @Provides
    @FeatureScope
    fun provideSetupStakingInteractor(
        extrinsicService: ExtrinsicService,
        sharedState: StakingSharedState,
    ) = SetupStakingInteractor(extrinsicService, sharedState)

    @Provides
    @FeatureScope
    fun provideSetupStakingSharedState() = SetupStakingSharedState()

    @Provides
    fun provideRewardDestinationChooserMixin(
        resourceManager: ResourceManager,
        appLinksProvider: AppLinksProvider,
        stakingInteractor: StakingInteractor,
        stakingRelayChainScenarioInteractor: StakingRelayChainScenarioInteractor,
        iconGenerator: AddressIconGenerator,
        accountDisplayUseCase: AddressDisplayUseCase,
        sharedState: StakingSharedState,
    ): RewardDestinationMixin.Presentation = RewardDestinationProvider(
        resourceManager, stakingInteractor, stakingRelayChainScenarioInteractor, iconGenerator, appLinksProvider, sharedState, accountDisplayUseCase
    )

    @Provides
    @FeatureScope
    fun provideStakingRewardsApi(networkApiCreator: NetworkApiCreator): StakingApi {
        return networkApiCreator.create(StakingApi::class.java)
    }

    @Provides
    @FeatureScope
    fun provideStakingRewardsRepository(
        rewardDataSource: StakingRewardsDataSource,
    ): StakingRewardsRepository {
        return StakingRewardsRepository(rewardDataSource)
    }

    @Provides
    @FeatureScope
    fun provideValidatorSetFetcher(
        stakingApi: StakingApi,
        stakingRelayChainScenarioRepository: StakingRelayChainScenarioRepository,
        chainRegistry: ChainRegistry
    ): SubQueryValidatorSetFetcher {
        return SubQueryValidatorSetFetcher(
            stakingApi,
            stakingRelayChainScenarioRepository,
            chainRegistry
        )
    }

    @Provides
    @FeatureScope
    fun providePayoutRepository(
        stakingRelayChainScenarioRepository: StakingRelayChainScenarioRepository,
        validatorSetFetcher: SubQueryValidatorSetFetcher,
        bulkRetriever: BulkRetriever,
        storageCache: StorageCache,
        chainRegistry: ChainRegistry,
    ): PayoutRepository {
        return PayoutRepository(stakingRelayChainScenarioRepository, bulkRetriever, validatorSetFetcher, storageCache, chainRegistry)
    }

    @Provides
    @FeatureScope
    fun providePayoutInteractor(
        sharedState: StakingSharedState,
        extrinsicService: ExtrinsicService,
    ) = PayoutInteractor(sharedState, extrinsicService)

    @Provides
    @FeatureScope
    fun provideBondMoreInteractor(
        sharedState: StakingSharedState,
        extrinsicService: ExtrinsicService,
    ) = BondMoreInteractor(extrinsicService, sharedState)

    @Provides
    @FeatureScope
    fun provideUnbondInteractor(
        extrinsicService: ExtrinsicService,
        stakingRelayChainScenarioRepository: StakingRelayChainScenarioRepository
    ) = UnbondInteractor(extrinsicService, stakingRelayChainScenarioRepository)

    @Provides
    @FeatureScope
    fun provideRedeemInteractor(
        extrinsicService: ExtrinsicService,
        stakingRelayChainScenarioRepository: StakingRelayChainScenarioRepository
    ) = RedeemInteractor(extrinsicService, stakingRelayChainScenarioRepository)

    @Provides
    @FeatureScope
    fun provideRebondInteractor(
        sharedState: StakingSharedState,
        extrinsicService: ExtrinsicService,
    ) = RebondInteractor(extrinsicService, sharedState)

    @Provides
    @FeatureScope
    fun provideControllerInteractor(
        sharedState: StakingSharedState,
        extrinsicService: ExtrinsicService,
    ) = ControllerInteractor(extrinsicService, sharedState)

    @Provides
    @FeatureScope
    fun provideCurrentValidatorsInteractor(
        stakingRelayChainScenarioRepository: StakingRelayChainScenarioRepository,
        stakingConstantsRepository: StakingConstantsRepository,
        validatorProvider: ValidatorProvider,
    ) = CurrentValidatorsInteractor(
        stakingRelayChainScenarioRepository, stakingConstantsRepository, validatorProvider
    )

    @Provides
    @FeatureScope
    fun provideChangeRewardDestinationInteractor(
        extrinsicService: ExtrinsicService,
    ) = ChangeRewardDestinationInteractor(extrinsicService)

    @Provides
    @FeatureScope
    fun provideSearchCustomValidatorsInteractor(
        validatorProvider: ValidatorProvider,
        sharedState: StakingSharedState
    ) = SearchCustomValidatorsInteractor(validatorProvider, sharedState)
}
