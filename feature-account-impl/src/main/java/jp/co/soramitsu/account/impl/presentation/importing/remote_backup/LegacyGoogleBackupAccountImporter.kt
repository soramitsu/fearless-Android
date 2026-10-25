package jp.co.soramitsu.account.impl.presentation.importing.remote_backup

import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.model.AddAccountPayload
import jp.co.soramitsu.account.api.domain.model.EthereumOnlyGoogleBackupException
import jp.co.soramitsu.backup.domain.models.BackupAccountType
import jp.co.soramitsu.backup.domain.models.DecryptedBackupAccount

/**
 * Import the original EVM key together with a Substrate mnemonic when both were backed up.
 *
 * Earlier releases uploaded phrases and seeds without checking them, so [restoringSecretTypes] names
 * the ones that restore the wallet the backup is filed under (null when that cannot be told). A
 * phrase or seed that restores another account is passed over for the seed or JSON beside it, and
 * used only when the backup holds nothing else, as before. The wallet it then creates is another
 * account, so it does not take the backup's address as its own.
 *
 * A new wallet needs a Substrate key, so seed and JSON material is used only when it carries one. An
 * Ethereum seed or keystore on its own is never passed where a Substrate one is expected: that
 * either fails late or derives a key from nothing.
 */
internal suspend fun importLegacyGoogleBackupAccount(
    interactor: AccountInteractor,
    backup: DecryptedBackupAccount,
    password: String,
    restoringSecretTypes: Set<BackupAccountType>? = null
) {
    val restoring = restoringSecretTypes ?: setOf(BackupAccountType.PASSPHRASE, BackupAccountType.SEED)
    val substrateSeed = backup.seed?.substrateSeed
    val substrateJson = backup.json?.substrateJson
    val seedRestores = substrateSeed != null && BackupAccountType.SEED in restoring
    val usesPhrase = BackupAccountType.PASSPHRASE in restoring || (!seedRestores && substrateJson == null)
    val usesSeed = seedRestores || substrateJson == null

    backup.mnemonicPhrase?.takeIf { usesPhrase }?.let { mnemonicPhrase ->
        val payload = AddAccountPayload.SubstrateOrEvm(
            accountName = backup.name,
            mnemonic = mnemonicPhrase,
            encryptionType = backup.cryptoType,
            substrateDerivationPath = backup.substrateDerivationPath.orEmpty(),
            ethereumDerivationPath = backup.ethDerivationPath.orEmpty(),
            googleBackupAddress = backup.address.takeIf { BackupAccountType.PASSPHRASE in restoring },
            isBackedUp = true
        )
        val backedUpEthereumPrivateKey = backup.seed?.ethSeed
        if (backedUpEthereumPrivateKey == null) {
            interactor.createAccount(payload).getOrThrow()
        } else {
            interactor.createAccountFromBackup(payload, backedUpEthereumPrivateKey).getOrThrow()
        }
        return
    }

    substrateSeed?.takeIf { usesSeed }?.let {
        interactor.importFromSeed(
            walletId = null,
            substrateSeed = substrateSeed,
            username = backup.name,
            derivationPath = backup.substrateDerivationPath.orEmpty(),
            selectedEncryptionType = backup.cryptoType,
            ethSeed = backup.seed?.ethSeed,
            googleBackupAddress = backup.address.takeIf { BackupAccountType.SEED in restoring }
        ).getOrThrow()
        return
    }

    substrateJson?.let {
        interactor.importFromJson(
            walletId = null,
            json = substrateJson,
            password = password,
            name = backup.name,
            ethJson = backup.json?.ethJson,
            googleBackupAddress = backup.address
        ).getOrThrow()
        return
    }

    if (backup.seed?.ethSeed != null || backup.json?.ethJson != null) {
        throw EthereumOnlyGoogleBackupException()
    }

    error("Backup has no recoverable wallet material")
}
