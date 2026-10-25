package jp.co.soramitsu.app.root.domain.keyloss

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyPublicSnapshotColumnsTest {
    @Test
    fun `released five and six column users are known public layouts`() {
        assertTrue(knownLegacyPublicSnapshotColumns("users", IDENTITY))
        assertTrue(knownLegacyPublicSnapshotColumns("users", IDENTITY + "networkType"))
    }

    @Test
    fun `canonical recovery ledger includes its explicit state column`() {
        assertTrue(knownLegacyPublicSnapshotColumns("legacy_users_recovery", IDENTITY + "recoveryState"))
        assertFalse(knownLegacyPublicSnapshotColumns("legacy_users_recovery", IDENTITY))
    }

    @Test
    fun `same column count cannot hide a renamed public identity`() {
        assertFalse(knownLegacyPublicSnapshotColumns("users", IDENTITY - "publicKey" + "unknownPublicKey"))
        assertFalse(knownLegacyPublicSnapshotColumns("legacy_users_recovery", IDENTITY - "publicKey" + "unknownPublicKey" + "recoveryState"))
    }

    @Test
    fun `additional or missing public fields require disclosure`() {
        assertFalse(knownLegacyPublicSnapshotColumns("users", IDENTITY + "unknownPublicKey"))
        assertFalse(knownLegacyPublicSnapshotColumns("users", IDENTITY - "publicKey"))
        assertFalse(knownLegacyPublicSnapshotColumns("users", emptySet()))
    }

    @Test
    fun `legacy network and recovery state columns are not interchangeable`() {
        assertFalse(knownLegacyPublicSnapshotColumns("users", IDENTITY + "recoveryState"))
        assertFalse(knownLegacyPublicSnapshotColumns("legacy_users_recovery", IDENTITY + "networkType"))
        assertFalse(knownLegacyPublicSnapshotColumns("legacy_users_recovery", IDENTITY + setOf("networkType", "recoveryState")))
    }

    @Test
    fun `unknown tables cannot be treated as a supported legacy identity family`() {
        assertFalse(knownLegacyPublicSnapshotColumns("future_users", IDENTITY))
        assertFalse(knownLegacyPublicSnapshotColumns("Users", IDENTITY))
    }

    private companion object {
        val IDENTITY = setOf("address", "username", "publicKey", "cryptoType", "position")
    }
}
