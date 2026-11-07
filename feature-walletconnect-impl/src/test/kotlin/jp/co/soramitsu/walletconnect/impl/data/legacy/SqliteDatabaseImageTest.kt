package jp.co.soramitsu.walletconnect.impl.data.legacy

import jp.co.soramitsu.walletconnect.impl.data.legacy.LegacyFixtures.assertRowsMatch
import jp.co.soramitsu.walletconnect.impl.data.legacy.LegacyFixtures.bytes
import jp.co.soramitsu.walletconnect.impl.data.legacy.LegacyFixtures.section
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

class SqliteDatabaseImageTest {

    private val plain by lazy { SqliteDatabaseImage(bytes("plain.db")) }
    private val expected by lazy { section("plain").getAsJsonObject("rows") }

    @Test
    fun `every value kind reads back as SQLite returns it, overflow chains included`() {
        assertRowsMatch(expected.getAsJsonObject("kinds"), plain.readTable("kinds")!!, "kinds")
    }

    @Test
    fun `rows written before ALTER TABLE ADD COLUMN take the column default`() {
        val rows = plain.readTable("grown")!!

        assertRowsMatch(expected.getAsJsonObject("grown"), rows, "grown")
        assertEquals(1L, rows.first()["flag"])
        assertEquals("it's", rows.first()["label"])
        assertEquals(-5L, rows.first()["neg"])
    }

    @Test
    fun `quoted table and column names are understood`() {
        assertRowsMatch(expected.getAsJsonObject("quoted name"), plain.readTable("quoted name")!!, "quoted name")
    }

    @Test
    fun `a table several interior levels deep reads completely and in rowid order`() {
        val rows = plain.readTable("many")!!

        assertRowsMatch(expected.getAsJsonObject("many"), rows, "many")
        assertEquals(rows.map { it.long("id") }.sortedBy { it }, rows.map { it.long("id") })
    }

    @Test
    fun `the schema lists the tables, a missing table reads as null and WITHOUT ROWID is refused`() {
        assertTrue(plain.tableNames().containsAll(listOf("kinds", "grown", "quoted name", "norowid", "many")))
        assertNull(plain.readTable("absent"))
        assertThrows(LegacyDatabaseException::class.java) { plain.readTable("norowid") }
    }

    @Test
    fun `a damaged image raises LegacyDatabaseException instead of crashing`() {
        val database = bytes("plain.db")
        val truncated = database.copyOf(database.size / 3)
        val badRoot = database.copyOf().also { it[100] = 0x42 } // page 1 is no longer a b-tree page

        assertThrows(LegacyDatabaseException::class.java) { SqliteDatabaseImage(truncated).readTable("many") }
        assertThrows(LegacyDatabaseException::class.java) { SqliteDatabaseImage(badRoot).tableNames() }
        assertThrows(LegacyDatabaseException::class.java) { SqliteDatabaseImage(ByteArray(4096)) }
    }

    @Test
    fun `columns, rowid aliases, defaults and affinity come from the CREATE statement`() {
        val columns = parseCreateTableColumns(
            "CREATE TABLE \"T\" (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `a b` TEXT DEFAULT 'x,y', " +
                "n INT PRIMARY KEY, r REAL DEFAULT -2.5, d DOUBLE PRECISION, v VARCHAR(10) DEFAULT \"\", " +
                "z INTEGER PRIMARY KEY DESC, f INTEGER DEFAULT TRUE, UNIQUE(id, n), CHECK (n > 0))"
        )

        assertEquals(listOf("id", "a b", "n", "r", "d", "v", "z", "f"), columns.map { it.name })
        assertEquals(listOf(true, false, false, false, false, false, false, false), columns.map { it.isRowidAlias })
        assertEquals(listOf(null, "x,y", null, -2.5, null, "", null, 1L), columns.map { it.defaultValue })
        assertEquals(listOf(false, false, false, true, true, false, false, false), columns.map { it.hasRealAffinity })
        assertThrows(LegacyDatabaseException::class.java) { parseCreateTableColumns("CREATE TABLE broken") }
    }

    @Test
    fun `varints and serial types follow the file format`() {
        assertEquals(0x7fL, byteArrayOf(0x7f).varint(0).value)
        assertEquals(2, byteArrayOf(0x81.toByte(), 0x00).varint(0).length)
        assertEquals(128L, byteArrayOf(0x81.toByte(), 0x00).varint(0).value)
        val nine = ByteArray(9) { 0xff.toByte() }
        assertEquals(-1L, nine.varint(0).value)
        assertEquals(9, nine.varint(0).length)
        assertThrows(LegacyDatabaseException::class.java) { byteArrayOf(0x81.toByte()).varint(0) }

        val types = listOf(0L, 1, 2, 3, 4, 5, 6, 7, 8, 9, 12, 13, 22, 23)
        assertEquals(listOf(0, 1, 2, 3, 4, 6, 8, 8, 0, 0, 0, 0, 5, 5), types.map { it.serialTypeLength() })
        listOf(-1L, 10L, 11L).forEach { reserved ->
            assertThrows(LegacyDatabaseException::class.java) { reserved.serialTypeLength() }
        }
        assertEquals(-2L, byteArrayOf(0xff.toByte(), 0xfe.toByte()).serialValue(2, 0, 2))
        assertEquals("hi", "hi".toByteArray().serialValue(17, 0, 2))
        assertNull(ByteArray(0).serialValue(0, 0, 0))
    }

    @Test
    fun `fixed-width values decode as SQLite writes them`() {
        val float = ByteBuffer.allocate(Double.SIZE_BYTES).putDouble(1.5).array()

        assertEquals(1.5, float.serialValue(7, 0, 8))
        assertEquals(0L, ByteArray(0).serialValue(8, 0, 0))
        assertEquals(1L, ByteArray(0).serialValue(9, 0, 0))
        assertEquals(0x0102030405L, byteArrayOf(0, 1, 2, 3, 4, 5).serialValue(5, 0, 6))
        assertEquals(Long.MIN_VALUE, ByteArray(8).also { it[0] = 0x80.toByte() }.serialValue(6, 0, 8))
        assertArrayEquals(byteArrayOf(1, 2), byteArrayOf(1, 2).serialValue(16, 0, 2) as ByteArray)
    }

    @Test
    fun `rows read typed values and booleans`() {
        val row = SqliteRow(mapOf("i" to 3L, "d" to 2.0, "s" to "7", "z" to 0L, "n" to null))

        assertEquals(3L, row.long("i"))
        assertEquals(2L, row.long("d"))
        assertEquals(7L, row.long("s"))
        assertEquals("7", row.string("s"))
        assertFalse(row.boolean("z")!!)
        assertNull(row.boolean("n"))
        assertNull(row.string("i"))
    }
}
