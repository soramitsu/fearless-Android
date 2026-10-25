package jp.co.soramitsu.account.impl.domain.recovery

import com.google.gson.JsonParser
import io.mockk.every
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import jp.co.soramitsu.account.impl.data.repository.WalletSecretMutationCoordinator
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecrets
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferenceSnapshot
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretArchive
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretScope
import jp.co.soramitsu.fearless_utils.encrypt.keypair.ethereum.EthereumKeypairFactory
import jp.co.soramitsu.fearless_utils.scale.toHexString
import jp.co.soramitsu.testshared.HashMapEncryptedPreferences
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchivedWalletRecoveryTest {
    private val preferences = HashMapEncryptedPreferences()
    private val coordinator = mockk<WalletSecretMutationCoordinator>(relaxed = true)
    private val recovery = ArchivedWalletRecovery(preferences, coordinator)

    @Test fun malformedRecordsStayVisibleAndExportTheirCompleteOriginalValue() = runBlocking {
        val key = WalletSecretArchive.keyFor("7:ACCESS_SECRETS")
        val exact = "  unknown format\nwith trailing bytes\u0000  "
        preferences.putEncryptedString(key, exact)
        preferences.putEncryptedString(WalletSecretArchive.keyFor("pin_code"), "old pin value")
        val record = recovery.read().single()
        assertEquals(key, record.key)
        assertTrue(record.readable)
        assertTrue(record.addresses.isEmpty())
        assertTrue(record.unprovedParts > 0)
        val json = JsonParser.parseString(recovery.exactExport(record)).asJsonObject
        assertEquals(exact, json["storedData"].asString)
        assertFalse(record.toString().contains(exact))
        assertEquals(exact, preferences.getDecryptedString(key))
    }

    @Test fun malformedArchiveNamesRemainVisibleWithExactExportButNoInvalidPurgeAction() = runBlocking {
        preferences.putEncryptedString(WalletSecretArchive.KEY_PREFIX, "unknown original bytes")
        val record = recovery.read().single()
        assertTrue(record.readable)
        assertFalse(record.canPurge)
        assertTrue(record.addresses.isEmpty())
        assertEquals("unknown original bytes", JsonParser.parseString(recovery.exactExport(record)).asJsonObject["storedData"].asString)
    }

    @Test fun oversizedFingerprintIsNeverMisrepresentedAsExactOriginalData() = runBlocking {
        val key = WalletSecretArchive.keyFor("7:SUBSTRATE_SECRETS")
        preferences.putEncryptedString(key, "placeholder for a retained oversized value")
        val snapshot = mockk<EncryptedPreferenceSnapshot> {
            every { plaintext } returns "fearless-oversized-ciphertext-sha256:fixture"
            every { hasDecryptedValue } returns false
        }
        val storage = object : EncryptedPreferences by preferences {
            override fun getDecryptedStringSnapshot(field: String) = snapshot
        }
        val service = ArchivedWalletRecovery(storage, coordinator)
        val record = service.read().single()
        assertFalse(record.readable)
        assertTrue(record.addresses.isEmpty())
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) { service.exactExport(record) }
        assertTrue(preferences.hasKey(key))
    }

    @Test fun oneRecordFailureCannotHideOtherRecordsOrTheirExactData() = runBlocking {
        val first = WalletSecretArchive.keyFor("7:SUBSTRATE_SECRETS")
        val second = WalletSecretArchive.keyFor("8:ETHEREUM_SECRETS")
        preferences.putEncryptedString(first, "unavailable")
        preferences.putEncryptedString(second, "unknown but readable")
        val storage = object : EncryptedPreferences by preferences {
            override fun getDecryptedStringSnapshot(field: String) =
                if (field == first) error("Unavailable") else preferences.getDecryptedStringSnapshot(field)
        }
        val records = ArchivedWalletRecovery(storage, coordinator).read()
        assertEquals(2, records.size)
        assertFalse(records.first { it.key == first }.readable)
        assertTrue(records.first { it.key == second }.readable)
    }

    @Test fun confirmedRestorationAndPurgeUseTheExactPreviewSnapshot() = runBlocking {
        val key = WalletSecretArchive.keyFor("7:ETHEREUM_SECRETS")
        val pair = EthereumKeypairFactory.createWithPrivateKey(ByteArray(32) { 7 })
        preferences.putEncryptedString(key, EthereumSecrets(ethereumKeypair = pair).toHexString())
        val record = recovery.read().single()
        assertEquals(WalletSecretScope.Ethereum, record.addresses.single().scope)
        assertTrue(record.canPurge)
        coEvery { coordinator.restoreArchived(key, record.snapshot!!, WalletSecretScope.Ethereum, "Recovered") } returns 19
        assertEquals(19L, recovery.restore(record, WalletSecretScope.Ethereum, "Recovered"))
        recovery.purge(record)
        coVerify(exactly = 1) { coordinator.purgeArchived(key, record.snapshot!!) }
        assertTrue(preferences.hasKey(key)) // Only the coordinator owns mutations.
    }
}
