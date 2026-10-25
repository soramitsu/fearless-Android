package jp.co.soramitsu.common.data.storage.encrypt

import android.content.SharedPreferences
import jp.co.soramitsu.common.data.storage.PinPreferenceSnapshot
import jp.co.soramitsu.common.data.storage.PreferencesImpl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class PinPreferencePersistenceTest {
    private val values = mutableMapOf<String, Any>()
    private val shared = mock<SharedPreferences>()
    private val editor = mock<SharedPreferences.Editor>()
    private val crypto = mock<EncryptionUtil>()
    private val preferences = PreferencesImpl(shared)
    private val storage = EncryptedPreferencesImpl(preferences, crypto)
    private var commitSucceeds = true
    private var corruptReadback = false

    @Before
    fun setUp() {
        WalletSecureStorageHealth.resetForTest()
        val staged = mutableMapOf<String, Any>()
        whenever(shared.all).thenAnswer { values.toMap() }
        whenever(shared.getString(any(), anyOrNull())).thenAnswer { values[it.getArgument<String>(0)] as String? }
        whenever(shared.edit()).thenReturn(editor)
        whenever(editor.putString(any(), anyOrNull())).thenAnswer {
            staged[it.getArgument(0)] = it.getArgument<String>(1)
            editor
        }
        whenever(editor.putInt(any(), any())).thenAnswer { staged[it.getArgument(0)] = it.getArgument<Int>(1); editor }
        whenever(editor.putLong(any(), any())).thenAnswer { staged[it.getArgument(0)] = it.getArgument<Long>(1); editor }
        whenever(editor.putFloat(any(), any())).thenAnswer { staged[it.getArgument(0)] = it.getArgument<Float>(1); editor }
        whenever(editor.putBoolean(any(), any())).thenAnswer { staged[it.getArgument(0)] = it.getArgument<Boolean>(1); editor }
        whenever(editor.putStringSet(any(), anyOrNull())).thenAnswer { staged[it.getArgument(0)] = it.getArgument<Set<String>>(1); editor }
        whenever(editor.commit()).thenAnswer {
            // Like Android, a failed disk write may already have changed memory.
            values.putAll(staged)
            staged.clear()
            if (corruptReadback) values[PIN] = "different ciphertext"
            commitSucceeds
        }
        whenever(crypto.encrypt(any())).thenAnswer { "v2:test:" + it.getArgument<String>(0) }
        whenever(crypto.isModernCiphertext(anyOrNull())).thenAnswer { it.getArgument<String?>(0)?.startsWith("v2:test:") == true }
        whenever(crypto.decrypt(any())).thenAnswer {
            it.getArgument<String>(0).takeIf { it.startsWith("v2:test:") }?.removePrefix("v2:test:").orEmpty()
        }
        whenever(crypto.assessPinHealth()).thenAnswer {
            val snapshot = preferences.pinPreferenceSnapshot()
            PinHealth.assess(snapshot.exists, snapshot.ciphertext?.let(crypto::decrypt))
        }
    }

    @After
    fun tearDown() {
        WalletSecureStorageHealth.resetForTest()
        WalletPinHealth.replace(null)
    }

    @Test
    fun `typed snapshots preserve representation and cannot inspect other preference keys`() {
        val originalSet = mutableSetOf("a", "b")
        values[PIN] = originalSet
        val snapshot = preferences.pinPreferenceSnapshot()
        originalSet += "c"
        assertTrue(snapshot.exists)
        assertEquals(null, snapshot.ciphertext)
        assertTrue(snapshot.matches(PinPreferenceSnapshot(setOf("a", "b"))))
        assertFalse(snapshot.matches(preferences.pinPreferenceSnapshot()))
        assertFalse(PinPreferenceSnapshot(1).matches(PinPreferenceSnapshot(1L)))
        assertThrows(IllegalArgumentException::class.java) { preferences.pinPreferenceSnapshot("1:SUBSTRATE_SECRETS") }
    }

    @Test
    fun `PIN health uses authenticated effective health even when the raw PIN is absent`() {
        whenever(crypto.assessPinHealth()).thenReturn(PinHealth.UNREADABLE, PinHealth.HEALTHY, PinHealth.ABSENT)
        assertFalse(values.containsKey(PIN))
        assertEquals(PinHealth.UNREADABLE, storage.getPinHealth())
        assertEquals(PinHealth.HEALTHY, storage.getPinHealth())
        assertEquals(PinHealth.ABSENT, storage.getPinHealth())
        whenever(crypto.assessPinHealth()).thenThrow(WalletSecureStorageUnavailableException("provider unavailable"))
        assertThrows(WalletSecureStorageUnavailableException::class.java) { storage.getPinHealth() }
    }

    @Test
    fun `reset retains all typed damaged values exactly and healthy changes retain no extra copies`() {
        val damaged: List<Any> = listOf("bad ciphertext", 42, 53L, 1.25f, false, setOf("unchanged"))
        damaged.forEachIndexed { slot, original ->
            values[PIN] = original
            storage.savePinCodeDurably("123456")
            assertEquals(original, values[WalletSecretArchive.keyFor(PIN, slot)])
            assertEquals("v2:test:123456", values[PIN])
        }
        val archive = values.filterKeys { it != PIN }
        storage.savePinCodeDurably("654321")
        assertEquals(archive, values.filterKeys { it != PIN })
        assertEquals(PinHealth.HEALTHY, WalletPinHealth.health.value)
    }

    @Test
    fun `changed PIN or conflicting archive rejects the entire write`() {
        values[PIN] = 42
        val snapshot = preferences.pinPreferenceSnapshot()
        values[PIN] = 43
        assertFalse(preferences.replacePinDurably(snapshot, "new", WalletSecretArchive.keyFor(PIN)))
        values[PIN] = 42
        values[WalletSecretArchive.keyFor(PIN)] = 99
        val before = values.toMap()
        assertFalse(preferences.replacePinDurably(snapshot, "new", WalletSecretArchive.keyFor(PIN)))
        assertEquals(before, values)
    }

    @Test
    fun `full archives and invalid replacement PIN do not write`() {
        values[PIN] = "bad ciphertext"
        repeat(WalletSecretArchive.MAX_SLOTS) { values[WalletSecretArchive.keyFor(PIN, it)] = it }
        val before = values.toMap()
        assertThrows(WalletRecoveryStateIntegrityException::class.java) { storage.savePinCodeDurably("123456") }
        assertThrows(IllegalArgumentException::class.java) { storage.savePinCodeDurably("12345") }
        assertEquals(before, values)
        assertTrue(WalletSecureStorageHealth.isHealthy())
    }

    @Test
    fun `ambiguous commit or mismatched readback latches process restart`() {
        for (failCommit in listOf(true, false)) {
            WalletSecureStorageHealth.resetForTest()
            values[PIN] = 42
            commitSucceeds = !failCommit
            corruptReadback = !failCommit
            val failure = assertThrows(WalletSecureStorageUnavailableException::class.java) {
                storage.savePinCodeDurably("123456")
            }
            assertEquals(WalletSecureStorageFailureKind.PROCESS_RESTART_REQUIRED, failure.kind)
            assertFalse(WalletSecureStorageHealth.isHealthy())
            assertEquals(42, values[WalletSecretArchive.keyFor(PIN)])
        }
    }

    private companion object { const val PIN = "pin_code" }
}
