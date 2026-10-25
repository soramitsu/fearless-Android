package jp.co.soramitsu.coredb.migrations

import android.database.Cursor
import com.google.gson.JsonParser
import androidx.sqlite.db.SupportSQLiteDatabase
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock

class LegacyNodeMigrationTest {
    @Test
    fun `new parent seed metadata only relocates legacy URLs and preserves released identities`() {
        val stream = checkNotNull(javaClass.getResourceAsStream("/migrations/legacy-node-parent-seeds-v1.json"))
        val historical = stream.bufferedReader().use { JsonParser.parseReader(it).asJsonObject.getAsJsonArray("rows") }
        var relocated = 0
        historical.forEachIndexed { index, value ->
            value.asJsonArray.forEachIndexed { column, cell ->
                val original = if (cell.isJsonNull) null else cell.asString
                val expected = original
                    ?.replace(
                        "https://raw.githubusercontent.com/soramitsu/shared-features-utils/master/",
                        "https://raw.githubusercontent.com/soramitsu/fearless-Android/master/remote-config/assets/"
                    )
                    ?.replace(
                        "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/",
                        "https://raw.githubusercontent.com/soramitsu/fearless-Android/master/remote-config/production/"
                    )
                if (expected != original) {
                    assertTrue(column in listOf(3, 8, 14))
                    relocated++
                }
                assertEquals("Seed $index column $column", expected, legacyNodeParentRows[index][column]?.toString())
            }
        }
        assertEquals(16, relocated)
    }

    @Test
    fun `all sixteen released ordinals and thirteen complete parent seeds are pinned`() {
        assertEquals(16, legacyNodeNetworkChainIds.size)
        assertEquals(
            "6ba28629a989e812ccb7f5cbf1d4f668f5d43991464313cce611f8714503e356",
            sha256(legacyNodeNetworkChainIds.joinToString("\n").toByteArray())
        )
        assertEquals("", legacyNodeNetworkChainIds[15])
        assertEquals("0x1ab7fbd1d7c3532386268ec23fe4ff69f5bb6b3e3697947df3a2ec2786424de3", legacyNodeNetworkChainIds[3])
        assertEquals(13, legacyNodeParentRows.size)
        assertTrue(legacyNodeParentRows.all { it.size == 16 })
        assertEquals(13, legacyNodeParentRows.map { it[0] }.toSet().size)
        val canonical = legacyNodeParentRows.joinToString("\n") { row ->
            row.joinToString("\u0000") { it?.toString() ?: "<null>" }
        }
        assertEquals("69bb3e72704bc7817e440f0345442277a7b88bbcba8c8f36e5474c85c5c24de1", sha256(canonical.toByteArray()))
        val fixture = checkNotNull(javaClass.getResourceAsStream("/migrations/legacy-node-parent-seeds-v1.json"))
        fixture.use {
            assertEquals("3ea197a9c584abdd13617ec1aff3672c793b7361676c9006cadc3ae22471b99e", sha256(it.readBytes()))
        }
        (0..14).filter { it != 6 }.forEach { ordinal ->
            assertEquals(legacyNodeNetworkChainIds[ordinal], legacyNodeTargetChainId(ordinal, emptySet()))
        }
    }

    @Test
    fun `acala uses its released correction only without an old exact parent`() {
        val old = legacyNodeNetworkChainIds[6]
        assertEquals(old, legacyNodeTargetChainId(6, setOf(old)))
        assertEquals(LEGACY_CORRECTED_ACALA_CHAIN_ID, legacyNodeTargetChainId(6, emptySet()))
        assertEquals(LEGACY_CORRECTED_ACALA_CHAIN_ID, legacyNodeTargetChainId(6, setOf(LEGACY_CORRECTED_ACALA_CHAIN_ID)))
        assertThrows(IllegalStateException::class.java) {
            legacyNodeTargetChainId(6, setOf(old, LEGACY_CORRECTED_ACALA_CHAIN_ID))
        }
    }

    @Test
    fun `undefined and out of range ordinals never receive a guessed chain`() {
        listOf(-1, 15, 16, Int.MAX_VALUE).forEach { ordinal ->
            assertThrows(IllegalStateException::class.java) { legacyNodeTargetChainId(ordinal, emptySet()) }
        }
    }

    @Test
    fun `seed payload accounting uses utf8 nulls integer text and exact cumulative boundary`() {
        val rows = listOf(listOf("一", null, 12), listOf("a"))
        assertEquals(10L, requireBoundedLegacySeedPayload(rows, 4L, TonUpgradePayloadLimits(3, 5, 10)))
        assertEquals(4L, requireBoundedLegacySeedPayload(emptyList(), 4L, TonUpgradePayloadLimits(3, 5, 10)))
    }

    @Test
    fun `seed cell row total and overflow limits reject without database mutation`() {
        listOf(
            TonUpgradePayloadLimits(2, 5, 10) to 0L,
            TonUpgradePayloadLimits(3, 4, 10) to 0L,
            TonUpgradePayloadLimits(3, 5, 8) to 4L
        ).forEach { (limits, start) ->
            assertThrows(TonUpgradeSqlIntegrityException::class.java) {
                requireBoundedLegacySeedPayload(listOf(listOf("一", 12)), start, limits)
            }
        }
        assertThrows(TonUpgradeSqlIntegrityException::class.java) {
            requireBoundedLegacySeedPayload(
                listOf(listOf("1234")), Long.MAX_VALUE - 2,
                TonUpgradePayloadLimits(Long.MAX_VALUE - 1, Long.MAX_VALUE - 1, Long.MAX_VALUE - 1)
            )
        }
    }

    @Test
    fun `healthy existing and pinned missing parents have read only admission`() {
        listOf(setOf(KUSAMA), emptySet()).forEach { parents ->
            val fixture = Fixture(parents = parents)
            assertTrue(LegacyNodeMigration.requireSafe(fixture.database))
            assertTrue(fixture.statements.isEmpty())
            assertEquals(0, fixture.begins)
        }
    }

    @Test
    fun `missing unpinned parent stays archived but exact old parent is supported`() {
        val id = legacyNodeNetworkChainIds[3]
        val missing = Fixture(parents = emptySet(), ordinals = listOf(3))
        assertFalse(LegacyNodeMigration.requireSafe(missing.database))
        assertTrue(missing.statements.isEmpty())
        assertTrue(LegacyNodeMigration.requireSafe(Fixture(parents = setOf(id), ordinals = listOf(3)).database))
    }

    @Test
    fun `catalog only path needs no transaction or parent payload admission`() {
        val fixture = Fixture(retained = false, outerTransaction = false)
        assertFalse(LegacyNodeMigration.requireSafe(fixture.database, TonUpgradePayloadLimits(1, 1, 1)))
        assertEquals(0, fixture.transactionReads)
        assertTrue(fixture.queries.none { it.contains("FROM chains") || it.contains("FROM `chains`") })
        LegacyNodeMigration.migrate(fixture.database)
        assertEquals(2, fixture.statements.size)
        assertTrue(fixture.statements.none { it.contains("DROP TABLE nodes") })
        assertEquals(1, fixture.begins)
        assertEquals(1, fixture.ends)
        assertEquals(1, fixture.successes)
    }

    @Test
    fun `invalid flags stay archived without synthesizing an active node`() {
        val fixture = Fixture(retained = false, invalidFlags = true, outerTransaction = false)
        assertFalse(LegacyNodeMigration.requireSafe(fixture.database))
        LegacyNodeMigration.migrate(fixture.database)
        assertEquals(1, fixture.begins)
        assertEquals(2, fixture.statements.size)
        assertTrue(fixture.statements.all { it.startsWith("ALTER TABLE chain_nodes") })
    }

    @Test
    fun `unsupported owned row storage stays archived before payload is read`() {
        val fixture = Fixture(unsupportedOwnedRow = true)
        assertFalse(LegacyNodeMigration.requireSafe(fixture.database))
        assertTrue(fixture.queries.none { it.startsWith("SELECT length") })
        assertTrue(fixture.statements.isEmpty())
    }

    @Test
    fun `oversized retained node stays archived before grouping or parent identity reads`() {
        val fixture = Fixture(nodeLengths = listOf(1_048_577L, 10L, 1L, 1L, 1L))
        assertFalse(LegacyNodeMigration.requireSafe(fixture.database))
        assertTrue(fixture.queries.none { it.startsWith("WITH legacy") || it.startsWith("SELECT id FROM chains") })
        assertTrue(fixture.statements.isEmpty())
    }

    @Test
    fun `case insensitive staging collision never drops the unowned object`() {
        val fixture = Fixture(stageCollision = true)
        assertThrows(IllegalStateException::class.java) { LegacyNodeMigration.migrate(fixture.database) }
        val probe = fixture.queries.single { it.contains("sqlite_temp_master") }
        assertEquals(2, Regex("COLLATE NOCASE").findAll(probe).count())
        assertTrue(fixture.statements.isEmpty())
    }

    @Test
    fun `conflicting duplicate labels stay archived without choosing an active label`() {
        val fixture = Fixture(conflictingDuplicates = true)
        LegacyNodeMigration.migrate(fixture.database)
        assertEquals(2, fixture.statements.size)
        assertTrue(fixture.statements.all { it.startsWith("ALTER TABLE chain_nodes") })
    }

    @Test
    fun `source and target comparisons explicitly preserve binary endpoint and label identity`() {
        val fixture = Fixture()
        LegacyNodeMigration.migrate(fixture.database)
        val admission = fixture.queries.single { it.contains("HAVING") }
        assertTrue(admission.contains("link COLLATE BINARY AS url"))
        assertTrue(admission.contains("name COLLATE BINARY AS name"))
        assertTrue(admission.contains("GROUP BY chainId COLLATE BINARY, url COLLATE BINARY"))
        assertTrue(admission.contains("COUNT(DISTINCT name COLLATE BINARY)"))
        assertTrue(fixture.statements.single { it.startsWith("UPDATE chain_nodes") }.contains("s.url COLLATE BINARY = chain_nodes.url"))
        assertTrue(fixture.statements.single { it.startsWith("INSERT INTO chain_nodes") }.contains("n.url COLLATE BINARY = s.url"))
    }

    @Test
    fun `projected parent and node row limits include additions before mutation`() {
        listOf(
            Fixture(parents = emptySet(), parentRows = 65_536L),
            Fixture(nodeRows = 262_144L, addedNodes = 1L)
        ).forEach { fixture ->
            assertFalse(LegacyNodeMigration.requireSafe(fixture.database))
            LegacyNodeMigration.migrate(fixture.database)
            assertEquals(2, fixture.statements.size)
            assertTrue(fixture.statements.all { it.startsWith("ALTER TABLE chain_nodes") })
        }
    }

    @Test
    fun `caller transaction remains entirely caller owned on healthy transfer`() {
        val fixture = Fixture()
        LegacyNodeMigration.migrate(fixture.database)
        assertEquals(0, fixture.begins)
        assertEquals(0, fixture.ends)
        assertEquals(0, fixture.successes)
        assertTrue(fixture.statements.any { it.startsWith("CREATE TEMP TABLE") })
        assertTrue(fixture.statements.none { it.contains("REPLACE") || it.contains("foreign_keys") })
        assertTrue(fixture.statements.last() == "DROP TABLE temp.`_migration_30_legacy_nodes`")
        assertFalse(fixture.statements.contains("DROP TABLE nodes"))
    }

    @Test
    fun `missing seed uses bound complete values without replacing a parent`() {
        val fixture = Fixture(parents = emptySet())
        LegacyNodeMigration.migrate(fixture.database)
        val row = legacyNodeParentRows.single { it[0] == KUSAMA }
        assertEquals(listOf(row + KUSAMA), fixture.bindings)
        assertTrue(fixture.statements.single { it.startsWith("INSERT INTO chains") }.contains("WHERE NOT EXISTS"))
    }

    @Test
    fun `caller failure cleans only owned staging and leaves rollback to caller`() {
        val original = IllegalStateException("synthetic transfer write failure")
        val fixture = Fixture(executionFailure = original)
        assertSame(original, assertThrows(IllegalStateException::class.java) { LegacyNodeMigration.migrate(fixture.database) })
        assertTrue(fixture.statements.last() == "DROP TABLE temp.`_migration_30_legacy_nodes`")
        assertFalse(fixture.statements.contains("DROP TABLE nodes"))
        assertEquals(0, fixture.ends)
        assertEquals(0, fixture.successes)
    }

    @Test
    fun `owned transaction closes without success after transfer failure`() {
        val fixture = Fixture(outerTransaction = false, executionFailure = IllegalStateException("synthetic failure"))
        assertThrows(IllegalStateException::class.java) { LegacyNodeMigration.migrate(fixture.database) }
        assertEquals(1, fixture.begins)
        assertEquals(1, fixture.ends)
        assertEquals(0, fixture.successes)
    }

    @Test
    fun `post transfer verification failure preserves original table and cleans staging`() {
        val fixture = Fixture(verificationFailure = true)
        assertThrows(IllegalStateException::class.java) { LegacyNodeMigration.migrate(fixture.database) }
        assertFalse(fixture.statements.contains("DROP TABLE nodes"))
        assertTrue(fixture.statements.last().startsWith("DROP TABLE temp."))
    }

    @Test
    fun `archive only still validates the downstream graph without staging endpoints`() {
        val fixture = Fixture(retained = false)
        var validations = 0
        LegacyNodeMigration.migrate(fixture.database) { validations++ }
        assertEquals(1, validations)
        assertTrue(fixture.statements.none { it.startsWith("CREATE TEMP TABLE") || it.contains("DROP TABLE nodes") })
        assertTrue(fixture.statements.contains(";ROLLBACK TO SAVEPOINT fearless_upgrade_preview"))
    }

    @Test
    fun `eligible transfer is previewed rolled back then applied in the same transaction`() {
        val fixture = Fixture()
        var validations = 0
        LegacyNodeMigration.migrate(fixture.database) {
            validations++
            assertEquals(1, fixture.statements.count { it.startsWith("INSERT INTO chain_nodes") })
        }
        assertEquals(1, validations)
        assertEquals(2, fixture.statements.count { it.startsWith("INSERT INTO chain_nodes") })
        assertEquals(0, fixture.ends)
        assertFalse(fixture.statements.contains("DROP TABLE nodes"))
    }

    @Test
    fun `positive downstream copy limit chooses validated archive only and never reapplies candidate`() {
        val fixture = Fixture()
        var validations = 0
        LegacyNodeMigration.migrate(fixture.database) {
            if (++validations == 1) throw MigrationPayloadLimitException("candidate copy budget")
            assertTrue(fixture.statements.last() == "SAVEPOINT fearless_upgrade_preview")
            assertEquals(1, fixture.statements.count { it.startsWith("RELEASE SAVEPOINT") })
        }
        assertEquals(2, validations)
        assertEquals(1, fixture.statements.count { it.startsWith("INSERT INTO chain_nodes") })
        assertEquals(2, fixture.statements.count { it.startsWith(";ROLLBACK TO SAVEPOINT") })
        assertFalse(fixture.statements.contains("DROP TABLE nodes"))
    }

    @Test
    fun `fallback failure preserves both diagnostics and never claims successful migration`() {
        val fixture = Fixture()
        val candidate = MigrationPayloadLimitException("candidate budget")
        val fallback = IllegalStateException("archive graph failure")
        var validations = 0
        assertSame(fallback, assertThrows(IllegalStateException::class.java) {
            LegacyNodeMigration.migrate(fixture.database) {
                if (++validations == 1) throw candidate else throw fallback
            }
        })
        assertEquals(2, validations)
        assertEquals(listOf(candidate), fallback.suppressed.toList())
        assertEquals(0, fixture.successes)
    }

    @Test
    fun `SQL metadata and cancellation errors never trigger fallback`() {
        listOf(IllegalStateException("SQL read failure"), TonUpgradeSqlIntegrityException("invalid scalar length"),
            java.util.concurrent.CancellationException("cancelled")).forEach { original ->
            val fixture = Fixture()
            var validations = 0
            assertSame(original, assertThrows(RuntimeException::class.java) {
                LegacyNodeMigration.migrate(fixture.database) { validations++; throw original }
            })
            assertEquals(1, validations)
            assertEquals(1, fixture.statements.count { it.startsWith("INSERT INTO chain_nodes") })
        }
    }

    @Test
    fun `failed preview cleanup prevents a resource failure becoming archive fallback`() {
        val cleanup = IllegalStateException("rollback failed")
        val fixture = Fixture(statementFailure = { sql -> if (sql.startsWith(";ROLLBACK")) cleanup else null })
        val resource = MigrationPayloadLimitException("candidate budget")
        var validations = 0
        assertSame(resource, assertThrows(MigrationPayloadLimitException::class.java) {
            LegacyNodeMigration.migrate(fixture.database) { validations++; throw resource }
        })
        assertEquals(1, validations)
        assertEquals(listOf(cleanup), resource.suppressed.toList())
        assertTrue(fixture.statements.none { it.startsWith("RELEASE SAVEPOINT") })
    }

    @Test
    fun `empty candidate failure is not retried as another identical archive plan`() {
        val fixture = Fixture(retained = false)
        val resource = MigrationPayloadLimitException("preexisting data budget")
        var validations = 0
        assertSame(resource, assertThrows(MigrationPayloadLimitException::class.java) {
            LegacyNodeMigration.migrate(fixture.database) { validations++; throw resource }
        })
        assertEquals(1, validations)
        assertTrue(fixture.statements.none { it.startsWith("INSERT INTO chain_nodes") })
    }

    @Test
    fun `owned transaction cleanup keeps original mutation failure`() {
        val original = IllegalStateException("mutation")
        val cleanup = IllegalStateException("end transaction")
        val fixture = Fixture(outerTransaction = false, executionFailure = original, cleanupFailure = cleanup)
        assertSame(original, assertThrows(IllegalStateException::class.java) { LegacyNodeMigration.migrate(fixture.database) })
        assertEquals(listOf(cleanup), original.suppressed.toList())
        assertEquals(1, fixture.ends)
        assertEquals(0, fixture.successes)
    }

    @Test
    fun `cleanup failure after a successful owned transfer propagates`() {
        val cleanup = IllegalStateException("end transaction")
        val fixture = Fixture(outerTransaction = false, cleanupFailure = cleanup)
        assertSame(cleanup, assertThrows(IllegalStateException::class.java) { LegacyNodeMigration.migrate(fixture.database) })
        assertEquals(1, fixture.ends)
        assertEquals(1, fixture.successes)
    }

    @Test
    fun `invalid payload metadata and query failures are never archive eligibility decisions`() {
        val malformed = Fixture(nodeLengths = listOf(-1L, 1L, 1L, 1L, 1L))
        val failure = assertThrows(TonUpgradeSqlIntegrityException::class.java) { LegacyNodeMigration.requireSafe(malformed.database) }
        assertFalse(failure is MigrationPayloadLimitException)
        val original = IllegalStateException("payload query failed")
        val fixture = Fixture(queryFailure = original)
        assertSame(original, assertThrows(IllegalStateException::class.java) { LegacyNodeMigration.requireSafe(fixture.database) })
        assertTrue(fixture.statements.isEmpty())
    }

    @Test
    fun `ambiguous acala and undefined sources decline transfer without guessing parent metadata`() {
        val old = legacyNodeNetworkChainIds[6]
        listOf(Fixture(parents = setOf(old, LEGACY_CORRECTED_ACALA_CHAIN_ID), ordinals = listOf(6)),
            Fixture(ordinals = listOf(15))).forEach { fixture ->
            assertFalse(LegacyNodeMigration.requireSafe(fixture.database))
            assertTrue(fixture.statements.isEmpty())
        }
    }

    private class Fixture(
        val retained: Boolean = true,
        val invalidFlags: Boolean = false,
        val unsupportedOwnedRow: Boolean = false,
        val parents: Set<String> = setOf(KUSAMA),
        val ordinals: List<Int> = listOf(0),
        val nodeLengths: List<Long> = listOf(5, 30, 1, 1, 1),
        val stageCollision: Boolean = false,
        val conflictingDuplicates: Boolean = false,
        val parentRows: Long = 1,
        val nodeRows: Long = 1,
        val addedNodes: Long = 1,
        val outerTransaction: Boolean = true,
        val executionFailure: RuntimeException? = null,
        val verificationFailure: Boolean = false,
        val cleanupFailure: RuntimeException? = null,
        val queryFailure: RuntimeException? = null,
        val statementFailure: (String) -> Throwable? = { null }
    ) {
        val queries = mutableListOf<String>()
        val statements = mutableListOf<String>()
        val bindings = mutableListOf<List<Any?>>()
        var begins = 0
        var ends = 0
        var successes = 0
        var transactionReads = 0
        val database: SupportSQLiteDatabase = mock {
            on { inTransaction() } doAnswer { transactionReads++; outerTransaction }
            on { beginTransaction() } doAnswer { begins++; Unit }
            on { endTransaction() } doAnswer { ends++; cleanupFailure?.let { throw it }; Unit }
            on { setTransactionSuccessful() } doAnswer { successes++; Unit }
            on { query(any<String>()) } doAnswer { invocation ->
                val sql = invocation.arguments[0] as String
                queries += sql
                if (sql.startsWith("SELECT length")) queryFailure?.let { throw it }
                val rows: List<List<Any?>> = when {
                    sql.startsWith("SELECT 1 FROM `") -> emptyList()
                    sql.contains("typeof(isDefault)") -> truth(invalidFlags)
                    sql == "SELECT 1 FROM nodes WHERE isDefault = 0 OR isActive = 1 LIMIT 1" -> truth(retained)
                    sql.contains("typeof(networkType)") -> truth(unsupportedOwnedRow)
                    sql.startsWith("SELECT length") && sql.contains("FROM `nodes`") -> listOf(nodeLengths)
                    sql.startsWith("SELECT length") && sql.contains("FROM `chains`") -> parents.map { List(16) { 1L } }
                    sql.contains("sqlite_temp_master") -> truth(stageCollision)
                    sql.startsWith("SELECT id FROM chains") -> parents.map { listOf(it) }
                    sql.startsWith("SELECT DISTINCT networkType") -> ordinals.map { listOf(it.toLong()) }
                    sql.contains("HAVING") -> truth(conflictingDuplicates)
                    sql == "SELECT COUNT(*) FROM chains" -> listOf(listOf(parentRows))
                    sql == "SELECT COUNT(*) FROM chain_nodes" -> listOf(listOf(nodeRows))
                    sql.startsWith("WITH legacy") && sql.contains("SELECT COUNT(*) FROM merged") -> listOf(listOf(addedNodes))
                    sql.startsWith("SELECT 1 FROM temp.") -> truth(verificationFailure)
                    else -> error("Unexpected query: $sql")
                }
                cursor(rows)
            }
            on { execSQL(any<String>()) } doAnswer { invocation ->
                val sql = invocation.arguments[0] as String
                statements += sql
                statementFailure(sql)?.let { throw it }
                if (sql.startsWith("UPDATE chain_nodes")) executionFailure?.let { throw it }
                Unit
            }
            on { execSQL(any<String>(), any<Array<Any?>>()) } doAnswer { invocation ->
                statements += invocation.arguments[0] as String
                @Suppress("UNCHECKED_CAST")
                bindings += (invocation.arguments[1] as Array<Any?>).toList()
                Unit
            }
        }
    }

    private companion object {
        const val KUSAMA = "b0a8d493285c2df73290dfb7e61f870f17b41801197a149ca93654499ea3dafe"
        fun truth(value: Boolean): List<List<Any?>> = if (value) listOf(listOf(1L)) else emptyList()
        fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        fun cursor(rows: List<List<Any?>>): Cursor {
            var row = -1
            return mock {
                on { moveToFirst() } doAnswer { row = 0; rows.isNotEmpty() }
                on { moveToNext() } doAnswer { ++row < rows.size }
                on { isNull(any()) } doAnswer { rows[row][it.arguments[0] as Int] == null }
                on { getType(any()) } doAnswer {
                    if (rows[row][it.arguments[0] as Int] is Long) Cursor.FIELD_TYPE_INTEGER else Cursor.FIELD_TYPE_STRING
                }
                on { getLong(any()) } doAnswer { rows[row][it.arguments[0] as Int] as Long }
                on { getInt(any()) } doAnswer { (rows[row][it.arguments[0] as Int] as Long).toInt() }
                on { getString(any()) } doAnswer { rows[row][it.arguments[0] as Int] as String }
            }
        }
    }
}
