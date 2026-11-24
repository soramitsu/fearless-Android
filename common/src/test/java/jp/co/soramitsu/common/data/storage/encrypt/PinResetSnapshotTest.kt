package jp.co.soramitsu.common.data.storage.encrypt

import jp.co.soramitsu.common.data.storage.PinPreferenceSnapshot
import jp.co.soramitsu.testshared.HashMapEncryptedPreferences
import org.junit.Assert.*
import org.junit.Test

class PinResetSnapshotTest {
    @Test fun proofSnapshotDistinguishesStoredTypesAndCopiedSets() {
        val source = mutableSetOf("old")
        val snapshot = PinResetSnapshot(typed = PinPreferenceSnapshot(source))
        source.add("changed")
        assertTrue(snapshot.matches(PinResetSnapshot(typed = PinPreferenceSnapshot(setOf("old")))))
        assertFalse(snapshot.matches(PinResetSnapshot(typed = PinPreferenceSnapshot(source))))
        assertFalse(PinResetSnapshot(typed = PinPreferenceSnapshot(123)).matches(PinResetSnapshot(typed = PinPreferenceSnapshot("123"))))
        assertFalse(snapshot.matches(PinResetSnapshot(unencrypted = EncryptedPreferenceSnapshot.unencrypted("old"))))
        assertFalse(PinResetSnapshot().matches(snapshot))
        assertTrue(PinResetSnapshot().matches(PinResetSnapshot()))
    }

    @Test fun resetRejectsChangedOldPinWithoutArchivingOrOverwritingTheNewValue() {
        val store = HashMapEncryptedPreferences()
        store.putEncryptedString("pin_code", "unreadable-old-record")
        val proof = store.capturePinResetSnapshot()
        store.putEncryptedString("pin_code", "654321")
        assertThrows(WalletSecretConcurrentMutationException::class.java) { store.saveRecoveredPinCodeDurably("123456", proof) }
        assertEquals("654321", store.getDecryptedString("pin_code"))
        assertFalse(store.hasKey(WalletSecretArchive.keyFor("pin_code", 0)))
    }

    @Test fun readablePinCannotBeReplacedEvenByItsExactProofTimeSnapshot() {
        val store = HashMapEncryptedPreferences()
        store.putEncryptedString("pin_code", "123456")
        val proof = store.capturePinResetSnapshot()
        assertThrows(WalletSecretConcurrentMutationException::class.java) { store.saveRecoveredPinCodeDurably("654321", proof) }
        assertEquals("123456", store.getDecryptedString("pin_code"))
    }

    @Test fun resetRetainsExactOldPinAndChangesOnlyTheExpectedRecord() {
        val store = HashMapEncryptedPreferences()
        store.putEncryptedString("pin_code", "unreadable-old-record")
        val proof = store.capturePinResetSnapshot()
        assertTrue(proof.matches(store.capturePinResetSnapshot()))
        store.saveRecoveredPinCodeDurably("123456", proof)
        assertEquals("123456", store.getDecryptedString("pin_code"))
        assertEquals("unreadable-old-record", store.getDecryptedString(WalletSecretArchive.keyFor("pin_code", 0)))
        assertFalse(proof.matches(store.capturePinResetSnapshot()))
    }

    @Test fun aChangedPinWrapInvalidatesAnOtherwiseEqualSnapshot() {
        val base = PinResetSnapshot(typed = PinPreferenceSnapshot(null))
        assertTrue(base.withKeyringPinRecord("wrap-a").matches(base.withKeyringPinRecord("wrap-a")))
        assertFalse(base.withKeyringPinRecord("wrap-a").matches(base.withKeyringPinRecord("wrap-b")))
        assertFalse(base.withKeyringPinRecord("wrap-a").matches(base))
    }

    @Test fun aForgottenReadablePinIsReplacedOnlyAgainstItsExactSnapshot() {
        val store = HashMapEncryptedPreferences()
        store.putEncryptedString("pin_code", "123456")
        val proof = store.capturePinResetSnapshot()
        store.saveForgottenPinCodeDurably("654321", proof)
        assertEquals("654321", store.getDecryptedString("pin_code"))
        assertThrows(WalletSecretConcurrentMutationException::class.java) { store.saveForgottenPinCodeDurably("111111", proof) }
        store.putEncryptedString("pin_code", "unreadable")
        val unreadable = store.capturePinResetSnapshot()
        assertThrows(WalletSecretConcurrentMutationException::class.java) { store.saveForgottenPinCodeDurably("111111", unreadable) }
    }

    @Test fun theLegacyPinAndItsHistoryAreRemovedTogether() {
        val store = HashMapEncryptedPreferences()
        store.putEncryptedString("pin_code", "123456")
        store.putEncryptedString(WalletSecretArchive.keyFor("pin_code", 0), "old")
        store.putEncryptedString(WalletSecretArchive.keyFor("pin_code", 3), "older")
        store.putEncryptedString("7:SUBSTRATE_SECRETS", "kept")
        store.removeLegacyPinDurably()
        assertFalse(store.hasKey("pin_code"))
        assertFalse(store.hasKey(WalletSecretArchive.keyFor("pin_code", 0)))
        assertFalse(store.hasKey(WalletSecretArchive.keyFor("pin_code", 3)))
        assertEquals("kept", store.getDecryptedString("7:SUBSTRATE_SECRETS"))
    }
}
