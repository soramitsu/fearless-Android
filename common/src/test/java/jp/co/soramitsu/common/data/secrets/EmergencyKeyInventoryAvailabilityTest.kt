package jp.co.soramitsu.common.data.secrets

import android.content.SharedPreferences
import jp.co.soramitsu.common.data.storage.PreferencesImpl
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferencesImpl
import jp.co.soramitsu.common.data.storage.encrypt.EncryptionUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class EmergencyKeyInventoryAvailabilityTest {
    private class Fixture(noise: List<String>) {
        val values = linkedMapOf<String, String>().apply {
            noise.forEach { put(it, "public unrelated value") }
            put(KEY, CIPHERTEXT)
        }
        private val before = values.toMap()
        private val shared = mock(SharedPreferences::class.java)
        private val encryption = mock(EncryptionUtil::class.java)
        val reader: WalletEmergencyRecoveryReader

        init {
            require(noise.size == noise.toSet().size)
            whenever(shared.all).thenAnswer { LinkedHashMap(values) }
            whenever(shared.getString(any(), anyOrNull())).thenAnswer { invocation ->
                values[invocation.getArgument<String>(0)]
            }
            whenever(encryption.decrypt(CIPHERTEXT)).thenReturn(PLAINTEXT)
            reader = WalletEmergencyRecoveryReader(
                EncryptedPreferencesImpl(PreferencesImpl(shared), encryption)
            )
        }

        fun assertHealthyRecovery() {
            try {
                assertEquals(1, reader.storedKeyCount())
                val entry = reader.read().single()
                assertEquals(KEY, entry.storageKey)
                assertEquals(42L, entry.walletId)
                assertEquals(EmergencyRecoveryState.ACTIVE, entry.state)
                assertEquals(true, entry.readable)
                assertEquals(listOf(EmergencyRecoveryField(EmergencyRecoveryFieldType.STORED_DATA, PLAINTEXT)), entry.fields)
                verify(encryption).decrypt(CIPHERTEXT)
            } finally {
                assertUnchanged()
            }
        }

        fun assertUnchanged() {
            assertEquals(before, values)
            verify(shared, never()).edit()
        }
    }

    @Test
    fun countBoundaryControlStillFindsHealthyKey() {
        Fixture(shortNoise(8_191)).assertHealthyRecovery()
    }

    @Test
    fun unrelatedMatchingNamesBeyondCountBoundDoNotHideHealthyKey() {
        Fixture(shortNoise(8_192)).assertHealthyRecovery()
    }

    @Test
    fun byteBoundaryControlStillFindsHealthyKey() {
        Fixture(wideNoise(1_023)).assertHealthyRecovery()
    }

    @Test
    fun unrelatedMatchingNamesBeyondByteBoundDoNotHideHealthyKey() {
        Fixture(wideNoise(1_024)).assertHealthyRecovery()
    }

    @Test
    fun directReadCannotBeBlockedByUnrelatedCountWithoutCountPreview() {
        val fixture = Fixture(shortNoise(8_192))
        try {
            assertEquals(KEY, fixture.reader.read().single().storageKey)
        } finally {
            fixture.assertUnchanged()
        }
    }

    @Test
    fun recoverableKeyCountBoundRemainsExplicit() {
        val fixture = Fixture((1L..8_193L).map { "$it:SUBSTRATE_SECRETS" })
        try {
            assertThrows(IllegalStateException::class.java) { fixture.reader.storedKeyCount() }
            assertThrows(IllegalStateException::class.java) { fixture.reader.read() }
        } finally {
            fixture.assertUnchanged()
        }
    }

    @Test
    fun recoverableTotalKeyByteBoundRemainsExplicit() {
        val prefix = "42:"
        val suffix = ":ACCESS_SECRETS"
        val names = (0 until 1_024).map { index ->
            prefix + index.toString(16).padStart(1_024 - prefix.length - suffix.length, '0') + suffix
        }
        val fixture = Fixture(names)
        try {
            assertThrows(IllegalStateException::class.java) { fixture.reader.storedKeyCount() }
            assertThrows(IllegalStateException::class.java) { fixture.reader.read() }
        } finally {
            fixture.assertUnchanged()
        }
    }

    @Test
    fun oversizedUnrelatedNameIsSkippedWithoutChangingStoredBytes() {
        Fixture(listOf("1" + "x".repeat(1_024))).assertHealthyRecovery()
    }

    private fun shortNoise(count: Int) = (0 until count).map { "1-unrelated-$it" }

    private fun wideNoise(count: Int) = (0 until count).map { index ->
        val prefix = "1-unrelated-${index.toString().padStart(5, '0')}"
        prefix + "x".repeat(1_024 - prefix.length)
    }

    private companion object {
        const val KEY = "42:SUBSTRATE_SECRETS"
        const val CIPHERTEXT = "public mocked ciphertext"
        const val PLAINTEXT = "public undecodable stored recovery fixture"
    }
}
