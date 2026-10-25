package jp.co.soramitsu.backup.domain

import jp.co.soramitsu.backup.FakeStorage
import jp.co.soramitsu.backup.LegacyBackupFixtures
import jp.co.soramitsu.backup.backupService
import jp.co.soramitsu.backup.domain.exceptions.BackupWrongPasswordException
import jp.co.soramitsu.backup.domain.exceptions.DecryptionException
import jp.co.soramitsu.backup.domain.models.BackupAccountType
import jp.co.soramitsu.backup.domain.models.DecryptedBackupAccount
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.fearless_utils.encrypt.keypair.ECDSAUtils
import jp.co.soramitsu.fearless_utils.encrypt.keypair.ethereum.EthereumKeypairFactory
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.hash.Hasher.keccak256
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackupAccountCryptoCompatTest {

    private val mapper = AccountJsonMapper()
    private val crypto = BackupAccountCrypto()

    @Test
    fun `decrypts every v1 fixture produced by production 1_1_1_44`() {
        val fixtures = LegacyBackupFixtures.all.filter { it.file.startsWith("v1-") }
        assertEquals(8, fixtures.size)

        fixtures.forEach { fixture ->
            val decrypted = crypto.decryptAccount(mapper.mapJsonStringToEncryptedAccount(fixture.content), fixture.password)
            assertEquals(fixture.file, fixture.expected, decrypted)
        }
    }

    @Test
    fun `decrypts v0 fixtures produced by 1_0_1 that 2_4_1 to 4_2_0 could not read`() {
        val fixtures = LegacyBackupFixtures.all.filter { it.file.startsWith("v0-") }
        assertEquals(2, fixtures.size)

        fixtures.forEach { fixture ->
            assertTrue(fixture.file, "backupAccountType" !in fixture.content)
            val decrypted = crypto.decryptAccount(mapper.mapJsonStringToEncryptedAccount(fixture.content), fixture.password)
            assertEquals(fixture.file, fixture.expected, decrypted)
            assertEquals(listOf(BackupAccountType.PASSPHRASE), decrypted.backupAccountType)
        }
    }

    @Test
    fun `rc encrypted file decrypts with production oracle`() {
        // The production 1.1.1.44 decryptor read this RC-written file back and re-encoded it byte for byte.
        val fixture = LegacyBackupFixtures.named("rc-written-v1-verified.json")

        val decrypted = crypto.decryptAccount(mapper.mapJsonStringToEncryptedAccount(fixture.content), fixture.password)

        assertEquals(fixture.expected, decrypted)
        assertEquals(fixture.content, mapper.mapEncryptedAccountToJson(mapper.mapJsonStringToEncryptedAccount(fixture.content)))
    }

    @Test
    fun `decrypted ethereum keys restore the recorded ethereum accounts`() {
        // 0x9858EfFD232B4033E47d90003D41EC34EcaEda94 is BIP-44 m/44'/60'/0'/0/0 of "abandon ... about".
        val fixtures = LegacyBackupFixtures.all.filter { it.ethereumAddress != null }
        assertEquals(4, fixtures.size)

        fixtures.forEach { fixture ->
            val decrypted = crypto.decryptAccount(mapper.mapJsonStringToEncryptedAccount(fixture.content), fixture.password)
            val publicKey = EthereumKeypairFactory.createWithPrivateKey(decrypted.seed!!.ethSeed!!.fromHex()).publicKey
            val address = ECDSAUtils.decompressed(publicKey).keccak256().copyOfRange(12, 32).toHexString(withPrefix = true)

            assertEquals(fixture.file, fixture.ethereumAddress, address)
        }
    }

    @Test
    fun `wrong password on passphrase throws DecryptionException`() = runBlocking<Unit> {
        val fixture = LegacyBackupFixtures.named("v1-passphrase-sr25519-paths.json")

        val failure = importFailure(fixture, "not the password")

        assertEquals(DecryptionException::class, failure::class)
    }

    @Test
    fun `wrong password on seed only maps to BackupWrongPasswordException`() = runBlocking<Unit> {
        listOf("v1-seed-substrate-ed25519.json", "v1-seed-both-ecdsa.json", "v1-seed-ethereum-only.json").forEach { file ->
            val failure = importFailure(LegacyBackupFixtures.named(file), "not the password")

            assertEquals(file, BackupWrongPasswordException::class, failure::class)
        }
    }

    @Test
    fun `wrong password on 4_2_0 mixed backup is a DecryptionException`() = runBlocking<Unit> {
        // 4.2.0 checks the seeds first, so this used to surface as IllegalArgumentException.
        val failure = importFailure(LegacyBackupFixtures.named("v1-mixed-4.2.0.json"), "not the password")

        assertTrue(failure is DecryptionException)
    }

    @Test
    fun `json only does not check password at decrypt`() {
        val fixture = LegacyBackupFixtures.named("v1-json-only.json")

        val decrypted = crypto.decryptAccount(mapper.mapJsonStringToEncryptedAccount(fixture.content), "any password")

        assertEquals(fixture.expected, decrypted)
    }

    @Test
    fun `empty derivation path round trips to null`() {
        val account = DecryptedBackupAccount(
            name = "Empty paths",
            address = "5FM25N8HGtacre9TnWQGRK32d1kUp1Gs6Dd7YiYpSPZLjmj6",
            mnemonicPhrase = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about",
            substrateDerivationPath = "",
            ethDerivationPath = "",
            cryptoType = CryptoType.ED25519,
            backupAccountType = listOf(BackupAccountType.PASSPHRASE)
        )

        val encrypted = crypto.encryptAccount(account, "password")
        val decrypted = crypto.decryptAccount(mapper.mapJsonStringToEncryptedAccount(mapper.mapEncryptedAccountToJson(encrypted)), "password")

        assertTrue(encrypted.encryptedSubstrateDerivationPath != null)
        assertNull(decrypted.substrateDerivationPath)
        assertNull(decrypted.ethDerivationPath)
        assertEquals(account.mnemonicPhrase, decrypted.mnemonicPhrase)
    }

    private suspend fun importFailure(fixture: LegacyBackupFixtures.Fixture, password: String): Exception {
        val storage = FakeStorage().apply { addAppFile(fixture.expected.address, "file", fixture.content) }
        try {
            backupService(storage).importBackupAccount(fixture.expected.address, password)
        } catch (e: Exception) {
            return e
        }
        fail("${fixture.file} opened with a wrong password")
        error("unreachable")
    }
}
