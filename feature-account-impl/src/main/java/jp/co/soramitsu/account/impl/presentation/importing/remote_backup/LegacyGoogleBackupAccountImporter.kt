package jp.co.soramitsu.account.impl.presentation.importing.remote_backup

import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.model.AddAccountPayload
import jp.co.soramitsu.backup.domain.models.DecryptedBackupAccount

/** Import the original EVM key together with a Substrate mnemonic when both were backed up. */
internal suspend fun importLegacyGoogleBackupAccount(
    interactor: AccountInteractor,
    backup: DecryptedBackupAccount,
    password: String
) {
    backup.mnemonicPhrase?.let { mnemonicPhrase ->
        val payload = AddAccountPayload.SubstrateOrEvm(
            accountName = backup.name,
            mnemonic = mnemonicPhrase,
            encryptionType = backup.cryptoType,
            substrateDerivationPath = backup.substrateDerivationPath.orEmpty(),
            ethereumDerivationPath = backup.ethDerivationPath.orEmpty(),
            googleBackupAddress = backup.address,
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

    backup.seed?.let { seed ->
        interactor.importFromSeed(
            walletId = null,
            substrateSeed = seed.substrateSeed.orEmpty(),
            username = backup.name,
            derivationPath = backup.substrateDerivationPath.orEmpty(),
            selectedEncryptionType = backup.cryptoType,
            ethSeed = seed.ethSeed,
            googleBackupAddress = backup.address
        ).getOrThrow()
        return
    }

    backup.json?.let { json ->
        interactor.importFromJson(
            walletId = null,
            json = json.substrateJson.orEmpty(),
            password = password,
            name = backup.name,
            ethJson = json.ethJson,
            googleBackupAddress = backup.address
        ).getOrThrow()
        return
    }

    error("Backup has no recoverable wallet material")
}
