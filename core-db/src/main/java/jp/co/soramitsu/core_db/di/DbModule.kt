package jp.co.soramitsu.core_db.di

import android.content.Context
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.di.scope.ApplicationScope
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.dao.AccountDao
import jp.co.soramitsu.core_db.dao.AccountStakingDao
import jp.co.soramitsu.core_db.dao.AssetDao
import jp.co.soramitsu.core_db.dao.ChainDao
import jp.co.soramitsu.core_db.dao.NodeDao
import jp.co.soramitsu.core_db.dao.PhishingAddressDao
import jp.co.soramitsu.core_db.dao.StakingRewardDao
import jp.co.soramitsu.core_db.dao.StakingTotalRewardDao
import jp.co.soramitsu.core_db.dao.StorageDao
import jp.co.soramitsu.core_db.dao.TokenDao
import jp.co.soramitsu.core_db.dao.TransactionDao
import jp.co.soramitsu.core_db.migrations.PrefsToDbActiveNodeMigrator

@Module
class DbModule {

    @Provides
    @ApplicationScope
    fun providePrefsToDbActiveNodeMigrator(
        gson: Gson,
        preferences: Preferences,
    ) = PrefsToDbActiveNodeMigrator(gson, preferences)

    @Provides
    @ApplicationScope
    fun provideAppDatabase(
        context: Context,
        prefsToDbActiveNodeMigrator: PrefsToDbActiveNodeMigrator,
    ): AppDatabase {
        return AppDatabase.get(context, prefsToDbActiveNodeMigrator)
    }

    @Provides
    @ApplicationScope
    fun provideUserDao(appDatabase: AppDatabase): AccountDao {
        return appDatabase.userDao()
    }

    @Provides
    @ApplicationScope
    fun provideNodeDao(appDatabase: AppDatabase): NodeDao {
        return appDatabase.nodeDao()
    }

    @Provides
    @ApplicationScope
    fun provideAssetDao(appDatabase: AppDatabase): AssetDao {
        return appDatabase.assetDao()
    }

    @Provides
    @ApplicationScope
    fun provideTransactionDao(appDatabase: AppDatabase): TransactionDao {
        return appDatabase.transactionsDao()
    }

    @Provides
    @ApplicationScope
    fun providePhishingAddressDao(appDatabase: AppDatabase): PhishingAddressDao {
        return appDatabase.phishingAddressesDao()
    }

    @Provides
    @ApplicationScope
    fun provideStorageDao(appDatabase: AppDatabase): StorageDao {
        return appDatabase.storageDao()
    }

    @Provides
    @ApplicationScope
    fun provideTokenDao(appDatabase: AppDatabase): TokenDao {
        return appDatabase.tokenDao()
    }

    @Provides
    @ApplicationScope
    fun provideAccountStakingDao(appDatabase: AppDatabase): AccountStakingDao {
        return appDatabase.accountStakingDao()
    }

    @Provides
    @ApplicationScope
    fun provideStakingRewardDao(appDatabase: AppDatabase): StakingRewardDao {
        return appDatabase.stakingRewardsDao()
    }

    @Provides
    @ApplicationScope
    fun provideStakingTotalRewardDao(appDatabase: AppDatabase): StakingTotalRewardDao {
        return appDatabase.stakingTotalRewardDao()
    }
    @Provides
    @ApplicationScope
    fun provideChainDao(appDatabase: AppDatabase): ChainDao {
        return appDatabase.chainDao()
    }
}
