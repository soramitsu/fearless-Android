package jp.co.soramitsu.coredb.migrations

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.google.gson.JsonParser
import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals

/** Reads the same immutable inputs as the device tests without packaging them as app assets. */
internal object JvmMigrationDatabase {
    const val DATABASE_VERSION = 30

    private val moduleDirectory = sequenceOf(File("."), File("core-db"))
        .first { File(it, "schemas").isDirectory }

    fun install(context: Context, name: String, version: Int = DATABASE_VERSION) {
        val (release, digest) = when (version) {
            30 -> "2.0.0" to "c9b071ca0037caec64236de73ecb2bed69321743379f8020ebf8f824319b5cf2"
            71 -> "3.7.4" to "ca0c5efa69b56e46bba3cd218194e443133c83dd03ff044b972af65657d075a9"
            else -> error("No frozen binary for version $version")
        }
        val fixture = File(moduleDirectory, "src/androidTest/assets/migrations/$release/app-database-v$version.db")
        val bytes = fixture.readBytes()
        assertEquals(
            digest,
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        )
        context.deleteDatabase(name)
        context.getDatabasePath(name).apply { parentFile!!.mkdirs() }.writeBytes(bytes)
    }

    fun schemaFile(version: Int): File = sequenceOf(
        "jp.co.soramitsu.coredb.AppDatabase", "jp.co.soramitsu.core_db.AppDatabase"
    ).map { File(moduleDirectory, "schemas/$it/$version.json") }.first(File::isFile)

    fun createExported(context: Context, name: String, version: Int): SupportSQLiteDatabase {
        val schemaFile = schemaFile(version)
        val schema = JsonParser.parseString(schemaFile.readText()).asJsonObject["database"].asJsonObject
        assertEquals(version, schema["version"].asInt)
        context.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        schema["entities"].asJsonArray.forEach { element ->
                            val entity = element.asJsonObject
                            val table = entity["tableName"].asString
                            db.execSQL(entity["createSql"].asString.replace("\${TABLE_NAME}", table))
                            entity["indices"]?.asJsonArray?.forEach { index ->
                                db.execSQL(index.asJsonObject["createSql"].asString.replace("\${TABLE_NAME}", table))
                            }
                        }
                        schema["setupQueries"].asJsonArray.forEach { db.execSQL(it.asString) }
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                        error("Unexpected fixture upgrade $oldVersion -> $newVersion")
                    }
                }).build()
        )
        val db = helper.writableDatabase
        return object : SupportSQLiteDatabase by db {
            override fun close() = helper.close()
        }
    }
}
