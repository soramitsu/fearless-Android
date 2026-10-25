package jp.co.soramitsu.backup

import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import jp.co.soramitsu.backup.domain.AccountJsonMapper
import jp.co.soramitsu.backup.domain.BackupAccountCrypto
import jp.co.soramitsu.backup.domain.exceptions.BackupSecretNotVerifiedException
import jp.co.soramitsu.backup.domain.exceptions.BackupUnavailableException
import jp.co.soramitsu.backup.domain.exceptions.DecodingException
import jp.co.soramitsu.backup.domain.exceptions.DecryptionException
import jp.co.soramitsu.backup.domain.exceptions.FileNotFoundException
import jp.co.soramitsu.backup.domain.models.BackupAccountMeta
import jp.co.soramitsu.backup.domain.models.BackupAccountType
import jp.co.soramitsu.backup.domain.models.DecryptedBackupAccount
import jp.co.soramitsu.backup.domain.models.Json
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock

class BackupServiceTest {

    private val storage = FakeStorage()
    private val service = backupService(storage)
    private val mixed = LegacyBackupFixtures.named("v1-mixed-4.2.0.json")
    private val crypto = BackupAccountCrypto()
    private val mapper = AccountJsonMapper()

    @Test
    fun `restores a 4_2_0 backup listed from Drive`() = runBlocking<Unit> {
        storage.addAppFile(mixed.expected.address, "f1", mixed.content, name = "Mixed")

        val listed = service.getBackupAccounts()
        val restored = service.importBackupAccount(mixed.expected.address, mixed.password)

        assertEquals(listOf(BackupAccountMeta("Mixed", mixed.expected.address, "f1")), listed)
        assertEquals(mixed.expected, restored)
        assertEquals(1, storage.appListCalls)
    }

    @Test
    fun `restores a 2_3_1 backup that 2_4_1 to 4_2_0 rejected`() = runBlocking<Unit> {
        val legacy = LegacyBackupFixtures.named("v0-passphrase-1.0.1.json")
        storage.addAppFile(legacy.expected.address, "f1", legacy.content)

        assertEquals(legacy.expected, service.importBackupAccount(legacy.expected.address, legacy.password))
    }

    @Test
    fun `lists one entry per address and imports the newest copy that opens`() = runBlocking<Unit> {
        val address = mixed.expected.address
        storage.addAppFile(address, "old", mixed.content, name = "Old name", modifiedTime = 1)
        storage.addAppFile(address, "new", encrypt(mixed.expected.copy(name = "New name"), "new password"), name = "New name", modifiedTime = 2)

        val listed = service.getBackupAccounts()

        assertEquals(listOf(BackupAccountMeta("New name", address, "new")), listed)
        assertEquals("New name", service.importBackupAccount(address, "new password").name)
        assertEquals("Mixed", service.importBackupAccount(address, mixed.password).name)
        assertThrows(DecryptionException::class.java) { runBlocking { service.importBackupAccount(address, "neither") } }
    }

    @Test
    fun `skips a copy this version cannot read when an older one opens`() = runBlocking<Unit> {
        val address = mixed.expected.address
        storage.addAppFile(address, "old", mixed.content, modifiedTime = 1)
        storage.addAppFile(address, "future", """{"name":"F","address":"$address","cryptoType":"ED25519","backupAccountType":["ton"]}""", modifiedTime = 2)

        assertEquals(mixed.expected, service.importBackupAccount(address, mixed.password))

        storage.appFiles.removeAll { it.fileId == "old" }
        service.getBackupAccounts()
        assertThrows(DecodingException::class.java) { runBlocking { service.importBackupAccount(address, mixed.password) } }
    }

    @Test
    fun `import with a cold index lists once before failing`() = runBlocking<Unit> {
        storage.addAppFile(mixed.expected.address, "f1", mixed.content)

        assertEquals(mixed.expected, service.importBackupAccount(mixed.expected.address, mixed.password))
        assertEquals(1, storage.appListCalls)

        assertThrows(FileNotFoundException::class.java) { runBlocking { service.importBackupAccount("5Unknown", "pw") } }
        assertEquals(2, storage.appListCalls)
    }

    @Test
    fun `lists every copy of each wallet newest first`() = runBlocking<Unit> {
        val address = mixed.expected.address
        storage.addAppFile(address, "old", mixed.content, name = "Phone", modifiedTime = 1)
        storage.addAppFile(address, "new", mixed.content, name = "Tablet", modifiedTime = 2)
        storage.addAppFile("5Other", "c", mixed.content, name = "Other")
        storage.addWebFile("Ext.json", address, "w", "{}")

        assertEquals(
            listOf(
                BackupAccountMeta("Tablet", address, "new"),
                BackupAccountMeta("Phone", address, "old"),
                BackupAccountMeta("Other", "5Other", "c")
            ),
            service.getBackupCopies()
        )
        assertEquals(0, storage.webListCalls)
    }

    @Test
    fun `delete with a cold index lists first and removes only the confirmed copies`() = runBlocking<Unit> {
        val address = mixed.expected.address
        storage.addAppFile(address, "a", mixed.content)
        storage.addAppFile(address, "b", mixed.content)
        storage.addAppFile("5Other", "c", mixed.content)
        storage.addWebFile("Ext.json", address, "w", "{}")

        service.deleteBackupAccount(address, listOf("a", "b"))

        assertEquals(1, storage.appListCalls)
        assertEquals(listOf("a", "b"), storage.deletedFileIds)
        assertEquals(listOf("c"), storage.appFiles.map { it.fileId })
    }

    @Test
    fun `delete keeps a copy another device uploaded after the confirmation`() = runBlocking<Unit> {
        // Drive deletes skip the trash: an unconfirmed copy may hold the only key to another account.
        val address = mixed.expected.address
        storage.addAppFile(address, "confirmed", mixed.content, modifiedTime = 1)
        storage.addAppFile(address, "other device", encrypt(mixed.expected, "other password"), modifiedTime = 2)

        service.deleteBackupAccount(address, listOf("confirmed"))

        assertEquals(listOf("confirmed"), storage.deletedFileIds)
        assertEquals(mixed.expected, service.importBackupAccount(address, "other password"))
    }

    @Test
    fun `delete never removes a file filed under another address`() = runBlocking<Unit> {
        storage.addAppFile(mixed.expected.address, "a", mixed.content)
        storage.addAppFile("5Funded", "funded", mixed.content)

        assertThrows(FileNotFoundException::class.java) {
            runBlocking { service.deleteBackupAccount(mixed.expected.address, listOf("funded")) }
        }

        assertEquals(emptyList<String>(), storage.deletedFileIds)
    }

    @Test
    fun `delete reports a backup that was already gone`() = runBlocking<Unit> {
        val address = mixed.expected.address
        storage.addAppFile(address, "gone", mixed.content)
        storage.missingFileIds += "gone"

        assertThrows(FileNotFoundException::class.java) { runBlocking { service.deleteBackupAccount(address, listOf("gone")) } }
    }

    @Test
    fun `delete succeeds when another copy was already gone`() = runBlocking<Unit> {
        val address = mixed.expected.address
        storage.addAppFile(address, "gone", mixed.content)
        storage.addAppFile(address, "present", mixed.content)
        storage.missingFileIds += "gone"

        service.deleteBackupAccount(address, listOf("gone", "present"))

        assertEquals(listOf("present"), storage.deletedFileIds)
    }

    @Test
    fun `logout clears the index`() = runBlocking<Unit> {
        storage.addAppFile(mixed.expected.address, "f1", mixed.content)
        service.getBackupAccounts()

        service.logout()
        service.importBackupAccount(mixed.expected.address, mixed.password)

        assertEquals(1, storage.logoutCalls)
        assertEquals(2, storage.appListCalls)
    }

    @Test
    fun `save uploads a 4_2_0 readable file under the wallet address and records it`() = runBlocking<Unit> {
        service.saveBackupAccount(mixed.expected, "save password")

        val saved = storage.appFiles.single()
        assertEquals("${mixed.expected.address}.json", saved.fileName)
        assertEquals(mixed.expected.name, saved.description)
        val uploaded = storage.contents.getValue(saved.fileId)
        assertEquals(mixed.expected, crypto.decryptAccount(mapper.mapJsonStringToEncryptedAccount(uploaded), "save password"))

        assertEquals(mixed.expected, service.importBackupAccount(mixed.expected.address, "save password"))
        assertEquals(0, storage.appListCalls)
    }

    @Test
    fun `re-backing up a restored 4_2_0 wallet passes verification`() = runBlocking<Unit> {
        storage.addAppFile(mixed.expected.address, "f1", mixed.content)
        val restored = service.importBackupAccount(mixed.expected.address, mixed.password)

        service.saveBackupAccount(restored, "new password")

        assertEquals(listOf("saved-0"), storage.savedFileIds)
    }

    @Test
    fun `save refuses an unverified phrase and uploads nothing`() = runBlocking<Unit> {
        val drifted = mixed.expected.copy(
            mnemonicPhrase = "legal winner thank year wave sausage worth useful legal winner thank yellow"
        )

        val failure = assertThrows(BackupSecretNotVerifiedException::class.java) {
            runBlocking { service.saveBackupAccount(drifted, "password") }
        }

        assertEquals(BackupAccountType.PASSPHRASE, failure.backupAccountType)
        assertFalse(storage.touched)
    }

    @Test
    fun `a downloaded backup with a drifted phrase names the seed that restores its wallet`() = runBlocking<Unit> {
        // 4.2.0 uploaded without checking: this phrase restores another account than the file is filed under.
        val drifted = mixed.expected.copy(
            mnemonicPhrase = "legal winner thank year wave sausage worth useful legal winner thank yellow"
        )
        storage.addAppFile(drifted.address, "f1", encrypt(drifted, "password"))

        val restored = service.importBackupAccount(drifted.address, "password")

        assertEquals(drifted, restored)
        assertEquals(setOf(BackupAccountType.SEED), service.restoringSecretTypes(restored))
        assertEquals(setOf(BackupAccountType.PASSPHRASE, BackupAccountType.SEED), service.restoringSecretTypes(mixed.expected))
    }

    @Test
    fun `leaving out a refused phrase uploads a seed and JSON backup that restores the wallet`() = runBlocking<Unit> {
        // What AccountInteractorImpl does after the refusal above (critique T-7).
        val drifted = mixed.expected.copy(
            mnemonicPhrase = "legal winner thank year wave sausage worth useful legal winner thank yellow"
        )

        service.saveBackupAccount(drifted.copy(backupAccountType = listOf(BackupAccountType.SEED, BackupAccountType.JSON)), "password")

        val restored = service.importBackupAccount(drifted.address, "password")
        assertNull(restored.mnemonicPhrase)
        assertEquals(listOf(BackupAccountType.SEED, BackupAccountType.JSON), restored.backupAccountType)
        assertEquals(mixed.expected.seed, restored.seed)
        assertEquals(setOf(BackupAccountType.SEED), service.restoringSecretTypes(restored))
    }

    @Test
    fun `save keeps a JSON backup when the phrase is not offered`() = runBlocking<Unit> {
        val jsonOnly = mixed.expected.copy(backupAccountType = listOf(BackupAccountType.JSON))

        service.saveBackupAccount(jsonOnly, "password")

        val uploaded = storage.contents.getValue(storage.savedFileIds.single())
        val encrypted = mapper.mapJsonStringToEncryptedAccount(uploaded)
        assertNull(encrypted.encryptedMnemonicPhrase)
        assertNull(encrypted.encryptedSeed)
        assertEquals(listOf("json"), encrypted.backupAccountType)
    }

    @Test
    fun `save leaves out a JSON export of another account and keeps the verified phrase`() = runBlocking<Unit> {
        // The Polkadot JSON of a wallet with a separate Polkadot chain account holds that account.
        val chainAccountJson = keystore("sr25519").replace(""""address":"a"""", """"address":"5GrwvaEF5zXb26Fz9rcQpDWS57CtERHpNehXCPcNoHGKutQY"""")

        service.saveBackupAccount(mixed.expected.copy(json = Json(chainAccountJson, null)), "password")

        val encrypted = mapper.mapJsonStringToEncryptedAccount(storage.contents.getValue(storage.savedFileIds.single()))
        assertEquals(listOf("passphrase", "seed"), encrypted.backupAccountType)
        assertNull(encrypted.json)
        assertEquals(mixed.expected.copy(backupAccountType = listOf(BackupAccountType.PASSPHRASE, BackupAccountType.SEED), json = null), crypto.decryptAccount(encrypted, "password"))
    }

    @Test
    fun `unavailable build explains instead of touching Drive`() = runBlocking<Unit> {
        val unavailable = backupService(storage, isAvailable = false)

        assertFalse(unavailable.authorize(mock<ActivityResultLauncher<Intent>>()))
        listOf<suspend () -> Unit>(
            { unavailable.getBackupAccounts() },
            { unavailable.getWebBackupAccounts() },
            { unavailable.saveBackupAccount(mixed.expected, "pw") },
            { unavailable.importBackupAccount(mixed.expected.address, "pw") },
            { unavailable.importWebBackupAccount(BackupAccountMeta("n", "a", "f")) },
            { unavailable.getBackupCopies() },
            { unavailable.deleteBackupAccount(mixed.expected.address, listOf("f1")) }
        ).forEach { call ->
            assertThrows(BackupUnavailableException::class.java) { runBlocking { call() } }
        }
        assertFalse(storage.touched)
        assertTrue(service.authorize(mock<ActivityResultLauncher<Intent>>()))
    }

    @Test
    fun `web list folds referenced ethereum files and keys entries by file id`() = runBlocking<Unit> {
        storage.addWebFile("Both.json", "5Both/eth-1", "sub-1", keystore("sr25519"))
        storage.addWebFile("Both.json", "0xEthBoth", "eth-1", keystore("ethereum"))
        storage.addWebFile("Plain.json", "5Plain", "sub-2", keystore("ed25519"))
        storage.addWebFile("First.json", "", "sub-3", keystore("sr25519"))
        storage.addWebFile("Second.json", "", "sub-4", keystore("sr25519"))
        storage.addWebFile("Orphan.json", "0xOrphan", "eth-2", keystore("ethereum"))

        val listed = service.getWebBackupAccounts()

        assertEquals(
            listOf(
                BackupAccountMeta("Both", "5Both", "sub-1"),
                BackupAccountMeta("Plain", "5Plain", "sub-2"),
                BackupAccountMeta("First", "", "sub-3"),
                BackupAccountMeta("Second", "", "sub-4"),
                BackupAccountMeta("Orphan", "0xOrphan", "eth-2")
            ),
            listed
        )
    }

    @Test
    fun `web list leaves out files in the app data root that are not extension backups`() = runBlocking<Unit> {
        // Passkey backups share the root; a later app version or another device may have written them.
        storage.addWebFile("Plain.json", "5Plain", "sub-2", keystore("ed25519"))
        storage.addWebFile("fearless-passkey-backup-k1.bin", "", "passkey-1", "binary")
        storage.addWebFile("fearless-passkey-generation-g1.bin", "", "passkey-2", "binary")

        assertEquals(listOf(BackupAccountMeta("Plain", "5Plain", "sub-2")), service.getWebBackupAccounts())
        assertThrows(FileNotFoundException::class.java) {
            runBlocking { service.importWebBackupAccount(BackupAccountMeta("fearless-passkey-backup-k1.bin", "", "passkey-1")) }
        }
    }

    @Test
    fun `imports extension backups through their file id`() = runBlocking<Unit> {
        storage.addWebFile("Both.json", "5Both/eth-1", "sub-1", keystore("sr25519"))
        storage.addWebFile("Both.json", "0xEthBoth", "eth-1", keystore("ethereum"))
        storage.addWebFile("First.json", "", "sub-3", keystore("ed25519"))
        storage.addWebFile("Second.json", "", "sub-4", keystore("ecdsa"))
        val listed = service.getWebBackupAccounts()

        val both = service.importWebBackupAccount(listed[0])
        val second = service.importWebBackupAccount(listed[2])

        assertEquals(Json(keystore("sr25519"), keystore("ethereum")), both.json)
        assertEquals(listOf(BackupAccountType.JSON), both.backupAccountType)
        assertEquals("Second", second.name)
        assertEquals(Json(keystore("ecdsa"), null), second.json)
    }

    @Test
    fun `routes an unreferenced ethereum file as ethereum JSON`() = runBlocking<Unit> {
        storage.addWebFile("Orphan.json", "0xOrphan", "eth-2", keystore("ethereum"))

        val orphan = service.importWebBackupAccount(service.getWebBackupAccounts().single())

        assertEquals(Json(substrateJson = null, ethJson = keystore("ethereum")), orphan.json)
    }

    @Test
    fun `address based web import resolves through a cold index and prefers the name`() = runBlocking<Unit> {
        storage.addWebFile("First.json", "", "sub-3", keystore("ed25519"))
        storage.addWebFile("Second.json", "", "sub-4", keystore("ecdsa"))

        val second = service.importWebBackupAccount(address = "", name = "Second")

        assertEquals(Json(keystore("ecdsa"), null), second.json)
        assertEquals(1, storage.webListCalls)
    }

    @Test
    fun `app backups do not shadow extension backups of the same wallet`() = runBlocking<Unit> {
        // shared_features indexed both under the address, so the extension import read the app file.
        storage.addAppFile("5Both", "app-1", mixed.content)
        storage.addWebFile("Both.json", "5Both", "sub-1", keystore("sr25519"))
        service.getBackupAccounts()

        val web = service.importWebBackupAccount(address = "5Both", name = "Both")

        assertEquals(Json(keystore("sr25519"), null), web.json)
    }

    private fun encrypt(account: DecryptedBackupAccount, password: String): String {
        return mapper.mapEncryptedAccountToJson(crypto.encryptAccount(account, password))
    }

    private fun keystore(content: String) =
        """{"encoded":"x","encoding":{"content":["pkcs8","$content"],"type":["scrypt","xsalsa20-poly1305"],"version":"3"},"address":"a"}"""
}
