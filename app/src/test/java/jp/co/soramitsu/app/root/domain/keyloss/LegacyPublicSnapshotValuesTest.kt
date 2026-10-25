package jp.co.soramitsu.app.root.domain.keyloss

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyPublicSnapshotValuesTest {
    @Test
    fun `both saved identities and a present name remain complete`() {
        assertTrue(hasRequiredLegacyPublicSnapshotValues("Saved name", IDENTITIES))
        assertTrue(hasRequiredLegacyPublicSnapshotValues("", IDENTITIES))
    }

    @Test
    fun `a missing name or either required identity is incomplete`() {
        assertFalse(hasRequiredLegacyPublicSnapshotValues(null, IDENTITIES))
        assertFalse(hasRequiredLegacyPublicSnapshotValues("Saved name", IDENTITIES.take(1)))
        assertFalse(hasRequiredLegacyPublicSnapshotValues("Saved name", IDENTITIES.drop(1)))
        assertFalse(hasRequiredLegacyPublicSnapshotValues("Saved name", emptyList()))
    }

    @Test
    fun `a different identity field cannot stand in for omitted legacy values`() {
        assertFalse(hasRequiredLegacyPublicSnapshotValues("Saved name", listOf("ethereumAddress" to "public")))
        assertFalse(hasRequiredLegacyPublicSnapshotValues("Saved name", IDENTITIES.take(1) + ("unknownPublicKey" to "public")))
    }

    private companion object {
        val IDENTITIES = listOf("address" to "synthetic-address", "publicKey" to "synthetic-public-key")
    }
}
