package jp.co.soramitsu.common.data.storage.encrypt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Archived wallet ciphertext is protected payload: while any exists the
 * device-wide master key can never be regenerated over it, and an archived
 * legacy CBC payload still authenticates the existing key by the schema of
 * the key it was archived from.
 */
class WalletSecretArchiveAttestationTest {

    @Test
    fun archiveKeysAreProtectedAuthenticationCandidates() {
        listOf(
            WalletSecretArchive.keyFor("5:ACCESS_SECRETS"),
            WalletSecretArchive.keyFor("5:TON_SECRETS", slot = 3),
            WalletSecretArchive.keyFor("legacy_v1_meta_5_public_ab01")
        ).forEach { key ->
            assertTrue(key, WalletMasterKeyAttestation.isProtectedPayloadKey(key))
            assertTrue(key, WalletMasterKeyAttestation.isAuthenticationCandidateKey(key))
        }
    }

    @Test
    fun archiveKeysAreValidatedWithTheSchemaOfTheirSourceKey() {
        assertEquals(
            "5:ACCESS_SECRETS",
            WalletMasterKeyAttestation.semanticValidationKey(
                WalletSecretArchive.keyFor("5:ACCESS_SECRETS")
            )
        )
        assertEquals(
            "5:TON_SECRETS",
            WalletMasterKeyAttestation.semanticValidationKey(
                WalletSecretArchive.keyFor("5:TON_SECRETS", slot = 3)
            )
        )
        assertEquals(
            "legacy_v1_meta_5_public_ab01",
            WalletMasterKeyAttestation.semanticValidationKey(
                WalletSecretArchive.keyFor("legacy_v1_meta_5_public_ab01")
            )
        )
        assertNull(
            WalletMasterKeyAttestation.semanticValidationKey(
                WalletSecretArchive.keyFor("unrelated_preference")
            )
        )
    }

    @Test
    fun archiveNamesAreDistinctFromQuarantineAndActiveNamespaces() {
        val archiveKey = WalletSecretArchive.keyFor("5:ACCESS_SECRETS")
        assertFalse(archiveKey.startsWith(WalletSecretQuarantine.KEY_PREFIX))
        assertFalse(archiveKey.first().isDigit())
        assertNull(WalletSecretArchive.sourceKeyOf("5:ACCESS_SECRETS"))
    }
}
