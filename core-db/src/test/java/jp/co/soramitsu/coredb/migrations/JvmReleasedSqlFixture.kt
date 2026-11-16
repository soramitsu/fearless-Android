package jp.co.soramitsu.coredb.migrations

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import org.junit.Assert.assertEquals

/** Replays the frozen version-71 SQL fixture and checks rollback byte for byte. */
internal object JvmReleasedSqlFixture {
    fun execute(
        name: String,
        mutate: (SupportSQLiteDatabase) -> Unit,
        rollback: Boolean,
        transaction: Boolean,
        check: (SupportSQLiteDatabase) -> Unit
    ) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(context.cacheDir, "ton-cache-repair-${UUID.randomUUID()}")
        kotlin.check(directory.mkdir())
        File(directory, "case.txt").writeText(name + "\n")
        val file = File(directory, "app.db")
        JvmMigrationDatabase.install(context, file.absolutePath, 71)
        open(file).use { helper ->
            val db = helper.writableDatabase
            assertEquals(file.canonicalFile, File(checkNotNull(db.path)).canonicalFile)
            db.execSQL("INSERT INTO meta_accounts(id, substratePublicKey, substrateCryptoType, substrateAccountId, name, isSelected, position, isBackedUp, initialized) VALUES(42, X'd75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a', 'ED25519', X'd75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a', 'Public historical fixture 雪', 1, 0, 1, 1)")
            mutate(db)
        }
        val before = digest(file)
        try {
            open(file).use { helper ->
                val db = helper.writableDatabase
                if (transaction) db.beginTransaction()
                try {
                    check(db)
                    if (transaction && !rollback) db.setTransactionSuccessful()
                } finally {
                    if (transaction) db.endTransaction()
                }
            }
        } finally {
            File(directory, "database.sha256").writeText(before + "\n" + digest(file) + "\n")
        }
        if (rollback || !transaction) assertEquals("Rollback must retain every database byte", before, digest(file))
    }

    private fun open(file: File): SupportSQLiteOpenHelper {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(file.absolutePath)
                .callback(object : SupportSQLiteOpenHelper.Callback(71) {
                    override fun onConfigure(db: SupportSQLiteDatabase) {
                        db.setForeignKeyConstraintsEnabled(false)
                    }
                    override fun onCreate(db: SupportSQLiteDatabase) { error("Shipped fixture is missing") }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                        error("Unexpected fixture version change")
                    }
                })
                .build()
        )
    }

    private fun digest(file: File): String = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
        .joinToString("") { "%02x".format(it.toInt() and 255) }
}
