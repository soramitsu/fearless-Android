package jp.co.soramitsu.common.data.storage.encrypt

import jp.co.soramitsu.testshared.HashMapEncryptedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A payload the upgrade rewrites in canonical form keeps its stored value in
 * the archive. The copy never takes a slot that holds another value, and a
 * retried rewrite reuses the slot that already holds the same value.
 */
class WalletSecretArchiveCopyTest {

    @Test
    fun firstFreeSlotReceivesTheStoredValue() {
        val preferences = HashMapEncryptedPreferences().apply {
            putEncryptedString(WalletSecretArchive.keyFor(SOURCE), "an earlier archive")
        }

        val copy = checkNotNull(preferences.planArchiveCopy(SOURCE, STORED))

        assertEquals(WalletSecretArchive.keyFor(SOURCE, slot = 1), copy.destinationKey)
        assertNull(copy.expectedState)
        assertEquals(STORED, copy.valueToPut)
    }

    @Test
    fun aSlotAlreadyHoldingTheValueIsReusedWithoutAnotherWrite() {
        val preferences = HashMapEncryptedPreferences().apply {
            putEncryptedString(WalletSecretArchive.keyFor(SOURCE), STORED)
        }

        val copy = checkNotNull(preferences.planArchiveCopy(SOURCE, STORED))

        assertEquals(WalletSecretArchive.keyFor(SOURCE), copy.destinationKey)
        assertEquals(STORED, checkNotNull(copy.expectedState).plaintext)
        assertNull(copy.valueToPut)
    }

    @Test
    fun noCopyIsPlannedWhenEverySlotHoldsAnotherValue() {
        val preferences = HashMapEncryptedPreferences().apply {
            repeat(WalletSecretArchive.MAX_SLOTS) { slot ->
                putEncryptedString(WalletSecretArchive.keyFor(SOURCE, slot), "other-$slot")
            }
        }

        assertNull(preferences.planArchiveCopy(SOURCE, STORED))
    }

    private companion object {
        const val SOURCE = "5:ETHEREUM_SECRETS"
        const val STORED = "0x0102"
    }
}
