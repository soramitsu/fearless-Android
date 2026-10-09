package jp.co.soramitsu.wallet.impl.data.network.model.request

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import jp.co.soramitsu.wallet.impl.domain.interfaces.TransactionFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GiantsquidHistoryRequestTest {

    @Test
    fun `builds bounded selected history sections with escaped account literal`() {
        val address = "account\"} mutation { slashes { id } } #\nnext"
        val query = GiantsquidHistoryRequest(
            accountAddress = address,
            limit = 37,
            offset = 74,
            filters = setOf(TransactionFilter.TRANSFER, TransactionFilter.EXTRINSIC)
        ).query
        val escapedAddress = JsonPrimitive(address).toString()

        assertTrue(query.contains("id_eq: $escapedAddress"))
        assertTrue(query.contains("accountId_eq: $escapedAddress"))
        assertTrue(query.contains("limit: 37, offset: 74"))
        assertTrue(query.contains("transfers("))
        assertTrue(query.contains("bonds("))
        assertTrue(query.contains("slashes("))
        assertTrue(query.contains("type"))
        assertFalse(query.contains("rewards("))
        assertFalse(query.lines().any { it.trimStart().startsWith("mutation") })
        assertEquals(1, Regex("(?m)^  slashes\\(").findAll(query).count())
    }

    @Test
    fun `requests transfer reward bond and slash fields by default`() {
        val query = GiantsquidHistoryRequest("account", limit = 25, offset = 0).query

        for (root in listOf("transfers(", "rewards(", "bonds(", "slashes(")) {
            assertTrue("missing $root", query.contains(root))
        }
        for (field in listOf("extrinsicHash", "validatorId", "accountId", "success", "era", "timestamp")) {
            assertTrue("missing $field", query.contains(field))
        }
    }

    @Test
    fun `isolates a reward-only request to one reward root`() {
        val query = GiantsquidHistoryRequest(
            accountAddress = "account",
            filters = setOf(TransactionFilter.REWARD)
        ).query

        assertEquals(1, Regex("(?m)^  rewards\\(").findAll(query).count())
        assertFalse(query.contains("transfers("))
        assertFalse(query.contains("bonds("))
        assertFalse(query.contains("slashes("))
    }

    @Test
    fun `serializes only the GraphQL query contract`() {
        val request = GiantsquidHistoryRequest(
            accountAddress = "account",
            filters = setOf(TransactionFilter.EXTRINSIC)
        )

        val serialized = JsonParser.parseString(Gson().toJson(request)).asJsonObject

        assertEquals(setOf("query"), serialized.keySet())
        assertEquals(request.query, serialized.get("query").asString)
    }

    @Test
    fun `rejects empty or invalid query bounds and filters`() {
        assertThrows(IllegalArgumentException::class.java) { GiantsquidHistoryRequest("", 1, 0) }
        assertThrows(IllegalArgumentException::class.java) { GiantsquidHistoryRequest("account", 0, 0) }
        assertThrows(IllegalArgumentException::class.java) { GiantsquidHistoryRequest("account", -1, 0) }
        assertThrows(IllegalArgumentException::class.java) { GiantsquidHistoryRequest("account", 101, 0) }
        assertThrows(IllegalArgumentException::class.java) { GiantsquidHistoryRequest("account", 1, -1) }
        assertThrows(IllegalArgumentException::class.java) { GiantsquidHistoryRequest("a".repeat(513), 1, 0) }
        assertThrows(IllegalArgumentException::class.java) {
            GiantsquidHistoryRequest("account", 1, 0, emptySet())
        }
    }
}
