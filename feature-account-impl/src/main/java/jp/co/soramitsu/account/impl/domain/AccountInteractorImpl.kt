package jp.co.soramitsu.account.impl.domain

import jp.co.soramitsu.common.data.storage.encrypt.PinHealth

import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import java.io.File
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.AddAccountPayload
import jp.co.soramitsu.account.api.domain.model.AndroidUniversalWalletMigrationSnapshotBuilder
import jp.co.soramitsu.account.api.domain.model.ImportJsonData
import jp.co.soramitsu.account.api.domain.model.LightMetaAccount
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.MetaAccountOrdering
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.account.api.domain.model.supportedEcosystems
import jp.co.soramitsu.backup.BackupService
import jp.co.soramitsu.backup.domain.exceptions.FileNotFoundException
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
import jp.co.soramitsu.common.data.storage.pin.PinAttemptClock
import jp.co.soramitsu.common.data.storage.pin.PinAttemptLimiter
import jp.co.soramitsu.common.interfaces.FileProvider
import jp.co.soramitsu.common.model.UNIVERSAL_WALLET_CUTOFF_AT_MILLIS
import jp.co.soramitsu.common.model.UniversalWalletMigrationSnapshot
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
import jp.co.soramitsu.fearless_utils.encrypt.junction.BIP32JunctionDecoder
import jp.co.soramitsu.fearless_utils.encrypt.junction.SubstrateJunctionDecoder
import jp.co.soramitsu.fearless_utils.encrypt.mnemonic.EnglishWordList
import jp.co.soramitsu.fearless_utils.encrypt.mnemonic.Mnemonic
import jp.co.soramitsu.fearless_utils.encrypt.mnemonic.MnemonicCreator
import jp.co.soramitsu.fearless_utils.encrypt.seed.substrate.SubstrateSeedFactory
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.scale.EncodableStruct
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withContext

class AccountInteractorImpl(
    private val accountRepository: AccountRepository,
    private val fileProvider: FileProvider,
    private val preferences: Preferences,
    private val backupService: BackupService,
    private val walletInteractor: WalletInteractor,
    private val context: CoroutineContext = Dispatchers.Default,
    private val pinAttemptClock: PinAttemptClock = PinAttemptClock.system,
    private val migrationSnapshotBuilder: AndroidUniversalWalletMigrationSnapshotBuilder =
        AndroidUniversalWalletMigrationSnapshotBuilder(UNIVERSAL_WALLET_CUTOFF_AT_MILLIS)
) : AccountInteractor {

    private val pinAttemptLimiter = PinAttemptLimiter(preferences, pinAttemptClock)

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

    override suspend fun createAccountFromBackup(
        payload: AddAccountPayload.SubstrateOrEvm,
        ethereumPrivateKeyHex: String
    ): Result<Long> {
        return runCatching { accountRepository.createAccountFromBackup(payload, ethereumPrivateKeyHex) }
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
        json: String?,
        password: String,
        name: String,
        ethJson: String?,
        googleBackupAddress: String?
    ): Result<Unit> {
        return runCatching {
            if (walletId == null) {
                json?.let {
                    accountRepository.importFromJson(it, password, name, ethJson, googleBackupAddress)
                }
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

    override suspend fun getPinHealth(): PinHealth = accountRepository.getPinHealth()

    override suspend fun savePin(code: String) {
        accountRepository.savePinCode(code)
        pinAttemptLimiter.reset()
    }

    override suspend fun isPinCorrect(code: String): Boolean {
        // No PIN is read or compared while a wrong-PIN delay runs.
        if (pinAttemptLimiter.remainingDelayMillis() > 0L) {
            return false
        }

        val pinCode = accountRepository.getPinCode()
        if (PinHealth.assess(true, pinCode) != PinHealth.HEALTHY) {
            // Nothing can match a PIN this device cannot read, so it is no wrong guess.
            return false
        }

        if (pinCode == code) {
            pinAttemptLimiter.reset()
            return true
        }

        pinAttemptLimiter.registerFailure()
        return false
    }

    override suspend fun getPinRetryDelayMillis(): Long = pinAttemptLimiter.remainingDelayMillis()

    override suspend fun registerBiometricUnlock() {
        pinAttemptLimiter.reset()
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

    override fun selectedMetaAccountFlow() = accountRepository.selectedMetaAccountFlow().flowOn(Dispatchers.IO)

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
            // Keystore exports stay in internal storage; the FileProvider
            // shares this one directory with the app the user picks.
            fileProvider.getFileInInternalShareStorage(fileName)
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

    override fun universalWalletMigrationSnapshotFlow(): Flow<UniversalWalletMigrationSnapshot> {
        return accountRepository.lightMetaAccountsFlow()
            .map(migrationSnapshotBuilder::build)
            .flowOn(context)
    }

    override suspend fun universalWalletMigrationSnapshot(): UniversalWalletMigrationSnapshot {
        return withContext(context) {
            migrationSnapshotBuilder.build(accountRepository.lightMetaAccountsFlow().first())
        }
    }

    override suspend fun isWalletRecoveryRequired(metaId: Long): Boolean {
        return accountRepository.isWalletRecoveryRequired(metaId)
    }

    override fun walletRecoveryRequiredFlow(metaId: Long): Flow<Boolean> {
        return accountRepository.walletRecoveryRequiredFlow(metaId)
    }

    override suspend fun saveGoogleBackupAccount(metaId: Long, googleBackupPassword: String): Set<BackupAccountType> {
        return withContext(Dispatchers.IO) {
            val wallet = getMetaAccount(metaId)
            val googleBackupAddress = googleBackupFileAddress(wallet) ?: error("error obtaining google backup address")

            val jsonResult = generateRestoreJson(
                metaId = metaId,
                chainId = polkadotChainId,
                password = googleBackupPassword
            )
            val substrateJson = jsonResult.getOrNull()
            val ethJsonResult = generateRestoreJson(
                metaId = metaId,
                chainId = moonriverChainId,
                password = googleBackupPassword
            )
            val ethJson = ethJsonResult.getOrNull()

            val substrateSecrets = accountRepository.getSubstrateSecrets(metaId)
            val ethereumSecrets = accountRepository.getEthereumSecrets(metaId)

            val substrateDerivationPath = substrateSecrets?.get(SubstrateSecrets.SubstrateDerivationPath).orEmpty()
            val ethereumDerivationPath = ethereumSecrets?.get(EthereumSecrets.EthereumDerivationPath).orEmpty()
            val entropy = substrateSecrets?.get(SubstrateSecrets.Entropy)?.clone()
            val mnemonic = entropy?.let { MnemonicCreator.fromEntropy(it).words }
            val substrateSeed = (
                    substrateSecrets?.get(SubstrateSecrets.Seed) ?: mnemonic?.let {
                        seedFromMnemonic(
                            mnemonic,
                            substrateDerivationPath.nullIfEmpty()
                        )
                    }
                    )?.toHexString(true)
            val ethSeed = ethereumSecrets?.get(EthereumSecrets.EthereumKeypair)?.get(KeyPairSchema.PrivateKey)?.toHexString(withPrefix = true)

            val backupAccountTypes = getSupportedBackupTypes(metaId).toList()

            val substrateCryptoType = wallet.substrateCryptoType ?: error("Need substrate CryptoType to backup wallet: ${wallet.id} - ${wallet.name}")
            uploadVerifiedGoogleBackup(
                account = DecryptedBackupAccount(
                    name = wallet.name,
                    address = googleBackupAddress,
                    mnemonicPhrase = mnemonic,
                    substrateDerivationPath = substrateDerivationPath,
                    ethDerivationPath = ethereumDerivationPath,
                    cryptoType = substrateCryptoType,
                    backupAccountType = backupAccountTypes,
                    seed = Seed(substrateSeed = substrateSeed, ethSeed),
                    json = Json(substrateJson = substrateJson, ethJson)
                )
            ) { account ->
                backupService.saveBackupAccount(account = account, password = googleBackupPassword)
            }
        }
    }

    override suspend fun getGoogleBackupAccounts(): List<BackupAccountMeta> {
        return backupService.getBackupAccounts()
    }

    override suspend fun getExtensionGoogleBackups(): List<BackupAccountMeta> {
        return backupService.getWebBackupAccounts()
    }

    override fun isGoogleBackupAvailable(): Boolean = backupService.isAvailable

    override suspend fun getGoogleBackupCopies(walletId: Long): List<BackupAccountMeta> {
        val address = googleBackupFileAddress(getMetaAccount(walletId))
        // Listed even without an address, so a lapsed Google sign-in is still reported.
        return backupService.getBackupCopies().filter { it.address == address }
    }

    override suspend fun deleteGoogleBackupAccount(walletId: Long, copies: List<BackupAccountMeta>) {
        // Never the stored googleBackupAddress: a wallet imported with a phrase that restores another
        // account keeps the address of that account's backup, which may be the only one restoring it.
        val address = googleBackupFileAddress(getMetaAccount(walletId))
        val fileIds = copies.filter { it.address == address }.mapNotNull { it.fileId }
        try {
            if (address != null) backupService.deleteBackupAccount(address, fileIds)
        } catch (e: FileNotFoundException) {
            // Already removed, for example from another device: the wallet has no Drive backup either way.
        }
        updateWalletOnGoogleBackupDelete(walletId)
    }

    // A wallet uploads its backups under its own Westend address (see saveGoogleBackupAccount).
    private suspend fun googleBackupFileAddress(wallet: MetaAccount): String? {
        return wallet.address(getChain(westendChainId))
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

                    WalletEcosystem.Bitcoin,
                    WalletEcosystem.Solana,
                    WalletEcosystem.Iroha -> null
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

    private fun seedFromMnemonic(mnemonic: String, derivationPath: String?): ByteArray {
        val password = derivationPath?.let { SubstrateJunctionDecoder.decode(it).password }
        return SubstrateSeedFactory.deriveSeed32(mnemonic, password).seed
    }
}
