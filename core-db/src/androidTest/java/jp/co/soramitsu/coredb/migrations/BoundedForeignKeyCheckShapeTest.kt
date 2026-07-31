package jp.co.soramitsu.coredb.migrations

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import jp.co.soramitsu.coredb.AppDatabase
import jp.co.soramitsu.coredb.migrations.fixtures.ReleasedV374DatabaseFixture
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@Suppress("LargeClass", "LongMethod", "MagicNumber")
class BoundedForeignKeyCheckShapeTest {

    private val instrumentation =
        InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val createdDatabases = mutableSetOf<String>()

    @get:Rule
    val migrationHelper = MigrationTestHelper(
        instrumentation,
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @get:Rule
    val legacyMigrationHelper = MigrationTestHelper(
        instrumentation,
        LEGACY_DATABASE_CANONICAL_NAME,
        FrameworkSQLiteOpenHelperFactory()
    )

    @After
    fun deleteTestDatabases() {
        createdDatabases.forEach(context::deleteDatabase)
        createdDatabases.clear()
    }

    @Test
    fun frozenReleasedVersion71ForeignKeyShapesPass() {
        val databaseName = "bounded-fk-released-v71"
        createdDatabases += databaseName
        ReleasedV374DatabaseFixture.install(context, databaseName)

        withExistingDatabase(
            databaseName = databaseName,
            version = ReleasedV374DatabaseFixture.DATABASE_VERSION
        ) { database ->
            requireExactCheck(
                database,
                TON_UPGRADE_FOREIGN_KEY_CHECK_LIMITS
            )
        }
    }

    @Test
    fun checkedInVersion76And77ForeignKeyShapesPass() {
        listOf(76, 77).forEach { version ->
            val databaseName = "bounded-fk-room-v$version"
            createdDatabases += databaseName
            migrationHelper.createDatabase(databaseName, version).use { database ->
                database.setForeignKeyConstraintsEnabled(true)
                requireExactCheck(
                    database,
                    WALLET_INTEGRITY_FOREIGN_KEY_CHECK_LIMITS
                )
            }
        }
    }

    @Test
    fun checkedInFreshVersion28ForeignKeyShapePassesItsReleasedCohort() {
        val databaseName = "bounded-fk-room-v28"
        createdDatabases += databaseName
        legacyMigrationHelper.createDatabase(databaseName, 28).use { database ->
            database.setForeignKeyConstraintsEnabled(true)
            requireExactCheck(database, DB28_V2_FOREIGN_KEY_CHECK_LIMITS)
        }
    }

    @Test
    fun version28UnexpectedForeignKeyChildStillFailsClosed() {
        val databaseName = "bounded-fk-room-v28-rogue"
        createdDatabases += databaseName
        legacyMigrationHelper.createDatabase(databaseName, 28).use { database ->
            database.setForeignKeyConstraintsEnabled(true)
            database.execSQL(
                """
                CREATE TABLE rogue_v28(
                    chainId TEXT NOT NULL,
                    FOREIGN KEY(chainId) REFERENCES chains(id)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )

            val failure = assertThrows(IllegalStateException::class.java) {
                requireExactCheck(database, DB28_V2_FOREIGN_KEY_CHECK_LIMITS)
            }
            assertTrue(
                failure.message.orEmpty().contains(
                    "Unexpected foreign-key child table rogue_v28"
                )
            )
        }
    }

    @Test
    fun missingExtraAndChangedForeignKeysFailExactShapeCheck() {
        val incompatibleChildren = listOf(
            "missing" to
                """
                CREATE TABLE child(
                    parentId TEXT NOT NULL
                )
                """.trimIndent(),
            "extra" to
                """
                CREATE TABLE child(
                    parentId TEXT NOT NULL,
                    secondParentId TEXT NOT NULL,
                    FOREIGN KEY(parentId) REFERENCES parent(id)
                        ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(secondParentId) REFERENCES parent(otherId)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            "changed-action" to
                """
                CREATE TABLE child(
                    parentId TEXT NOT NULL,
                    FOREIGN KEY(parentId) REFERENCES parent(id)
                        ON UPDATE NO ACTION ON DELETE NO ACTION
                )
                """.trimIndent(),
            "changed-target" to
                """
                CREATE TABLE child(
                    parentId TEXT NOT NULL,
                    FOREIGN KEY(parentId) REFERENCES parent(otherId)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
        )

        incompatibleChildren.forEach { (suffix, childSql) ->
            withCreatedDatabase(
                databaseName = "bounded-fk-shape-$suffix",
                statements = listOf(PARENT_TABLE_SQL, childSql)
            ) { database ->
                val failure = assertThrows(IllegalStateException::class.java) {
                    requireExactCheck(database, simpleLimits())
                }
                assertTrue(
                    failure.message.orEmpty().contains(
                        "missing, extra, or incompatible"
                    )
                )
            }
        }
    }

    @Test
    fun unexpectedForeignKeyChildFailsBeforeGlobalCheck() {
        withCreatedDatabase(
            databaseName = "bounded-fk-unexpected-child",
            statements = listOf(
                PARENT_TABLE_SQL,
                CANONICAL_CHILD_SQL,
                """
                CREATE TABLE rogue(
                    parentId TEXT NOT NULL,
                    FOREIGN KEY(parentId) REFERENCES parent(id)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
        ) { database ->
            val failure = assertThrows(IllegalStateException::class.java) {
                requireExactCheck(database, simpleLimits())
            }
            assertTrue(
                failure.message.orEmpty().contains(
                    "Unexpected foreign-key child table rogue"
                )
            )
        }
    }

    @Test
    fun splitConstraintsCannotImpersonateReleasedCompositeForeignKey() {
        withCreatedDatabase(
            databaseName = "bounded-fk-split-composite",
            statements = listOf(
                """
                CREATE TABLE pool(
                    base TEXT NOT NULL,
                    target TEXT NOT NULL,
                    PRIMARY KEY(base, target)
                )
                """.trimIndent(),
                """
                CREATE TABLE holding(
                    userBase TEXT NOT NULL,
                    userTarget TEXT NOT NULL,
                    FOREIGN KEY(userBase) REFERENCES pool(base)
                        ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(userTarget) REFERENCES pool(target)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
        ) { database ->
            val failure = assertThrows(IllegalStateException::class.java) {
                requireExactCheck(database, compositeLimits())
            }
            assertTrue(
                failure.message.orEmpty().contains(
                    "missing, extra, or incompatible"
                )
            )
        }
    }

    @Test
    fun changedParentPrimaryKeyFailsBeforeGlobalCheck() {
        withCreatedDatabase(
            databaseName = "bounded-fk-parent-key",
            statements = listOf(
                """
                CREATE TABLE parent(
                    otherId TEXT PRIMARY KEY NOT NULL,
                    id TEXT NOT NULL UNIQUE
                )
                """.trimIndent(),
                CANONICAL_CHILD_SQL
            )
        ) { database ->
            val failure = assertThrows(IllegalStateException::class.java) {
                requireExactCheck(database, simpleLimits())
            }
            assertTrue(
                failure.message.orEmpty().contains(
                    "incompatible foreign-key parent key"
                )
            )
        }
    }

    @Test
    fun productionSchemaAndForeignKeyCapsAreExact() {
        listOf(
            TON_UPGRADE_FOREIGN_KEY_CHECK_LIMITS,
            WALLET_INTEGRITY_FOREIGN_KEY_CHECK_LIMITS,
            DB28_V2_FOREIGN_KEY_CHECK_LIMITS,
            DB31_UPGRADE_FOREIGN_KEY_CHECK_LIMITS
        ).forEach { limits ->
            assertEquals(128, limits.maximumSchemaObjects)
            assertEquals(48, limits.maximumOrdinaryTables)
            assertEquals(
                2,
                limits.maximumForeignKeyDefinitionsPerTable
            )
        }
    }

    private fun simpleLimits(): MigrationForeignKeyCheckLimits {
        return MigrationForeignKeyCheckLimits(
            maximumSchemaObjects = 16,
            maximumOrdinaryTables = 8,
            maximumForeignKeyDefinitionsPerTable = 2,
            maximumRowsByTable = mapOf("child" to 8),
            expectedForeignKeysByTable = mapOf(
                "child" to setOf(
                    MigrationForeignKeyDefinition(
                        parentTable = "parent",
                        columns = listOf(
                            MigrationForeignKeyColumnMapping(
                                from = "parentId",
                                to = "id"
                            )
                        ),
                        onDelete = FOREIGN_KEY_CASCADE
                    )
                )
            ),
            expectedParentPrimaryKeysByTable = mapOf(
                "parent" to listOf("id")
            )
        )
    }

    private fun compositeLimits(): MigrationForeignKeyCheckLimits {
        return MigrationForeignKeyCheckLimits(
            maximumSchemaObjects = 16,
            maximumOrdinaryTables = 8,
            maximumForeignKeyDefinitionsPerTable = 2,
            maximumRowsByTable = mapOf("holding" to 8),
            expectedForeignKeysByTable = mapOf(
                "holding" to setOf(
                    MigrationForeignKeyDefinition(
                        parentTable = "pool",
                        columns = listOf(
                            MigrationForeignKeyColumnMapping(
                                from = "userBase",
                                to = "base"
                            ),
                            MigrationForeignKeyColumnMapping(
                                from = "userTarget",
                                to = "target"
                            )
                        ),
                        onDelete = FOREIGN_KEY_CASCADE
                    )
                )
            ),
            expectedParentPrimaryKeysByTable = mapOf(
                "pool" to listOf("base", "target")
            )
        )
    }

    private fun requireExactCheck(
        database: SupportSQLiteDatabase,
        limits: MigrationForeignKeyCheckLimits
    ) {
        requireBoundedForeignKeyCheck(
            database = database,
            limits = limits
        ) { message ->
            IllegalStateException(message)
        }
    }

    private fun <T> withExistingDatabase(
        databaseName: String,
        version: Int,
        action: (SupportSQLiteDatabase) -> T
    ): T {
        val callback = object : SupportSQLiteOpenHelper.Callback(version) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                error("Expected an installed database")
            }

            override fun onUpgrade(
                db: SupportSQLiteDatabase,
                oldVersion: Int,
                newVersion: Int
            ) {
                error("Unexpected database upgrade")
            }
        }
        return withOpenHelper(databaseName, callback, action)
    }

    private fun <T> withCreatedDatabase(
        databaseName: String,
        statements: List<String>,
        action: (SupportSQLiteDatabase) -> T
    ): T {
        createdDatabases += databaseName
        context.deleteDatabase(databaseName)
        val callback = object : SupportSQLiteOpenHelper.Callback(1) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                statements.forEach { statement ->
                    db.execSQL(statement)
                }
            }

            override fun onUpgrade(
                db: SupportSQLiteDatabase,
                oldVersion: Int,
                newVersion: Int
            ) {
                error("Unexpected database upgrade")
            }
        }
        return withOpenHelper(databaseName, callback, action)
    }

    private fun <T> withOpenHelper(
        databaseName: String,
        callback: SupportSQLiteOpenHelper.Callback,
        action: (SupportSQLiteDatabase) -> T
    ): T {
        val openHelper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(callback)
                .build()
        )
        return try {
            val database = openHelper.writableDatabase
            database.setForeignKeyConstraintsEnabled(true)
            action(database)
        } finally {
            openHelper.close()
        }
    }

    private companion object {
        const val LEGACY_DATABASE_CANONICAL_NAME =
            "jp.co.soramitsu.core_db.AppDatabase"

        val PARENT_TABLE_SQL =
            """
            CREATE TABLE parent(
                id TEXT PRIMARY KEY NOT NULL,
                otherId TEXT NOT NULL UNIQUE
            )
            """.trimIndent()

        val CANONICAL_CHILD_SQL =
            """
            CREATE TABLE child(
                parentId TEXT NOT NULL,
                FOREIGN KEY(parentId) REFERENCES parent(id)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
    }
}
