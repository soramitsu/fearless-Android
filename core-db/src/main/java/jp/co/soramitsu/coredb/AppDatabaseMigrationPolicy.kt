package jp.co.soramitsu.coredb

import androidx.room.migration.Migration

internal const val APP_DATABASE_VERSION = 77
internal const val EARLIEST_SUPPORTED_DATABASE_VERSION = 9

/**
 * Fails before Room opens the user database if a release accidentally omits,
 * duplicates, or skips an upgrade step.
 *
 * Fearless intentionally uses adjacent migrations. Requiring every edge makes
 * the preservation guarantee auditable and prevents a future missing migration
 * from being masked by a destructive fallback.
 */
internal fun requireCompleteAppDatabaseUpgradePath(migrations: Collection<Migration>) {
    val invalidEdges = migrations
        .filter { it.endVersion != it.startVersion + 1 }
        .map { "${it.startVersion}->${it.endVersion}" }

    require(invalidEdges.isEmpty()) {
        "App database migrations must be adjacent upgrades; invalid edges: ${invalidEdges.joinToString()}"
    }

    val migrationsByStart = migrations.groupBy(Migration::startVersion)
    val duplicateStarts = migrationsByStart
        .filterValues { it.size != 1 }
        .keys
        .sorted()

    require(duplicateStarts.isEmpty()) {
        "App database migration graph has duplicate starts: ${duplicateStarts.joinToString()}"
    }

    val missingStarts = (EARLIEST_SUPPORTED_DATABASE_VERSION until APP_DATABASE_VERSION)
        .filterNot(migrationsByStart::containsKey)

    require(missingStarts.isEmpty()) {
        "App database migration graph is incomplete after versions: ${missingStarts.joinToString()}"
    }

    val outOfRangeEdges = migrations
        .filter {
            it.startVersion < EARLIEST_SUPPORTED_DATABASE_VERSION ||
                it.endVersion > APP_DATABASE_VERSION
        }
        .map { "${it.startVersion}->${it.endVersion}" }

    require(outOfRangeEdges.isEmpty()) {
        "App database migration graph has unsupported edges: ${outOfRangeEdges.joinToString()}"
    }
}
