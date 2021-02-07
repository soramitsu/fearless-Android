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
import jp.co.soramitsu.core_db.dao.TransactionDao
import jp.co.soramitsu.core_db.migration.AddPhishingAddressesTable
import jp.co.soramitsu.core_db.migration.AddTokenTable
import jp.co.soramitsu.core_db.model.AccountLocal
import jp.co.soramitsu.core_db.model.AssetLocal
import jp.co.soramitsu.core_db.model.NodeLocal
import jp.co.soramitsu.core_db.model.PhishingAddressLocal
import jp.co.soramitsu.core_db.model.TokenLocal
import jp.co.soramitsu.core_db.model.TransactionLocal
import jp.co.soramitsu.core_db.prepopulate.nodes.DefaultNodes

@Database(
    version = 11,
    entities = [
        AccountLocal::class,
        NodeLocal::class,
        TransactionLocal::class,
        AssetLocal::class,
        TokenLocal::class,
        PhishingAddressLocal::class
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
                            super.onCreate(db)
                            db.execSQL(defaultNodes.prepopulateQuery)
                        }
                    })
                    .addMigrations(AddTokenTable, AddPhishingAddressesTable)
                    .build()
            }
            return instance!!
        }
    }

    abstract fun nodeDao(): NodeDao

    abstract fun userDao(): AccountDao

    abstract fun assetDao(): AssetDao

    abstract fun transactionsDao(): TransactionDao

    abstract fun phishingAddressesDao(): PhishingAddressDao
}