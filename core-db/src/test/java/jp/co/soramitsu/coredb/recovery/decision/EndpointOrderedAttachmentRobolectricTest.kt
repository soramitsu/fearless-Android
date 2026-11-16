package jp.co.soramitsu.coredb.recovery.decision

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import java.util.UUID
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Runs the shared SQL contract with Robolectric on an isolated test database. */
@RunWith(RobolectricTestRunner::class)
class EndpointOrderedAttachmentRobolectricTest : EndpointOrderedAttachmentContract() {
    override fun openDatabase(): EndpointSelectionStoreContract.Database {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "endpoint-ordered-attachment-contract-${UUID.randomUUID()}.db"
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
        return object : EndpointSelectionStoreContract.Database {
            override val db: SupportSQLiteDatabase get() = helper.writableDatabase
            override fun reopen() {
                helper.close()
            check(helper.writableDatabase.isOpen)
            }
            override fun close() { helper.close(); context.deleteDatabase(checkNotNull(helper.databaseName)) }
        }
    }
}
