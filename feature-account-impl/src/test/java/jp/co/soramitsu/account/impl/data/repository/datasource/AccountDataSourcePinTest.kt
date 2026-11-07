package jp.co.soramitsu.account.impl.data.repository.datasource

import com.google.gson.Gson
import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.common.data.secrets.v1.SecretStoreV1
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.data.storage.encrypt.PinHealth
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretArchive
import jp.co.soramitsu.common.data.storage.envelope.PinVerification
import jp.co.soramitsu.common.data.storage.envelope.WalletPinAuthority
import jp.co.soramitsu.common.data.storage.envelope.WalletPinKdfParameters
import jp.co.soramitsu.common.data.storage.envelope.WalletSecretKeyring
import jp.co.soramitsu.common.data.storage.envelope.WalletSecretSealer
import jp.co.soramitsu.common.data.storage.envelope.WalletSecretSession
import jp.co.soramitsu.coredb.dao.MetaAccountDao
import jp.co.soramitsu.testshared.InMemoryKeyringDeviceFactor
import jp.co.soramitsu.testshared.InMemoryKeyringPreferences
import java.security.SecureRandom
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.testshared.HashMapEncryptedPreferences
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountDataSourcePinTest {
    private val storage = HashMapEncryptedPreferences()

    @Test
    fun `PIN health and reset preserve the unreadable value before publishing the new PIN`() = runTest {
        val source = source(storage)
        assertEquals(PinHealth.ABSENT, source.getPinHealth())
        storage.putEncryptedString("pin_code", "malformed value")
        assertEquals(PinHealth.UNREADABLE, source.getPinHealth())
        source.savePinCode("123456")
        assertEquals("malformed value", storage.getDecryptedString(WalletSecretArchive.keyFor("pin_code")))
        assertEquals(PinVerification.CORRECT, source.verifyPinCode("123456"))
        assertEquals(PinVerification.WRONG, source.verifyPinCode("654321"))
        assertEquals(PinHealth.HEALTHY, source.getPinHealth())
    }

    @Test
    fun `a wrong stored PIN type is unreadable, never usable authentication text`() = runTest {
        val wrongType = object : EncryptedPreferences by storage {
            override fun getDecryptedString(field: String): String? = throw ClassCastException("wrong stored type")
        }
        storage.putEncryptedString("pin_code", "123456")
        assertEquals(PinVerification.UNREADABLE, source(wrongType).verifyPinCode("123456"))
    }

    @Test
    fun `with sealing on, a first PIN opens the wallet key and writes no legacy PIN`() = runTest {
        val keyringPreferences = InMemoryKeyringPreferences()
        val keyring = WalletSecretKeyring(keyringPreferences, InMemoryKeyringDeviceFactor(), SecureRandom(), FAST_KDF)
        val authority = WalletPinAuthority(storage, keyring, WalletSecretSealer(keyringPreferences, keyring, SecureRandom(), true))
        val source = source(storage, authority)
        try {
            source.savePinCode("123456")
            assertFalse(storage.hasKey("pin_code"))
            assertEquals(PinHealth.HEALTHY, source.getPinHealth())
            WalletSecretSession.clear()
            assertEquals(PinVerification.WRONG, source.verifyPinCode("000000"))
            assertEquals(PinVerification.CORRECT, source.verifyPinCode("123456"))
            assertTrue(WalletSecretSession.isOpen())
        } finally {
            WalletSecretSession.clear()
            jp.co.soramitsu.common.data.storage.encrypt.WalletPinHealth.resetForTest()
        }
    }

    private fun legacyAuthority(encrypted: EncryptedPreferences): WalletPinAuthority {
        val keyringPreferences = InMemoryKeyringPreferences()
        val keyring = WalletSecretKeyring(keyringPreferences, InMemoryKeyringDeviceFactor(), SecureRandom(), FAST_KDF)
        return WalletPinAuthority(encrypted, keyring, WalletSecretSealer(keyringPreferences, keyring, SecureRandom(), false))
    }

    private fun source(encrypted: EncryptedPreferences, authority: WalletPinAuthority = legacyAuthority(encrypted)): AccountDataSourceImpl {
        val dao = mockk<MetaAccountDao>()
        val chains = mockk<ChainsRepository>()
        every { dao.selectedMetaAccountInfoFlow() } returns emptyFlow()
        every { chains.chainsByIdFlow() } returns flowOf(emptyMap())
        return AccountDataSourceImpl(mockk(), Gson(), dao, mockk<SecretStoreV1>(), chains, authority)
    }

    private companion object {
        val FAST_KDF = WalletPinKdfParameters(memoryKiB = 64, iterations = 1, parallelism = 1)
    }
}
