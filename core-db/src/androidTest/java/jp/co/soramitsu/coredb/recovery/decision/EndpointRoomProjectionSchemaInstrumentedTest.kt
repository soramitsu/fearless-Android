package jp.co.soramitsu.coredb.recovery.decision

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID

/** Fresh core-test-only files; never opens a wallet database or replaces retained evidence. */
class EndpointRoomProjectionSchemaInstrumentedTest : EndpointRoomProjectionSchemaContract() {
    override fun openDatabase(): Database {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "jp.co.soramitsu.core_db.test")
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
            override fun close() { helper.close() }
        }
    }
}
