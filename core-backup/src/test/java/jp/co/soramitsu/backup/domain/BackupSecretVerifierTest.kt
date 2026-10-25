package jp.co.soramitsu.backup.domain

import jp.co.soramitsu.backup.LegacyBackupFixtures
import jp.co.soramitsu.backup.domain.exceptions.BackupSecretNotVerifiedException
import jp.co.soramitsu.backup.domain.models.BackupAccountType
import jp.co.soramitsu.backup.domain.models.DecryptedBackupAccount
import jp.co.soramitsu.backup.domain.models.Json
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** Critique D-7: a phrase or seed that restores a different account is never uploaded. */
class BackupSecretVerifierTest {

    private val verifier = BackupSecretVerifier()

    // 4.2.0 backup of an ed25519 mnemonic wallet: PASSPHRASE, SEED and JSON, all matching the wallet.
    private val mixed = LegacyBackupFixtures.named("v1-mixed-4.2.0.json").expected

    @Test
    fun `derivation matches independent anchors`() {
        // subkey: ed25519 //Alice of the Substrate development phrase.
        assertEquals(
            "88dc3417d5058ec4b4503e0c12ea1a0a89be200fe98922423d4334014fa6b0ee",
            FearlessUtilsBackupKeyDerivation.substrateAccountIdFromMnemonic(DEV_PHRASE, "//Alice", CryptoType.ED25519).toHexString()
        )
        // BIP-44 m/44'/60'/0'/0/0 of "abandon ... about", the key behind 0x9858EfFD232B4033E47d90003D41EC34EcaEda94.
        assertEquals(
            "1ab42cc412b618bdea3a599e3c9bae199ebf030895b039e9db1e30dafb12b727",
            FearlessUtilsBackupKeyDerivation.ethereumPrivateKeyFromMnemonic(ABANDON_PHRASE, "//44//60//0/0/0").toHexString()
        )
    }

    @Test
    fun `passes material that restores the wallet unchanged`() {
        assertEquals(mixed, verifier.verifiedForUpload(mixed))
    }

    @Test
    fun `accepts every 4_2_0 fixture whose key types derive on the JVM`() {
        // sr25519 needs the native library, which JVM tests do not load; see the sr25519 routing test.
        listOf("v1-passphrase-ed25519-empty-paths.json", "v1-seed-substrate-ed25519.json", "v1-seed-both-ecdsa.json", "v1-json-only.json").forEach { file ->
            val account = LegacyBackupFixtures.named(file).expected
            assertEquals(file, account, verifier.verifiedForUpload(account))
        }
    }

    @Test
    fun `refuses a phrase that derives another substrate account`() {
        val drifted = mixed.copy(mnemonicPhrase = LEGAL_PHRASE)

        assertRefused(BackupAccountType.PASSPHRASE, drifted)
    }

    @Test
    fun `refuses a phrase whose substrate path does not lead to the wallet`() {
        assertRefused(BackupAccountType.PASSPHRASE, mixed.copy(substrateDerivationPath = "//polkadot"))
        assertRefused(BackupAccountType.PASSPHRASE, mixed.copy(substrateDerivationPath = "not a path"))
    }

    @Test
    fun `refuses a declared passphrase that is missing or not bip39`() {
        assertRefused(BackupAccountType.PASSPHRASE, mixed.copy(mnemonicPhrase = null))
        assertRefused(BackupAccountType.PASSPHRASE, mixed.copy(mnemonicPhrase = "abandon abandon abandon"))
    }

    @Test
    fun `refuses a seed that derives another substrate account`() {
        val legalSeed = LegacyBackupFixtures.named("v1-seed-both-ecdsa.json").expected.seed!!.substrateSeed

        assertRefused(BackupAccountType.SEED, mixed.copy(seed = mixed.seed!!.copy(substrateSeed = legalSeed)))
    }

    @Test
    fun `refuses a seed backup that cannot restore the substrate account`() {
        val ethereumOnly = LegacyBackupFixtures.named("v1-seed-ethereum-only.json").expected

        assertRefused(BackupAccountType.SEED, ethereumOnly)
        assertRefused(BackupAccountType.SEED, mixed.copy(seed = mixed.seed!!.copy(ethSeed = "0x1234")))
    }

    @Test
    fun `refuses a passphrase alone when it derives another ethereum key than the wallet has`() {
        val otherEthereumKey = LegacyBackupFixtures.named("v1-seed-both-ecdsa.json").expected.seed!!.ethSeed
        val passphraseOnly = mixed.copy(
            backupAccountType = listOf(BackupAccountType.PASSPHRASE, BackupAccountType.JSON),
            seed = mixed.seed!!.copy(ethSeed = otherEthereumKey)
        )

        assertRefused(BackupAccountType.PASSPHRASE, passphraseOnly)
        assertRefused(BackupAccountType.PASSPHRASE, passphraseOnly.copy(ethDerivationPath = ""))
    }

    @Test
    fun `lets the ethereum key travel in the seed when the phrase does not derive it`() {
        // As restored by the RC importer: phrase for Substrate, the backed-up key for Ethereum.
        val otherEthereumKey = LegacyBackupFixtures.named("v1-seed-both-ecdsa.json").expected.seed!!.ethSeed
        val account = mixed.copy(seed = mixed.seed!!.copy(ethSeed = otherEthereumKey))

        assertEquals(account, verifier.verifiedForUpload(account))
    }

    @Test
    fun `drops material of undeclared types`() {
        val jsonOnly = mixed.copy(backupAccountType = listOf(BackupAccountType.JSON))

        val verified = verifier.verifiedForUpload(jsonOnly)

        assertNull(verified.mnemonicPhrase)
        assertNull(verified.seed)
        assertEquals(mixed.json, verified.json)
    }

    @Test
    fun `drops the seed and json of a verified passphrase backup without those types`() {
        val passphraseOnly = mixed.copy(backupAccountType = listOf(BackupAccountType.PASSPHRASE))

        val verified = verifier.verifiedForUpload(passphraseOnly)

        assertEquals(mixed.mnemonicPhrase, verified.mnemonicPhrase)
        assertNull(verified.seed)
        assertNull(verified.json)
    }

    @Test
    fun `refuses json that cannot restore the substrate account`() {
        val jsonOnly = mixed.copy(backupAccountType = listOf(BackupAccountType.JSON))

        assertRefused(BackupAccountType.JSON, jsonOnly.copy(json = Json(substrateJson = null, ethJson = "{}")))
        assertRefused(BackupAccountType.JSON, jsonOnly.copy(json = Json(substrateJson = "not json", ethJson = null)))
        assertRefused(BackupAccountType.JSON, jsonOnly.copy(json = Json(substrateJson = keystore(address = null), ethJson = null)))
    }

    @Test
    fun `refuses a json export of another account on its own`() {
        // A wallet whose Polkadot account is a separate chain account exports that account's keypair.
        val chainAccountJson = Json(substrateJson = keystore(OTHER_ADDRESS), ethJson = mixed.json!!.ethJson)

        assertRefused(BackupAccountType.JSON, mixed.copy(backupAccountType = listOf(BackupAccountType.JSON), json = chainAccountJson))
    }

    @Test
    fun `drops a json export of another account beside a verified phrase and seed`() {
        val chainAccountJson = Json(substrateJson = keystore(OTHER_ADDRESS), ethJson = mixed.json!!.ethJson)

        val verified = verifier.verifiedForUpload(mixed.copy(json = chainAccountJson))

        assertEquals(listOf(BackupAccountType.PASSPHRASE, BackupAccountType.SEED), verified.backupAccountType)
        assertNull(verified.json)
        assertEquals(mixed.mnemonicPhrase, verified.mnemonicPhrase)
        assertEquals(mixed.seed, verified.seed)
    }

    @Test
    fun `keeps a json export filed under another network prefix of the same account`() {
        val polkadotAddress = SS58Encoder.encode(SS58Encoder.decode(mixed.address), POLKADOT_PREFIX)
        val account = mixed.copy(json = mixed.json!!.copy(substrateJson = keystore(polkadotAddress)))

        assertEquals(account, verifier.verifiedForUpload(account))
    }

    @Test
    fun `requires a backup type and an ss58 address`() {
        assertThrows(IllegalArgumentException::class.java) {
            verifier.verifiedForUpload(mixed.copy(backupAccountType = emptyList()))
        }
        assertThrows(IllegalArgumentException::class.java) { verifier.verifiedForUpload(mixed.copy(address = "")) }
    }

    @Test
    fun `routes sr25519 through the importer derivation`() {
        val sr25519 = LegacyBackupFixtures.named("v1-passphrase-sr25519-path-password.json").expected
        val derivation = RecordingDerivation(SS58Encoder.decode(sr25519.address))

        BackupSecretVerifier(derivation).verifiedForUpload(sr25519)

        assertEquals(listOf(Triple(sr25519.mnemonicPhrase, sr25519.substrateDerivationPath, CryptoType.SR25519)), derivation.mnemonicCalls)
        assertRefused(BackupAccountType.PASSPHRASE, sr25519, BackupSecretVerifier(RecordingDerivation(ByteArray(32))))
    }

    @Test
    fun `restoring types name the phrase and seed of a downloaded backup that restore its wallet`() {
        assertEquals(setOf(BackupAccountType.PASSPHRASE, BackupAccountType.SEED), verifier.restoringSecretTypes(mixed))
        assertEquals(
            setOf(BackupAccountType.PASSPHRASE),
            verifier.restoringSecretTypes(LegacyBackupFixtures.named("v1-passphrase-ed25519-empty-paths.json").expected)
        )
        assertEquals(
            setOf(BackupAccountType.SEED),
            verifier.restoringSecretTypes(LegacyBackupFixtures.named("v1-seed-both-ecdsa.json").expected)
        )
    }

    @Test
    fun `restoring types leave out a phrase or seed an older app uploaded for another account`() {
        val legalSeed = LegacyBackupFixtures.named("v1-seed-both-ecdsa.json").expected.seed!!.substrateSeed

        assertEquals(setOf(BackupAccountType.SEED), verifier.restoringSecretTypes(mixed.copy(mnemonicPhrase = LEGAL_PHRASE)))
        assertEquals(setOf(BackupAccountType.PASSPHRASE), verifier.restoringSecretTypes(mixed.copy(seed = mixed.seed!!.copy(substrateSeed = legalSeed))))
        assertEquals(emptySet<BackupAccountType>(), verifier.restoringSecretTypes(mixed.copy(mnemonicPhrase = "abandon abandon", substrateDerivationPath = "not a path")))
        assertEquals(emptySet<BackupAccountType>(), verifier.restoringSecretTypes(LegacyBackupFixtures.named("v1-seed-ethereum-only.json").expected))
        assertEquals(emptySet<BackupAccountType>(), verifier.restoringSecretTypes(LegacyBackupFixtures.named("v1-json-only.json").expected))
    }

    @Test
    fun `restoring types are unknown when the backup address is not ss58`() {
        // Extension backups are filed under "" or an Ethereum address and carry only JSON.
        assertNull(verifier.restoringSecretTypes(mixed.copy(address = "")))
        assertNull(verifier.restoringSecretTypes(mixed.copy(address = "0x9858effd232b4033e47d90003d41ec34ecaeda94")))
    }

    @Test
    fun `restoring types route sr25519 through the importer derivation`() {
        val sr25519 = LegacyBackupFixtures.named("v1-passphrase-sr25519-path-password.json").expected
        val derivation = RecordingDerivation(SS58Encoder.decode(sr25519.address))

        assertEquals(setOf(BackupAccountType.PASSPHRASE), BackupSecretVerifier(derivation).restoringSecretTypes(sr25519))
        assertEquals(listOf(Triple(sr25519.mnemonicPhrase, sr25519.substrateDerivationPath, CryptoType.SR25519)), derivation.mnemonicCalls)
        assertEquals(emptySet<BackupAccountType>(), BackupSecretVerifier(RecordingDerivation(ByteArray(32))).restoringSecretTypes(sr25519))
    }

    private fun assertRefused(
        type: BackupAccountType,
        account: DecryptedBackupAccount,
        verifier: BackupSecretVerifier = this.verifier
    ) {
        val failure = assertThrows(BackupSecretNotVerifiedException::class.java) { verifier.verifiedForUpload(account) }
        assertEquals(type, failure.backupAccountType)
    }

    private fun keystore(address: String?) =
        """{"encoded":"x","encoding":{"content":["pkcs8","ed25519"],"type":["scrypt","xsalsa20-poly1305"],"version":"3"}""" +
            address?.let { ""","address":"$it"""" }.orEmpty() + "}"

    private class RecordingDerivation(private val accountId: ByteArray) : BackupKeyDerivation {
        val mnemonicCalls = mutableListOf<Triple<String?, String?, CryptoType>>()

        override fun substrateAccountIdFromMnemonic(
            mnemonic: String,
            derivationPath: String?,
            cryptoType: CryptoType
        ): ByteArray {
            mnemonicCalls += Triple(mnemonic, derivationPath, cryptoType)
            return accountId
        }

        override fun substrateAccountIdFromSeed(
            seed: ByteArray,
            derivationPath: String?,
            cryptoType: CryptoType
        ) = accountId

        override fun ethereumPrivateKeyFromMnemonic(mnemonic: String, derivationPath: String) = ByteArray(32)
    }

    private companion object {
        const val DEV_PHRASE = "bottom drive obey lake curtain smoke basket hold race lonely fit walk"
        const val ABANDON_PHRASE = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        const val LEGAL_PHRASE = "legal winner thank year wave sausage worth useful legal winner thank yellow"
        const val POLKADOT_PREFIX: Short = 0

        // The Substrate development account //Alice.
        const val OTHER_ADDRESS = "5GrwvaEF5zXb26Fz9rcQpDWS57CtERHpNehXCPcNoHGKutQY"
    }
}
