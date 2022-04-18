package jp.co.soramitsu.core_db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import jp.co.soramitsu.common.data.secrets.v1.SecretStoreV1
import jp.co.soramitsu.common.data.secrets.v2.SecretStoreV2
import jp.co.soramitsu.core_db.converters.CryptoTypeConverters
import jp.co.soramitsu.core_db.converters.LongMathConverters
import jp.co.soramitsu.core_db.converters.OperationConverters
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
import jp.co.soramitsu.core_db.migrations.AddAccountStakingTable_14_15
import jp.co.soramitsu.core_db.migrations.AddChainExplorersTable_33_34
import jp.co.soramitsu.core_db.migrations.AddChainRegistryTables_27_28
import jp.co.soramitsu.core_db.migrations.AddNetworkTypeToStorageCache_13_14
import jp.co.soramitsu.core_db.migrations.AddOperationsTablesToDb_23_24
import jp.co.soramitsu.core_db.migrations.AddPhishingAddressesTable_10_11
import jp.co.soramitsu.core_db.migrations.AddRuntimeCacheTable_11_12
import jp.co.soramitsu.core_db.migrations.AddStakingRewardsTable_15_16
import jp.co.soramitsu.core_db.migrations.AddStorageCacheTable_12_13
import jp.co.soramitsu.core_db.migrations.AddTokenTable_9_10
import jp.co.soramitsu.core_db.migrations.AddTotalRewardsTableToDb_21_22
import jp.co.soramitsu.core_db.migrations.AssetsMigration_38_39
import jp.co.soramitsu.core_db.migrations.AssetsMigration_40_41
import jp.co.soramitsu.core_db.migrations.AssetsOrderMigration
import jp.co.soramitsu.core_db.migrations.ChainAssetsMigration_39_40
import jp.co.soramitsu.core_db.migrations.ChangePrimaryKeyForRewards_16_17
import jp.co.soramitsu.core_db.migrations.DifferentCurrenciesMigrations_37_38
import jp.co.soramitsu.core_db.migrations.EthereumDerivationPathMigration
import jp.co.soramitsu.core_db.migrations.FixAssetsMigration_36_37
import jp.co.soramitsu.core_db.migrations.MigrateTablesToV2_29_30
import jp.co.soramitsu.core_db.migrations.MigrateTablesToV2_30_31
import jp.co.soramitsu.core_db.migrations.MigrateTablesToV2_32_33
import jp.co.soramitsu.core_db.migrations.RemoveAccountForeignKeyFromAsset_17_18
import jp.co.soramitsu.core_db.migrations.RemoveLegacyData_35_36
import jp.co.soramitsu.core_db.migrations.RemoveStakingRewardsTable_22_23
import jp.co.soramitsu.core_db.migrations.V2Migration
import jp.co.soramitsu.core_db.model.AccountLocal
import jp.co.soramitsu.core_db.model.AccountStakingLocal
import jp.co.soramitsu.core_db.model.AssetLocal
import jp.co.soramitsu.core_db.model.OperationLocal
import jp.co.soramitsu.core_db.model.PhishingAddressLocal
import jp.co.soramitsu.core_db.model.StorageEntryLocal
import jp.co.soramitsu.core_db.model.TokenLocal
import jp.co.soramitsu.core_db.model.TotalRewardLocal
import jp.co.soramitsu.core_db.model.chain.ChainAccountLocal
import jp.co.soramitsu.core_db.model.chain.ChainAssetLocal
import jp.co.soramitsu.core_db.model.chain.ChainExplorerLocal
import jp.co.soramitsu.core_db.model.chain.ChainLocal
import jp.co.soramitsu.core_db.model.chain.ChainNodeLocal
import jp.co.soramitsu.core_db.model.chain.ChainRuntimeInfoLocal
import jp.co.soramitsu.core_db.model.chain.MetaAccountLocal

@Database(
    version = 42,
    entities = [
        AccountLocal::class,
        AssetLocal::class,
        TokenLocal::class,
        PhishingAddressLocal::class,
        StorageEntryLocal::class,
        AccountStakingLocal::class,
        TotalRewardLocal::class,
        OperationLocal::class,

        ChainLocal::class,
        ChainNodeLocal::class,
        ChainAssetLocal::class,
        ChainRuntimeInfoLocal::class,
        MetaAccountLocal::class,
        ChainAccountLocal::class,
        ChainExplorerLocal::class
    ],
    autoMigrations = [AutoMigration(from = 41, to = 42)]
)
@TypeConverters(
    LongMathConverters::class,
    OperationConverters::class,
    CryptoTypeConverters::class
)

abstract class AppDatabase : RoomDatabase() {

    companion object {

        private var instance: AppDatabase? = null

        @Synchronized
        fun get(
            context: Context,
            storeV1: SecretStoreV1,
            storeV2: SecretStoreV2
        ): AppDatabase {
            if (instance == null) {
                instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java, "app.db"
                )
                    .fallbackToDestructiveMigration()
                    .addMigrations(AddTokenTable_9_10, AddPhishingAddressesTable_10_11, AddRuntimeCacheTable_11_12)
                    .addMigrations(AddStorageCacheTable_12_13, AddNetworkTypeToStorageCache_13_14)
                    .addMigrations(AddAccountStakingTable_14_15, AddStakingRewardsTable_15_16, ChangePrimaryKeyForRewards_16_17)
                    .addMigrations(RemoveAccountForeignKeyFromAsset_17_18)
                    .addMigrations(AddTotalRewardsTableToDb_21_22, RemoveStakingRewardsTable_22_23)
                    .addMigrations(AddOperationsTablesToDb_23_24)
                    .addMigrations(AddChainRegistryTables_27_28, V2Migration(storeV1, storeV2), MigrateTablesToV2_29_30)
                    .addMigrations(MigrateTablesToV2_30_31)
                    .addMigrations(EthereumDerivationPathMigration(storeV2))
                    .addMigrations(MigrateTablesToV2_32_33)
                    .addMigrations(AddChainExplorersTable_33_34)
                    .addMigrations(AssetsOrderMigration())
                    .addMigrations(RemoveLegacyData_35_36)
                    .addMigrations(FixAssetsMigration_36_37)
                    .addMigrations(DifferentCurrenciesMigrations_37_38)
                    .addMigrations(AssetsMigration_38_39)
                    .addMigrations(ChainAssetsMigration_39_40)
                    .addMigrations(AssetsMigration_40_41)
                    .build()
            }
            return instance!!
        }
    }

    abstract fun userDao(): AccountDao

    abstract fun assetDao(): AssetDao

    abstract fun operationDao(): OperationDao

    abstract fun phishingAddressesDao(): PhishingAddressDao

    abstract fun storageDao(): StorageDao

    abstract fun tokenDao(): TokenDao

    abstract fun accountStakingDao(): AccountStakingDao

    abstract fun stakingTotalRewardDao(): StakingTotalRewardDao

    abstract fun chainDao(): ChainDao

    abstract fun metaAccountDao(): MetaAccountDao
}
