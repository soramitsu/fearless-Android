package jp.co.soramitsu.walletconnect.impl.data.legacy

import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class PreferencesReownImportAttemptsTest {

    @Test
    fun `attempts are counted per app version and a refund never goes below zero`() {
        val values = mutableMapOf<String, Int>()
        val oldVersion = PreferencesReownImportAttempts(preferences(values), "4.3.2")

        oldVersion.record()
        oldVersion.record()
        oldVersion.refund()
        assertEquals(1, PreferencesReownImportAttempts(preferences(values), "4.3.2").count())

        val newVersion = PreferencesReownImportAttempts(preferences(values), "4.3.3")
        assertEquals(0, newVersion.count())
        newVersion.refund()
        assertEquals(0, newVersion.count())
        assertEquals(setOf(PreferencesReownImportAttempts.ATTEMPTS_PREFIX + "4.3.2", PreferencesReownImportAttempts.ATTEMPTS_PREFIX + "4.3.3"), values.keys)
    }

    private fun preferences(values: MutableMap<String, Int>): SharedPreferences {
        val editor = mockk<SharedPreferences.Editor>()
        val pending = mutableMapOf<String, Int>()
        every { editor.putInt(any(), any()) } answers {
            pending[firstArg()] = secondArg()
            editor
        }
        every { editor.commit() } answers {
            values.putAll(pending)
            pending.clear()
            true
        }
        return mockk {
            every { getInt(any(), any()) } answers { values[firstArg()] ?: secondArg() }
            every { edit() } returns editor
        }
    }
}
