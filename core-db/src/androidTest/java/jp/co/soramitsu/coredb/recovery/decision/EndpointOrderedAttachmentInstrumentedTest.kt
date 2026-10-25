package jp.co.soramitsu.coredb.recovery.decision

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.runner.RunWith
import java.util.UUID

/** Runs the same SQL contract inside the isolated core-db test package; never opens the wallet database. */
@RunWith(AndroidJUnit4::class)
class EndpointOrderedAttachmentInstrumentedTest : EndpointOrderedAttachmentContract() {
    override fun openDatabase(): EndpointSelectionStoreContract.Database {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "jp.co.soramitsu.core_db.test")
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
            override fun close() { helper.close() }
        }
    }
}
