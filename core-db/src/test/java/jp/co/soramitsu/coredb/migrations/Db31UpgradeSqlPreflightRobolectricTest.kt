package jp.co.soramitsu.coredb.migrations

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class Db31UpgradeSqlPreflightRobolectricTest {

    private val context: Context =
        ApplicationProvider.getApplicationContext()

    @After
    fun deleteTestDatabase() {
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun frozenReleasedVersion30To31SchemaPassesExactContract() {
        JvmMigrationDatabase.install(context, DATABASE_NAME)
        val callback = object : SupportSQLiteOpenHelper.Callback(31) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                error("The frozen released database fixture was not installed")
            }

            override fun onUpgrade(
                db: SupportSQLiteDatabase,
                oldVersion: Int,
                newVersion: Int
            ) {
                assertEquals(30, oldVersion)
                assertEquals(31, newVersion)
                MigrateTablesToV2_30_31.migrate(db)
            }
        }
        val openHelper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(DATABASE_NAME)
                .callback(callback)
                .build()
        )

        try {
            val database = openHelper.writableDatabase
            assertEquals(31, database.version)
            Db31UpgradeSqlPreflight.requireSafeBeforePreferenceAccess(
                database
            )
        } finally {
            openHelper.close()
        }
    }

    private companion object {
        const val DATABASE_NAME = "db31-upgrade-exact-contract"
    }
}
