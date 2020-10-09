package jp.co.soramitsu.core_db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import jp.co.soramitsu.core_db.converters.LongMathConverters
import jp.co.soramitsu.core_db.converters.TokenConverters
import jp.co.soramitsu.core_db.dao.AccountDao
import jp.co.soramitsu.core_db.dao.AssetDao
import jp.co.soramitsu.core_db.dao.NodeDao
import jp.co.soramitsu.core_db.dao.TransactionDao
import jp.co.soramitsu.core_db.model.AccountLocal
import jp.co.soramitsu.core_db.model.AssetLocal
import jp.co.soramitsu.core_db.model.NodeLocal
import jp.co.soramitsu.core_db.model.TransactionLocal

@Database(
    version = 7,
    entities = [
        AccountLocal::class,
        NodeLocal::class,
        TransactionLocal::class,
        AssetLocal::class
    ])
@TypeConverters(LongMathConverters::class, TokenConverters::class)
abstract class AppDatabase : RoomDatabase() {

    companion object {

        private const val defaultNodes = "insert into nodes (id, name, link, networkType, isDefault) values " +
            "(1, 'Kusama Parity Node', 'wss://kusama-rpc.polkadot.io', 0, 1)," +
            "(2, 'Kusama, Web3 Foundation node', 'wss://cc3-5.kusama.network', 0, 1)," +
            "(3, 'Polkadot Parity Node', 'wss://rpc.polkadot.io', 1, 1)," +
            "(4, 'Polkadot, Web3 Foundation node', 'wss://cc1-1.polkadot.network', 1, 1)," +
            "(5, 'Westend Parity Node', 'wss://westend-rpc.polkadot.io', 2, 1)"

        private var instance: AppDatabase? = null

        @Synchronized
        fun get(context: Context): AppDatabase {
            if (instance == null) {
                instance = Room.databaseBuilder(context.applicationContext,
                    AppDatabase::class.java, "app.db")
                    .fallbackToDestructiveMigration()
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            db.execSQL(defaultNodes)
                        }
                    })
                    .build()
            }
            return instance!!
        }
    }

    abstract fun nodeDao(): NodeDao

    abstract fun userDao(): AccountDao

    abstract fun assetDao(): AssetDao

    abstract fun transactionsDao(): TransactionDao
}