package jp.co.soramitsu.coredb.migrations

import androidx.test.platform.app.InstrumentationRegistry
import jp.co.soramitsu.coredb.APP_DATABASE_VERSION
import jp.co.soramitsu.coredb.migrations.fixtures.ReleasedSchemaFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Keeps [AllReleasedSchemasUpgradeTest] honest: it must see one exact schema
 * for every Room version a distributed build left on a device
 * (`tools/upgrade-fixtures/android/releases.json`), and both physical
 * variants of DB 27.
 */
class ReleasedSchemaFixtureCoverageTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun everyShippedDatabaseVersionHasAnExactReleasedSchema() {
        val fixtures = ReleasedSchemaFixtures.all(context)

        assertEquals(
            SHIPPED_DATABASE_VERSIONS,
            fixtures.map { it.databaseVersion }.distinct().sorted()
        )
        assertTrue(fixtures.all { it.databaseVersion < APP_DATABASE_VERSION })
        assertTrue(SOURCE_ONLY_DATABASE_VERSION !in fixtures.map { it.databaseVersion })
    }

    @Test
    fun bothShippedPhysicalLayoutsOfDatabase27AreCovered() {
        val version27 = ReleasedSchemaFixtures.all(context)
            .filter { it.databaseVersion == 27 }
            .associateBy { it.directory }

        assertEquals(setOf("1.9.13", "1.9.15"), version27.keys)
        // Kotlin 1.4.31 (1.9.12-1.9.13) put the autoincrement id first; Kotlin 1.6.10
        // (1.9.14-1.9.15) put it last. Room's identity hash is the same for both.
        val nodes = version27.mapValues { (_, fixture) ->
            fixture.statements.single { it.startsWith("CREATE TABLE IF NOT EXISTS `nodes`") }
        }
        assertTrue(nodes.getValue("1.9.13").contains("(`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name`"))
        assertTrue(nodes.getValue("1.9.15").endsWith("`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)"))
        assertEquals(
            version27.getValue("1.9.13").roomIdentityHash,
            version27.getValue("1.9.15").roomIdentityHash
        )
    }

    @Test
    fun everyFixtureWithoutIdentityHashIsSourceDerived() {
        val withoutIdentity = ReleasedSchemaFixtures.all(context)
            .filter { it.roomIdentityHash == null }
            .map { it.directory }

        // 2.2.3 (DB 54) shipped only on Play; its schema is the 2.2.4 APK DDL without
        // the address_book index Migration_54_55 adds, and Room's hash for it is unknown.
        assertEquals(listOf("2.2.3"), withoutIdentity)
    }

    private companion object {
        /** Every Room version a distributed build left on a device, except DB 9. */
        val SHIPPED_DATABASE_VERSIONS = listOf(
            10, 20, 22, 25, 26, 27, 30, 33, 37, 40, 41, 45, 46, 49, 54, 55, 56, 57, 58, 59,
            60, 61, 62, 63, 64, 65, 66, 67, 69, 70, 71, 73, 74, 75
        )

        /** 0.4.0-1.0.1 published no APK; ReleasedV04DatabaseFixture covers DB 9. */
        const val SOURCE_ONLY_DATABASE_VERSION = 9
    }
}
