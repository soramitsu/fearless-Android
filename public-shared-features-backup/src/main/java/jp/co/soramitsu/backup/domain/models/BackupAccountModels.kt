package jp.co.soramitsu.backup.domain.models

import jp.co.soramitsu.core.models.CryptoType

data class BackupAccountMeta(
    val name: String,
    val address: String
)

enum class BackupAccountType {
    PASSPHRASE,
    SEED,
    JSON
}

data class DecryptedBackupAccount(
    val name: String,
    val address: String,
    val mnemonicPhrase: String?,
    val substrateDerivationPath: String?,
    val ethDerivationPath: String?,
    val cryptoType: CryptoType,
    val backupAccountType: List<BackupAccountType>,
    val seed: Seed?,
    val json: Json?
)

data class Seed(
    val substrateSeed: String?,
    val ethSeed: String?
)

data class Json(
    val substrateJson: String?,
    val ethJson: String?
)
