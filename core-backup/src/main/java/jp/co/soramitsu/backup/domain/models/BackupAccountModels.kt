package jp.co.soramitsu.backup.domain.models

import jp.co.soramitsu.core.models.CryptoType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A backup listed on Google Drive. [fileId] is the Drive file the entry was read from. Chrome
 * extension backups of Substrate-only wallets all have an empty [address], so only the file id
 * tells them apart.
 */
data class BackupAccountMeta(
    val name: String,
    val address: String,
    val fileId: String? = null
)

enum class BackupAccountType {
    PASSPHRASE,
    SEED,
    JSON
}

data class DecryptedBackupAccount(
    val name: String,
    val address: String,
    val mnemonicPhrase: String? = null,
    val substrateDerivationPath: String? = null,
    val ethDerivationPath: String? = null,
    val cryptoType: CryptoType,
    val backupAccountType: List<BackupAccountType>,
    val seed: Seed? = null,
    val json: Json? = null
)

/**
 * The Drive file format written by shared_features 1.0.1 (app 2.3.1) through 1.1.1.44 (app 4.2.0).
 * Field names and order must stay as they are: older apps read files written by this one.
 */
@Serializable
data class EncryptedBackupAccount(
    @SerialName("name") val name: String,
    @SerialName("address") val address: String,
    @SerialName("encryptedMnemonicPhrase") val encryptedMnemonicPhrase: String? = null,
    @SerialName("encryptedEthDerivationPath") val encryptedEthDerivationPath: String? = null,
    @SerialName("encryptedSubstrateDerivationPath") val encryptedSubstrateDerivationPath: String? = null,
    @SerialName("cryptoType") val cryptoType: String,
    // Missing from 1.0.1 files, which always hold a mnemonic.
    @SerialName("backupAccountType") val backupAccountType: List<String> = emptyList(),
    @SerialName("encryptedSeed") val encryptedSeed: Seed? = null,
    @SerialName("json") val json: Json? = null
)

@Serializable
data class Seed(
    @SerialName("substrateSeed") val substrateSeed: String? = null,
    @SerialName("ethSeed") val ethSeed: String? = null
)

@Serializable
data class Json(
    @SerialName("substrateJson") val substrateJson: String? = null,
    @SerialName("ethJson") val ethJson: String? = null
)
