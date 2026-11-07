package jp.co.soramitsu.walletconnect.impl.data.legacy

import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreferencesReownImportLedgerTest {
    private val values = mutableMapOf<String, Any>()

    @Test
    fun `the ledger remembers completion and counts failed attempts across instances`() {
        val first = PreferencesReownImportLedger(preferences())
        assertFalse(first.isDone)
        assertEquals(0, first.failedAttempts)

        first.recordFailedAttempt()
        first.recordFailedAttempt()
        assertFalse(first.keepsLegacyFiles)
        first.markDone(keepLegacyFiles = true)

        val second = PreferencesReownImportLedger(preferences())
        assertTrue(second.isDone)
        assertTrue(second.keepsLegacyFiles)
        assertEquals(2, second.failedAttempts)
        assertEquals(
            setOf(PreferencesReownImportLedger.DONE, PreferencesReownImportLedger.KEEP_LEGACY_FILES, PreferencesReownImportLedger.FAILED_ATTEMPTS),
            values.keys
        )
    }

    private fun preferences(): SharedPreferences {
        val editor = mockk<SharedPreferences.Editor>()
        val pending = mutableMapOf<String, Any>()
        every { editor.putBoolean(any(), any()) } answers {
            pending[firstArg()] = secondArg<Boolean>()
            editor
        }
        every { editor.putInt(any(), any()) } answers {
            pending[firstArg()] = secondArg<Int>()
            editor
        }
        every { editor.commit() } answers {
            values.putAll(pending)
            pending.clear()
            true
        }
        return mockk {
            every { getBoolean(any(), any()) } answers { values[firstArg()] as? Boolean ?: secondArg() }
            every { getInt(any(), any()) } answers { values[firstArg()] as? Int ?: secondArg() }
            every { edit() } returns editor
        }
    }
}
