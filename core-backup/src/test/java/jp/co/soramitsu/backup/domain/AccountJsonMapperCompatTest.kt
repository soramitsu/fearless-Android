package jp.co.soramitsu.backup.domain

import jp.co.soramitsu.backup.LegacyBackupFixtures
import jp.co.soramitsu.backup.domain.exceptions.DecodingException
import jp.co.soramitsu.backup.domain.models.EncryptedBackupAccount
import jp.co.soramitsu.backup.domain.models.Seed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AccountJsonMapperCompatTest {

    private val mapper = AccountJsonMapper()

    // A valid scrypt/secretbox layout: salt, N = 32768, p = 1, r = 8, nonce, MAC of an empty message.
    private val encryptedField = "11".repeat(32) + "00800000" + "01000000" + "08000000" + "22".repeat(24) + "33".repeat(16)

    @Test
    fun `writes production field names and order with explicit nulls`() {
        val account = EncryptedBackupAccount(
            name = "Wallet",
            address = "5Address",
            encryptedMnemonicPhrase = "aa",
            cryptoType = "SR25519",
            backupAccountType = listOf("passphrase", "seed"),
            encryptedSeed = Seed(substrateSeed = "bb")
        )

        assertEquals(
            """{"name":"Wallet","address":"5Address","encryptedMnemonicPhrase":"aa","encryptedEthDerivationPath":null,""" +
                """"encryptedSubstrateDerivationPath":null,"cryptoType":"SR25519","backupAccountType":["passphrase","seed"],""" +
                """"encryptedSeed":{"substrateSeed":"bb","ethSeed":null},"json":null}""",
            mapper.mapEncryptedAccountToJson(account)
        )
    }

    @Test
    fun `production fixtures re-encode byte for byte`() {
        LegacyBackupFixtures.all.filter { it.file.startsWith("v1-") }.forEach { fixture ->
            assertEquals(fixture.file, fixture.content, mapper.mapEncryptedAccountToJson(mapper.mapJsonStringToEncryptedAccount(fixture.content)))
        }
    }

    @Test
    fun `parses files with missing optionals and unknown keys`() {
        val account = mapper.mapJsonStringToEncryptedAccount(
            """{"name":"W","address":"5A","cryptoType":"ED25519","backupAccountType":["seed"],""" +
                """"encryptedSeed":{"ethSeed":"$encryptedField","futureSeed":"x"},"futureField":{"a":1}}"""
        )

        assertEquals(Seed(substrateSeed = null, ethSeed = encryptedField), account.encryptedSeed)
        assertEquals(null, account.json)
    }

    @Test
    fun `infers passphrase for v0 files without backupAccountType`() {
        val account = mapper.mapJsonStringToEncryptedAccount(
            """{"name":"W","address":"5A","encryptedMnemonicPhrase":"$encryptedField",""" +
                """"encryptedEthDerivationPath":"$encryptedField","encryptedSubstrateDerivationPath":"$encryptedField","cryptoType":"SR25519"}"""
        )

        assertEquals(listOf("passphrase"), account.backupAccountType)
    }

    @Test
    fun `rejects files this version cannot read`() {
        val unreadable = mapOf(
            "not json" to "<html>",
            "future backup type" to """{"name":"W","address":"5A","cryptoType":"ED25519","backupAccountType":["ton"],"encryptedMnemonicPhrase":"$encryptedField"}""",
            "unknown crypto type" to """{"name":"W","address":"5A","cryptoType":"BLS","backupAccountType":["passphrase"],"encryptedMnemonicPhrase":"$encryptedField"}""",
            "no type and no phrase" to """{"name":"W","address":"5A","cryptoType":"ED25519","json":{"substrateJson":"{}"}}""",
            "passphrase without phrase" to """{"name":"W","address":"5A","cryptoType":"ED25519","backupAccountType":["passphrase"]}""",
            "seed without seeds" to """{"name":"W","address":"5A","cryptoType":"ED25519","backupAccountType":["seed"],"encryptedSeed":{}}""",
            "json without json" to """{"name":"W","address":"5A","cryptoType":"ED25519","backupAccountType":["json"]}""",
            "field not hex" to """{"name":"W","address":"5A","cryptoType":"ED25519","backupAccountType":["passphrase"],"encryptedMnemonicPhrase":"zz"}""",
            "field too short" to """{"name":"W","address":"5A","cryptoType":"ED25519","backupAccountType":["passphrase"],"encryptedMnemonicPhrase":"0011"}""",
            "foreign scrypt cost" to """{"name":"W","address":"5A","cryptoType":"ED25519","backupAccountType":["passphrase"],"encryptedMnemonicPhrase":"${encryptedField.replace("00800000", "00000040")}"}"""
        )

        unreadable.forEach { (case, content) ->
            assertThrows(case, DecodingException::class.java) { mapper.mapJsonStringToEncryptedAccount(content) }
        }
    }
}
