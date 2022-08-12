package jp.co.soramitsu.feature_staking_impl.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jp.co.soramitsu.common.data.network.rpc.BulkRetriever
import jp.co.soramitsu.common.mixin.api.UpdatesMixin
import jp.co.soramitsu.core.storage.StorageCache
import jp.co.soramitsu.core.updater.UpdateSystem
import jp.co.soramitsu.core_db.dao.AccountStakingDao
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.updaters.AccountUpdateScope
import jp.co.soramitsu.feature_staking_api.data.StakingSharedState
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters.AccountNominationsUpdater
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters.AccountRewardDestinationUpdater
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters.AccountValidatorPrefsUpdater
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters.ActiveEraUpdater
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters.CounterForNominatorsUpdater
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters.CurrentEraUpdater
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters.DelegatorStateUpdater
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters.HistoryDepthUpdater
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters.MaxNominatorsUpdater
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters.MinBondUpdater
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters.StakingLedgerUpdater
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters.TotalIssuanceUpdater
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters.ValidatorExposureUpdater
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters.controller.AccountControllerBalanceUpdater
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters.historical.HistoricalTotalValidatorRewardUpdater
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters.historical.HistoricalUpdateMediator
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters.historical.HistoricalValidatorRewardPointsUpdater
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters.scope.AccountStakingScope
import jp.co.soramitsu.feature_staking_impl.scenarios.relaychain.StakingRelayChainScenarioRepository
import jp.co.soramitsu.feature_wallet_api.data.cache.AssetCache
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.network.updaters.BlockNumberUpdater
import jp.co.soramitsu.runtime.network.updaters.SingleChainUpdateSystem
import javax.inject.Named
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
class StakingUpdatersModule {

    @Provides
    @Singleton
    fun provideAccountStakingScope(
        accountRepository: AccountRepository,
        accountStakingDao: AccountStakingDao,
        sharedState: StakingSharedState,
    ) = AccountStakingScope(
        accountRepository,
        accountStakingDao,
        sharedState
    )

    @Provides
    @Singleton
    @Named("StakingBlockNumberUpdater")
    fun provideBlockNumberUpdater(
        chainRegistry: ChainRegistry,
        stakingSharedState: StakingSharedState,
        storageCache: StorageCache,
    ) = BlockNumberUpdater(chainRegistry, stakingSharedState, storageCache)

    @Provides
    @Singleton
    fun provideActiveEraUpdater(
        sharedState: StakingSharedState,
        chainRegistry: ChainRegistry,
        storageCache: StorageCache,
    ) = ActiveEraUpdater(
        sharedState,
        chainRegistry,
        storageCache
    )

    @Provides
    @Singleton
    fun provideElectedNominatorsUpdater(
        sharedState: StakingSharedState,
        chainRegistry: ChainRegistry,
        bulkRetriever: BulkRetriever,
        storageCache: StorageCache,
    ) = ValidatorExposureUpdater(
        bulkRetriever,
        sharedState,
        chainRegistry,
        storageCache
    )

    @Provides
    @Singleton
    fun provideTotalInsuranceUpdater(
        sharedState: StakingSharedState,
        chainRegistry: ChainRegistry,
        storageCache: StorageCache,
    ) = TotalIssuanceUpdater(
        sharedState,
        storageCache,
        chainRegistry
    )

    @Provides
    @Singleton
    fun provideCurrentEraUpdater(
        sharedState: StakingSharedState,
        chainRegistry: ChainRegistry,
        storageCache: StorageCache,
    ) = CurrentEraUpdater(
        sharedState,
        chainRegistry,
        storageCache
    )

    @Provides
    @Singleton
    fun provideStakingLedgerUpdater(
        stakingRelayChainScenarioRepository: StakingRelayChainScenarioRepository,
        sharedState: StakingSharedState,
        chainRegistry: ChainRegistry,
        accountStakingDao: AccountStakingDao,
        assetCache: AssetCache,
        storageCache: StorageCache,
        updatesMixin: UpdatesMixin,
        accountUpdateScope: AccountUpdateScope,
    ): StakingLedgerUpdater {
        return StakingLedgerUpdater(
            stakingRelayChainScenarioRepository,
            sharedState,
            chainRegistry,
            accountStakingDao,
            storageCache,
            assetCache,
            updatesMixin,
            accountUpdateScope
        )
    }

    @Provides
    @Singleton
    fun provideAccountValidatorPrefsUpdater(
        storageCache: StorageCache,
        scope: AccountStakingScope,
        sharedState: StakingSharedState,
        chainRegistry: ChainRegistry,
    ) = AccountValidatorPrefsUpdater(
        scope,
        storageCache,
        sharedState,
        chainRegistry,
    )

    @Provides
    @Singleton
    fun provideAccountNominationsUpdater(
        storageCache: StorageCache,
        scope: AccountStakingScope,
        sharedState: StakingSharedState,
        chainRegistry: ChainRegistry,
    ) = AccountNominationsUpdater(
        scope,
        storageCache,
        sharedState,
        chainRegistry,
    )

    @Provides
    @Singleton
    fun provideAccountRewardDestinationUpdater(
        storageCache: StorageCache,
        scope: AccountStakingScope,
        sharedState: StakingSharedState,
        chainRegistry: ChainRegistry,
    ) = AccountRewardDestinationUpdater(
        scope,
        storageCache,
        sharedState,
        chainRegistry,
    )

    @Provides
    @Singleton
    fun provideHistoryDepthUpdater(
        sharedState: StakingSharedState,
        chainRegistry: ChainRegistry,
        storageCache: StorageCache,
    ) = HistoryDepthUpdater(
        sharedState,
        chainRegistry, storageCache
    )

    @Provides
    @Singleton
    fun provideHistoricalMediator(
        sharedState: StakingSharedState,
        chainRegistry: ChainRegistry,
        bulkRetriever: BulkRetriever,
        stakingRelayChainScenarioRepository: StakingRelayChainScenarioRepository,
        storageCache: StorageCache,
    ) = HistoricalUpdateMediator(
        historicalUpdaters = listOf(
            HistoricalTotalValidatorRewardUpdater(),
            HistoricalValidatorRewardPointsUpdater(),
        ),
        stakingSharedState = sharedState,
        chainRegistry = chainRegistry,
        bulkRetriever = bulkRetriever,
        stakingRepository = stakingRelayChainScenarioRepository,
        storageCache = storageCache
    )

    @Provides
    @Singleton
    fun provideAccountControllerBalanceUpdater(
        assetCache: AssetCache,
        scope: AccountStakingScope,
        sharedState: StakingSharedState,
        chainRegistry: ChainRegistry,
        updatesMixin: UpdatesMixin
    ) = AccountControllerBalanceUpdater(
        scope,
        sharedState,
        chainRegistry,
        assetCache,
        updatesMixin
    )

    @Provides
    @Singleton
    fun provideMinBondUpdater(
        sharedState: StakingSharedState,
        chainRegistry: ChainRegistry,
        storageCache: StorageCache,
    ) = MinBondUpdater(
        sharedState,
        chainRegistry,
        storageCache
    )

    @Provides
    @Singleton
    fun provideMaxNominatorsUpdater(
        sharedState: StakingSharedState,
        chainRegistry: ChainRegistry,
        storageCache: StorageCache,
    ) = MaxNominatorsUpdater(
        storageCache,
        sharedState,
        chainRegistry
    )

    @Provides
    @Singleton
    fun provideCounterForNominatorsUpdater(
        sharedState: StakingSharedState,
        chainRegistry: ChainRegistry,
        storageCache: StorageCache,
    ) = CounterForNominatorsUpdater(
        sharedState,
        chainRegistry,
        storageCache
    )

    @Provides
    @Singleton
    fun provideDelegatorStateUpdater(
        scope: AccountStakingScope,
        sharedState: StakingSharedState,
        chainRegistry: ChainRegistry,
        storageCache: StorageCache,
    ) = DelegatorStateUpdater(
        scope,
        storageCache,
        sharedState,
        chainRegistry
    )

    @Provides
    @Singleton
    @Named("StakingChainUpdateSystem")
    fun provideStakingUpdaterSystem(
        activeEraUpdater: ActiveEraUpdater,
        validatorExposureUpdater: ValidatorExposureUpdater,
        totalIssuanceUpdater: TotalIssuanceUpdater,
        currentEraUpdater: CurrentEraUpdater,
        stakingLedgerUpdater: StakingLedgerUpdater,
        accountValidatorPrefsUpdater: AccountValidatorPrefsUpdater,
        accountNominationsUpdater: AccountNominationsUpdater,
        rewardDestinationUpdater: AccountRewardDestinationUpdater,
        historyDepthUpdater: HistoryDepthUpdater,
        historicalUpdateMediator: HistoricalUpdateMediator,
        accountControllerBalanceUpdater: AccountControllerBalanceUpdater,
        minBondUpdater: MinBondUpdater,
        maxNominatorsUpdater: MaxNominatorsUpdater,
        counterForNominatorsUpdater: CounterForNominatorsUpdater,
        delegatorStateUpdater: DelegatorStateUpdater,
        @Named("StakingBlockNumberUpdater") blockNumberUpdater: BlockNumberUpdater,

        chainRegistry: ChainRegistry,
        stakingSharedState: StakingSharedState
    ): UpdateSystem = SingleChainUpdateSystem(
        updaters = listOf(
            activeEraUpdater,
            validatorExposureUpdater,
            totalIssuanceUpdater,
            currentEraUpdater,
            stakingLedgerUpdater,
            accountValidatorPrefsUpdater,
            accountNominationsUpdater,
            rewardDestinationUpdater,
            historyDepthUpdater,
            historicalUpdateMediator,
            accountControllerBalanceUpdater,
            minBondUpdater,
            maxNominatorsUpdater,
            counterForNominatorsUpdater,
            delegatorStateUpdater,
            blockNumberUpdater
        ),
        chainRegistry = chainRegistry,
        singleAssetSharedState = stakingSharedState
    )
}
