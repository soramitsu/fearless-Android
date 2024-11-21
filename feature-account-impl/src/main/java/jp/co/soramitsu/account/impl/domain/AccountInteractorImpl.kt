package jp.co.soramitsu.account.impl.domain

import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import java.io.File
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.Account
import jp.co.soramitsu.account.api.domain.model.AccountType
import jp.co.soramitsu.account.api.domain.model.AddAccountPayload
import jp.co.soramitsu.account.api.domain.model.ImportJsonData
import jp.co.soramitsu.account.api.domain.model.LightMetaAccount
import jp.co.soramitsu.account.api.domain.model.MetaAccountOrdering
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.backup.BackupService
import jp.co.soramitsu.backup.domain.models.BackupAccountMeta
import jp.co.soramitsu.backup.domain.models.DecryptedBackupAccount
import jp.co.soramitsu.backup.domain.models.Json
import jp.co.soramitsu.backup.domain.models.Seed
import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.secrets.v2.MetaAccountSecrets
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecrets
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.interfaces.FileProvider
import jp.co.soramitsu.core.model.Language
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.moonriverChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.polkadotChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.westendChainId
import jp.co.soramitsu.shared_utils.encrypt.mnemonic.Mnemonic
import jp.co.soramitsu.shared_utils.encrypt.mnemonic.MnemonicCreator
import jp.co.soramitsu.shared_utils.extensions.toHexString
import jp.co.soramitsu.shared_utils.scale.EncodableStruct
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

class AccountInteractorImpl(
    private val accountRepository: AccountRepository,
    private val fileProvider: FileProvider,
    private val preferences: Preferences,
    private val backupService: BackupService,
    private val context: CoroutineContext = Dispatchers.Default
) : AccountInteractor {

    override suspend fun generateMnemonic(length: Mnemonic.Length): List<String> {
        return accountRepository.generateMnemonic(length)
    }

    override fun getCryptoTypes(): List<CryptoType> {
        return accountRepository.getEncryptionTypes()
    }

    override suspend fun getPreferredCryptoType(): CryptoType {
        return accountRepository.getPreferredCryptoType()
    }

    override suspend fun createAccount(payload: AddAccountPayload): Result<Long> {
        return runCatching { accountRepository.createAccount(payload) }
    }

    override suspend fun createAccount(
        accountName: String,
        mnemonic: String,
        encryptionType: CryptoType,
        substrateDerivationPath: String,
        ethereumDerivationPath: String,
        accountType: AccountType,
        isBackedUp: Boolean
    ): Result<Unit> {
        return runCatching {
            accountRepository.createAccount(
                accountName,
                mnemonic,
                encryptionType,
                substrateDerivationPath,
                ethereumDerivationPath,
                isBackedUp
            )
        }
    }

    override suspend fun createChainAccount(
        metaId: Long,
        chainId: ChainId,
        accountName: String,
        mnemonicWords: String,
        cryptoType: CryptoType,
        substrateDerivationPath: String,
        ethereumDerivationPath: String
    ): Result<Unit> {
        return runCatching {
            accountRepository.createChainAccount(
                metaId,
                chainId,
                accountName,
                mnemonicWords,
                cryptoType,
                substrateDerivationPath,
                ethereumDerivationPath
            )
        }
    }

    override suspend fun importFromMnemonic(
        mnemonic: String,
        walletName: String,
        substrateDerivationPath: String,
        ethereumDerivationPath: String,
        selectedEncryptionType: CryptoType,
        withEth: Boolean,
        isBackedUp: Boolean,
        googleBackupAddress: String?
    ): Result<Long> {
        return runCatching {
            accountRepository.importFromMnemonic(
                mnemonic = mnemonic,
                accountName = walletName,
                substrateDerivationPath = substrateDerivationPath,
                ethereumDerivationPath = ethereumDerivationPath,
                selectedEncryptionType = selectedEncryptionType,
                withEth = withEth,
                isBackedUp = isBackedUp,
                googleBackupAddress = googleBackupAddress
            )
        }
    }

    override suspend fun importChainAccountFromMnemonic(
        metaId: Long,
        chainId: ChainId,
        accountName: String,
        mnemonicWords: String,
        cryptoType: CryptoType,
        substrateDerivationPath: String,
        ethereumDerivationPath: String
    ): Result<Unit> {
        return runCatching {
            accountRepository.importChainAccountFromMnemonic(
                metaId,
                chainId,
                accountName,
                mnemonicWords,
                cryptoType,
                substrateDerivationPath,
                ethereumDerivationPath
            )
        }
    }

    override suspend fun importFromSeed(
        substrateSeed: String,
        username: String,
        derivationPath: String,
        selectedEncryptionType: CryptoType,
        ethSeed: String?,
        googleBackupAddress: String?
    ): Result<Unit> {
        return runCatching {
            accountRepository.importFromSeed(
                substrateSeed,
                username,
                derivationPath,
                selectedEncryptionType,
                ethSeed,
                googleBackupAddress
            )
        }
    }

    @Deprecated("We don't import chain accounts anymore. Only ecosystem account import is allowed")
    override suspend fun importChainFromSeed(
        metaId: Long,
        chainId: ChainId,
        accountName: String,
        seed: String,
        substrateDerivationPath: String,
        selectedEncryptionType: CryptoType
    ): Result<Unit> {
        return runCatching {
            accountRepository.importChainFromSeed(
                metaId,
                chainId,
                accountName,
                seed,
                substrateDerivationPath,
                selectedEncryptionType
            )
        }
    }

    override fun validateJsonBackup(json: String, password: String) {
        accountRepository.validateJsonBackup(json, password)
    }

    override suspend fun importFromJson(
        json: String,
        password: String,
        name: String,
        ethJson: String?,
        googleBackupAddress: String?
    ): Result<Unit> {
        return runCatching {
            accountRepository.importFromJson(json, password, name, ethJson, googleBackupAddress)
        }
    }

    override suspend fun importChainFromJson(
        metaId: Long,
        chainId: ChainId,
        accountName: String,
        json: String,
        password: String
    ): Result<Unit> {
        return runCatching {
            accountRepository.importChainFromJson(metaId, chainId, accountName, json, password)
        }
    }

    override suspend fun isCodeSet(): Boolean {
        return accountRepository.isCodeSet()
    }

    override suspend fun savePin(code: String) {
        return accountRepository.savePinCode(code)
    }

    override suspend fun isPinCorrect(code: String): Boolean {
        val pinCode = accountRepository.getPinCode()

        return pinCode == code
    }

    override suspend fun isBiometricEnabled(): Boolean {
        return accountRepository.isBiometricEnabled()
    }

    override suspend fun setBiometricOn() {
        return accountRepository.setBiometricOn()
    }

    override suspend fun setBiometricOff() {
        return accountRepository.setBiometricOff()
    }

    override suspend fun getAccount(address: String): Account {
        return accountRepository.getAccount(address)
    }

    override fun selectedAccountFlow() = accountRepository.selectedAccountFlow()

    override fun selectedMetaAccountFlow() = accountRepository.selectedMetaAccountFlow()

    override suspend fun selectedMetaAccount() =
        withContext(context) { accountRepository.getSelectedMetaAccount() }

    override suspend fun selectedLightMetaAccount() =
        accountRepository.getSelectedLightMetaAccount()

    override fun lightMetaAccountsFlow(): Flow<List<LightMetaAccount>> {
        return accountRepository.lightMetaAccountsFlow()
    }

    override suspend fun selectMetaAccount(metaId: Long) {
        accountRepository.selectMetaAccount(metaId)
    }

    override suspend fun deleteAccount(metaId: Long) = withContext(Dispatchers.Default) {
        accountRepository.deleteAccount(metaId)
    }

    override suspend fun updateAccountPositionsInNetwork(idsInNewOrder: List<Long>) =
        with(Dispatchers.Default) {
            val ordering = idsInNewOrder.mapIndexed { index, id ->
                MetaAccountOrdering(id, index)
            }

            accountRepository.updateAccountsOrdering(ordering)
        }

    override suspend fun processAccountJson(json: String): Result<ImportJsonData> {
        return runCatching {
            accountRepository.processAccountJson(json)
        }
    }

    override fun getLanguages(): List<Language> {
        return accountRepository.getLanguages()
    }

    override suspend fun getSelectedLanguage(): Language {
        return accountRepository.selectedLanguage()
    }

    override suspend fun changeSelectedLanguage(language: Language) {
        return accountRepository.changeLanguage(language)
    }

    override suspend fun generateRestoreJson(metaId: Long, chainId: ChainId, password: String) =
        runCatching {
            accountRepository.generateRestoreJson(metaId, chainId, password)
        }

    override suspend fun getMetaAccount(metaId: Long) = accountRepository.getMetaAccount(metaId)

    override suspend fun getChainAccountSecrets(metaId: Long, chainId: ChainId) =
        accountRepository.getChainAccountSecrets(metaId, chainId)

    override fun polkadotAddressForSelectedAccountFlow() =
        accountRepository.polkadotAddressForSelectedAccountFlow()

    override fun getMetaAccountsGoogleAddresses(): Flow<List<String>> =
        accountRepository.googleAddressAllWalletsFlow()

    override suspend fun googleBackupAddressForWallet(walletId: Long) =
        accountRepository.googleBackupAddressForWallet(walletId)

    override suspend fun isGoogleBackupSupported(walletId: Long) =
        accountRepository.isGoogleBackupSupported(walletId)

    override suspend fun getSupportedBackupTypes(walletId: Long) =
        accountRepository.getSupportedBackupTypes(walletId)

    override suspend fun getChain(chainId: ChainId) = accountRepository.getChain(chainId)

    override suspend fun createFileInTempStorageAndRetrieveAsset(fileName: String): Result<File> =
        runCatching {
            fileProvider.getFileInExternalCacheStorage(fileName)
        }

    override suspend fun updateAccountName(metaId: Long, name: String) {
        accountRepository.updateMetaAccountName(metaId, name)
    }

    override suspend fun updateWalletBackedUp(metaId: Long) {
        accountRepository.updateMetaAccountBackedUp(metaId)
    }

    override suspend fun updateWalletOnGoogleBackupDelete(metaId: Long) {
        accountRepository.updateWalletOnGoogleBackupDelete(metaId)
    }

    override suspend fun updateFavoriteChain(chainId: ChainId, isFavorite: Boolean, metaId: Long) {
        accountRepository.updateFavoriteChain(metaId, chainId, isFavorite)
    }

    override fun observeSelectedMetaAccountFavoriteChains(): Flow<Map<ChainId, Boolean>> {
        return accountRepository.selectedMetaAccountFlow()
            .flatMapLatest {
                accountRepository.observeFavoriteChains(it.id)
            }.flowOn(Dispatchers.IO)
    }

    override suspend fun saveGoogleBackupAccount(metaId: Long, googleBackupPassword: Int) {
        withContext(Dispatchers.IO) {
            val wallet = getMetaAccount(metaId)
            val westendChain = getChain(westendChainId)
            val googleBackupAddress = wallet.address(westendChain) ?: error("error obtaining google backup address")

            val jsonResult = generateRestoreJson(
                metaId = metaId,
                chainId = polkadotChainId,
                password = googleBackupPassword.toString()
            )
            val substrateJson = jsonResult.getOrNull()
            val ethJsonResult = generateRestoreJson(
                metaId = metaId,
                chainId = moonriverChainId,
                password = googleBackupPassword.toString()
            )
            val ethJson = ethJsonResult.getOrNull()

            val substrateSecrets = accountRepository.getSubstrateSecrets(metaId)
            val ethereumSecrets = accountRepository.getEthereumSecrets(metaId)

            val substrateDerivationPath = substrateSecrets?.get(SubstrateSecrets.SubstrateDerivationPath).orEmpty()
            val ethereumDerivationPath = ethereumSecrets?.get(EthereumSecrets.EthereumDerivationPath).orEmpty()
            val entropy = substrateSecrets?.get(SubstrateSecrets.Entropy)?.clone()
            val mnemonic = entropy?.let { MnemonicCreator.fromEntropy(it).words }
            val substrateSeed = substrateSecrets?.get(SubstrateSecrets.Seed)?.toHexString(true)
            val ethSeed = ethereumSecrets?.get(EthereumSecrets.EthereumKeypair)?.get(KeyPairSchema.PrivateKey)?.toHexString(withPrefix = true)

            val backupAccountTypes = getSupportedBackupTypes(metaId).toList()

            backupService.saveBackupAccount(
                account = DecryptedBackupAccount(
                    name = wallet.name,
                    address = googleBackupAddress,
                    mnemonicPhrase = mnemonic,
                    substrateDerivationPath = substrateDerivationPath,
                    ethDerivationPath = ethereumDerivationPath,
                    cryptoType = wallet.substrateCryptoType!!,
                    backupAccountType = backupAccountTypes,
                    seed = Seed(substrateSeed = substrateSeed, ethSeed),
                    json = Json(substrateJson = substrateJson, ethJson)
                ),
                password = googleBackupPassword.toString()
            )
        }
    }

    override suspend fun getGoogleBackupAccounts(): List<BackupAccountMeta> {
        return backupService.getBackupAccounts()
    }

    override suspend fun getExtensionGoogleBackups(): List<BackupAccountMeta> {
        return backupService.getWebBackupAccounts()
    }

    override suspend fun deleteGoogleBackupAccount(walletId: Long, address: String) {
        kotlin.runCatching { backupService.deleteBackupAccount(address) }
            .onSuccess {
                updateWalletOnGoogleBackupDelete(walletId)
            }
    }

    override suspend fun authorizeGoogleBackup(launcher: ActivityResultLauncher<Intent>): Boolean {
        backupService.logout()
        return backupService.authorize(launcher)
    }

    override suspend fun getSubstrateSecrets(metaId: Long): EncodableStruct<SubstrateSecrets>? {
        return accountRepository.getSubstrateSecrets(metaId)
    }
}
