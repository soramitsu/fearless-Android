package jp.co.soramitsu.staking.impl.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.data.memory.ComputationalCache
import jp.co.soramitsu.common.data.network.AppLinksProvider
import jp.co.soramitsu.common.data.network.NetworkApiCreator
import jp.co.soramitsu.common.data.network.rpc.BulkRetriever
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core.storage.StorageCache
import jp.co.soramitsu.coredb.dao.AccountStakingDao
import jp.co.soramitsu.coredb.dao.StakingTotalRewardDao
import jp.co.soramitsu.account.api.extrinsic.ExtrinsicService
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.presentation.account.AddressDisplayUseCase
import jp.co.soramitsu.staking.api.data.StakingSharedState
import jp.co.soramitsu.staking.api.domain.api.IdentityRepository
import jp.co.soramitsu.staking.api.domain.api.StakingRepository
import jp.co.soramitsu.staking.impl.data.network.subquery.StakingApi
import jp.co.soramitsu.staking.impl.data.network.subquery.SubQueryDelegationHistoryFetcher
import jp.co.soramitsu.staking.impl.data.network.subquery.SubQueryValidatorSetFetcher
import jp.co.soramitsu.staking.impl.data.repository.IdentityRepositoryImpl
import jp.co.soramitsu.staking.impl.data.repository.PayoutRepository
import jp.co.soramitsu.staking.impl.data.repository.StakingConstantsRepository
import jp.co.soramitsu.staking.impl.data.repository.StakingRepositoryImpl
import jp.co.soramitsu.staking.impl.data.repository.StakingRewardsRepository
import jp.co.soramitsu.staking.impl.data.repository.datasource.ParachainStakingStoriesDataSourceImpl
import jp.co.soramitsu.staking.impl.data.repository.datasource.StakingRewardsDataSource
import jp.co.soramitsu.staking.impl.data.repository.datasource.StakingStoriesDataSource
import jp.co.soramitsu.staking.impl.data.repository.datasource.StakingStoriesDataSourceImpl
import jp.co.soramitsu.staking.impl.data.repository.datasource.SubqueryStakingRewardsDataSource
import jp.co.soramitsu.staking.impl.domain.EraTimeCalculatorFactory
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.domain.alerts.AlertsInteractor
import jp.co.soramitsu.staking.impl.domain.payout.PayoutInteractor
import jp.co.soramitsu.staking.impl.domain.recommendations.CollatorRecommendatorFactory
import jp.co.soramitsu.staking.impl.domain.recommendations.ValidatorRecommendatorFactory
import jp.co.soramitsu.staking.impl.domain.recommendations.settings.RecommendationSettingsProviderFactory
import jp.co.soramitsu.staking.impl.domain.recommendations.settings.SettingsStorage
import jp.co.soramitsu.staking.impl.domain.rewards.RewardCalculatorFactory
import jp.co.soramitsu.staking.impl.domain.setup.SetupStakingInteractor
import jp.co.soramitsu.staking.impl.domain.staking.bond.BondMoreInteractor
import jp.co.soramitsu.staking.impl.domain.staking.controller.ControllerInteractor
import jp.co.soramitsu.staking.impl.domain.staking.rebond.RebondInteractor
import jp.co.soramitsu.staking.impl.domain.staking.redeem.RedeemInteractor
import jp.co.soramitsu.staking.impl.domain.staking.rewardDestination.ChangeRewardDestinationInteractor
import jp.co.soramitsu.staking.impl.domain.staking.unbond.UnbondInteractor
import jp.co.soramitsu.staking.impl.domain.validators.CollatorProvider
import jp.co.soramitsu.staking.impl.domain.validators.ValidatorProvider
import jp.co.soramitsu.staking.impl.domain.validators.current.CurrentValidatorsInteractor
import jp.co.soramitsu.staking.impl.domain.validators.current.search.SearchCustomBlockProducerInteractor
import jp.co.soramitsu.staking.impl.domain.validators.current.search.SearchCustomValidatorsInteractor
import jp.co.soramitsu.staking.impl.presentation.common.SetupStakingProcess
import jp.co.soramitsu.staking.impl.presentation.common.SetupStakingSharedState
import jp.co.soramitsu.staking.impl.presentation.common.rewardDestination.RewardDestinationMixin
import jp.co.soramitsu.staking.impl.presentation.common.rewardDestination.RewardDestinationProvider
import jp.co.soramitsu.staking.impl.scenarios.StakingScenarioInteractor
import jp.co.soramitsu.staking.impl.scenarios.parachain.StakingParachainScenarioInteractor
import jp.co.soramitsu.staking.impl.scenarios.parachain.StakingParachainScenarioRepository
import jp.co.soramitsu.staking.impl.scenarios.relaychain.StakingRelayChainScenarioInteractor
import jp.co.soramitsu.staking.impl.scenarios.relaychain.StakingRelayChainScenarioRepository
import jp.co.soramitsu.wallet.impl.domain.AssetUseCase
import jp.co.soramitsu.wallet.api.domain.TokenUseCase
import jp.co.soramitsu.wallet.api.domain.implementations.AssetUseCaseImpl
import jp.co.soramitsu.wallet.api.domain.implementations.TokenUseCaseImpl
import jp.co.soramitsu.wallet.api.domain.interfaces.TokenRepository
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletConstants
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import jp.co.soramitsu.wallet.api.presentation.mixin.assetSelector.AssetSelectorFactory
import jp.co.soramitsu.wallet.api.presentation.mixin.assetSelector.AssetSelectorMixin
import jp.co.soramitsu.wallet.api.presentation.mixin.fee.FeeLoaderMixin
import jp.co.soramitsu.wallet.api.presentation.mixin.fee.FeeLoaderProvider
import jp.co.soramitsu.runtime.di.LOCAL_STORAGE_SOURCE
import jp.co.soramitsu.runtime.di.REMOTE_STORAGE_SOURCE
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.repository.ChainStateRepository
import jp.co.soramitsu.runtime.storage.source.StorageDataSource

@InstallIn(SingletonComponent::class)
@Module
class StakingFeatureModule {

    @Provides
    @Singleton
    @Named("StakingTokenUseCase")
    fun provideTokenUseCase(
        tokenRepository: TokenRepository,
        sharedState: StakingSharedState,
    ): TokenUseCase = TokenUseCaseImpl(
        tokenRepository,
        sharedState
    )

    @Provides
    @Singleton
    @Named("StakingFeeLoader")
    fun provideFeeLoaderMixin(
        resourceManager: ResourceManager,
        @Named("StakingTokenUseCase") tokenUseCase: TokenUseCase,
    ): FeeLoaderMixin.Presentation = FeeLoaderProvider(
        resourceManager,
        tokenUseCase
    )

    @Provides
    @Singleton
    @Named("StakingAssetUseCase")
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
    @Singleton
    @Named("StakingAssetSelector")
    fun provideAssetSelectorMixinFactory(
        @Named("StakingAssetUseCase") assetUseCase: AssetUseCase,
        singleAssetSharedState: StakingSharedState,
        resourceManager: ResourceManager
    ): AssetSelectorMixin.Presentation.Factory = AssetSelectorFactory(
        assetUseCase,
        singleAssetSharedState,
        resourceManager
    )

    @Provides
    @Singleton
    fun provideStakingSharedState(
        chainRegistry: ChainRegistry,
        preferences: Preferences
    ): StakingSharedState = StakingSharedState(chainRegistry, preferences)

    @Provides
    @Singleton
    fun provideStakingStoriesDataSource(
        setupStakingSharedState: SetupStakingSharedState
    ): StakingStoriesDataSource {
        return when (val state = setupStakingSharedState.setupStakingProcess.value) {
            is SetupStakingProcess.Initial -> if (state.stakingType == Chain.Asset.StakingType.RELAYCHAIN) {
                StakingStoriesDataSourceImpl()
            } else {
                ParachainStakingStoriesDataSourceImpl()
            }

            is SetupStakingProcess.ReadyToSubmit.Stash,
            is SetupStakingProcess.SelectBlockProducersStep.Validators,
            is SetupStakingProcess.SetupStep.Stash -> {
                StakingStoriesDataSourceImpl()
            }

            is SetupStakingProcess.ReadyToSubmit.Parachain,
            is SetupStakingProcess.SelectBlockProducersStep.Collators,
            is SetupStakingProcess.SetupStep.Parachain -> {
                ParachainStakingStoriesDataSourceImpl()
            }
        }
    }

    @Provides
    @Singleton
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
    @Singleton
    fun provideParachainStakingRepository(
        @Named(REMOTE_STORAGE_SOURCE) remoteStorageSource: StorageDataSource,
        @Named(LOCAL_STORAGE_SOURCE) localStorageSource: StorageDataSource
    ) = StakingParachainScenarioRepository(remoteStorageSource, localStorageSource)

    @Provides
    @Singleton
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
    @Singleton
    fun provideStakingRepository(
        @Named(LOCAL_STORAGE_SOURCE) localStorageSource: StorageDataSource,
        stakingStoriesDataSource: StakingStoriesDataSource
    ): StakingRepository = StakingRepositoryImpl(
        localStorage = localStorageSource,
        stakingStoriesDataSource = stakingStoriesDataSource
    )

    @Provides
    @Singleton
    fun provideIdentityRepository(
        bulkRetriever: BulkRetriever,
        chainRegistry: ChainRegistry
    ): IdentityRepository = IdentityRepositoryImpl(
        bulkRetriever,
        chainRegistry
    )

    @Provides
    @Singleton
    fun provideStakingInteractor(
        walletRepository: WalletRepository,
        accountRepository: AccountRepository,
        stakingRepository: StakingRepository,
        stakingRewardsRepository: StakingRewardsRepository,
        stakingSharedState: StakingSharedState,
        @Named("StakingAssetUseCase") assetUseCase: AssetUseCase,
        chainStateRepository: ChainStateRepository,
        chainRegistry: ChainRegistry,
        addressIconGenerator: AddressIconGenerator
    ) = StakingInteractor(
        walletRepository,
        accountRepository,
        stakingRepository,
        stakingRewardsRepository,
        stakingSharedState,
        assetUseCase,
        chainStateRepository,
        chainRegistry,
        addressIconGenerator
    )

    @Provides
    @Singleton
    fun provideParachainScenarioInteractor(
        interactor: StakingInteractor,
        accountRepository: AccountRepository,
        stakingConstantsRepository: StakingConstantsRepository,
        stakingParachainScenarioRepository: StakingParachainScenarioRepository,
        identityRepository: IdentityRepository,
        stakingSharedState: StakingSharedState,
        iconGenerator: AddressIconGenerator,
        resourceManager: ResourceManager,
        delegationHistoryFetcher: SubQueryDelegationHistoryFetcher,
        walletRepository: WalletRepository
    ): StakingParachainScenarioInteractor {
        return StakingParachainScenarioInteractor(
            interactor,
            accountRepository,
            stakingConstantsRepository,
            stakingParachainScenarioRepository,
            identityRepository,
            stakingSharedState,
            iconGenerator,
            resourceManager,
            delegationHistoryFetcher,
            walletRepository
        )
    }

    @Provides
    @Singleton
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
        payoutRepository: PayoutRepository,
        walletConstants: WalletConstants
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
            payoutRepository,
            walletConstants
        )
    }

    @Provides
    @Singleton
    fun provideEraTimeCalculatorFactory(
        stakingRelayChainScenarioRepository: StakingRelayChainScenarioRepository
    ) = EraTimeCalculatorFactory(stakingRelayChainScenarioRepository)

    @Provides
    @Singleton
    fun provideAlertsInteractor(
        stakingRelayChainScenarioRepository: StakingRelayChainScenarioRepository,
        stakingConstantsRepository: StakingConstantsRepository,
        sharedState: StakingSharedState,
        walletRepository: WalletRepository,
        accountRepository: AccountRepository
    ) = AlertsInteractor(
        stakingRelayChainScenarioRepository,
        stakingConstantsRepository,
        sharedState,
        walletRepository,
        accountRepository
    )

    @Provides
    @Singleton
    fun provideRewardCalculatorFactory(
        stakingRelayChainScenarioRepository: StakingRelayChainScenarioRepository,
        repository: StakingRepository,
        sharedState: StakingSharedState,
        stakingScenarioInteractor: StakingScenarioInteractor,
        stakingApi: StakingApi
    ) = RewardCalculatorFactory(stakingRelayChainScenarioRepository, repository, sharedState, stakingScenarioInteractor, stakingApi)

    @Provides
    @Singleton
    fun provideValidatorRecommendatorFactory(
        validatorProvider: ValidatorProvider,
        computationalCache: ComputationalCache,
        sharedState: StakingSharedState
    ) = ValidatorRecommendatorFactory(validatorProvider, sharedState, computationalCache)

    @Provides
    @Singleton
    fun provideCollatorRecommendatorFactory(
        collatorProvider: CollatorProvider,
        computationalCache: ComputationalCache,
        sharedState: StakingSharedState
    ) = CollatorRecommendatorFactory(collatorProvider, sharedState, computationalCache)

    @Provides
    @Singleton
    fun provideValidatorProvider(
        stakingRelayChainScenarioRepository: StakingRelayChainScenarioRepository,
        identityRepository: IdentityRepository,
        rewardCalculatorFactory: RewardCalculatorFactory,
        stakingConstantsRepository: StakingConstantsRepository
    ) = ValidatorProvider(
        stakingRelayChainScenarioRepository,
        identityRepository,
        rewardCalculatorFactory,
        stakingConstantsRepository
    )

    @Provides
    @Singleton
    fun provideCollatorProvider(
        stakingParachainScenarioRepository: StakingParachainScenarioRepository,
        identityRepository: IdentityRepository,
        rewardCalculatorFactory: RewardCalculatorFactory,
        stakingConstantsRepository: StakingConstantsRepository,
        accountRepository: AccountRepository
    ) = CollatorProvider(
        stakingParachainScenarioRepository,
        identityRepository,
        rewardCalculatorFactory,
        stakingConstantsRepository,
        accountRepository
    )

    @Provides
    @Singleton
    fun provideStakingConstantsRepository(
        chainRegistry: ChainRegistry
    ) = StakingConstantsRepository(chainRegistry)

    @Provides
    @Singleton
    fun provideRecommendationSettingsProviderFactory(
        stakingConstantsRepository: StakingConstantsRepository,
        computationalCache: ComputationalCache,
        sharedState: StakingSharedState
    ) = RecommendationSettingsProviderFactory(
        computationalCache,
        stakingConstantsRepository,
        sharedState
    )

    @Provides
    @Singleton
    fun provideSetupStakingInteractor(
        extrinsicService: ExtrinsicService,
        sharedState: StakingSharedState
    ) = SetupStakingInteractor(extrinsicService, sharedState)

    @Provides
    @Singleton
    fun provideSetupStakingSharedState() = SetupStakingSharedState()

    @Provides
    fun provideRewardDestinationChooserMixin(
        resourceManager: ResourceManager,
        appLinksProvider: AppLinksProvider,
        stakingInteractor: StakingInteractor,
        stakingRelayChainScenarioInteractor: StakingRelayChainScenarioInteractor,
        iconGenerator: AddressIconGenerator,
        accountDisplayUseCase: AddressDisplayUseCase,
        sharedState: StakingSharedState
    ): RewardDestinationMixin.Presentation = RewardDestinationProvider(
        resourceManager,
        stakingInteractor,
        stakingRelayChainScenarioInteractor,
        iconGenerator,
        appLinksProvider,
        sharedState,
        accountDisplayUseCase
    )

    @Provides
    @Singleton
    fun provideStakingRewardsApi(networkApiCreator: NetworkApiCreator): StakingApi {
        return networkApiCreator.create(StakingApi::class.java)
    }

    @Provides
    @Singleton
    fun provideStakingRewardsRepository(
        rewardDataSource: StakingRewardsDataSource
    ): StakingRewardsRepository {
        return StakingRewardsRepository(rewardDataSource)
    }

    @Provides
    @Singleton
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
    @Singleton
    fun provideDelegationHistoryFetcher(
        stakingApi: StakingApi,
        chainRegistry: ChainRegistry
    ): SubQueryDelegationHistoryFetcher {
        return SubQueryDelegationHistoryFetcher(
            stakingApi,
            chainRegistry
        )
    }

    @Provides
    @Singleton
    fun providePayoutRepository(
        stakingRelayChainScenarioRepository: StakingRelayChainScenarioRepository,
        validatorSetFetcher: SubQueryValidatorSetFetcher,
        bulkRetriever: BulkRetriever,
        storageCache: StorageCache,
        chainRegistry: ChainRegistry
    ): PayoutRepository {
        return PayoutRepository(stakingRelayChainScenarioRepository, bulkRetriever, validatorSetFetcher, storageCache, chainRegistry)
    }

    @Provides
    @Singleton
    fun providePayoutInteractor(
        sharedState: StakingSharedState,
        extrinsicService: ExtrinsicService
    ) = PayoutInteractor(sharedState, extrinsicService)

    @Provides
    @Singleton
    fun provideBondMoreInteractor(
        sharedState: StakingSharedState,
        extrinsicService: ExtrinsicService
    ) = BondMoreInteractor(extrinsicService, sharedState)

    @Provides
    @Singleton
    fun provideUnbondInteractor(
        extrinsicService: ExtrinsicService
    ) = UnbondInteractor(extrinsicService)

    @Provides
    @Singleton
    fun provideRedeemInteractor(
        extrinsicService: ExtrinsicService
    ) = RedeemInteractor(extrinsicService)

    @Provides
    @Singleton
    fun provideRebondInteractor(
        sharedState: StakingSharedState,
        extrinsicService: ExtrinsicService
    ) = RebondInteractor(extrinsicService, sharedState)

    @Provides
    @Singleton
    fun provideControllerInteractor(
        sharedState: StakingSharedState,
        extrinsicService: ExtrinsicService
    ) = ControllerInteractor(extrinsicService, sharedState)

    @Provides
    @Singleton
    fun provideCurrentValidatorsInteractor(
        stakingRelayChainScenarioRepository: StakingRelayChainScenarioRepository,
        stakingConstantsRepository: StakingConstantsRepository,
        validatorProvider: ValidatorProvider
    ) = CurrentValidatorsInteractor(
        stakingRelayChainScenarioRepository,
        stakingConstantsRepository,
        validatorProvider
    )

    @Provides
    @Singleton
    fun provideChangeRewardDestinationInteractor(
        extrinsicService: ExtrinsicService
    ) = ChangeRewardDestinationInteractor(extrinsicService)

    @Provides
    @Singleton
    fun provideSearchCustomValidatorsInteractor(
        validatorProvider: ValidatorProvider,
        sharedState: StakingSharedState
    ) = SearchCustomValidatorsInteractor(validatorProvider, sharedState)

    @Provides
    @Singleton
    fun provideSearchCustomBlockProducerInteractor(
        collatorProvider: CollatorProvider,
        validatorProvider: ValidatorProvider,
        sharedState: StakingSharedState,
        computationalCache: ComputationalCache,
        addressIconGenerator: AddressIconGenerator
    ) = SearchCustomBlockProducerInteractor(collatorProvider, validatorProvider, sharedState, computationalCache, addressIconGenerator)

    @Provides
    @Singleton
    fun provideSettingsStorage() = SettingsStorage()
}
