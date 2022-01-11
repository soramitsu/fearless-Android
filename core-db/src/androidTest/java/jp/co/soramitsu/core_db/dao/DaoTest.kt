package jp.co.soramitsu.core_db.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import jp.co.soramitsu.core_db.AppDatabase
import org.junit.After
import org.junit.Before
import java.io.IOException

abstract class DaoTest<D : Any>(private val daoFetcher: (AppDatabase) -> D) {
    protected lateinit var dao: D
    protected lateinit var db: AppDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .build()

        dao =  daoFetcher(db)
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }
}
