package jp.co.soramitsu.coredb.recovery.decision

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import java.util.UUID
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Exercises the shared projection schema contract on disposable JVM SQLite files. */
@RunWith(RobolectricTestRunner::class)
class EndpointRoomProjectionSchemaRobolectricTest : EndpointRoomProjectionSchemaContract() {
    override fun openDatabase(): Database {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
            .name("endpoint-room-projection-${UUID.randomUUID()}")
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) = Unit
                override fun onUpgrade(
                    db: SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int
                ) = error("Unexpected upgrade")
            }).build()
        )
        return object : Database {
            override val db = helper.writableDatabase
            override fun close() { helper.close(); context.deleteDatabase(checkNotNull(helper.databaseName)) }
        }
    }
}
