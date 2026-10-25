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

    @Test
    fun healthyForeignKeyRowsPassBoundedGlobalCheck() {
        withCreatedDatabase(
            databaseName = "bounded-fk-healthy-rows",
            statements = listOf(PARENT_TABLE_SQL, CANONICAL_CHILD_SQL)
        ) { database ->
            database.execSQL("INSERT INTO parent(id, otherId) VALUES('current', 'other')")
            database.execSQL("INSERT INTO child(parentId) VALUES('current')")
            database.query("PRAGMA foreign_key_check").use { cursor ->
                assertTrue("The healthy graph has no violation", !cursor.moveToFirst())
            }

            requireExactCheck(database, simpleLimits())
        }
    }

    @Test
    fun untoleratedForeignKeyViolationReportsIntendedFailure() {
        withCreatedDatabase(
            databaseName = "bounded-fk-untolerated-row",
            statements = listOf(PARENT_TABLE_SQL, CANONICAL_CHILD_SQL)
        ) { database ->
            database.setForeignKeyConstraintsEnabled(false)
            database.execSQL("INSERT INTO child(parentId) VALUES('missing')")
            database.setForeignKeyConstraintsEnabled(true)
            database.query("PRAGMA foreign_key_check").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("child", cursor.getString(cursor.getColumnIndexOrThrow("table")))
                assertEquals("parent", cursor.getString(cursor.getColumnIndexOrThrow("parent")))
                assertTrue("Exactly one violation was seeded", !cursor.moveToNext())
            }

            val failure = assertThrows(IllegalStateException::class.java) {
                requireExactCheck(database, simpleLimits())
            }
            assertEquals("The database contains a foreign-key violation", failure.message)
        }
    }

    @Test
    fun exactToleratedForeignKeyPairPassesBoundedGlobalCheck() {
        withCreatedDatabase(
            databaseName = "bounded-fk-exact-tolerated-pair",
            statements = listOf(PARENT_TABLE_SQL, CANONICAL_CHILD_SQL)
        ) { database ->
            database.setForeignKeyConstraintsEnabled(false)
            database.execSQL("INSERT INTO child(parentId) VALUES('missing')")
            database.setForeignKeyConstraintsEnabled(true)

            listOf(
                MigrationForeignKeyViolationScope("other_child", "parent"),
                MigrationForeignKeyViolationScope("child", "other_parent")
            ).forEach { wrongPair ->
                val failure = assertThrows(IllegalStateException::class.java) {
                    requireBoundedForeignKeyCheck(
                        database = database,
                        limits = simpleLimits(),
                        toleratedViolations = setOf(wrongPair)
                    ) { message -> IllegalStateException(message) }
                }
                assertEquals("The database contains a foreign-key violation", failure.message)
            }

            requireBoundedForeignKeyCheck(
                database = database,
                limits = simpleLimits(),
                toleratedViolations = setOf(MigrationForeignKeyViolationScope("child", "parent"))
            ) { message -> IllegalStateException(message) }
        }
    }

    @Test
    fun laterUntoleratedForeignKeyPairStillFailsAfterToleratedPair() {
        withCreatedDatabase(
            databaseName = "bounded-fk-later-untolerated-pair",
            statements = listOf(
                PARENT_TABLE_SQL,
                CANONICAL_CHILD_SQL,
                """
                CREATE TABLE second_child(
                    parentId TEXT NOT NULL,
                    FOREIGN KEY(parentId) REFERENCES parent(id)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
        ) { database ->
            database.setForeignKeyConstraintsEnabled(false)
            database.execSQL("INSERT INTO child(parentId) VALUES('missing')")
            database.execSQL("INSERT INTO second_child(parentId) VALUES('missing')")
            database.setForeignKeyConstraintsEnabled(true)

            val actualOrder = database.query("PRAGMA foreign_key_check").use { cursor ->
                val childIndex = cursor.getColumnIndexOrThrow("table")
                val parentIndex = cursor.getColumnIndexOrThrow("parent")
                buildList {
                    while (cursor.moveToNext()) {
                        add(
                            MigrationForeignKeyViolationScope(
                                cursor.getString(childIndex),
                                cursor.getString(parentIndex)
                            )
                        )
                    }
                }
            }
            assertEquals(2, actualOrder.size)
            assertEquals(
                setOf(
                    MigrationForeignKeyViolationScope("child", "parent"),
                    MigrationForeignKeyViolationScope("second_child", "parent")
                ),
                actualOrder.toSet()
            )
            val baseLimits = simpleLimits()
            val twoChildLimits = baseLimits.copy(
                maximumRowsByTable = baseLimits.maximumRowsByTable + ("second_child" to 8),
                expectedForeignKeysByTable = baseLimits.expectedForeignKeysByTable +
                    ("second_child" to baseLimits.expectedForeignKeysByTable.getValue("child"))
            )

            val failure = assertThrows(IllegalStateException::class.java) {
                requireBoundedForeignKeyCheck(
                    database = database,
                    limits = twoChildLimits,
                    toleratedViolations = setOf(actualOrder.first())
                ) { message -> IllegalStateException(message) }
            }
            assertEquals("The database contains a foreign-key violation", failure.message)
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
