package jp.co.soramitsu.common.data.secrets.v1

import jp.co.soramitsu.testshared.HashMapEncryptedPreferences
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SecretStoreV1Test {

    @Test
    fun `hostile compact length is rejected before allocating`() {
        val preferences = HashMapEncryptedPreferences()
        val activeKey = "security_source_$ADDRESS"
        val hostile = "0xffffffff01020304"
        preferences.putEncryptedString(activeKey, hostile)

        assertThrows(LegacyV1SecretCorruptionException::class.java) {
            runBlocking {
                SecretStoreV1Impl(preferences).getSecuritySource(ADDRESS)
            }
        }

        assertEquals(hostile, preferences.getDecryptedString(activeKey))
    }

    @Test
    fun `oversized plaintext is rejected without mutating the active record`() {
        val preferences = HashMapEncryptedPreferences()
        val activeKey = "security_source_$ADDRESS"
        val oversized = "0x" + "00".repeat(524_288)
        preferences.putEncryptedString(activeKey, oversized)

        assertThrows(LegacyV1SecretCorruptionException::class.java) {
            runBlocking {
                SecretStoreV1Impl(preferences).getSecuritySource(ADDRESS)
            }
        }

        assertEquals(oversized, preferences.getDecryptedString(activeKey))
    }

    private companion object {
        const val ADDRESS =
            "5FHneW46xGXgs5mUiveU4sbTyGBzmstZuZpZ9EznVVQMbZ8"
    }
}
