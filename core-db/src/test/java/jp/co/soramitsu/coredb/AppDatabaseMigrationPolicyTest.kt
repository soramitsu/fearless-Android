package jp.co.soramitsu.coredb

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import jp.co.soramitsu.coredb.migrations.LegacyAutoHideNormalizationMigration
import jp.co.soramitsu.coredb.migrations.FiatCurrencyIdentityMigration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.kotlin.mock

class AppDatabaseMigrationPolicyTest {

    @Test
    fun `complete adjacent release migration graph is accepted`() {
        requireCompleteAppDatabaseUpgradePath(completeGraph())
    }

    @Test
    fun `production migration graph is complete and normalizes legacy auto hides at 80 to 81`() {
        val migrations = AppDatabase.migrations(
            storeV1 = mock(),
            storeV2 = mock(),
            encryptedPreferences = mock(),
            substrateSecretStore = mock(),
            ethereumSecretStore = mock()
        ).asList()

        requireCompleteAppDatabaseUpgradePath(migrations)
        assertSame(LegacyAutoHideNormalizationMigration, migrations.single { it.startVersion == 80 })
        assertSame(FiatCurrencyIdentityMigration, migrations.single { it.startVersion == 83 })
        assertEquals(APP_DATABASE_VERSION, migrations.maxOf(Migration::endVersion))
    }

    @Test
    fun `missing current schema migration fails closed`() {
        val graph = completeGraph()
            .filterNot { it.startVersion == APP_DATABASE_VERSION - 1 }

        assertThrows(IllegalArgumentException::class.java) {
            requireCompleteAppDatabaseUpgradePath(graph)
        }
    }

    @Test
    fun `missing historical migration fails closed`() {
        val graph = completeGraph()
            .filterNot { it.startVersion == 31 }

        assertThrows(IllegalArgumentException::class.java) {
            requireCompleteAppDatabaseUpgradePath(graph)
        }
    }

    @Test
    fun `duplicate migration start fails closed`() {
        val graph = completeGraph() + migration(31, 32)

        assertThrows(IllegalArgumentException::class.java) {
            requireCompleteAppDatabaseUpgradePath(graph)
        }
    }

    @Test
    fun `skipping migration edge fails closed`() {
        val graph = completeGraph()
            .filterNot { it.startVersion == 31 } +
            migration(31, 33)

        assertThrows(IllegalArgumentException::class.java) {
            requireCompleteAppDatabaseUpgradePath(graph)
        }
    }

    @Test
    fun `migration below supported floor fails closed`() {
        val graph = completeGraph() +
            migration(
                EARLIEST_SUPPORTED_DATABASE_VERSION - 1,
                EARLIEST_SUPPORTED_DATABASE_VERSION
            )

        assertThrows(IllegalArgumentException::class.java) {
            requireCompleteAppDatabaseUpgradePath(graph)
        }
    }

    @Test
    fun `migration above current schema fails closed`() {
        val graph = completeGraph() +
            migration(
                APP_DATABASE_VERSION,
                APP_DATABASE_VERSION + 1
            )

        assertThrows(IllegalArgumentException::class.java) {
            requireCompleteAppDatabaseUpgradePath(graph)
        }
    }

    private fun completeGraph(): List<Migration> {
        return (
            EARLIEST_SUPPORTED_DATABASE_VERSION until
                APP_DATABASE_VERSION
            ).map { migration(it, it + 1) }
    }

    private fun migration(start: Int, end: Int): Migration {
        return object : Migration(start, end) {
            override fun migrate(database: SupportSQLiteDatabase) = Unit
        }
    }
}
