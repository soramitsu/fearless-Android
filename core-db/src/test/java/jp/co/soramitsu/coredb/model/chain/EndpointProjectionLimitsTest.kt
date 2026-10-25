package jp.co.soramitsu.coredb.model.chain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class EndpointProjectionLimitsTest {
    @Test fun `aggregate ASCII boundary reserves scalar bytes and rejects one extra byte`() {
        assertEquals(1_048_448L, EndpointProjectionLimits.MAX_TEXT_BYTES)
        val remaining = EndpointProjectionLimits.MAX_TEXT_BYTES.toInt() - 2
        EndpointProjectionLimits.requireTextFits("c", "u".repeat(remaining), "n")
        assertThrows(IllegalArgumentException::class.java) {
            EndpointProjectionLimits.requireTextFits("cc", "u".repeat(remaining), "n")
        }
    }

    @Test fun `separately bounded fields can still exceed the aggregate limit`() {
        val part = "x".repeat(600_000)
        assertThrows(IllegalArgumentException::class.java) { EndpointProjectionLimits.requireTextFits("c", part, part) }
    }

    @Test fun `combining and supplementary characters use exact UTF8 byte lengths`() {
        val suffix = "雪e\u0301\uD83D\uDE00"
        val used = suffix.toByteArray(Charsets.UTF_8).size
        val prefix = "x".repeat(EndpointProjectionLimits.MAX_TEXT_BYTES.toInt() - used)
        EndpointProjectionLimits.requireTextFits("", prefix, suffix)
        assertThrows(IllegalArgumentException::class.java) { EndpointProjectionLimits.requireTextFits("x", prefix, suffix) }
    }

    @Test fun `unpaired surrogates fail before SQLite can replacement encode them`() {
        for (bad in listOf("\uD800", "\uDC00", "\uD800x", "\uDC00\uD800")) {
            assertThrows(IllegalArgumentException::class.java) { EndpointProjectionLimits.requireTextFits("chain", "url", bad) }
        }
        EndpointProjectionLimits.requireTextFits("", "", "")
        EndpointProjectionLimits.requireTextFits("chain", "url", "\uD83D\uDE00")
    }
}
