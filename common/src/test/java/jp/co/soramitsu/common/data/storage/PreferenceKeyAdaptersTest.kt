package jp.co.soramitsu.common.data.storage

import android.content.SharedPreferences
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferencesImpl
import jp.co.soramitsu.common.data.storage.encrypt.EncryptionUtil
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecureStorageHealth
import jp.co.soramitsu.testshared.HashMapEncryptedPreferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class PreferenceKeyAdaptersTest {
    @Before fun before() { WalletSecureStorageHealth.resetForTest() }

    @After fun after() { WalletSecureStorageHealth.resetForTest() }

    @Test fun encryptedWrapperDelegatesNamesWithoutDecryptingValues() {
        val preferences = mock<Preferences>()
        val crypto = mock<EncryptionUtil>()
        doAnswer { invocation ->
            (invocation.arguments[2] as (String) -> Unit)("recovery_a")
            null
        }.whenever(preferences).forEachKeyWithPrefixes(eq(setOf("recovery_")), eq(32), any())
        val seen = mutableListOf<String>()
        EncryptedPreferencesImpl(preferences, crypto).forEachKeyWithPrefixes(setOf("recovery_"), 32) { seen += it }
        assertEquals(listOf("recovery_a"), seen)
        verify(preferences, never()).getString(any())
        verifyNoInteractions(crypto)
    }

    @Test fun androidPreferenceWrapperVisitsOnlySnapshotNames() {
        val shared = mock<SharedPreferences>()
        whenever(shared.all).thenReturn(mapOf("recovery_a" to "ciphertext", "unrelated" to 42))
        val seen = mutableListOf<String>()
        PreferencesImpl(shared).forEachKeyWithPrefixes(setOf("recovery_"), 32) { seen += it }
        assertEquals(listOf("recovery_a"), seen)
        verify(shared, never()).getString(any(), any())
        assertThrows(IllegalArgumentException::class.java) {
            PreferencesImpl(shared).forEachKeyWithPrefixes(setOf(""), 32) {}
        }
        verify(shared, times(1)).all
    }

    @Test fun plainFixtureVisitsItsSnapshotDespiteCallbackMutation() {
        val prefs = HashMapEncryptedPreferences().apply {
            putEncryptedString("recovery_a", "a")
            putEncryptedString("recovery_b", "b")
        }
        val seen = mutableListOf<String>()
        prefs.forEachKeyWithPrefixes(setOf("recovery_"), 32) {
            seen += it
            prefs.removeKey(it)
            prefs.putEncryptedString("recovery_new", "c")
        }
        assertEquals(setOf("recovery_a", "recovery_b"), seen.toSet())
        assertEquals("c", prefs.getDecryptedString("recovery_new"))
    }
}
