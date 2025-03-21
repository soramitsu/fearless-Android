package jp.co.soramitsu.coredb.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import jp.co.soramitsu.common.data.secrets.v1.SecretStoreV1
import jp.co.soramitsu.common.data.secrets.v2.SecretStoreV2
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecretStore
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecretStore
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.coredb.AppDatabase
import jp.co.soramitsu.coredb.dao.AccountStakingDao
import jp.co.soramitsu.coredb.dao.AddressBookDao
import jp.co.soramitsu.coredb.dao.AssetDao
import jp.co.soramitsu.coredb.dao.ChainDao
import jp.co.soramitsu.coredb.dao.MetaAccountDao
import jp.co.soramitsu.coredb.dao.NomisScoresDao
import jp.co.soramitsu.coredb.dao.OperationDao
import jp.co.soramitsu.coredb.dao.PhishingDao
import jp.co.soramitsu.coredb.dao.PoolDao
import jp.co.soramitsu.coredb.dao.SoraCardDao
import jp.co.soramitsu.coredb.dao.StakingTotalRewardDao
import jp.co.soramitsu.coredb.dao.StorageDao
import jp.co.soramitsu.coredb.dao.TokenPriceDao
import jp.co.soramitsu.coredb.dao.TonConnectDao

@InstallIn(SingletonComponent::class)
@Module
class DbModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        context: Context,
        storeV1: SecretStoreV1,
        storeV2: SecretStoreV2,
        encryptedPreferences: EncryptedPreferences,
        substrateSecretStore: SubstrateSecretStore,
        ethereumSecretStore: EthereumSecretStore
    ): AppDatabase {
        return AppDatabase.get(context, storeV1, storeV2, encryptedPreferences, substrateSecretStore, ethereumSecretStore)
    }

    @Provides
    @Singleton
    fun provideAssetDao(appDatabase: AppDatabase): AssetDao {
        return appDatabase.assetDao()
    }

    @Provides
    @Singleton
    fun provideOperationHistoryDao(appDatabase: AppDatabase): OperationDao {
        return appDatabase.operationDao()
    }

    @Provides
    @Singleton
    fun providePhishingDao(appDatabase: AppDatabase): PhishingDao {
        return appDatabase.phishingDao()
    }

    @Provides
    @Singleton
    fun provideStorageDao(appDatabase: AppDatabase): StorageDao {
        return appDatabase.storageDao()
    }

    @Provides
    @Singleton
    fun provideTokenDao(appDatabase: AppDatabase): TokenPriceDao {
        return appDatabase.tokenDao()
    }

    @Provides
    @Singleton
    fun provideAccountStakingDao(appDatabase: AppDatabase): AccountStakingDao {
        return appDatabase.accountStakingDao()
    }

    @Provides
    @Singleton
    fun provideStakingTotalRewardDao(appDatabase: AppDatabase): StakingTotalRewardDao {
        return appDatabase.stakingTotalRewardDao()
    }

    @Provides
    @Singleton
    fun provideChainDao(appDatabase: AppDatabase): ChainDao {
        return appDatabase.chainDao()
    }

    @Provides
    @Singleton
    fun provideMetaAccountDao(appDatabase: AppDatabase): MetaAccountDao {
        return appDatabase.metaAccountDao()
    }

    @Provides
    @Singleton
    fun provideAddressBookDao(appDatabase: AppDatabase): AddressBookDao {
        return appDatabase.addressBookDao()
    }

    @Provides
    @Singleton
    fun provideSoraCardDao(appDatabase: AppDatabase): SoraCardDao {
        return appDatabase.soraCardDao()
    }

    @Provides
    @Singleton
    fun provideNomisScoresDao(appDatabase: AppDatabase): NomisScoresDao {
        return appDatabase.nomisScoresDao()
    }

    @Provides
    @Singleton
    fun providePoolsDao(appDatabase: AppDatabase): PoolDao {
        return appDatabase.poolDao()
    }

    @Provides
    @Singleton
    fun provideTonConnectDao(appDatabase: AppDatabase): TonConnectDao {
        return appDatabase.tonConnectDao()
    }
}
