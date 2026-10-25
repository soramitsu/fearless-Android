package jp.co.soramitsu.backup.domain

import jp.co.soramitsu.backup.domain.exceptions.DecryptionException
import jp.co.soramitsu.backup.domain.models.BackupAccountType
import jp.co.soramitsu.backup.domain.models.DecryptedBackupAccount
import jp.co.soramitsu.backup.domain.models.EncryptedBackupAccount
import jp.co.soramitsu.backup.domain.models.Seed
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.fearless_utils.encrypt.json.coders.type.cryptor.XSalsa20Poly1305Cryptor
import jp.co.soramitsu.fearless_utils.encrypt.json.coders.type.keyGenerator.ScryptKeyGenerator
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import kotlin.IllegalStateException

internal class BackupAccountCrypto {
    fun encryptAccount(decryptedBackupAccount: DecryptedBackupAccount, password: String): EncryptedBackupAccount {
        val encryptedSubstrateDerivationPathBytes =
            decryptedBackupAccount.substrateDerivationPath?.let {
                encrypt(decryptedBackupAccount.substrateDerivationPath, password)
            }

        val encryptedEthDerivationPathBytes = decryptedBackupAccount.ethDerivationPath?.let {
            encrypt(decryptedBackupAccount.ethDerivationPath, password)
        }

        val encryptedMnemonicPhraseBytes = decryptedBackupAccount.mnemonicPhrase?.let {
            encrypt(decryptedBackupAccount.mnemonicPhrase, password)
        }

        val encryptedSeed = decryptedBackupAccount.seed?.let {
            val encryptedEthSeed = decryptedBackupAccount.seed.ethSeed?.let {
                encrypt(it, password)
            }
            val encryptedSubstrateSeed = decryptedBackupAccount.seed.substrateSeed?.let {
                encrypt(it, password)
            }
            Seed(
                encryptedSubstrateSeed,
                encryptedEthSeed
            )
        }

        return EncryptedBackupAccount(
            name = decryptedBackupAccount.name,
            address = decryptedBackupAccount.address,
            encryptedMnemonicPhrase = encryptedMnemonicPhraseBytes,
            encryptedEthDerivationPath = encryptedEthDerivationPathBytes,
            encryptedSubstrateDerivationPath = encryptedSubstrateDerivationPathBytes,
            cryptoType = decryptedBackupAccount.cryptoType.toString(),
            json = decryptedBackupAccount.json,
            encryptedSeed = encryptedSeed,
            backupAccountType = decryptedBackupAccount.backupAccountType.map {
                it.toString().lowercase()
            }
        )
    }

    fun decryptAccount(encryptedBackupAccount: EncryptedBackupAccount, password: String): DecryptedBackupAccount {
        val types = encryptedBackupAccount.backupAccountType
            .map {
                BackupAccountType.valueOf(it.uppercase())
            }

        var seed: Seed? = null
        if (types.contains(BackupAccountType.SEED)) {
            val encryptedSeed = requireNotNull(encryptedBackupAccount.encryptedSeed) {
                "${BackupAccountType.SEED} is selected but encryptedSeed is null"
            }

            if (encryptedSeed.substrateSeed == null && encryptedSeed.ethSeed == null) {
                throw IllegalArgumentException(
                    "${BackupAccountType.SEED} is selected but" +
                            "encryptedSeed.ethSeed == null and encryptedSeed.substrateSeed == null"
                )
            }

            val substrateSeed = encryptedSeed.substrateSeed?.let {
                decrypt(it, password)
            }

            val ethSeed = encryptedSeed.ethSeed?.let {
                decrypt(it, password)
            }

            if (substrateSeed == null && ethSeed == null) {
                throw IllegalArgumentException(
                    "${BackupAccountType.SEED} is selected but" +
                            "ethSeed == null and substrateSeed == null"
                )
            }

            seed = Seed(substrateSeed = substrateSeed, ethSeed = ethSeed)
        }

        var passphrase: String? = null
        if (types.contains(BackupAccountType.PASSPHRASE)) {
            val encryptedMnemonic = requireNotNull(encryptedBackupAccount.encryptedMnemonicPhrase) {
                "${BackupAccountType.PASSPHRASE} is selected but encryptedMnemonicPhrase is null"
            }

            passphrase = decryptNotNull(encryptedMnemonic, password)
        }

        if (types.contains(BackupAccountType.JSON)) {
            requireNotNull(encryptedBackupAccount.json) {
                "${BackupAccountType.JSON} is selected but json is null"
            }
        }

        val ethDerivationPath = encryptedBackupAccount.encryptedEthDerivationPath?.let {
            decrypt(encryptedBackupAccount.encryptedEthDerivationPath, password)
        }

        val substrateDerivationPath = encryptedBackupAccount.encryptedSubstrateDerivationPath?.let {
            decrypt(encryptedBackupAccount.encryptedSubstrateDerivationPath, password)
        }

        return DecryptedBackupAccount(
            name = encryptedBackupAccount.name,
            address = encryptedBackupAccount.address,
            mnemonicPhrase = passphrase,
            substrateDerivationPath = substrateDerivationPath,
            ethDerivationPath = ethDerivationPath,
            cryptoType = CryptoType.valueOf(encryptedBackupAccount.cryptoType),
            json = encryptedBackupAccount.json,
            seed = seed,
            backupAccountType = types
        )
    }

    private fun encrypt(message: String, password: String): String {
        return XSalsa20Poly1305Cryptor.encrypt(
            ScryptKeyGenerator.generate(password.toByteArray()),
            message.toByteArray()
        ).toHexString()
    }

    private fun decryptNotNull(encryptedMessage: String, password: String): String {
        return decrypt(encryptedMessage, password) ?: throw DecryptionException()
    }

    private fun decrypt(encryptedMessage: String, password: String): String? {
        val encryptedMessageBytes = try {
            encryptedMessage.fromHex()
        } catch (e: IllegalStateException) {
            throw DecryptionException(e)
        }

        return XSalsa20Poly1305Cryptor.decrypt(
            ScryptKeyGenerator.generate(encryptedMessageBytes, password.toByteArray())
        )?.let {
            String(it)
        }
    }
}
