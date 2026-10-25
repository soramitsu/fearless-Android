package jp.co.soramitsu.account.impl.data.repository.datasource

import com.google.gson.Gson
import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.common.data.secrets.v1.SecretStoreV1
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.data.storage.encrypt.PinHealth
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretArchive
import jp.co.soramitsu.coredb.dao.MetaAccountDao
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.testshared.HashMapEncryptedPreferences
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
        assertEquals("123456", source.getPinCode())
        assertEquals(PinHealth.HEALTHY, source.getPinHealth())
    }

    @Test
    fun `wrong typed PIN is never returned as usable authentication text`() = runTest {
        val wrongType = object : EncryptedPreferences by storage {
            override fun getDecryptedString(field: String): String? = throw ClassCastException("wrong stored type")
        }
        assertEquals("", source(wrongType).getPinCode())
    }

    private fun source(encrypted: EncryptedPreferences): AccountDataSourceImpl {
        val dao = mockk<MetaAccountDao>()
        val chains = mockk<ChainsRepository>()
        every { dao.selectedMetaAccountInfoFlow() } returns emptyFlow()
        every { chains.chainsByIdFlow() } returns flowOf(emptyMap())
        return AccountDataSourceImpl(mockk(), encrypted, Gson(), dao, mockk<SecretStoreV1>(), chains)
    }
}
