package jp.co.soramitsu.core_db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import jp.co.soramitsu.core_db.converters.CryptoTypeConverters
import jp.co.soramitsu.core_db.converters.LongMathConverters
import jp.co.soramitsu.core_db.converters.NetworkTypeConverters
import jp.co.soramitsu.core_db.converters.OperationConverters
import jp.co.soramitsu.core_db.converters.TokenConverters
import jp.co.soramitsu.core_db.dao.AccountDao
import jp.co.soramitsu.core_db.dao.AccountStakingDao
import jp.co.soramitsu.core_db.dao.AssetDao
import jp.co.soramitsu.core_db.dao.ChainDao
import jp.co.soramitsu.core_db.dao.MetaAccountDao
import jp.co.soramitsu.core_db.dao.NodeDao
import jp.co.soramitsu.core_db.dao.OperationDao
import jp.co.soramitsu.core_db.dao.PhishingAddressDao
import jp.co.soramitsu.core_db.dao.StakingTotalRewardDao
import jp.co.soramitsu.core_db.dao.StorageDao
import jp.co.soramitsu.core_db.dao.TokenDao
import jp.co.soramitsu.core_db.migrations.AddAccountStakingTable_14_15
import jp.co.soramitsu.core_db.migrations.AddChainRegistryTables_25_26
import jp.co.soramitsu.core_db.migrations.AddNetworkTypeToStorageCache_13_14
import jp.co.soramitsu.core_db.migrations.AddOperationsTablesToDb_23_24
import jp.co.soramitsu.core_db.migrations.AddPhishingAddressesTable_10_11
import jp.co.soramitsu.core_db.migrations.AddRuntimeCacheTable_11_12
import jp.co.soramitsu.core_db.migrations.AddStakingRewardsTable_15_16
import jp.co.soramitsu.core_db.migrations.AddStorageCacheTable_12_13
import jp.co.soramitsu.core_db.migrations.AddTokenTable_9_10
import jp.co.soramitsu.core_db.migrations.AddTotalRewardsTableToDb_21_22
import jp.co.soramitsu.core_db.migrations.ChangePrimaryKeyForRewards_16_17
import jp.co.soramitsu.core_db.migrations.MoveActiveNodeTrackingToDb_18_19
import jp.co.soramitsu.core_db.migrations.PrefsToDbActiveNodeMigrator
import jp.co.soramitsu.core_db.migrations.RemoveAccountForeignKeyFromAsset_17_18
import jp.co.soramitsu.core_db.migrations.RemoveStakingRewardsTable_22_23
import jp.co.soramitsu.core_db.migrations.UpdateDefaultNodesList
import jp.co.soramitsu.core_db.model.AccountLocal
import jp.co.soramitsu.core_db.model.AccountStakingLocal
import jp.co.soramitsu.core_db.model.AssetLocal
import jp.co.soramitsu.core_db.model.NodeLocal
import jp.co.soramitsu.core_db.model.OperationLocal
import jp.co.soramitsu.core_db.model.PhishingAddressLocal
import jp.co.soramitsu.core_db.model.StorageEntryLocal
import jp.co.soramitsu.core_db.model.TokenLocal
import jp.co.soramitsu.core_db.model.TotalRewardLocal
import jp.co.soramitsu.core_db.model.chain.ChainAccountLocal
import jp.co.soramitsu.core_db.model.chain.ChainAssetLocal
import jp.co.soramitsu.core_db.model.chain.ChainLocal
import jp.co.soramitsu.core_db.model.chain.ChainNodeLocal
import jp.co.soramitsu.core_db.model.chain.ChainRuntimeInfoLocal
import jp.co.soramitsu.core_db.model.chain.MetaAccountLocal
import jp.co.soramitsu.core_db.prepopulate.nodes.LATEST_DEFAULT_NODES
import jp.co.soramitsu.core_db.prepopulate.nodes.defaultNodesInsertQuery

@Database(
    version = 26,
    entities = [
        AccountLocal::class,
        NodeLocal::class,
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
        ChainAccountLocal::class
    ]
)
@TypeConverters(
    LongMathConverters::class,
    NetworkTypeConverters::class,
    TokenConverters::class,
    OperationConverters::class,
    CryptoTypeConverters::class
)

abstract class AppDatabase : RoomDatabase() {

    companion object {

        private var instance: AppDatabase? = null

        @Synchronized
        fun get(
            context: Context,
            prefsToDbActiveNodeMigrator: PrefsToDbActiveNodeMigrator,
        ): AppDatabase {
            if (instance == null) {
                instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java, "app.db"
                )
                    .fallbackToDestructiveMigration()
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            db.execSQL(defaultNodesInsertQuery(LATEST_DEFAULT_NODES))
                        }
                    })
                    .addMigrations(AddTokenTable_9_10, AddPhishingAddressesTable_10_11, AddRuntimeCacheTable_11_12)
                    .addMigrations(AddStorageCacheTable_12_13, AddNetworkTypeToStorageCache_13_14)
                    .addMigrations(AddAccountStakingTable_14_15, AddStakingRewardsTable_15_16, ChangePrimaryKeyForRewards_16_17)
                    .addMigrations(RemoveAccountForeignKeyFromAsset_17_18)
                    .addMigrations(MoveActiveNodeTrackingToDb_18_19(prefsToDbActiveNodeMigrator))
                    .addMigrations(UpdateDefaultNodesList(LATEST_DEFAULT_NODES, fromVersion = 19))
                    .addMigrations(UpdateDefaultNodesList(LATEST_DEFAULT_NODES, fromVersion = 20))
                    .addMigrations(AddTotalRewardsTableToDb_21_22, RemoveStakingRewardsTable_22_23)
                    .addMigrations(AddOperationsTablesToDb_23_24)
                    .addMigrations(UpdateDefaultNodesList(LATEST_DEFAULT_NODES, fromVersion = 24))
                    .addMigrations(AddChainRegistryTables_25_26)
                    .build()
            }
            return instance!!
        }
    }

    abstract fun nodeDao(): NodeDao

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
