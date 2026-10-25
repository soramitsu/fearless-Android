package jp.co.soramitsu.account.impl.presentation.importing.remote_backup

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.model.AddAccountPayload
import jp.co.soramitsu.account.api.domain.model.EthereumOnlyGoogleBackupException
import jp.co.soramitsu.backup.domain.models.BackupAccountType
import jp.co.soramitsu.backup.domain.models.DecryptedBackupAccount
import jp.co.soramitsu.backup.domain.models.Json
import jp.co.soramitsu.backup.domain.models.Seed
import jp.co.soramitsu.core.models.CryptoType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class LegacyGoogleBackupAccountImporterTest {

    @Test
    fun `mnemonic backup restores its independent EVM key in one creation`() = runTest {
        val interactor = mock<AccountInteractor>()
        whenever(interactor.createAccountFromBackup(any(), any())).thenReturn(Result.success(77L))
        val backedUpEvmKey = "0x" + "00".repeat(31) + "07"
        val backup = backup(seed = Seed(substrateSeed = null, ethSeed = backedUpEvmKey))

        importLegacyGoogleBackupAccount(interactor, backup, "password")

        val payload = argumentCaptor<AddAccountPayload.SubstrateOrEvm>()
        verify(interactor).createAccountFromBackup(payload.capture(), eq(backedUpEvmKey))
        assertEquals(backup.mnemonicPhrase, payload.firstValue.mnemonic)
        assertEquals("", payload.firstValue.ethereumDerivationPath)
        verify(interactor, never()).createAccount(any())
        verify(interactor, never()).importFromSeed(any(), any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `mnemonic backup without EVM seed keeps existing derivation`() = runTest {
        val interactor = mock<AccountInteractor>()
        whenever(interactor.createAccount(any())).thenReturn(Result.success(78L))

        importLegacyGoogleBackupAccount(interactor, backup(seed = null), "password")

        verify(interactor).createAccount(any())
        verify(interactor, never()).createAccountFromBackup(any(), any())
    }

    @Test
    fun `wallet creation failure cannot be reported as restored`() = runTest {
        val interactor = mock<AccountInteractor>()
        val failure = IllegalStateException("synthetic mutation failure")
        whenever(interactor.createAccountFromBackup(any(), any())).thenReturn(Result.failure(failure))
        val backup = backup(seed = Seed(substrateSeed = null, ethSeed = "synthetic-key"))

        val actual = runCatching {
            importLegacyGoogleBackupAccount(interactor, backup, "password")
        }.exceptionOrNull()

        assertSame(failure, actual)
    }

    @Test
    fun `backup without recoverable material fails closed`() = runTest {
        val interactor = mock<AccountInteractor>()
        val backup = backup(mnemonicPhrase = null, seed = null)

        val actual = runCatching {
            importLegacyGoogleBackupAccount(interactor, backup, "password")
        }.exceptionOrNull()

        assertEquals("Backup has no recoverable wallet material", actual?.message)
        verifyNoInteractions(interactor)
    }

    @Test
    fun `every passphrase backup the production code wrote is created from its phrase and paths`() = runTest {
        listOf(
            "v1-passphrase-sr25519-paths.json",
            "v1-passphrase-ed25519-empty-paths.json",
            "v1-passphrase-sr25519-path-password.json",
            "v0-passphrase-1.0.1.json",
            "v0-passphrase-empty-paths-1.0.1.json"
        ).forEach { file ->
            val fixture = fixture(file)
            val interactor = mock<AccountInteractor>()
            whenever(interactor.createAccount(any())).thenReturn(Result.success(1L))

            importLegacyGoogleBackupAccount(interactor, fixture.backup, fixture.password)

            val payload = argumentCaptor<AddAccountPayload.SubstrateOrEvm>()
            verify(interactor).createAccount(payload.capture())
            assertEquals(
                file,
                AddAccountPayload.SubstrateOrEvm(
                    accountName = fixture.backup.name,
                    mnemonic = fixture.backup.mnemonicPhrase!!,
                    encryptionType = fixture.backup.cryptoType,
                    substrateDerivationPath = fixture.backup.substrateDerivationPath.orEmpty(),
                    ethereumDerivationPath = fixture.backup.ethDerivationPath.orEmpty(),
                    googleBackupAddress = fixture.backup.address,
                    isBackedUp = true
                ),
                payload.firstValue
            )
        }
    }

    @Test
    fun `mixed 4_2_0 backups restore the phrase together with the backed-up EVM key`() = runTest {
        listOf("v1-mixed-4.2.0.json", "rc-written-v1-verified.json").forEach { file ->
            val backup = fixture(file).backup
            val interactor = mock<AccountInteractor>()
            whenever(interactor.createAccountFromBackup(any(), any())).thenReturn(Result.success(2L))

            importLegacyGoogleBackupAccount(interactor, backup, fixture(file).password, setOf(BackupAccountType.PASSPHRASE, BackupAccountType.SEED))

            val payload = argumentCaptor<AddAccountPayload.SubstrateOrEvm>()
            verify(interactor).createAccountFromBackup(payload.capture(), eq(backup.seed!!.ethSeed!!))
            assertEquals(file, backup.mnemonicPhrase, payload.firstValue.mnemonic)
            verify(interactor, never()).importFromSeed(any(), any(), any(), any(), any(), any(), any())
            verify(interactor, never()).importFromJson(any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `seed backups pass their 0x seeds and path unchanged`() = runTest {
        listOf("v1-seed-substrate-ed25519.json", "v1-seed-both-ecdsa.json").forEach { file ->
            val backup = fixture(file).backup
            val interactor = mock<AccountInteractor>()
            whenever(interactor.importFromSeed(anyOrNull(), anyOrNull(), any(), any(), any(), anyOrNull(), anyOrNull())).thenReturn(Result.success(Unit))

            importLegacyGoogleBackupAccount(interactor, backup, fixture(file).password)

            verify(interactor).importFromSeed(
                walletId = null,
                substrateSeed = backup.seed!!.substrateSeed,
                username = backup.name,
                derivationPath = backup.substrateDerivationPath.orEmpty(),
                selectedEncryptionType = backup.cryptoType,
                ethSeed = backup.seed!!.ethSeed,
                googleBackupAddress = backup.address
            )
            assertTrue(file, backup.seed!!.substrateSeed!!.startsWith("0x"))
        }
    }

    @Test
    fun `json backups import the substrate keystore with its ethereum keystore`() = runTest {
        val fixture = fixture("v1-json-only.json")
        val interactor = mock<AccountInteractor>()
        whenever(interactor.importFromJson(anyOrNull(), anyOrNull(), any(), any(), anyOrNull(), anyOrNull())).thenReturn(Result.success(Unit))

        importLegacyGoogleBackupAccount(interactor, fixture.backup, "backup password")

        verify(interactor).importFromJson(
            walletId = null,
            json = fixture.backup.json!!.substrateJson,
            password = "backup password",
            name = fixture.backup.name,
            ethJson = fixture.backup.json!!.ethJson,
            googleBackupAddress = fixture.backup.address
        )
    }

    @Test
    fun `an ethereum-only seed backup is refused without deriving a substrate key from nothing`() = runTest {
        val interactor = mock<AccountInteractor>()

        val actual = runCatching {
            importLegacyGoogleBackupAccount(interactor, fixture("v1-seed-ethereum-only.json").backup, "password")
        }.exceptionOrNull()

        assertTrue(actual is EthereumOnlyGoogleBackupException)
        verifyNoInteractions(interactor)
    }

    @Test
    fun `an orphan extension ethereum keystore is never imported as substrate json`() = runTest {
        // BackupService routes an unreferenced extension Ethereum file as Json(substrateJson = null, ethJson).
        val ethereumKeystore = fixture("v1-json-only.json").backup.json!!.ethJson
        val orphan = DecryptedBackupAccount(
            name = "Extension wallet",
            address = "0x9858effd232b4033e47d90003d41ec34ecaeda94",
            cryptoType = CryptoType.ED25519,
            backupAccountType = listOf(BackupAccountType.JSON),
            json = Json(substrateJson = null, ethJson = ethereumKeystore)
        )
        val interactor = mock<AccountInteractor>()

        val actual = runCatching { importLegacyGoogleBackupAccount(interactor, orphan, "password", null) }.exceptionOrNull()

        assertTrue(actual is EthereumOnlyGoogleBackupException)
        verifyNoInteractions(interactor)
    }

    @Test
    fun `a drifted phrase from an older upload is passed over for the seed that restores the wallet`() = runTest {
        val backup = fixture("v1-mixed-4.2.0.json").backup
        val interactor = mock<AccountInteractor>()
        whenever(interactor.importFromSeed(anyOrNull(), anyOrNull(), any(), any(), any(), anyOrNull(), anyOrNull())).thenReturn(Result.success(Unit))

        importLegacyGoogleBackupAccount(interactor, backup, "password", setOf(BackupAccountType.SEED))

        verify(interactor).importFromSeed(
            walletId = null,
            substrateSeed = backup.seed!!.substrateSeed,
            username = backup.name,
            derivationPath = "",
            selectedEncryptionType = backup.cryptoType,
            ethSeed = backup.seed!!.ethSeed,
            googleBackupAddress = backup.address
        )
        verify(interactor, never()).createAccount(any())
        verify(interactor, never()).createAccountFromBackup(any(), any())
    }

    @Test
    fun `when neither phrase nor seed restores the wallet its keystore is imported`() = runTest {
        val backup = fixture("v1-mixed-4.2.0.json").backup
        val interactor = mock<AccountInteractor>()
        whenever(interactor.importFromJson(anyOrNull(), anyOrNull(), any(), any(), anyOrNull(), anyOrNull())).thenReturn(Result.success(Unit))

        importLegacyGoogleBackupAccount(interactor, backup, "password", emptySet())

        verify(interactor).importFromJson(null, backup.json!!.substrateJson, "password", backup.name, backup.json!!.ethJson, backup.address)
        verify(interactor, never()).createAccountFromBackup(any(), any())
        verify(interactor, never()).importFromSeed(any(), any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `an unverified phrase or seed that is all a backup holds is still imported as before`() = runTest {
        val phraseOnly = fixture("v1-passphrase-ed25519-empty-paths.json").backup
        val phraseInteractor = mock<AccountInteractor>()
        whenever(phraseInteractor.createAccount(any())).thenReturn(Result.success(3L))

        importLegacyGoogleBackupAccount(phraseInteractor, phraseOnly, "password", emptySet())

        // The wallet it creates is another account, so the backup's address is not recorded as its own.
        val payload = argumentCaptor<AddAccountPayload.SubstrateOrEvm>()
        verify(phraseInteractor).createAccount(payload.capture())
        assertNull(payload.firstValue.googleBackupAddress)

        val seedOnly = fixture("v1-seed-both-ecdsa.json").backup
        val seedInteractor = mock<AccountInteractor>()
        whenever(seedInteractor.importFromSeed(anyOrNull(), anyOrNull(), any(), any(), any(), anyOrNull(), anyOrNull())).thenReturn(Result.success(Unit))

        importLegacyGoogleBackupAccount(seedInteractor, seedOnly, "password", emptySet())

        verify(seedInteractor).importFromSeed(anyOrNull(), eq(seedOnly.seed!!.substrateSeed), any(), any(), any(), anyOrNull(), eq(null))
    }

    @Test
    fun `a phrase that restores the backup's wallet records the backup's address`() = runTest {
        val phraseOnly = fixture("v1-passphrase-ed25519-empty-paths.json").backup
        val interactor = mock<AccountInteractor>()
        whenever(interactor.createAccount(any())).thenReturn(Result.success(3L))

        importLegacyGoogleBackupAccount(interactor, phraseOnly, "password", setOf(BackupAccountType.PASSPHRASE))

        val payload = argumentCaptor<AddAccountPayload.SubstrateOrEvm>()
        verify(interactor).createAccount(payload.capture())
        assertEquals(phraseOnly.address, payload.firstValue.googleBackupAddress)
    }

    private fun backup(
        mnemonicPhrase: String? = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about",
        seed: Seed?
    ) = DecryptedBackupAccount(
        name = "Recovered wallet",
        address = "backup-address",
        mnemonicPhrase = mnemonicPhrase,
        substrateDerivationPath = "",
        ethDerivationPath = null,
        cryptoType = CryptoType.ED25519,
        backupAccountType = listOf(BackupAccountType.PASSPHRASE),
        seed = seed,
        json = null
    )

    private class Fixture(val password: String, val backup: DecryptedBackupAccount)

    // Decrypted contents of the Drive files the production 4.2.0 and 2.3.1 code wrote, as the
    // backup module's compatibility tests assert them (legacy-backup/manifest.json).
    private fun fixture(file: String): Fixture {
        val manifest = requireNotNull(javaClass.classLoader!!.getResourceAsStream("legacy-backup/manifest.json")).bufferedReader().use { it.readText() }
        val entry = JsonParser.parseString(manifest).asJsonObject.getAsJsonArray("fixtures")
            .map { it.asJsonObject }
            .single { it.string("file") == file }
        val expected = entry.getAsJsonObject("expected")

        return Fixture(
            password = entry.string("password")!!,
            backup = DecryptedBackupAccount(
                name = expected.string("name")!!,
                address = expected.string("address")!!,
                mnemonicPhrase = expected.string("mnemonicPhrase"),
                substrateDerivationPath = expected.string("substrateDerivationPath"),
                ethDerivationPath = expected.string("ethDerivationPath"),
                cryptoType = CryptoType.valueOf(expected.string("cryptoType")!!),
                backupAccountType = expected.getAsJsonArray("backupAccountType").map { BackupAccountType.valueOf(it.asString) },
                seed = expected.objectOrNull("seed")?.let { Seed(it.string("substrateSeed"), it.string("ethSeed")) },
                json = expected.objectOrNull("json")?.let { Json(it.string("substrateJson"), it.string("ethJson")) }
            )
        )
    }

    private fun JsonObject.string(key: String): String? = get(key)?.takeUnless { it.isJsonNull }?.asString

    private fun JsonObject.objectOrNull(key: String): JsonObject? = get(key)?.takeUnless { it.isJsonNull }?.asJsonObject
}
