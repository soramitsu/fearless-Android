package jp.co.soramitsu.coredb.recovery.decision

import org.junit.Assert.*
import org.junit.Test

/** Offline intent has exact UTF-8 bounds and never acquires network authority. */
class EndpointDecisionLimitsTest {
    @Test fun asciiBoundaryAndNextByteAreDistinct() {
        EndpointDecisionLimits.requireText("a".repeat(512), 512)
        assertThrows(IllegalArgumentException::class.java) {
            EndpointDecisionLimits.requireText("a".repeat(513), 512)
        }
    }

    @Test fun unicodeLimitsCountBytesWithoutReplacement() {
        for ((character, byteCount) in listOf("é" to 2, "界" to 3, "😀" to 4)) {
            EndpointDecisionLimits.requireText(character, byteCount)
            assertThrows(IllegalArgumentException::class.java) {
                EndpointDecisionLimits.requireText(character, byteCount - 1)
            }
        }
    }

    @Test fun malformedUtf16CannotBecomeReplacementCharacters() {
        for (value in listOf("\uD800", "\uDC00", "\uD800x", "\uD800\uD800", "x\uDC00")) {
            assertThrows(IllegalArgumentException::class.java) {
                EndpointDecisionLimits.requireText(value, 512)
            }
        }
    }

    @Test fun emptyLabelIsPermittedButEmptyUrlIsRejected() {
        val candidate = EndpointDecisionCandidate("", "wss://example.invalid")
        assertEquals("", candidate.name)
        assertThrows(IllegalArgumentException::class.java) { EndpointDecisionCandidate("name", "") }
    }

    @Test fun candidateUsesItsSeparateExactLabelAndUrlLimits() {
        val label = "😀".repeat(128)
        val url = "é".repeat(4096)
        val candidate = EndpointDecisionCandidate(label, url)
        assertEquals(label, candidate.name)
        assertEquals(url, candidate.url)
        assertThrows(IllegalArgumentException::class.java) { EndpointDecisionCandidate(label + "a", url) }
        assertThrows(IllegalArgumentException::class.java) { EndpointDecisionCandidate(label, url + "a") }
    }

    @Test fun candidateDoesNotNormalizeOrDiscloseUserText() {
        val name = "private label"
        val url = " wss://user:private@example.invalid/A%2fb?secret=x#fragment "
        val candidate = EndpointDecisionCandidate(name, url)
        assertEquals(name, candidate.name)
        assertEquals(url, candidate.url)
        assertEquals("EndpointDecisionCandidate", candidate.toString())
    }

    @Test fun sourceDigestRequiresExactlySixtyFourLowercaseHexCharacters() {
        EndpointDecisionLimits.requireDigest("0123456789abcdef".repeat(4))
        for (digest in listOf("", "a".repeat(63), "a".repeat(65), "A".repeat(64), "g".repeat(64))) {
            assertThrows(IllegalArgumentException::class.java) { EndpointDecisionLimits.requireDigest(digest) }
        }
    }

    @Test fun revisionIncrementCannotWrapOrAdmitNonpositiveValues() {
        assertEquals(2L, EndpointDecisionLimits.nextRevision(1))
        assertEquals(Long.MAX_VALUE, EndpointDecisionLimits.nextRevision(Long.MAX_VALUE - 1))
        for (revision in listOf(Long.MIN_VALUE, -1L, 0L, Long.MAX_VALUE)) {
            assertThrows(IllegalStateException::class.java) { EndpointDecisionLimits.nextRevision(revision) }
        }
    }
}
