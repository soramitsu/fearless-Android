package jp.co.soramitsu.coredb.migrations

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The repair before the 76 -> 77 preflight rebuilds tables from DDL frozen in
 * [RELEASED_V76_REBUILDABLE_TABLES]. If that DDL drifted from what Room
 * validates, a repaired database would fail to open, so it is checked here
 * against Room's own exports for the version the repair targets (76) and the
 * version Room validates after the upgrade (81).
 */
class RebuildableTableContractsTest {

    @Test
    fun `every version 76 table is either an identity table or rebuildable`() {
        val rebuildable = RELEASED_V76_REBUILDABLE_TABLES.map { it.contract.name }
        val identity = RELEASED_ROOM_SCHEMA_V76.map { it.name }
            .filter { it in RELEASED_V76_WALLET_IDENTITY_TABLES }

        assertEquals(rebuildable.size, rebuildable.toSet().size)
        assertEquals(
            RELEASED_ROOM_SCHEMA_V76.map { it.name }.toSet(),
            rebuildable.toSet() + identity
        )
        assertEquals(
            setOf("meta_accounts", "chain_accounts", "ton_connection", "room_master_table"),
            identity.toSet()
        )
        assertTrue(RELEASED_LEGACY_USERS_RECOVERY_TABLE.name in RELEASED_V76_WALLET_IDENTITY_TABLES)
        assertEquals(
            setOf("address_book", "assets", "favorite_chains"),
            RELEASED_V76_REBUILDABLE_TABLES
                .filter { it.rows == RebuildableRows.USER }
                .map { it.contract.name }
                .toSet()
        )
    }

    @Test
    fun `rebuild DDL is Room's DDL at version 76 and 81`() {
        listOf(76, 81).forEach { version ->
            val entities = roomEntities(version)
            RELEASED_V76_REBUILDABLE_TABLES.forEach { table ->
                val name = table.contract.name
                val entity = entities.getValue(name)
                assertEquals("$name at $version", entity["createSql"].asString, table.ddl.createSql)
                assertEquals(
                    "$name indexes at $version",
                    entity.getAsJsonArray("indices")?.map { it.asJsonObject["createSql"].asString }.orEmpty(),
                    table.ddl.indexSql
                )
            }
        }
    }

    @Test
    fun `rebuild DDL creates the canonical contract layout and indexes`() {
        RELEASED_V76_REBUILDABLE_TABLES.forEach { table ->
            val name = table.contract.name
            val entity = roomEntities(76).getValue(name)
            val fields = entity.getAsJsonArray("fields").map { it.asJsonObject }
            val primaryKey = entity.getAsJsonObject("primaryKey")
                .getAsJsonArray("columnNames")
                .map { it.asString }

            assertEquals(
                name,
                table.contract.columns.map { listOf(it.name, it.type, it.notNull, it.primaryKeyPosition) },
                fields.map { field ->
                    val column = field["columnName"].asString
                    listOf(
                        column,
                        field["affinity"].asString,
                        field["notNull"]?.asBoolean ?: false,
                        primaryKey.indexOf(column) + 1
                    )
                }
            )
            assertTrue(name, table.contract.columns.all { null in it.allowedDefaultValues })
            assertEquals(
                name,
                table.contract.indexes.map { it.name }.toSet(),
                table.ddl.indexSql.map { INDEX_NAME.find(it)!!.groupValues[1] }.toSet()
            )
        }
    }

    @Test
    fun `rebuilt foreign keys are the ones the preflight expects`() {
        RELEASED_V76_REBUILDABLE_TABLES.forEach { table ->
            val name = table.contract.name
            val room = roomEntities(76).getValue(name)
                .getAsJsonArray("foreignKeys")
                ?.map { it.asJsonObject }
                .orEmpty()
                .map { key ->
                    MigrationForeignKeyDefinition(
                        parentTable = key["table"].asString,
                        columns = key.getAsJsonArray("columns").map { it.asString }
                            .zip(key.getAsJsonArray("referencedColumns").map { it.asString })
                            .map { (from, to) -> MigrationForeignKeyColumnMapping(from, to) },
                        onUpdate = key["onUpdate"].asString,
                        onDelete = key["onDelete"].asString
                    )
                }
                .toSet()

            assertEquals(
                name,
                WALLET_INTEGRITY_FOREIGN_KEY_CHECK_LIMITS.expectedForeignKeysByTable[name].orEmpty(),
                room
            )
        }
    }

    @Test
    fun `only registry and pool caches lose dangling rows`() {
        val rowsByTable = RELEASED_V76_REBUILDABLE_TABLES.associate { it.contract.name to it.rows }
        RELEASED_V76_DANGLING_CACHE_ROWS.forEach { rows ->
            assertEquals(rows.childTable, RebuildableRows.CACHE, rowsByTable[rows.childTable])
            val definitions = WALLET_INTEGRITY_FOREIGN_KEY_CHECK_LIMITS
                .expectedForeignKeysByTable
                .getValue(rows.childTable)
            assertTrue(
                rows.childTable,
                definitions.any { definition ->
                    definition.parentTable == rows.parentTable &&
                        definition.columns.map { it.from } == rows.childColumns &&
                        definition.columns.map { it.to } == rows.parentColumns
                }
            )
        }
        assertEquals(
            setOf("chain_nodes", "chain_assets", "chain_explorers", "userpools", "nomis_wallet_score"),
            RELEASED_V76_DANGLING_CACHE_ROWS.map { it.childTable }.toSet()
        )
    }

    @Test
    fun `only user rows that point at the chain registry are tolerated`() {
        val rowsByTable = RELEASED_V76_REBUILDABLE_TABLES.associate { it.contract.name to it.rows }
        WALLET_INTEGRITY_TOLERATED_FOREIGN_KEY_VIOLATIONS.forEach { scope ->
            assertEquals(scope.childTable, "chains", scope.parentTable)
            assertTrue(
                scope.childTable,
                rowsByTable[scope.childTable] == RebuildableRows.USER ||
                    scope.childTable in RELEASED_V76_WALLET_IDENTITY_TABLES
            )
            assertTrue(
                scope.childTable,
                WALLET_INTEGRITY_FOREIGN_KEY_CHECK_LIMITS.expectedForeignKeysByTable
                    .getValue(scope.childTable)
                    .any { it.parentTable == "chains" }
            )
        }
        assertEquals(
            setOf("assets", "favorite_chains", "chain_accounts"),
            WALLET_INTEGRITY_TOLERATED_FOREIGN_KEY_VIOLATIONS.map { it.childTable }.toSet()
        )
    }

    @Test
    fun `the chains layout shipped 4x builds created is accepted`() {
        val chains = RELEASED_ROOM_SCHEMA_V76.single { it.name == "chains" }

        assertTrue(chains.allowedColumnLayouts().contains(SHIPPED_4X_CHAINS_LAYOUT))
    }

    private fun roomEntities(version: Int): Map<String, JsonObject> {
        val schema = File(repositoryRoot(), "$SCHEMA_DIRECTORY/$version.json")
        return JsonParser.parseString(schema.readText()).asJsonObject
            .getAsJsonObject("database")
            .getAsJsonArray("entities")
            .map { it.asJsonObject }
            .associateBy { it["tableName"].asString }
    }

    private fun repositoryRoot(): File {
        return generateSequence(File(requireNotNull(System.getProperty("user.dir")))) {
            it.parentFile
        }.firstOrNull {
            File(it, "settings.gradle").isFile && File(it, SCHEMA_DIRECTORY).isDirectory
        } ?: error("Cannot locate the Fearless Android repository root")
    }

    private companion object {
        const val SCHEMA_DIRECTORY = "core-db/schemas/jp.co.soramitsu.coredb.AppDatabase"
        val INDEX_NAME = Regex("INDEX IF NOT EXISTS `([^`]+)`")

        /**
         * PRAGMA table_info(chains) of the 3.7.4 fixture after the frozen
         * shipped 71 -> 75 SQL (core-db/src/androidTest/assets/migrations/
         * 4.x-shipped) and 75 -> 76: name, type, not null, default.
         */
        val SHIPPED_4X_CHAINS_LAYOUT = listOf(
            listOf("id", "TEXT", true, null, 1),
            listOf("paraId", "TEXT", false, null),
            listOf("parentId", "TEXT", false, null),
            listOf("rank", "INTEGER", false, null),
            listOf("name", "TEXT", true, null),
            listOf("minSupportedVersion", "TEXT", false, null),
            listOf("icon", "TEXT", true, null),
            listOf("prefix", "INTEGER", true, null),
            listOf("isEthereumBased", "INTEGER", true, null),
            listOf("isTestNet", "INTEGER", true, null),
            listOf("hasCrowdloans", "INTEGER", true, null),
            listOf("supportStakingPool", "INTEGER", true, null),
            listOf("isEthereumChain", "INTEGER", true, "0"),
            listOf("isChainlinkProvider", "INTEGER", true, "0"),
            listOf("supportNft", "INTEGER", true, "0"),
            listOf("isUsesAppId", "INTEGER", true, "0"),
            listOf("identityChain", "TEXT", false, "NULL"),
            listOf("ecosystem", "TEXT", true, null),
            listOf("androidMinAppVersion", "TEXT", false, "NULL"),
            listOf("staking_url", "TEXT", false, null),
            listOf("staking_type", "TEXT", false, null),
            listOf("history_url", "TEXT", false, null),
            listOf("history_type", "TEXT", false, null),
            listOf("crowdloans_url", "TEXT", false, null),
            listOf("crowdloans_type", "TEXT", false, null),
            listOf("remoteAssetsSource", "TEXT", false, "NULL"),
            listOf("tonBridgeUrl", "TEXT", false, "NULL"),
            listOf("xcm", "TEXT", false, "NULL")
        ).map { column ->
            ReleasedRoomColumnSpec(
                name = column[0] as String,
                type = column[1] as String,
                notNull = column[2] as Boolean,
                primaryKeyPosition = column.getOrNull(4) as Int? ?: 0,
                allowedDefaultValues = setOf(column[3] as String?)
            )
        }
    }
}
