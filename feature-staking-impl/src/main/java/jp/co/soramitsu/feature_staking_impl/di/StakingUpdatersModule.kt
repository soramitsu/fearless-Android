package jp.co.soramitsu.feature_staking_impl.di

import dagger.Module
import dagger.Provides
import jp.co.soramitsu.common.data.network.rpc.BulkRetriever
import jp.co.soramitsu.common.di.scope.FeatureScope
import jp.co.soramitsu.core.storage.StorageCache
import jp.co.soramitsu.core_db.dao.AccountStakingDao
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.updaters.AccountUpdateScope
import jp.co.soramitsu.feature_staking_api.di.StakingUpdaters
import jp.co.soramitsu.feature_staking_api.domain.api.StakingRepository
import jp.co.soramitsu.feature_staking_impl.data.StakingSharedState
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters.AccountNominationsUpdater
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters.AccountRewardDestinationUpdater
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters.AccountValidatorPrefsUpdater
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters.ActiveEraUpdater
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters.CounterForNominatorsUpdater
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters.CurrentEraUpdater
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
import jp.co.soramitsu.feature_wallet_api.data.cache.AssetCache
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry

@Module
class StakingUpdatersModule {

    @Provides
    @FeatureScope
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
    @FeatureScope
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
    @FeatureScope
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
    @FeatureScope
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
    @FeatureScope
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
    @FeatureScope
    fun provideStakingLedgerUpdater(
        stakingRepository: StakingRepository,
        sharedState: StakingSharedState,
        chainRegistry: ChainRegistry,
        accountStakingDao: AccountStakingDao,
        assetCache: AssetCache,
        storageCache: StorageCache,
        accountUpdateScope: AccountUpdateScope,
    ): StakingLedgerUpdater {
        return StakingLedgerUpdater(
            stakingRepository,
            sharedState,
            chainRegistry,
            accountStakingDao,
            storageCache,
            assetCache,
            accountUpdateScope
        )
    }

    @Provides
    @FeatureScope
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
    @FeatureScope
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
    @FeatureScope
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
    @FeatureScope
    fun provideHistoryDepthUpdater(
        sharedState: StakingSharedState,
        chainRegistry: ChainRegistry,
        storageCache: StorageCache,
    ) = HistoryDepthUpdater(
        sharedState,
        chainRegistry, storageCache
    )

    @Provides
    @FeatureScope
    fun provideHistoricalMediator(
        sharedState: StakingSharedState,
        chainRegistry: ChainRegistry,
        bulkRetriever: BulkRetriever,
        stakingRepository: StakingRepository,
        storageCache: StorageCache,
    ) = HistoricalUpdateMediator(
        historicalUpdaters = listOf(
            HistoricalTotalValidatorRewardUpdater(),
            HistoricalValidatorRewardPointsUpdater(),
        ),
        stakingSharedState= sharedState,
        chainRegistry = chainRegistry,
        bulkRetriever = bulkRetriever,
        stakingRepository = stakingRepository,
        storageCache = storageCache
    )

    @Provides
    @FeatureScope
    fun provideAccountControllerBalanceUpdater(
        assetCache: AssetCache,
        scope: AccountStakingScope,
        sharedState: StakingSharedState,
        chainRegistry: ChainRegistry,
    ) = AccountControllerBalanceUpdater(
        scope,
        sharedState,
        chainRegistry,
        assetCache
    )

    @Provides
    @FeatureScope
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
    @FeatureScope
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
    @FeatureScope
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
    @FeatureScope
    fun provideStakingUpdaters(
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
    ) = StakingUpdaters(
        updaters = arrayOf(
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
        )
    )
}
