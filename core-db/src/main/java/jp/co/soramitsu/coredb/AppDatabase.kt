package jp.co.soramitsu.coredb

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import jp.co.soramitsu.common.data.secrets.v1.SecretStoreV1
import jp.co.soramitsu.common.data.secrets.v2.SecretStoreV2
import jp.co.soramitsu.coredb.converters.CryptoTypeConverters
import jp.co.soramitsu.coredb.converters.LongMathConverters
import jp.co.soramitsu.coredb.converters.OperationConverters
import jp.co.soramitsu.coredb.dao.AccountDao
import jp.co.soramitsu.coredb.dao.AccountStakingDao
import jp.co.soramitsu.coredb.dao.AddressBookDao
import jp.co.soramitsu.coredb.dao.AssetDao
import jp.co.soramitsu.coredb.dao.ChainDao
import jp.co.soramitsu.coredb.dao.MetaAccountDao
import jp.co.soramitsu.coredb.dao.OperationDao
import jp.co.soramitsu.coredb.dao.PhishingDao
import jp.co.soramitsu.coredb.dao.SoraCardDao
import jp.co.soramitsu.coredb.dao.StakingTotalRewardDao
import jp.co.soramitsu.coredb.dao.StorageDao
import jp.co.soramitsu.coredb.dao.TokenPriceDao
import jp.co.soramitsu.coredb.migrations.AddAccountStakingTable_14_15
import jp.co.soramitsu.coredb.migrations.AddChainExplorersTable_33_34
import jp.co.soramitsu.coredb.migrations.AddChainRegistryTables_27_28
import jp.co.soramitsu.coredb.migrations.AddNetworkTypeToStorageCache_13_14
import jp.co.soramitsu.coredb.migrations.AddOperationsTablesToDb_23_24
import jp.co.soramitsu.coredb.migrations.AddPhishingAddressesTable_10_11
import jp.co.soramitsu.coredb.migrations.AddRuntimeCacheTable_11_12
import jp.co.soramitsu.coredb.migrations.AddStakingRewardsTable_15_16
import jp.co.soramitsu.coredb.migrations.AddStorageCacheTable_12_13
import jp.co.soramitsu.coredb.migrations.AddTokenTable_9_10
import jp.co.soramitsu.coredb.migrations.AddTotalRewardsTableToDb_21_22
import jp.co.soramitsu.coredb.migrations.AssetsMigration_38_39
import jp.co.soramitsu.coredb.migrations.AssetsMigration_40_41
import jp.co.soramitsu.coredb.migrations.AssetsOrderMigration
import jp.co.soramitsu.coredb.migrations.ChainAssetsMigration_39_40
import jp.co.soramitsu.coredb.migrations.ChangePrimaryKeyForRewards_16_17
import jp.co.soramitsu.coredb.migrations.DifferentCurrenciesMigrations_37_38
import jp.co.soramitsu.coredb.migrations.EthereumDerivationPathMigration
import jp.co.soramitsu.coredb.migrations.FixAssetsMigration_36_37
import jp.co.soramitsu.coredb.migrations.MigrateTablesToV2_29_30
import jp.co.soramitsu.coredb.migrations.MigrateTablesToV2_30_31
import jp.co.soramitsu.coredb.migrations.MigrateTablesToV2_32_33
import jp.co.soramitsu.coredb.migrations.Migration_41_42
import jp.co.soramitsu.coredb.migrations.Migration_42_43
import jp.co.soramitsu.coredb.migrations.Migration_43_44
import jp.co.soramitsu.coredb.migrations.Migration_44_45
import jp.co.soramitsu.coredb.migrations.Migration_45_46
import jp.co.soramitsu.coredb.migrations.Migration_46_47
import jp.co.soramitsu.coredb.migrations.Migration_47_48
import jp.co.soramitsu.coredb.migrations.Migration_48_49
import jp.co.soramitsu.coredb.migrations.Migration_49_50
import jp.co.soramitsu.coredb.migrations.Migration_50_51
import jp.co.soramitsu.coredb.migrations.RemoveAccountForeignKeyFromAsset_17_18
import jp.co.soramitsu.coredb.migrations.RemoveLegacyData_35_36
import jp.co.soramitsu.coredb.migrations.RemoveStakingRewardsTable_22_23
import jp.co.soramitsu.coredb.migrations.V2Migration
import jp.co.soramitsu.coredb.model.AccountLocal
import jp.co.soramitsu.coredb.model.AccountStakingLocal
import jp.co.soramitsu.coredb.model.AddressBookContact
import jp.co.soramitsu.coredb.model.AssetLocal
import jp.co.soramitsu.coredb.model.OperationLocal
import jp.co.soramitsu.coredb.model.PhishingLocal
import jp.co.soramitsu.coredb.model.SoraCardInfoLocal
import jp.co.soramitsu.coredb.model.StorageEntryLocal
import jp.co.soramitsu.coredb.model.TokenPriceLocal
import jp.co.soramitsu.coredb.model.TotalRewardLocal
import jp.co.soramitsu.coredb.model.chain.ChainAccountLocal
import jp.co.soramitsu.coredb.model.chain.ChainAssetLocal
import jp.co.soramitsu.coredb.model.chain.ChainExplorerLocal
import jp.co.soramitsu.coredb.model.chain.ChainLocal
import jp.co.soramitsu.coredb.model.chain.ChainNodeLocal
import jp.co.soramitsu.coredb.model.chain.ChainRuntimeInfoLocal
import jp.co.soramitsu.coredb.model.chain.MetaAccountLocal

@Database(
    version = 51,
    entities = [
        AccountLocal::class,
        AddressBookContact::class,
        AssetLocal::class,
        TokenPriceLocal::class,
        PhishingLocal::class,
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
        ChainExplorerLocal::class,
        SoraCardInfoLocal::class
    ]
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
                instance = Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "app.db")
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
                    .addMigrations(Migration_41_42)
                    .addMigrations(Migration_42_43)
                    .addMigrations(Migration_43_44)
                    .addMigrations(Migration_44_45)
                    .addMigrations(Migration_45_46)
                    .addMigrations(Migration_46_47)
                    .addMigrations(Migration_47_48)
                    .addMigrations(Migration_48_49)
                    .addMigrations(Migration_49_50)
                    .addMigrations(Migration_50_51)
                    .build()
            }
            return instance!!
        }
    }

    abstract fun userDao(): AccountDao

    abstract fun assetDao(): AssetDao

    abstract fun operationDao(): OperationDao

    abstract fun phishingDao(): PhishingDao

    abstract fun storageDao(): StorageDao

    abstract fun tokenDao(): TokenPriceDao

    abstract fun accountStakingDao(): AccountStakingDao

    abstract fun stakingTotalRewardDao(): StakingTotalRewardDao

    abstract fun chainDao(): ChainDao

    abstract fun metaAccountDao(): MetaAccountDao

    abstract fun addressBookDao(): AddressBookDao

    abstract fun soraCardDao(): SoraCardDao
}
