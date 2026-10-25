package jp.co.soramitsu.coredb

import java.io.File
import java.util.Properties
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Keeps the schema version at or above every database a distributed build has
 * already left on a device.
 *
 * Room refuses to open a newer database without a downgrade path, so a build
 * below the floor cannot start for those users. Older lines (master, develop,
 * 4.2.0 and earlier) still fall back to destructive migration and wipe every
 * table instead, which is why a rollback is always a forward fix.
 */
class AppDatabaseVersionFloorTest {

    @Test
    fun `schema version is at least every database already handed to users`() {
        val build = currentBuild()
        val distributed = ledger().filter { it.versionCode <= build.versionCode }
        val floor = checkNotNull(distributed.maxByOrNull(ReleasedDatabase::databaseVersion)) {
            "$LEDGER_PATH records no build up to ${build.label}"
        }

        assertTrue(
            "APP_DATABASE_VERSION $APP_DATABASE_VERSION is below database " +
                "${floor.databaseVersion} recorded for ${floor.label}; a build " +
                "below it cannot open the data that build leaves on devices",
            APP_DATABASE_VERSION >= floor.databaseVersion
        )
    }

    @Test
    fun `build being prepared records its database version`() {
        val build = currentBuild()
        val entry = checkNotNull(ledger().singleOrNull { it.versionCode == build.versionCode }) {
            "Add ${build.label}=$APP_DATABASE_VERSION to $LEDGER_PATH " +
                "before distributing versionCode ${build.versionCode}"
        }

        assertEquals(
            "$LEDGER_PATH names versionCode ${build.versionCode} differently " +
                "from versioning/version.properties",
            build.versionName,
            entry.versionName
        )
        // An under-recorded line lets a later hotfix sit below this build's database.
        assertEquals(
            "$LEDGER_PATH records database ${entry.databaseVersion} for ${build.label}, " +
                "but APP_DATABASE_VERSION is $APP_DATABASE_VERSION; update that line to " +
                "${build.label}=$APP_DATABASE_VERSION",
            APP_DATABASE_VERSION,
            entry.databaseVersion
        )
    }

    @Test
    fun `later builds never record a lower database version`() {
        val entries = ledger()
        val duplicateCodes = entries.groupBy(ReleasedDatabase::versionCode)
            .filterValues { it.size > 1 }
            .keys

        assertTrue(
            "$LEDGER_PATH repeats versionCodes $duplicateCodes",
            duplicateCodes.isEmpty()
        )

        entries.sortedBy(ReleasedDatabase::versionCode)
            .zipWithNext()
            .forEach { (earlier, later) ->
                assertTrue(
                    "${later.label} records database ${later.databaseVersion}, " +
                        "below ${earlier.databaseVersion} from ${earlier.label}",
                    later.databaseVersion >= earlier.databaseVersion
                )
            }
    }

    @Test
    fun `oldest database handed to users still has an upgrade path`() {
        val oldest = ledger().minBy(ReleasedDatabase::databaseVersion)

        // The policy requires adjacent migrations only from this version on, so
        // raising it would let older migrations go and fail those users closed.
        assertTrue(
            "EARLIEST_SUPPORTED_DATABASE_VERSION $EARLIEST_SUPPORTED_DATABASE_VERSION " +
                "is above database ${oldest.databaseVersion} recorded for " +
                "${oldest.label}; keep every shipped migration instead",
            EARLIEST_SUPPORTED_DATABASE_VERSION <= oldest.databaseVersion
        )
    }

    @Test
    fun `no exported schema is newer than the current schema version`() {
        val exported = File(repositoryRoot(), SCHEMA_DIRECTORY)
            .listFiles { file -> file.extension == "json" }
            .orEmpty()
            .mapNotNull { it.nameWithoutExtension.toIntOrNull() }
            .sorted()

        assertTrue(
            "$SCHEMA_DIRECTORY has no export for version $APP_DATABASE_VERSION",
            APP_DATABASE_VERSION in exported
        )
        assertEquals(
            "$SCHEMA_DIRECTORY holds a schema newer than APP_DATABASE_VERSION; " +
                "lowering the version would strand databases at that schema",
            APP_DATABASE_VERSION,
            exported.last()
        )
    }

    private fun ledger(): List<ReleasedDatabase> {
        val lines = File(repositoryRoot(), LEDGER_PATH).readLines()

        return lines.mapIndexedNotNull { index, rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) return@mapIndexedNotNull null

            val match = LEDGER_LINE.matchEntire(line)
                ?: error("$LEDGER_PATH:${index + 1} is not <versionName>+<versionCode>=<database>: $line")
            val (versionName, versionCode, databaseVersion) = match.destructured
            ReleasedDatabase(versionName, versionCode.toInt(), databaseVersion.toInt())
        }.also {
            check(it.isNotEmpty()) { "$LEDGER_PATH has no entries" }
        }
    }

    private fun currentBuild(): ReleasedDatabase {
        val properties = Properties().apply {
            File(repositoryRoot(), VERSION_PROPERTIES_PATH).inputStream().use(::load)
        }
        val versionName = requireNotNull(properties.getProperty("versionName")) {
            "$VERSION_PROPERTIES_PATH has no versionName"
        }
        val versionCode = requireNotNull(properties.getProperty("versionCode")?.toIntOrNull()) {
            "$VERSION_PROPERTIES_PATH has no numeric versionCode"
        }

        return ReleasedDatabase(versionName, versionCode, APP_DATABASE_VERSION)
    }

    private fun repositoryRoot(): File {
        return generateSequence(File(requireNotNull(System.getProperty("user.dir")))) {
            it.parentFile
        }.firstOrNull {
            File(it, "settings.gradle").isFile && File(it, LEDGER_PATH).isFile
        } ?: error("Cannot locate the Fearless Android repository root")
    }

    private data class ReleasedDatabase(
        val versionName: String,
        val versionCode: Int,
        val databaseVersion: Int
    ) {
        val label: String
            get() = "$versionName+$versionCode"
    }

    private companion object {
        const val LEDGER_PATH = "core-db/released-database-versions.txt"
        const val VERSION_PROPERTIES_PATH = "versioning/version.properties"
        const val SCHEMA_DIRECTORY = "core-db/schemas/jp.co.soramitsu.coredb.AppDatabase"
        val LEDGER_LINE = Regex("""([0-9A-Za-z.\-]+)\+([1-9][0-9]*)=([1-9][0-9]*)""")
    }
}
