package jp.co.soramitsu.common.data.secrets

import android.content.SharedPreferences
import jp.co.soramitsu.common.data.storage.PreferencesImpl
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferencesImpl
import jp.co.soramitsu.common.data.storage.encrypt.EncryptionUtil
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretArchive
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretQuarantine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class EmergencyRecoveryPagingTest {
    private class Fixture(keys: List<String>) {
        val values = linkedMapOf<String, String>().apply {
            keys.forEach { put(it, "public ciphertext $it") }
        }
        val decrypted = mutableListOf<String>()
        val shared = mock(SharedPreferences::class.java)
        val encryption = mock(EncryptionUtil::class.java)
        val reader: WalletEmergencyRecoveryReader

        init {
            require(keys.toSet().size == keys.size)
            whenever(shared.all).thenAnswer { LinkedHashMap(values) }
            whenever(shared.getString(any(), anyOrNull())).thenAnswer {
                values[it.getArgument<String>(0)]
            }
            whenever(encryption.decrypt(any())).thenAnswer {
                val raw = it.getArgument<String>(0)
                decrypted += raw
                "public exact undecodable plaintext: $raw"
            }
            reader = WalletEmergencyRecoveryReader(EncryptedPreferencesImpl(PreferencesImpl(shared), encryption))
        }

        fun assertReadOnly(expected: Map<String, String> = values.toMap()) {
            assertEquals(expected, values)
            verify(shared, never()).edit()
        }

        fun assertNoDecryption() {
            assertTrue(decrypted.isEmpty())
            verify(encryption, never()).decrypt(any())
        }
    }

    @Test
    fun everyRecognizedKeyBeyondBulkCountLimitIsReachableWithoutDecrypting() = runBlocking {
        val keys = (1..8_193).map { "$it:SUBSTRATE_SECRETS" }
        val fixture = Fixture(keys.reversed())
        val before = fixture.values.toMap()
        assertThrows(IllegalStateException::class.java) { fixture.reader.storedKeyCount() }
        assertThrows(IllegalStateException::class.java) { fixture.reader.read() }
        val observed = mutableListOf<String>()
        var cursor: String? = null
        var pages = 0
        do {
            val page = fixture.reader.listPage(cursor)
            assertTrue(page.keys.size <= 64)
            observed += page.keys.map { it.storageKey }
            cursor = page.nextStorageKey
            pages++
            assertTrue(pages <= 129)
        } while (cursor != null)
        assertEquals(129, pages)
        assertEquals(keys, observed)
        fixture.assertNoDecryption()
        fixture.assertReadOnly(before)
    }

    @Test
    fun everyRecognizedKeyBeyondBulkByteLimitIsReachable() = runBlocking {
        val keys = (0 until 1_025).map {
            "42:" + it.toString(16).padStart(1_024 - 3 - ":ACCESS_SECRETS".length, '0') + ":ACCESS_SECRETS"
        }
        val fixture = Fixture(keys.reversed())
        val before = fixture.values.toMap()
        assertThrows(IllegalStateException::class.java) { fixture.reader.storedKeyCount() }
        val observed = mutableListOf<String>()
        var cursor: String? = null
        do {
            val page = fixture.reader.listPage(cursor)
            assertTrue(page.keys.size <= 64)
            assertTrue(page.keys.all { it.storageKey.toByteArray().size == 1_024 })
            observed += page.keys.map { it.storageKey }
            cursor = page.nextStorageKey
        } while (cursor != null)
        assertEquals(keys, observed)
        fixture.assertNoDecryption()
        fixture.assertReadOnly(before)
    }

    @Test
    fun metadataKeepsWalletStateKindAndOrderingAcrossSingleEntryPages() = runBlocking {
        val active = "42:SUBSTRATE_SECRETS"
        val ethereum = "42:ETHEREUM_SECRETS"
        val setAside = WalletSecretQuarantine.KEY_PREFIX + active
        val archive = WalletSecretArchive.keyFor(active)
        val expected = listOf("2:TON_SECRETS", active, ethereum, setAside, archive, "security_source_public")
        val fixture = Fixture(expected.reversed())
        val pages = mutableListOf<EmergencyRecoveryKeyReference>()
        var cursor: String? = null
        do {
            val page = fixture.reader.listPage(cursor, 1)
            assertEquals(1, page.keys.size)
            pages += page.keys
            cursor = page.nextStorageKey
        } while (cursor != null)
        assertEquals(expected, pages.map { it.storageKey })
        assertEquals(listOf(2L, 42L, 42L, 42L, 42L, null), pages.map { it.walletId })
        assertEquals(EmergencyRecoveryState.SET_ASIDE, pages[3].state)
        assertEquals(EmergencyRecoveryState.ARCHIVED, pages[4].state)
        assertEquals(EmergencyRecoveryKeyKind.ETHEREUM, pages[2].kind)
        fixture.assertNoDecryption()
        fixture.assertReadOnly()
    }

    @Test
    fun emptyAndUnrecognizedInventoriesReturnAnActualEmptyPage() = runBlocking {
        for (keys in listOf(emptyList(), listOf("other", "42:not-a-wallet-key"))) {
            val fixture = Fixture(keys)
            assertEquals(emptyList<EmergencyRecoveryKeyReference>(), fixture.reader.listPage().keys)
            assertNull(fixture.reader.listPage().nextStorageKey)
            assertFalse(fixture.reader.hasStoredKeys())
            fixture.assertNoDecryption()
            fixture.assertReadOnly()
        }
    }

    @Test
    fun namesOnlyPresenceDoesNotApplyBulkInventoryCaps() = runBlocking {
        val fixture = Fixture((1..8_193).map { "$it:SUBSTRATE_SECRETS" })
        assertTrue(fixture.reader.hasStoredKeys())
        fixture.assertNoDecryption()
        fixture.assertReadOnly()
    }

    @Test
    fun explicitSelectionReadsOnlyCurrentSelectedBytes() = runBlocking {
        val fixture = Fixture(listOf("1:SUBSTRATE_SECRETS", "2:SUBSTRATE_SECRETS"))
        val selected = fixture.reader.listPage().keys[1].storageKey
        fixture.assertNoDecryption()
        fixture.values[selected] = "public changed selected ciphertext"
        val expected = fixture.values.toMap()
        val entry = fixture.reader.readKey(selected)
        assertEquals(selected, entry.storageKey)
        assertEquals(listOf("public changed selected ciphertext"), fixture.decrypted)
        assertEquals(listOf(EmergencyRecoveryField(EmergencyRecoveryFieldType.STORED_DATA,
            "public exact undecodable plaintext: public changed selected ciphertext")), entry.fields)
        fixture.assertReadOnly(expected)
    }

    @Test
    fun removedSelectedKeyIsUnreadableWithoutSubstitutingAnotherKey() = runBlocking {
        val fixture = Fixture(listOf("1:SUBSTRATE_SECRETS", "2:SUBSTRATE_SECRETS"))
        val selected = fixture.reader.listPage().keys.first().storageKey
        fixture.values.remove(selected)
        val entry = fixture.reader.readKey(selected)
        assertEquals(selected, entry.storageKey)
        assertFalse(entry.readable)
        assertTrue(entry.fields.isEmpty())
        fixture.assertNoDecryption()
        fixture.assertReadOnly()
    }

    @Test
    fun removedCursorFailsExplicitlyAndFirstPageCanBeRefreshed() = runBlocking {
        val fixture = Fixture(listOf("1:SUBSTRATE_SECRETS", "2:SUBSTRATE_SECRETS", "3:SUBSTRATE_SECRETS"))
        val cursor = checkNotNull(fixture.reader.listPage(pageSize = 1).nextStorageKey)
        fixture.values.remove(cursor)
        assertThrows(EmergencyRecoveryPageChangedException::class.java) {
            runBlocking { fixture.reader.listPage(cursor) }
        }
        assertEquals(listOf("2:SUBSTRATE_SECRETS", "3:SUBSTRATE_SECRETS"), fixture.reader.listPage().keys.map { it.storageKey })
        fixture.assertNoDecryption()
        fixture.assertReadOnly()
    }

    @Test
    fun invalidPageSizeAndUnrecognizedOrOversizedKeysRejectBeforeDecryption() = runBlocking {
        val fixture = Fixture(listOf("1:SUBSTRATE_SECRETS"))
        for (size in listOf(0, 65, Int.MAX_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) { runBlocking { fixture.reader.listPage(pageSize = size) } }
        }
        for (key in listOf("", "unrelated", "security_source_" + "x".repeat(1_024))) {
            assertThrows(IllegalArgumentException::class.java) { runBlocking { fixture.reader.listPage(key) } }
            assertThrows(IllegalArgumentException::class.java) { runBlocking { fixture.reader.readKey(key) } }
        }
        fixture.assertNoDecryption()
        fixture.assertReadOnly()
    }

    @Test
    fun cancelledPageAndPresenceCallsDoNotReadOrDecrypt() {
        val fixture = Fixture(listOf("1:SUBSTRATE_SECRETS"))
        for (operation in listOf<suspend () -> Unit>(
            { fixture.reader.listPage() }, { fixture.reader.hasStoredKeys() }, { fixture.reader.readKey("1:SUBSTRATE_SECRETS") }
        )) {
            assertThrows(CancellationException::class.java) {
                runBlocking { cancel("public cancellation control"); operation() }
            }
        }
        verify(fixture.shared, never()).all
        fixture.assertNoDecryption()
        fixture.assertReadOnly()
    }

    @Test
    fun inventoryProviderFailureDoesNotBecomeAnEmptyPage() {
        val fixture = Fixture(emptyList())
        whenever(fixture.shared.all).thenThrow(IllegalStateException("public inventory unavailable"))
        assertThrows(IllegalStateException::class.java) { runBlocking { fixture.reader.listPage() } }
        assertThrows(IllegalStateException::class.java) { runBlocking { fixture.reader.hasStoredKeys() } }
        fixture.assertNoDecryption()
        fixture.assertReadOnly()
    }

    @Test
    fun selectedDecryptCancellationAndFatalErrorsPropagate() {
        for (failure in listOf(CancellationException("public cancelled"), AssertionError("public fatal"))) {
            val fixture = Fixture(listOf("1:SUBSTRATE_SECRETS"))
            whenever(fixture.encryption.decrypt(any())).thenAnswer { throw failure }
            assertThrows(failure.javaClass) { runBlocking { fixture.reader.readKey("1:SUBSTRATE_SECRETS") } }
            fixture.assertReadOnly()
        }
    }
}
