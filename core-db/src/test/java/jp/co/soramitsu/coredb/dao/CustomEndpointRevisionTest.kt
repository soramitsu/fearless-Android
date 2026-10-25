package jp.co.soramitsu.coredb.dao

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CustomEndpointRevisionTest {
    @Test fun `positive revisions advance exactly once including the last representable revision`() {
        assertEquals(2L, nextCustomEndpointRevision(1))
        assertEquals(Long.MAX_VALUE, nextCustomEndpointRevision(Long.MAX_VALUE - 1))
    }

    @Test fun `exhausted and invalid revisions reject rather than wrap or normalize`() {
        for (revision in listOf(Long.MIN_VALUE, -1L, 0L, Long.MAX_VALUE)) {
            assertThrows(IllegalStateException::class.java) { nextCustomEndpointRevision(revision) }
        }
    }
}
