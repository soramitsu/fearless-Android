package jp.co.soramitsu.core_db.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jp.co.soramitsu.common.data.secrets.v1.SecretStoreV1
import jp.co.soramitsu.common.data.secrets.v2.SecretStoreV2
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.dao.AccountDao
import jp.co.soramitsu.core_db.dao.AccountStakingDao
import jp.co.soramitsu.core_db.dao.AssetDao
import jp.co.soramitsu.core_db.dao.ChainDao
import jp.co.soramitsu.core_db.dao.MetaAccountDao
import jp.co.soramitsu.core_db.dao.OperationDao
import jp.co.soramitsu.core_db.dao.PhishingAddressDao
import jp.co.soramitsu.core_db.dao.StakingTotalRewardDao
import jp.co.soramitsu.core_db.dao.StorageDao
import jp.co.soramitsu.core_db.dao.TokenDao
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
class DbModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        context: Context,
        storeV1: SecretStoreV1,
        storeV2: SecretStoreV2
    ): AppDatabase {
        return AppDatabase.get(context, storeV1, storeV2)
    }

    @Provides
    @Singleton
    fun provideUserDao(appDatabase: AppDatabase): AccountDao {
        return appDatabase.userDao()
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
    fun providePhishingAddressDao(appDatabase: AppDatabase): PhishingAddressDao {
        return appDatabase.phishingAddressesDao()
    }

    @Provides
    @Singleton
    fun provideStorageDao(appDatabase: AppDatabase): StorageDao {
        return appDatabase.storageDao()
    }

    @Provides
    @Singleton
    fun provideTokenDao(appDatabase: AppDatabase): TokenDao {
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
}
