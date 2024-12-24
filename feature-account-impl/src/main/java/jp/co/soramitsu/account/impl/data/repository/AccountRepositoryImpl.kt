package jp.co.soramitsu.account.impl.data.repository

import jp.co.soramitsu.account.api.domain.interfaces.AccountAlreadyExistsException
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.Account
import jp.co.soramitsu.account.api.domain.model.AddAccountPayload
import jp.co.soramitsu.account.api.domain.model.AuthType
import jp.co.soramitsu.account.api.domain.model.ImportJsonData
import jp.co.soramitsu.account.api.domain.model.LightMetaAccount
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.MetaAccountOrdering
import jp.co.soramitsu.account.api.domain.model.NomisScoreData
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.account.api.domain.model.cryptoType
import jp.co.soramitsu.account.api.domain.model.hasChainAccount
import jp.co.soramitsu.account.api.domain.model.hasEthereum
import jp.co.soramitsu.account.api.domain.model.hasSubstrate
import jp.co.soramitsu.account.api.presentation.importing.ImportAccountType
import jp.co.soramitsu.account.impl.data.mappers.toDomain
import jp.co.soramitsu.account.impl.data.repository.datasource.AccountDataSource
import jp.co.soramitsu.backup.domain.models.BackupAccountType
import jp.co.soramitsu.common.data.Keypair
import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecrets
import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.secrets.v2.SecretStoreV2
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecretStore
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecrets
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecretStore
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.data.secrets.v3.TonSecretStore
import jp.co.soramitsu.common.data.secrets.v3.TonSecrets
import jp.co.soramitsu.common.resources.LanguagesHolder
import jp.co.soramitsu.common.utils.DEFAULT_DERIVATION_PATH
import jp.co.soramitsu.common.utils.ethereumAddressFromPublicKey
import jp.co.soramitsu.common.utils.nullIfEmpty
import jp.co.soramitsu.common.utils.substrateAccountId
import jp.co.soramitsu.core.crypto.mapCryptoTypeToEncryption
import jp.co.soramitsu.core.crypto.mapEncryptionToCryptoType
import jp.co.soramitsu.core.model.Language
import jp.co.soramitsu.core.model.SecuritySource
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.coredb.dao.MetaAccountDao
import jp.co.soramitsu.coredb.dao.NomisScoresDao
import jp.co.soramitsu.coredb.model.MetaAccountLocal
import jp.co.soramitsu.coredb.model.NomisWalletScoreLocal
import jp.co.soramitsu.coredb.model.chain.FavoriteChainLocal
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.polkadotChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.westendChainId
import jp.co.soramitsu.shared_utils.encrypt.MultiChainEncryption
import jp.co.soramitsu.shared_utils.encrypt.json.JsonSeedDecoder
import jp.co.soramitsu.shared_utils.encrypt.json.JsonSeedEncoder
import jp.co.soramitsu.shared_utils.encrypt.junction.BIP32JunctionDecoder
import jp.co.soramitsu.shared_utils.encrypt.junction.SubstrateJunctionDecoder
import jp.co.soramitsu.shared_utils.encrypt.keypair.ethereum.EthereumKeypairFactory
import jp.co.soramitsu.shared_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.shared_utils.encrypt.mnemonic.Mnemonic
import jp.co.soramitsu.shared_utils.encrypt.mnemonic.MnemonicCreator
import jp.co.soramitsu.shared_utils.runtime.AccountId
import jp.co.soramitsu.shared_utils.scale.EncodableStruct
import jp.co.soramitsu.shared_utils.ss58.SS58Encoder.addressByte
import jp.co.soramitsu.shared_utils.ss58.SS58Encoder.toAccountId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bouncycastle.util.encoders.Hex

class AccountRepositoryImpl(
    private val accountDataSource: AccountDataSource,
    private val metaAccountDao: MetaAccountDao,
    private val legacyStoreV2: SecretStoreV2,
    private val jsonSeedDecoder: JsonSeedDecoder,
    private val jsonSeedEncoder: JsonSeedEncoder,
    private val languagesHolder: LanguagesHolder,
    private val chainsRepository: ChainsRepository,
    private val nomisScoresDao: NomisScoresDao,
    private val substrateSecretStore: SubstrateSecretStore,
    private val ethereumSecretStore: EthereumSecretStore,
    private val tonSecretStore: TonSecretStore,
    private val accountRepositoryDelegate: AccountRepositoryDelegate,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : AccountRepository {

    override fun getEncryptionTypes(): List<CryptoType> {
        return CryptoType.entries
    }

    override suspend fun selectAccount(metaAccountId: Long) {
        metaAccountDao.selectMetaAccount(metaAccountId)
    }

    override suspend fun getSelectedAccount(chainId: String): Account {
        return accountDataSource.selectedAccountMapping.first().getValue(chainId)!!
    }

    override suspend fun getSelectedMetaAccount(): MetaAccount {
        return accountDataSource.getSelectedMetaAccount()
    }

    override suspend fun getSelectedLightMetaAccount(): LightMetaAccount {
        return accountDataSource.getSelectedLightMetaAccount()
    }

    override suspend fun getLightMetaAccount(metaId: Long): LightMetaAccount {
        return accountDataSource.getLightMetaAccount(metaId)
    }

    override suspend fun getMetaAccount(metaId: Long): MetaAccount {
        return accountDataSource.getMetaAccount(metaId)
    }

    override fun selectedMetaAccountFlow(): Flow<MetaAccount> {
        return accountDataSource.selectedMetaAccountFlow()
    }

    override fun selectedLightMetaAccountFlow(): Flow<LightMetaAccount> {
        return accountDataSource.selectedLightMetaAccount()
    }

    override suspend fun findMetaAccount(accountId: ByteArray): MetaAccount? {
        return accountDataSource.findMetaAccount(accountId)
    }

    override suspend fun allMetaAccounts(): List<MetaAccount> {
        return accountDataSource.allMetaAccounts()
    }

    override fun allMetaAccountsFlow(): StateFlow<List<MetaAccount>> {
        return accountDataSource.observeAllMetaAccounts()
            .flowOn(dispatcher)
            .stateIn(GlobalScope, SharingStarted.Eagerly, emptyList())
    }

    override fun lightMetaAccountsFlow(): Flow<List<LightMetaAccount>> {
        return accountDataSource.lightMetaAccountsFlow()
    }

    override suspend fun selectMetaAccount(metaId: Long) {
        return accountDataSource.selectMetaAccount(metaId)
    }

    override suspend fun updateMetaAccountName(metaId: Long, newName: String) {
        return accountDataSource.updateMetaAccountName(metaId, newName)
    }

    override suspend fun updateMetaAccountBackedUp(metaId: Long) {
        return accountDataSource.updateMetaAccountBackedUp(metaId)
    }

    override suspend fun updateWalletOnGoogleBackupDelete(metaId: Long) {
        return accountDataSource.updateWalletOnGoogleBackupDelete(metaId)
    }

    override suspend fun getPreferredCryptoType(): CryptoType {
        return accountDataSource.getPreferredCryptoTypeOrSelected()
    }

    override suspend fun isAccountSelected(): Boolean {
        return accountDataSource.anyAccountSelected()
    }

    override suspend fun createAccount(payload: AddAccountPayload): Long {
        val metaAccountId = accountRepositoryDelegate.create(payload)
        if (payload !is AddAccountPayload.AdditionalEvm) {
            selectAccount(metaAccountId)
        }
        return metaAccountId
    }

    override suspend fun deleteAccount(metaId: Long) {
        accountDataSource.deleteMetaAccount(metaId)
    }

    override suspend fun importFromSeed(
        walletId: Long?,
        seed: String?,
        username: String,
        derivationPath: String,
        selectedEncryptionType: CryptoType,
        ethSeed: String?,
        googleBackupAddress: String?
    ) {
        if (walletId == null) {
            importNewAccount(seed!!, ethSeed, derivationPath, selectedEncryptionType, username, googleBackupAddress)
        } else {
            ethSeed?.let {
                importAdditionalAccount(walletId, ethSeed)
            }
        }
    }

    // only Eth for now
    private suspend fun importAdditionalAccount(walletId: Long, ethSeed: String) {
        withContext(dispatcher) {
            val ethSeedBytes = Hex.decode(ethSeed.removePrefix("0x"))
            val ethereumKeypair = EthereumKeypairFactory.createWithPrivateKey(ethSeedBytes)
            val localMetaAccount = metaAccountDao.getMetaAccount(walletId) ?: error("Account not found")
            val metaAccount = MetaAccountLocal(
                substratePublicKey = localMetaAccount.substratePublicKey,
                substrateAccountId = localMetaAccount.substrateAccountId,
                substrateCryptoType = localMetaAccount.substrateCryptoType,
                name = localMetaAccount.name,
                isSelected = true,
                position = localMetaAccount.position,
                ethereumPublicKey = ethereumKeypair.publicKey,
                ethereumAddress = ethereumKeypair.publicKey.ethereumAddressFromPublicKey(),
                tonPublicKey = null,
                isBackedUp = true,
                googleBackupAddress = localMetaAccount.googleBackupAddress,
                initialized = false
            )
            metaAccount.id = walletId

            metaAccountDao.updateMetaAccount(metaAccount)

            coroutineScope {
                launch {
                    val ethereumSecrets = EthereumSecrets(
                        ethereumKeypair = ethereumKeypair,
                        seed = ethSeedBytes,
                        ethereumDerivationPath = BIP32JunctionDecoder.DEFAULT_DERIVATION_PATH
                    )

                    ethereumSecretStore.put(walletId, ethereumSecrets)
                }
            }.join()
        }
    }

    private suspend fun importNewAccount(seed: String, ethSeed: String?, derivationPath: String, selectedEncryptionType: CryptoType, username: String, googleBackupAddress: String?) {
        withContext(dispatcher) {
            val substrateSeedBytes = Hex.decode(seed.removePrefix("0x"))
            val ethSeedBytes = ethSeed?.let { Hex.decode(it.removePrefix("0x")) }

            val derivationPathOrNull = derivationPath.nullIfEmpty()
            val decodedDerivationPath = derivationPathOrNull?.let {
                SubstrateJunctionDecoder.decode(it)
            }

            val keys = SubstrateKeypairFactory.generate(
                mapCryptoTypeToEncryption(selectedEncryptionType),
                substrateSeedBytes,
                decodedDerivationPath?.junctions.orEmpty()
            )

            val ethereumKeypair =
                ethSeedBytes?.let { EthereumKeypairFactory.createWithPrivateKey(it) }

            val position = metaAccountDao.getNextPosition()

            val metaAccount = MetaAccountLocal(
                substratePublicKey = keys.publicKey,
                substrateAccountId = keys.publicKey.substrateAccountId(),
                substrateCryptoType = selectedEncryptionType,
                name = username,
                isSelected = true,
                position = position,
                ethereumPublicKey = ethereumKeypair?.publicKey,
                ethereumAddress = ethereumKeypair?.publicKey?.ethereumAddressFromPublicKey(),
                tonPublicKey = null,
                isBackedUp = true,
                googleBackupAddress = googleBackupAddress,
                initialized = false
            )

            val metaAccountId = async { insertAccount(metaAccount) }

            coroutineScope {
                launch {
                    val substrateSecrets = SubstrateSecrets(
                        substrateKeyPair = keys,
                        substrateDerivationPath = derivationPath,
                        seed = substrateSeedBytes
                    )
                    substrateSecretStore.put(metaAccountId.await(), substrateSecrets)
                }
                if (ethereumKeypair != null) {
                    launch {
                        val ethereumSecrets = EthereumSecrets(
                            ethereumKeypair = ethereumKeypair,
                            seed = ethSeedBytes,
                            ethereumDerivationPath = BIP32JunctionDecoder.DEFAULT_DERIVATION_PATH
                        )

                        ethereumSecretStore.put(metaAccountId.await(), ethereumSecrets)
                    }
                }
                launch { selectAccount(metaAccountId.await()) }
            }.join()
        }
    }

    override fun validateJsonBackup(json: String, password: String) {
        jsonSeedDecoder.decode(json, password)
    }

    override suspend fun importFromJson(
        json: String,
        password: String,
        name: String,
        ethJson: String?,
        googleBackupAddress: String?
    ) {
        return withContext(dispatcher) {
            val substrateImportData = jsonSeedDecoder.decode(json, password)
            val ethImportData = ethJson?.let { jsonSeedDecoder.decode(ethJson, password) }

            val substrateKeys = substrateImportData.keypair
            val ethKeys = ethImportData?.keypair

            val position = metaAccountDao.getNextPosition()

            val ethereumKeypair =
                ethKeys?.let { EthereumKeypairFactory.createWithPrivateKey(it.privateKey) }

            val metaAccount = MetaAccountLocal(
                substratePublicKey = substrateKeys.publicKey,
                substrateAccountId = substrateKeys.publicKey.substrateAccountId(),
                substrateCryptoType = mapEncryptionToCryptoType(substrateImportData.multiChainEncryption.encryptionType),
                name = name,
                isSelected = true,
                position = position,
                ethereumAddress = ethereumKeypair?.publicKey?.ethereumAddressFromPublicKey(),
                ethereumPublicKey = ethereumKeypair?.publicKey,
                tonPublicKey = null,
                isBackedUp = true,
                googleBackupAddress = googleBackupAddress,
                initialized = false
            )

            val metaAccountId = async { insertAccount(metaAccount) }

            coroutineScope {
                launch {
                    val substrateSecrets = SubstrateSecrets(
                        substrateKeyPair = substrateKeys,
                        seed = substrateImportData.seed
                    )
                    substrateSecretStore.put(metaAccountId.await(), substrateSecrets)
                }
                if(ethereumKeypair != null) {
                    launch {
                        val ethereumSecrets = EthereumSecrets(
                            ethereumKeypair = ethereumKeypair
                        )

                        ethereumSecretStore.put(metaAccountId.await(), ethereumSecrets)
                    }
                }
                launch { selectAccount(metaAccountId.await()) }
            }.join()
        }
    }

    override suspend fun importAdditionalFromJson(
        walletId: Long,
        ethJson: String,
        password: String
    ) {
        return withContext(dispatcher) {
            val ethImportData = jsonSeedDecoder.decode(ethJson, password)
            val ethKeys = ethImportData.keypair

            val localMetaAccount = metaAccountDao.getMetaAccount(walletId) ?: error("Account not found")

            val ethereumKeypair = EthereumKeypairFactory.createWithPrivateKey(ethKeys.privateKey)

            val metaAccount = MetaAccountLocal(
                substratePublicKey = localMetaAccount.substratePublicKey,
                substrateAccountId = localMetaAccount.substrateAccountId,
                substrateCryptoType = localMetaAccount.substrateCryptoType,
                name = localMetaAccount.name,
                isSelected = localMetaAccount.isSelected,
                position = localMetaAccount.position,
                ethereumAddress = ethereumKeypair.publicKey.ethereumAddressFromPublicKey(),
                ethereumPublicKey = ethereumKeypair.publicKey,
                tonPublicKey = null,
                isBackedUp = true,
                googleBackupAddress = localMetaAccount.googleBackupAddress,
                initialized = false
            )
            metaAccount.id = walletId

            metaAccountDao.updateMetaAccount(metaAccount)

            coroutineScope {
                    launch {
                        val ethereumSecrets = EthereumSecrets(
                            ethereumKeypair = ethereumKeypair
                        )

                        ethereumSecretStore.put(walletId, ethereumSecrets)
                    }
            }.join()
        }
    }

    override suspend fun isCodeSet(): Boolean {
        return accountDataSource.getPinCode() != null
    }

    override suspend fun savePinCode(code: String) {
        return accountDataSource.savePinCode(code)
    }

    override suspend fun getPinCode(): String? {
        return accountDataSource.getPinCode()
    }

    override suspend fun generateMnemonic(length: Mnemonic.Length): List<String> {
        return withContext(dispatcher) {
            val generationResult = MnemonicCreator.randomMnemonic(length)

            generationResult.wordList
        }
    }

    override suspend fun isBiometricEnabled(): Boolean {
        return accountDataSource.getAuthType() == AuthType.BIOMETRY
    }

    override suspend fun setBiometricOn() {
        return accountDataSource.saveAuthType(AuthType.BIOMETRY)
    }

    override suspend fun setBiometricOff() {
        return accountDataSource.saveAuthType(AuthType.PINCODE)
    }

    override suspend fun updateAccountsOrdering(accountOrdering: List<MetaAccountOrdering>) {
        return accountDataSource.updateAccountPositions(accountOrdering)
    }

    override suspend fun processAccountJson(json: String): ImportJsonData {
        return withContext(dispatcher) {
            val importAccountMeta = jsonSeedDecoder.extractImportMetaData(json)

            with(importAccountMeta) {
                val cryptoType = mapEncryptionToCryptoType(encryption.encryptionType)

                ImportJsonData(name, cryptoType)
            }
        }
    }

    override suspend fun getSecuritySource(accountAddress: String): SecuritySource {
        return accountDataSource.getSecuritySource(accountAddress)!!
    }

    override suspend fun getChainAccountSecrets(
        metaId: Long?,
        chainId: ChainId
    ): EncodableStruct<ChainAccountSecrets>? {
        return (metaId?.let { getMetaAccount(it) } ?: getSelectedMetaAccount()).let { metaAccount ->
            if (metaAccount.hasChainAccount((chainId))) {
                metaAccount.chainAccounts[chainId]?.accountId?.let { accountId ->
                    legacyStoreV2.getChainAccountSecrets(metaAccount.id, accountId)
                }
            } else {
                null
            }
        }
    }

    override suspend fun generateRestoreJson(metaId: Long, chainId: ChainId, password: String) =
        withContext(dispatcher) {
            val chain = chainsRepository.getChain(chainId)
            val metaAccount = getMetaAccount(metaId)
            require(metaAccount.hasSubstrate || metaAccount.hasEthereum)
            val hasChainAccount = metaAccount.hasChainAccount(chainId)
            val (keypairSchema, seed) = if (hasChainAccount) {
                val secrets = getChainAccountSecrets(metaId, chainId)

                val keypairSchema = secrets?.get(ChainAccountSecrets.Keypair)

                val seed = secrets?.get(ChainAccountSecrets.Seed)
                keypairSchema to seed
            } else {
                val substrateSecrets = substrateSecretStore.get(metaId)
                val ethereumSecrets = ethereumSecretStore.get(metaId)


                val keypairSchema = if (chain.isEthereumBased) {
                    ethereumSecrets?.get(EthereumSecrets.EthereumKeypair)
                } else {
                    substrateSecrets?.get(SubstrateSecrets.SubstrateKeypair)
                }

                val seed = substrateSecrets?.get(SubstrateSecrets.Seed)
                keypairSchema to seed
            }

            val publicKey = keypairSchema?.get(KeyPairSchema.PublicKey)
            val privateKey = keypairSchema?.get(KeyPairSchema.PrivateKey)

            val nonce = keypairSchema?.get(KeyPairSchema.Nonce)
            val keypair = when {
                publicKey == null -> null
                privateKey == null -> null
                else -> Keypair(
                    publicKey = publicKey,
                    privateKey = privateKey,
                    nonce = nonce
                )
            } ?: throw IllegalArgumentException("No keypair found")
            val address = metaAccount.address(chain)
                ?: throw IllegalArgumentException("No address specified for chain ${chain.name}")

            val cryptoType = requireNotNull(metaAccount.cryptoType(chain)?.let { mapCryptoTypeToEncryption(it) })
            val multiChainEncryption =
                if (chain.isEthereumBased) MultiChainEncryption.Ethereum else MultiChainEncryption.Substrate(
                    cryptoType
                )

            jsonSeedEncoder.generate(
                keypair = keypair,
                seed = seed,
                password = password,
                name = metaAccount.name,
                multiChainEncryption = multiChainEncryption,
                genesisHash = chain.id,
                address = address
            )
        }

    override suspend fun isAccountExists(accountId: AccountId): Boolean {
        return accountDataSource.accountExists(accountId)
    }

    override suspend fun isInCurrentNetwork(address: String, chainId: ChainId): Boolean {
        val currentAccount = getSelectedAccount(chainId)

        return try {
            val otherAddressByte = address.addressByte()
            val currentAddressByte = currentAccount.address.addressByte()

            address.toAccountId() // decoded without exception

            otherAddressByte == currentAddressByte
        } catch (_: Exception) {
            false
        }
    }

    override fun getLanguages(): List<Language> {
        return languagesHolder.getLanguages()
    }

    override suspend fun selectedLanguage(): Language {
        return accountDataSource.getSelectedLanguage()
    }

    override suspend fun changeLanguage(language: Language) {
        return accountDataSource.changeSelectedLanguage(language)
    }

    private suspend fun insertAccount(
        metaAccount: MetaAccountLocal
    ) = try {
        metaAccountDao.insertMetaAccount(metaAccount)
    } catch (e: Throwable) {
        throw AccountAlreadyExistsException()
    }

    override fun polkadotAddressForSelectedAccountFlow(): Flow<String> {
        return selectedMetaAccountFlow().map {
            val chain = chainsRepository.getChain(polkadotChainId)
            it.address(chain) ?: ""
        }
    }

    override suspend fun isGoogleBackupSupported(walletId: Long): Boolean {
        val substrateSecrets = substrateSecretStore.get(walletId)
        return substrateSecrets?.get(SubstrateSecrets.Entropy) != null
    }

    override suspend fun getBestBackupType(walletId: Long, type: ImportAccountType): BackupAccountType? {
        when (type) {
            ImportAccountType.Substrate -> {
                val substrateSecrets = substrateSecretStore.get(walletId)
                if (substrateSecrets?.get(SubstrateSecrets.Entropy) != null) {
                    return BackupAccountType.PASSPHRASE
                }
                if (substrateSecrets?.get(SubstrateSecrets.Seed) != null) {
                    return BackupAccountType.SEED
                }
            }

            ImportAccountType.Ethereum -> {
                val ethereumSecrets = ethereumSecretStore.get(walletId)
                if (ethereumSecrets?.get(EthereumSecrets.Entropy) != null) {
                    return BackupAccountType.PASSPHRASE
                }
                if (ethereumSecrets?.get(EthereumSecrets.Seed) != null) {
                    return BackupAccountType.SEED
                }
            }

            ImportAccountType.Ton -> {
                val tonSecrets = tonSecretStore.get(walletId)
                if (tonSecrets != null) {
                    return BackupAccountType.PASSPHRASE
                }
            }
        }
        return null
    }

    override suspend fun getSupportedBackupTypes(walletId: Long): Set<BackupAccountType> {
        val substrateSecrets = substrateSecretStore.get(walletId)
        val ethereumSecrets = ethereumSecretStore.get(walletId)
        val tonSecrets = tonSecretStore.get(walletId)

        val types = mutableSetOf<BackupAccountType>()

        if (substrateSecrets != null || ethereumSecrets != null) {
            if (substrateSecrets?.get(SubstrateSecrets.Entropy) != null || ethereumSecrets?.get(EthereumSecrets.Entropy) != null) {
                types.add(BackupAccountType.PASSPHRASE)
                types.add(BackupAccountType.SEED)
            }

            if (substrateSecrets?.get(SubstrateSecrets.Seed) != null || ethereumSecrets?.get(EthereumSecrets.Seed) != null) {
                types.add(BackupAccountType.SEED)
            }

            types.add(BackupAccountType.JSON)
            return types
        }

        if (tonSecrets != null) {
            types.add(BackupAccountType.PASSPHRASE)
        }

        return types
    }

    override suspend fun googleBackupAddressForWallet(walletId: Long): String {
        val wallet = getMetaAccount(walletId)
        val chain = chainsRepository.getChain(westendChainId)
        return wallet.googleBackupAddress ?: wallet.address(chain) ?: ""
    }

    override fun googleAddressAllWalletsFlow(): Flow<List<String>> {
        return allMetaAccountsFlow().map { allMetaAccounts ->
            val westendChain = chainsRepository.getChain(westendChainId)
            allMetaAccounts.mapNotNull {
                it.googleBackupAddress ?: it.address(westendChain)
            }
        }
    }

    override suspend fun getChain(chainId: ChainId): Chain {
        return chainsRepository.getChain(chainId)
    }

    override suspend fun updateFavoriteChain(metaAccountId: Long, chainId: ChainId, isFavorite: Boolean) {
        metaAccountDao.insertOrReplaceFavoriteChain(
            FavoriteChainLocal(
                metaId = metaAccountId,
                chainId = chainId,
                isFavorite = isFavorite
            )
        )
    }

    override fun observeFavoriteChains(metaId: Long) = accountDataSource.observeFavoriteChains(metaId).map { list -> list.associate { it.chainId to it.isFavorite } }

    override fun observeNomisScores(): Flow<List<NomisScoreData>> {
        return nomisScoresDao.observeScores().map { scores ->
            scores.map(NomisWalletScoreLocal::toDomain)
        }
    }

    override fun observeNomisScore(metaId: Long): Flow<NomisScoreData?> {
        return nomisScoresDao.observeScore(metaId).map { score ->
            score?.toDomain()
        }
    }

    override suspend fun getSubstrateSecrets(metaId: Long): EncodableStruct<SubstrateSecrets>? {
        return substrateSecretStore.get(metaId)
    }

    override suspend fun getEthereumSecrets(metaId: Long): EncodableStruct<EthereumSecrets>? {
        return ethereumSecretStore.get(metaId)
    }

    override suspend fun getTonSecrets(metaId: Long): EncodableStruct<TonSecrets>? {
        return tonSecretStore.get(metaId)
    }
}
