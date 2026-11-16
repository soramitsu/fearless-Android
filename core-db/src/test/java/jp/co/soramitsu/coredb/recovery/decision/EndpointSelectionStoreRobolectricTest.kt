package jp.co.soramitsu.coredb.recovery.decision

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith
import java.util.UUID

/** Runs the shared SQL contract with Robolectric on an isolated test database. */
@RunWith(RobolectricTestRunner::class)
class EndpointSelectionStoreRobolectricTest : EndpointSelectionStoreContract() {
    override fun openDatabase(): Database {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "endpoint-selection-contract-${UUID.randomUUID()}.db"
        check(!context.getDatabasePath(name).exists())
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name).callback(object : SupportSQLiteOpenHelper.Callback(83) {
                override fun onConfigure(db: SupportSQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }
                override fun onCreate(db: SupportSQLiteDatabase) = Unit
                override fun onUpgrade(
                    db: SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int
                ) = error("Unexpected test schema upgrade")
            }).build()
        )
        return object : Database {
            override val db: SupportSQLiteDatabase get() = helper.writableDatabase
            override fun reopen() {
                helper.close()
            check(helper.writableDatabase.isOpen)
            }
            override fun close() { helper.close(); context.deleteDatabase(checkNotNull(helper.databaseName)) }
        }
    }
}
