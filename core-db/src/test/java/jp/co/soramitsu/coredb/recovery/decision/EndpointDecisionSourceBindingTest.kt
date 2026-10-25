package jp.co.soramitsu.coredb.recovery.decision

import jp.co.soramitsu.coredb.recovery.EndpointInventorySource
import jp.co.soramitsu.coredb.recovery.EndpointRecordRef
import org.junit.Assert.*
import org.junit.Test

class EndpointDecisionSourceBindingTest {
    @Test fun exactIndependentCapturesMatchWithoutExposingTheirFingerprint() {
        val ref = EndpointRecordRef(EndpointInventorySource.OWNED, 7, 4)
        val first = EndpointDecisionSourceBinding(ref, "11".repeat(32))
        val second = EndpointDecisionSourceBinding(ref.copy(), "11".repeat(32))
        assertNotSame(first, second)
        assertTrue(first.matches(second))
        assertTrue(second.matches(first))
        assertTrue(first.matches(first))
        assertFalse(first.toString().contains("11".repeat(32)))
    }

    @Test fun changedSourceRowRevisionOrTypedFingerprintNeverMatches() {
        val reference = EndpointDecisionSourceBinding(EndpointRecordRef(EndpointInventorySource.OWNED, 7, 4), "11".repeat(32))
        val changes = listOf(
            EndpointDecisionSourceBinding(EndpointRecordRef(EndpointInventorySource.CURRENT, 7), "11".repeat(32)),
            EndpointDecisionSourceBinding(EndpointRecordRef(EndpointInventorySource.OWNED, 8, 4), "11".repeat(32)),
            EndpointDecisionSourceBinding(EndpointRecordRef(EndpointInventorySource.OWNED, 7, 5), "11".repeat(32)),
            EndpointDecisionSourceBinding(EndpointRecordRef(EndpointInventorySource.OWNED, 7, 4), "22".repeat(32))
        )
        changes.forEach { assertFalse(reference.matches(it)); assertFalse(it.matches(reference)) }
    }
}
