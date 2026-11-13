package jp.co.soramitsu.walletconnect.impl.data.legacy

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import java.security.MessageDigest

/**
 * The fixtures in `src/test/resources/reown-legacy`, written by the real SQLCipher library with
 * `fixtures/make-reown-legacy-fixtures.py` (synthetic keys only), and the rows each table must read as.
 */
internal object LegacyFixtures {
    val manifest: JsonObject by lazy {
        JsonParser.parseString(String(bytes("fixtures.json"), Charsets.UTF_8)).asJsonObject
    }

    fun bytes(name: String): ByteArray =
        requireNotNull(LegacyFixtures::class.java.classLoader!!.getResourceAsStream("reown-legacy/$name")) { name }
            .use { it.readBytes() }

    fun section(name: String): JsonObject = manifest.getAsJsonObject(name)

    fun passphrase(section: String): ByteArray = section(section)["passphrase"].asString.hexToBytes()

    /** Asserts that [rows] hold exactly the values the generator read back through SQLCipher itself. */
    fun assertRowsMatch(
        expected: JsonObject,
        rows: List<SqliteRow>,
        table: String
    ) {
        val columns = expected.getAsJsonArray("columns").map { it.asString }
        val expectedRows = expected.getAsJsonArray("rows")
        assertEquals("$table row count", expectedRows.size(), rows.size)
        expectedRows.forEachIndexed { index, expectedRow ->
            columns.forEachIndexed { column, name ->
                assertValue("$table[$index].$name", expectedRow.asJsonArray[column], rows[index][name])
            }
        }
    }

    private fun assertValue(
        where: String,
        expected: JsonElement,
        actual: Any?
    ) {
        if (expected.isJsonNull) {
            assertEquals(where, null, actual)
            return
        }
        val value = expected.asJsonObject
        when {
            value.has("int") -> assertEquals(where, value["int"].asLong, actual)
            value.has("float") -> assertEquals(where, value["float"].asDouble, actual as Double, 0.0)
            value.has("text") -> assertEquals(where, value["text"].asString, actual)
            value.has("blob") -> assertEquals(where, value["blob"].asString, (actual as ByteArray).toHex())
            else -> {
                val data = (actual as? String)?.toByteArray(Charsets.UTF_8) ?: actual as ByteArray
                assertEquals("$where length", value["length"].asInt, data.size)
                assertEquals("$where sha256", value["sha256"].asString, MessageDigest.getInstance("SHA-256").digest(data).toHex())
            }
        }
    }
}
