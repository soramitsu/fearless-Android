package jp.co.soramitsu.backup.domain

import jp.co.soramitsu.backup.domain.exceptions.DecodingException
import jp.co.soramitsu.backup.domain.models.BackupAccountType
import jp.co.soramitsu.backup.domain.models.EncryptedBackupAccount
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal class AccountJsonMapper(private val json: Json = backupJson()) {

    fun mapEncryptedAccountToJson(encryptedAccount: EncryptedBackupAccount): String {
        return json.encodeToString(encryptedAccount)
    }

    fun mapJsonStringToEncryptedAccount(jsonString: String): EncryptedBackupAccount {
        val encryptedAccount = try {
            json.decodeFromString<EncryptedBackupAccount>(jsonString)
        } catch (e: SerializationException) {
            throw DecodingException(e)
        }

        return encryptedAccount.withLegacyBackupAccountType().also(::requireDecryptable)
    }

    // shared_features 1.0.1 (app 2.3.1) wrote no backupAccountType: every file held a mnemonic.
    private fun EncryptedBackupAccount.withLegacyBackupAccountType(): EncryptedBackupAccount {
        if (backupAccountType.isNotEmpty() || encryptedMnemonicPhrase == null) return this

        return copy(backupAccountType = listOf(BackupAccountType.PASSPHRASE.name.lowercase()))
    }

    // Rejects files that BackupAccountCrypto would fail on for reasons other than the password, so
    // that every failure left at decryption time means "wrong password".
    private fun requireDecryptable(account: EncryptedBackupAccount) {
        val types = account.backupAccountType.map { type ->
            BackupAccountType.entries.firstOrNull { it.name == type.uppercase() }
        }
        val encryptedSeed = account.encryptedSeed
        val problem = when {
            types.isEmpty() -> "Backup declares no backup type"
            null in types -> "Unsupported backup type in ${account.backupAccountType}"
            CryptoType.entries.none { it.name == account.cryptoType } -> "Unsupported crypto type ${account.cryptoType}"
            !types.filterNotNull().all { type ->
                when (type) {
                    BackupAccountType.PASSPHRASE -> account.encryptedMnemonicPhrase != null
                    BackupAccountType.SEED -> encryptedSeed?.substrateSeed != null || encryptedSeed?.ethSeed != null
                    BackupAccountType.JSON -> account.json != null
                }
            } -> "Backup misses the material of a declared type"
            else -> null
        }
        if (problem != null) throw decodingError(problem)

        listOfNotNull(
            account.encryptedMnemonicPhrase,
            account.encryptedSubstrateDerivationPath,
            account.encryptedEthDerivationPath,
            encryptedSeed?.substrateSeed,
            encryptedSeed?.ethSeed
        ).forEach(::requireScryptSecretBox)
    }

    // Every writer used `salt32 | N=32768 | p=1 | r=8 | nonce24 | secretbox`. Anything else is a
    // damaged file, and an arbitrary N would make scrypt exhaust memory.
    private fun requireScryptSecretBox(encryptedHex: String) {
        val bytes = try {
            encryptedHex.fromHex()
        } catch (e: IllegalStateException) {
            throw DecodingException(e)
        }
        if (bytes.size < MIN_ENCRYPTED_SIZE ||
            !bytes.copyOfRange(SALT_SIZE, SALT_SIZE + SCRYPT_PARAMS_SIZE).contentEquals(SCRYPT_PARAMS)
        ) {
            throw decodingError("Encrypted field has an unexpected layout")
        }
    }

    private fun decodingError(message: String) = DecodingException(IllegalArgumentException(message))

    private companion object {
        const val SALT_SIZE = 32
        const val SCRYPT_PARAMS_SIZE = 12
        const val NONCE_SIZE = 24
        const val MAC_SIZE = 16
        const val MIN_ENCRYPTED_SIZE = SALT_SIZE + SCRYPT_PARAMS_SIZE + NONCE_SIZE + MAC_SIZE

        // N = 32768, p = 1, r = 8, each little-endian u32.
        val SCRYPT_PARAMS = byteArrayOf(0, -128, 0, 0, 1, 0, 0, 0, 8, 0, 0, 0)
    }
}

// shared_features core JsonFactory.create(): every Fearless backup file was written with these settings.
private fun backupJson() = Json {
    prettyPrint = false
    isLenient = true
    ignoreUnknownKeys = true
    coerceInputValues = true
    encodeDefaults = true
}
