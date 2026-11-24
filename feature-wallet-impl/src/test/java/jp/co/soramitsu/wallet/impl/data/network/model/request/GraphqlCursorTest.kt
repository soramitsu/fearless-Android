package jp.co.soramitsu.wallet.impl.data.network.model.request

import com.google.gson.Gson
import jp.co.soramitsu.wallet.impl.domain.interfaces.TransactionFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphqlCursorTest {
    @Test
    fun `provider cursors remain one bounded GraphQL string in every history request`() {
        val cursor = "cursor\"}) { injected } #\n\\\u0000"
        val literal = graphqlStringLiteral(cursor)
        assertEquals(cursor, Gson().fromJson(literal, String::class.java))
        assertTrue(!literal.contains('\n'))
        val queries = listOf(
            SubqueryHistoryRequest("account", 20, cursor, setOf(TransactionFilter.TRANSFER), false).query,
            SubsquidHistoryRequest("account", 20, cursor).query,
            ReefRequestBuilder(setOf(TransactionFilter.TRANSFER), "account", 20, cursor).buildRequest().query
        )
        queries.forEach { assertTrue(it.contains("after: $literal")) }
    }

    @Test
    fun `null numeric and oversized cursors have explicit safe semantics`() {
        assertEquals("null", graphqlStringLiteral(null))
        assertEquals("\"123\"", graphqlStringLiteral("123"))
        assertTrue(runCatching { graphqlStringLiteral("a".repeat(4097)) }.isFailure)
        assertEquals(4098, graphqlStringLiteral("a".repeat(4096)).length)
    }
}
