package jp.co.soramitsu.feature_staking_impl.di

import dagger.Module
import dagger.Provides
import jp.co.soramitsu.common.data.network.rpc.BulkRetriever
import jp.co.soramitsu.common.di.scope.FeatureScope
import jp.co.soramitsu.common.utils.SuspendableProperty
import jp.co.soramitsu.core.storage.StorageCache
import jp.co.soramitsu.core_db.dao.AccountStakingDao
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.updaters.AccountUpdateScope
import jp.co.soramitsu.feature_staking_api.di.StakingUpdaters
import jp.co.soramitsu.feature_staking_api.domain.api.StakingRepository
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters.AccountNominationsUpdater
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters.AccountRewardDestinationUpdater
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters.AccountValidatorPrefsUpdater
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters.ActiveEraUpdater
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters.CurrentEraUpdater
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters.ElectionStatusUpdater
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters.HistoryDepthUpdater
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters.StakingLedgerUpdater
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters.TotalIssuanceUpdater
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters.ValidatorExposureUpdater
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters.ValidatorPrefsUpdater
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters.historical.HistoricalTotalValidatorRewardUpdater
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters.historical.HistoricalUpdateMediator
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters.historical.HistoricalValidatorRewardPointsUpdater
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters.scope.AccountStakingScope
import jp.co.soramitsu.feature_wallet_api.data.cache.AssetCache

@Module
class StakingUpdatersModule {

    @Provides
    @FeatureScope
    fun provideAccountStakingScope(
        accountRepository: AccountRepository,
        accountStakingDao: AccountStakingDao
    ) = AccountStakingScope(accountRepository, accountStakingDao)

    @Provides
    @FeatureScope
    fun provideActiveEraUpdater(
        runtimeProperty: SuspendableProperty<RuntimeSnapshot>,
        storageCache: StorageCache
    ) = ActiveEraUpdater(
        runtimeProperty,
        storageCache
    )

    @Provides
    @FeatureScope
    fun provideElectedNominatorsUpdater(
        runtimeProperty: SuspendableProperty<RuntimeSnapshot>,
        bulkRetriever: BulkRetriever,
        storageCache: StorageCache,
        accountRepository: AccountRepository,
    ) = ValidatorExposureUpdater(
        runtimeProperty,
        bulkRetriever,
        accountRepository,
        storageCache
    )

    @Provides
    @FeatureScope
    fun provideValidatorPrefsUpdater(
        runtimeProperty: SuspendableProperty<RuntimeSnapshot>,
        bulkRetriever: BulkRetriever,
        accountRepository: AccountRepository,
        storageCache: StorageCache
    ) = ValidatorPrefsUpdater(
        runtimeProperty,
        bulkRetriever,
        accountRepository,
        storageCache
    )

    @Provides
    @FeatureScope
    fun provideTotalInsuranceUpdater(
        runtimeProperty: SuspendableProperty<RuntimeSnapshot>,
        storageCache: StorageCache
    ) = TotalIssuanceUpdater(
        runtimeProperty,
        storageCache
    )

    @Provides
    @FeatureScope
    fun provideCurrentEraUpdater(
        runtimeProperty: SuspendableProperty<RuntimeSnapshot>,
        storageCache: StorageCache
    ) = CurrentEraUpdater(
        runtimeProperty,
        storageCache
    )

    @Provides
    @FeatureScope
    fun provideStakingLedgerUpdater(
        stakingRepository: StakingRepository,
        socketService: SocketService,
        runtimeProperty: SuspendableProperty<RuntimeSnapshot>,
        accountStakingDao: AccountStakingDao,
        assetCache: AssetCache,
        accountUpdateScope: AccountUpdateScope
    ): StakingLedgerUpdater {
        return StakingLedgerUpdater(
            socketService,
            stakingRepository,
            runtimeProperty,
            accountStakingDao,
            assetCache,
            accountUpdateScope
        )
    }

    @Provides
    @FeatureScope
    fun provideAccountValidatorPrefsUpdater(
        storageCache: StorageCache,
        scope: AccountStakingScope,
        runtimeProperty: SuspendableProperty<RuntimeSnapshot>
    ) = AccountValidatorPrefsUpdater(
        scope, storageCache, runtimeProperty
    )

    @Provides
    @FeatureScope
    fun provideAccountNominationsUpdater(
        storageCache: StorageCache,
        scope: AccountStakingScope,
        runtimeProperty: SuspendableProperty<RuntimeSnapshot>
    ) = AccountNominationsUpdater(
        scope, storageCache, runtimeProperty
    )

    @Provides
    @FeatureScope
    fun provideAccountRewardDestinationUpdater(
        storageCache: StorageCache,
        scope: AccountStakingScope,
        runtimeProperty: SuspendableProperty<RuntimeSnapshot>
    ) = AccountRewardDestinationUpdater(
        scope, storageCache, runtimeProperty
    )

    @Provides
    @FeatureScope
    fun provideElectionStatusUpdater(
        runtimeProperty: SuspendableProperty<RuntimeSnapshot>,
        storageCache: StorageCache,
    ) = ElectionStatusUpdater(
        runtimeProperty, storageCache
    )

    @Provides
    @FeatureScope
    fun provideHistoryDepthUpdater(
        runtimeProperty: SuspendableProperty<RuntimeSnapshot>,
        storageCache: StorageCache,
    ) = HistoryDepthUpdater(
        runtimeProperty, storageCache
    )

    @Provides
    @FeatureScope
    fun provideHistoricalMediator(
        runtimeProperty: SuspendableProperty<RuntimeSnapshot>,
        bulkRetriever: BulkRetriever,
        stakingRepository: StakingRepository,
        accountRepository: AccountRepository,
        storageCache: StorageCache,
    ) = HistoricalUpdateMediator(
        historicalUpdaters = listOf(
            HistoricalTotalValidatorRewardUpdater(),
            HistoricalValidatorRewardPointsUpdater()
        ),
        runtimeProperty = runtimeProperty,
        bulkRetriever = bulkRetriever,
        stakingRepository = stakingRepository,
        accountRepository = accountRepository,
        storageCache = storageCache
    )

    @Provides
    @FeatureScope
    fun provideStakingUpdaters(
        activeEraUpdater: ActiveEraUpdater,
        validatorExposureUpdater: ValidatorExposureUpdater,
        validatorPrefsUpdater: ValidatorPrefsUpdater,
        totalIssuanceUpdater: TotalIssuanceUpdater,
        currentEraUpdater: CurrentEraUpdater,
        stakingLedgerUpdater: StakingLedgerUpdater,
        accountValidatorPrefsUpdater: AccountValidatorPrefsUpdater,
        accountNominationsUpdater: AccountNominationsUpdater,
        electionStatusUpdater: ElectionStatusUpdater,
        rewardDestinationUpdater: AccountRewardDestinationUpdater,
        historyDepthUpdater: HistoryDepthUpdater,
        historicalUpdateMediator: HistoricalUpdateMediator,
    ) = StakingUpdaters(
        updaters = arrayOf(
            activeEraUpdater,
            validatorExposureUpdater,
            validatorPrefsUpdater,
            totalIssuanceUpdater,
            currentEraUpdater,
            stakingLedgerUpdater,
            accountValidatorPrefsUpdater,
            accountNominationsUpdater,
            electionStatusUpdater,
            rewardDestinationUpdater,
            historyDepthUpdater,
            historicalUpdateMediator
        )
    )
}
