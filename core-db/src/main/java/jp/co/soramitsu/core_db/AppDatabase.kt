package jp.co.soramitsu.core_db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import jp.co.soramitsu.core_db.converters.LongMathConverters
import jp.co.soramitsu.core_db.converters.NetworkTypeConverters
import jp.co.soramitsu.core_db.converters.TokenConverters
import jp.co.soramitsu.core_db.converters.TransactionConverters
import jp.co.soramitsu.core_db.dao.AccountDao
import jp.co.soramitsu.core_db.dao.AssetDao
import jp.co.soramitsu.core_db.dao.NodeDao
import jp.co.soramitsu.core_db.dao.PhishingAddressDao
import jp.co.soramitsu.core_db.dao.RuntimeDao
import jp.co.soramitsu.core_db.dao.StorageDao
import jp.co.soramitsu.core_db.dao.TokenDao
import jp.co.soramitsu.core_db.dao.TransactionDao
import jp.co.soramitsu.core_db.migrations.AddNetworkTypeToStorageCache_13_14
import jp.co.soramitsu.core_db.migrations.AddPhishingAddressesTable_10_11
import jp.co.soramitsu.core_db.migrations.AddRuntimeCacheTable_11_12
import jp.co.soramitsu.core_db.migrations.AddStorageCacheTable_12_13
import jp.co.soramitsu.core_db.migrations.AddTokenTable_9_10
import jp.co.soramitsu.core_db.model.AccountLocal
import jp.co.soramitsu.core_db.model.AssetLocal
import jp.co.soramitsu.core_db.model.NodeLocal
import jp.co.soramitsu.core_db.model.PhishingAddressLocal
import jp.co.soramitsu.core_db.model.RuntimeCacheEntry
import jp.co.soramitsu.core_db.model.StorageEntryLocal
import jp.co.soramitsu.core_db.model.TokenLocal
import jp.co.soramitsu.core_db.model.TransactionLocal
import jp.co.soramitsu.core_db.prepopulate.nodes.DefaultNodes

@Database(
    version = 14,
    entities = [
        AccountLocal::class,
        NodeLocal::class,
        TransactionLocal::class,
        AssetLocal::class,
        TokenLocal::class,
        RuntimeCacheEntry::class,
        PhishingAddressLocal::class,
        StorageEntryLocal::class
    ])
@TypeConverters(
    LongMathConverters::class,
    TokenConverters::class,
    NetworkTypeConverters::class,
    TransactionConverters::class
)
abstract class AppDatabase : RoomDatabase() {

    companion object {

        private var instance: AppDatabase? = null

        @Synchronized
        fun get(context: Context, defaultNodes: DefaultNodes): AppDatabase {
            if (instance == null) {
                instance = Room.databaseBuilder(context.applicationContext,
                    AppDatabase::class.java, "app.db")
                    .fallbackToDestructiveMigration()
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            db.execSQL(defaultNodes.prepopulateQuery)
                        }
                    })
                    .addMigrations(AddTokenTable_9_10, AddPhishingAddressesTable_10_11, AddRuntimeCacheTable_11_12)
                    .addMigrations(AddStorageCacheTable_12_13, AddNetworkTypeToStorageCache_13_14)
                    .build()
            }
            return instance!!
        }
    }

    abstract fun nodeDao(): NodeDao

    abstract fun userDao(): AccountDao

    abstract fun assetDao(): AssetDao

    abstract fun transactionsDao(): TransactionDao

    abstract fun runtimeDao(): RuntimeDao

    abstract fun phishingAddressesDao(): PhishingAddressDao

    abstract fun storageDao(): StorageDao

    abstract fun tokenDao(): TokenDao
}