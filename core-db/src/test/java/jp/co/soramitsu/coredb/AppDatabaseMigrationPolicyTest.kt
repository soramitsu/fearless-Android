package jp.co.soramitsu.coredb

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import jp.co.soramitsu.common.data.secrets.v1.SecretStoreV1Impl
import jp.co.soramitsu.common.data.secrets.v2.SecretStoreV2
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecretStore
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecretStore
import jp.co.soramitsu.testshared.HashMapEncryptedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AppDatabaseMigrationPolicyTest {

    @Test
    fun productionRegistryHasOneAdjacentMigrationForEverySupportedVersion() {
        val preferences = HashMapEncryptedPreferences()
        val migrations = AppDatabase.migrations(
            storeV1 = SecretStoreV1Impl(preferences),
            storeV2 = SecretStoreV2(preferences),
            encryptedPreferences = preferences,
            substrateSecretStore = SubstrateSecretStore(preferences),
            ethereumSecretStore = EthereumSecretStore(preferences)
        )

        requireCompleteAppDatabaseUpgradePath(migrations.asList())

        assertEquals(
            (EARLIEST_SUPPORTED_DATABASE_VERSION until APP_DATABASE_VERSION).toList(),
            migrations.map(Migration::startVersion)
        )
        assertEquals(APP_DATABASE_VERSION, migrations.last().endVersion)
    }

    @Test
    fun rejectsMissingMigrationInsteadOfAllowingADataWipingFallback() {
        val incomplete = completeTestGraph().filterNot { it.startVersion == 42 }

        assertThrows(IllegalArgumentException::class.java) {
            requireCompleteAppDatabaseUpgradePath(incomplete)
        }
    }

    @Test
    fun rejectsDuplicateMigrationStart() {
        val duplicate = completeTestGraph() + migration(42, 43)

        assertThrows(IllegalArgumentException::class.java) {
            requireCompleteAppDatabaseUpgradePath(duplicate)
        }
    }

    @Test
    fun rejectsSkippedVersionMigration() {
        val skipped = completeTestGraph()
            .filterNot { it.startVersion == 42 || it.startVersion == 43 }
            .plus(migration(42, 44))

        assertThrows(IllegalArgumentException::class.java) {
            requireCompleteAppDatabaseUpgradePath(skipped)
        }
    }

    @Test
    fun rejectsDowngradeMigration() {
        val downgrade = completeTestGraph() + migration(APP_DATABASE_VERSION, APP_DATABASE_VERSION - 1)

        assertThrows(IllegalArgumentException::class.java) {
            requireCompleteAppDatabaseUpgradePath(downgrade)
        }
    }

    @Test
    fun rejectsMigrationOutsideSupportedRange() {
        val outOfRange = completeTestGraph() +
            migration(EARLIEST_SUPPORTED_DATABASE_VERSION - 1, EARLIEST_SUPPORTED_DATABASE_VERSION)

        assertThrows(IllegalArgumentException::class.java) {
            requireCompleteAppDatabaseUpgradePath(outOfRange)
        }
    }

    private fun completeTestGraph(): List<Migration> =
        (EARLIEST_SUPPORTED_DATABASE_VERSION until APP_DATABASE_VERSION).map { start ->
            migration(start, start + 1)
        }

    private fun migration(start: Int, end: Int) = object : Migration(start, end) {
        override fun migrate(db: SupportSQLiteDatabase) = Unit
    }
}
