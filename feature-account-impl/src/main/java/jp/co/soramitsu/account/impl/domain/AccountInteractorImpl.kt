package jp.co.soramitsu.account.impl.domain

import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.AddAccountPayload
import jp.co.soramitsu.account.api.domain.model.ImportJsonData
import jp.co.soramitsu.account.api.domain.model.LightMetaAccount
import jp.co.soramitsu.account.api.domain.model.MetaAccountOrdering
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.account.api.domain.model.supportedEcosystems
import jp.co.soramitsu.backup.BackupService
import jp.co.soramitsu.backup.domain.models.BackupAccountMeta
import jp.co.soramitsu.backup.domain.models.BackupAccountType
import jp.co.soramitsu.backup.domain.models.DecryptedBackupAccount
import jp.co.soramitsu.backup.domain.models.Json
import jp.co.soramitsu.backup.domain.models.Seed
import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecrets
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.data.secrets.v3.TonSecrets
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.interfaces.FileProvider
import jp.co.soramitsu.common.model.WalletEcosystem
import jp.co.soramitsu.common.utils.ComponentHolder
import jp.co.soramitsu.common.utils.DEFAULT_DERIVATION_PATH
import jp.co.soramitsu.common.utils.deriveSeed32
import jp.co.soramitsu.common.utils.nullIfEmpty
import jp.co.soramitsu.core.model.Language
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.moonriverChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.polkadotChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.westendChainId
import jp.co.soramitsu.shared_utils.encrypt.junction.BIP32JunctionDecoder
import jp.co.soramitsu.shared_utils.encrypt.junction.SubstrateJunctionDecoder
import jp.co.soramitsu.shared_utils.encrypt.mnemonic.EnglishWordList
import jp.co.soramitsu.shared_utils.encrypt.mnemonic.Mnemonic
import jp.co.soramitsu.shared_utils.encrypt.mnemonic.MnemonicCreator
import jp.co.soramitsu.shared_utils.encrypt.seed.substrate.SubstrateSeedFactory
import jp.co.soramitsu.shared_utils.extensions.toHexString
import jp.co.soramitsu.shared_utils.scale.EncodableStruct
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.CoroutineContext

class AccountInteractorImpl(
    private val accountRepository: AccountRepository,
    private val fileProvider: FileProvider,
    private val preferences: Preferences,
    private val backupService: BackupService,
    private val walletInteractor: WalletInteractor,
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

    override suspend fun saveChainSelectFilter(metaId: Long, filterValue: String){
        walletInteractor.saveChainSelectFilter(metaId, filterValue)
    }

    override suspend fun importFromSeed(
        walletId: Long?,
        substrateSeed: String?,
        username: String,
        derivationPath: String,
        selectedEncryptionType: CryptoType,
        ethSeed: String?,
        googleBackupAddress: String?
    ): Result<Unit> {
        return runCatching {
            accountRepository.importFromSeed(
                walletId,
                substrateSeed,
                username,
                derivationPath,
                selectedEncryptionType,
                ethSeed,
                googleBackupAddress
            )
        }
    }

    override fun validateJsonBackup(json: String, password: String) {
        accountRepository.validateJsonBackup(json, password)
    }

    override suspend fun importFromJson(
        walletId: Long?,
        json: String,
        password: String,
        name: String,
        ethJson: String?,
        googleBackupAddress: String?
    ): Result<Unit> {
        return runCatching {
            if (walletId == null) {
                accountRepository.importFromJson(json, password, name, ethJson, googleBackupAddress)
            } else {
                ethJson?.let {
                    accountRepository.importAdditionalFromJson(walletId, ethJson, password)
                }
            }
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

    override fun selectedMetaAccountFlow() = accountRepository.selectedMetaAccountFlow()

    override suspend fun selectedMetaAccount() =
        withContext(context) { accountRepository.getSelectedMetaAccount() }

    override suspend fun selectedLightMetaAccount() =
        accountRepository.getSelectedLightMetaAccount()

    override fun selectedLightMetaAccountFlow(): Flow<LightMetaAccount> {
        return accountRepository.selectedLightMetaAccountFlow()
    }
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
    override suspend fun getLightMetaAccount(metaId: Long) = accountRepository.getLightMetaAccount(metaId)

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

    override suspend fun getBestBackupType(walletId: Long, type: WalletEcosystem): BackupAccountType? =
        accountRepository.getBestBackupType(walletId, type)

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

    override fun getMnemonic(metaId: Long): Flow<Mnemonic> {
        val result = lightMetaAccountsFlow().mapNotNull {
            it.firstOrNull { it.id == metaId }
        }.map {
            it.supportedEcosystems().mapNotNull {
                when (it) {
                    WalletEcosystem.Substrate -> accountRepository.getSubstrateSecrets(metaId)?.get(SubstrateSecrets.Entropy)?.let { entropy ->
                        MnemonicCreator.fromEntropy(entropy)
                    }

                    WalletEcosystem.Ethereum -> accountRepository.getEthereumSecrets(metaId)?.get(EthereumSecrets.Entropy)?.let { entropy ->
                        MnemonicCreator.fromEntropy(entropy)
                    }

                    WalletEcosystem.Ton -> accountRepository.getTonSecrets(metaId)?.get(TonSecrets.Seed)?.decodeToString()?.let { words ->
                        //MnemonicCreator.fromWords(words) // error entropy creation
                        Mnemonic(
                            words = words,
                            wordList = words.split(EnglishWordList.INSTANCE.space.toString()),
                            entropy = byteArrayOf()
                        )
                    }
                }
            }
        }.mapNotNull {
            // mnemonic for a wallet (substrate) or ethereum mnemonic for added ethereum account; or ton
            it.firstOrNull()
        }
        return result
    }

    override fun getSeedForSeedExport(metaId: Long): Flow<ComponentHolder> {
        val result = lightMetaAccountsFlow().mapNotNull {
            it.firstOrNull { it.id == metaId }
        }.map {
            val substrateSeed = accountRepository.getSubstrateSecrets(metaId)?.run {
                get(SubstrateSecrets.Seed) ?: seedFromMnemonic()
            }?.toHexString(true)
            val ethSeed = accountRepository.getEthereumSecrets(metaId)?.get(EthereumSecrets.EthereumKeypair)?.get(KeyPairSchema.PrivateKey)?.toHexString(withPrefix = true)
            ComponentHolder(
                listOf(
                    substrateSeed,
                    ethSeed
                )
            )
        }
        return result
    }


    override fun getDerivationPathForExport(metaId: Long): Flow<ComponentHolder> {
        val result = lightMetaAccountsFlow().mapNotNull {
            it.firstOrNull { it.id == metaId }
        }.map {
            val substrateDerivationPath = accountRepository.getSubstrateSecrets(metaId)?.get(SubstrateSecrets.SubstrateDerivationPath)
            val ethereumDerivationPath  = accountRepository.getEthereumSecrets(metaId)?.get(EthereumSecrets.EthereumDerivationPath).takeIf { path ->
                path != BIP32JunctionDecoder.DEFAULT_DERIVATION_PATH
            }
            ComponentHolder(
                listOf(
                    substrateDerivationPath,
                    ethereumDerivationPath
                )
            )
        }
        return result
    }

    private fun EncodableStruct<SubstrateSecrets>.seedFromMnemonic() =
        get(SubstrateSecrets.Entropy)?.let { entropy ->
            val mnemonicWords = MnemonicCreator.fromEntropy(entropy).words
            val derivationPath = get(SubstrateSecrets.SubstrateDerivationPath)?.nullIfEmpty()
            val password = derivationPath?.let { SubstrateJunctionDecoder.decode(it).password }
            SubstrateSeedFactory.deriveSeed32(mnemonicWords, password).seed
        }
}
