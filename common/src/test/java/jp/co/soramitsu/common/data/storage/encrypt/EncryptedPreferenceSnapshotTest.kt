package jp.co.soramitsu.common.data.storage.encrypt

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EncryptedPreferenceSnapshotTest {
    @Test fun onlyRealNonemptyPlaintextCanBePresentedAsDecryptedData() {
        val oversized = EncryptedPreferenceSnapshot.encrypted("fearless-oversized-ciphertext-sha256:fixture", "raw fixture", true)
        assertFalse(oversized.hasDecryptedValue)
        assertFalse(EncryptedPreferenceSnapshot.encrypted("", "raw undecryptable fixture", false).hasDecryptedValue)
        assertFalse(EncryptedPreferenceSnapshot.unencrypted("").hasDecryptedValue)
        assertTrue(EncryptedPreferenceSnapshot.encrypted("exact original plaintext", "ciphertext", false).hasDecryptedValue)
    }
}
