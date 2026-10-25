package jp.co.soramitsu.common.data.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class PreferenceKeyVisitationTest {
    @Test fun visitsBeyondTheOldCountAndAggregateBounds() {
        var seen = 0
        val keys = (0..9_000).asSequence().map { "recovery_" + it + "x".repeat(200) }.asIterable()
        forEachMatchingPreferenceKey(keys, setOf("recovery_"), 256) { seen++ }
        assertEquals(9_001, seen)
    }

    @Test fun oversizedNamesCannotSuppressFollowingValidNames() {
        val seen = mutableListOf<String>()
        forEachMatchingPreferenceKey(listOf("r" + "x".repeat(1024), "r1", "r" + "界".repeat(8), "other", "r2"), setOf("r"), 16) {
            seen += it
        }
        assertEquals(listOf("r1", "r2"), seen)
    }

    @Test fun overlappingPrefixesNeverDuplicateAName() {
        val seen = mutableListOf<String>()
        forEachMatchingPreferenceKey(listOf("root_a", "root_b"), setOf("r", "root_"), 16) { seen += it }
        assertEquals(listOf("root_a", "root_b"), seen)
    }

    @Test fun visitationDoesNotPrecollectTheInput() {
        var observed = 0
        val keys = sequence {
            yield("r1")
        check(observed == 1)
        yield("r2")
        }.asIterable()
        forEachMatchingPreferenceKey(keys, setOf("r"), 16) { observed++ }
        assertEquals(2, observed)
    }

    @Test fun callbackFailurePropagatesWithoutProcessingLaterNames() {
        val failure = IllegalStateException("sentinel")
        val actual = assertThrows(IllegalStateException::class.java) {
            forEachMatchingPreferenceKey(listOf("r1", "r2"), setOf("r"), 16) { throw failure }
        }
        assertSame(failure, actual)
    }

    @Test fun invalidBoundsAndPrefixesAreRejectedBeforeIteration() {
        val unreadable = Iterable<String> { error("Must not visit input") }
        for (prefixes in listOf(emptySet(), setOf(""), setOf("x".repeat(257)), setOf("界".repeat(86)))) {
            assertThrows(IllegalArgumentException::class.java) { forEachMatchingPreferenceKey(unreadable, prefixes, 16) {} }
        }
        for (limit in listOf(0, 1025)) {
            assertThrows(IllegalArgumentException::class.java) { forEachMatchingPreferenceKey(unreadable, setOf("r"), limit) {} }
        }
    }
}
