package jp.co.soramitsu.common.data.storage.encrypt

import android.content.SharedPreferences
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*

class WalletWrappedKeySnapshotTest {
    @Test
    fun `fingerprint distinguishes exact types and is independent of set order`() {
        val values = listOf(null, "7", 7, 7L, 7f, true, setOf("7"))
        assertEquals(values.size, values.map { WalletWrappedKeySnapshot(it).fingerprint() }.toSet().size)
        assertEquals(WalletWrappedKeySnapshot(linkedSetOf("a", "b")).fingerprint(),
            WalletWrappedKeySnapshot(linkedSetOf("b", "a")).fingerprint())
        assertNotEquals(WalletWrappedKeySnapshot("\uD800").fingerprint(), WalletWrappedKeySnapshot("\uD801").fingerprint())
        assertNotEquals(WalletWrappedKeySnapshot(0f).fingerprint(), WalletWrappedKeySnapshot(-0f).fingerprint())
    }

    @Test
    fun `snapshot retains original typed value and does not expose it`() {
        val editor = mock(SharedPreferences.Editor::class.java)
        val values = listOf<Any?>(null, "wrapped", 7, 7L, 7f, true, setOf("wrapped"))
        values.forEach {
            val snapshot = WalletWrappedKeySnapshot(it)
            assertEquals(it != null, snapshot.exists())
            assertTrue(snapshot.matches(it))
            assertFalse(snapshot.toString().contains("wrapped"))
            snapshot.retain(editor, "archive")
        }
        verify(editor).putString("archive", "wrapped")
        verify(editor).putInt("archive", 7)
        verify(editor).putLong("archive", 7L)
        verify(editor).putFloat("archive", 7f)
        verify(editor).putBoolean("archive", true)
        verify(editor).putStringSet("archive", setOf("wrapped"))
        verifyNoMoreInteractions(editor)
        assertFalse(WalletWrappedKeySnapshot(0f).matches(-0f))
        val mutable = mutableSetOf("old")
        val snapshot = WalletWrappedKeySnapshot(mutable)
        mutable.add("new")
        assertTrue(snapshot.matches(setOf("old")))
    }
}
