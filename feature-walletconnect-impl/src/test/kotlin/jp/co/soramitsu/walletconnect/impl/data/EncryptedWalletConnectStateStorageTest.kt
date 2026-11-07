package jp.co.soramitsu.walletconnect.impl.data

import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.common.data.storage.encrypt.EncryptionUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class EncryptedWalletConnectStateStorageTest {
    private val values = mutableMapOf<String, String>()
    private var commits = true
    private val preferences = fakePreferences()
    private val cipher = object : WalletConnectStateCipher {
        override fun seal(plaintext: String) = "sealed:" + plaintext.reversed()

        override fun open(sealed: String) = sealed.removePrefix("sealed:").takeIf { sealed.startsWith("sealed:") }?.reversed()
    }
    private val storage = EncryptedWalletConnectStateStorage(preferences, cipher)

    @Test
    fun `state is written sealed and read back`() {
        storage.write("""{"secret":"key"}""")

        val stored = values.getValue(EncryptedWalletConnectStateStorage.KEY_STATE)
        assertFalse(stored.contains("secret"))
        assertEquals("""{"secret":"key"}""", storage.read())
    }

    @Test
    fun `nothing stored reads as null and a corrupt value too`() {
        assertNull(storage.read())
        values[EncryptedWalletConnectStateStorage.KEY_STATE] = "garbage"
        assertNull(storage.read())
    }

    @Test
    fun `clear removes the value`() {
        storage.write("x")
        storage.clear()

        assertTrue(values.isEmpty())
    }

    @Test
    fun `a write that is not committed fails`() {
        commits = false

        assertThrows(IllegalStateException::class.java) { storage.write("x") }
    }

    @Test
    fun `the app key cipher refuses what the encryption util could not seal`() {
        val util = mockk<EncryptionUtil> {
            every { encrypt(any<String>()) } returns ""
            every { isModernCiphertext("") } returns false
        }

        assertThrows(IllegalStateException::class.java) { EncryptionUtilStateCipher(util).seal("x") }
    }

    @Test
    fun `the app key cipher seals with the encryption util and reads corrupt values as null`() {
        val util = mockk<EncryptionUtil> {
            every { encrypt("plain") } returns "v2:abc"
            every { isModernCiphertext("v2:abc") } returns true
            every { decrypt("v2:abc") } returns "plain"
            every { decrypt("v2:bad") } returns ""
        }
        val appCipher = EncryptionUtilStateCipher(util)

        assertEquals("v2:abc", appCipher.seal("plain"))
        assertEquals("plain", appCipher.open("v2:abc"))
        assertNull(appCipher.open("v2:bad"))
    }

    private fun fakePreferences(): SharedPreferences {
        val editor = mockk<SharedPreferences.Editor>()
        val pending = mutableMapOf<String, String?>()
        every { editor.putString(any(), any()) } answers {
            pending[firstArg()] = secondArg()
            editor
        }
        every { editor.remove(any()) } answers {
            pending[firstArg()] = null
            editor
        }
        every { editor.commit() } answers {
            if (commits) {
                pending.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
            }
            pending.clear()
            commits
        }
        return mockk {
            every { getString(any(), any()) } answers { values[firstArg()] ?: secondArg() }
            every { edit() } returns editor
        }
    }
}
