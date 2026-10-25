package jp.co.soramitsu.coredb.recovery.decision

import org.junit.Assert.*
import org.junit.Test

/** Canonical field framing must distinguish equal unframed byte concatenations. */
class EndpointDecisionDigestTest {
    @Test fun emptyDigestUsesSha256() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", EndpointDecisionDigest().finish())
    }

    @Test fun textFieldBoundariesCannotCollideByConcatenation() {
        val first = EndpointDecisionDigest().apply { text("ab"); text("c") }.finish()
        val second = EndpointDecisionDigest().apply { text("a"); text("bc") }.finish()
        assertNotEquals(first, second)
        assertEquals(first, EndpointDecisionDigest().apply { text("ab"); text("c") }.finish())
    }

    @Test fun signedIntegerExtremesAndFieldOrderRemainDistinct() {
        val values = listOf(Long.MIN_VALUE, -1L, 0L, 1L, Long.MAX_VALUE)
        assertEquals(values.size, values.map { EndpointDecisionDigest().apply { integer(it) }.finish() }.toSet().size)
        assertNotEquals(
            EndpointDecisionDigest().apply { integer(1); integer(2) }.finish(),
            EndpointDecisionDigest().apply { integer(2); integer(1) }.finish()
        )
    }

    @Test fun payloadHashDoesNotDependOnInternalChunkBoundaries() {
        val payload = byteArrayOf(0, 127, -128, -1, 65)
        val whole = EndpointDecisionDigest().apply { integer(payload.size.toLong()); bytes(payload) }.finish()
        val parts = EndpointDecisionDigest().apply {
            integer(payload.size.toLong()); bytes(payload.copyOfRange(0, 2)); bytes(payload.copyOfRange(2, 5))
        }.finish()
        assertEquals(whole, parts)
        assertNotEquals(whole, EndpointDecisionDigest().apply { integer(6); bytes(payload) }.finish())
    }
}
