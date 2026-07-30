package jp.co.soramitsu.coredb

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.assertThrows
import org.junit.Test

class AppDatabaseMigrationPolicyTest {

    @Test
    fun `complete adjacent release migration graph is accepted`() {
        requireCompleteAppDatabaseUpgradePath(completeGraph())
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
