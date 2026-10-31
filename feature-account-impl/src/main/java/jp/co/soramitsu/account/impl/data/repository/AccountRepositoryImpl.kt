package jp.co.soramitsu.account.impl.data.repository

import jp.co.soramitsu.common.data.storage.encrypt.PinHealth

import jp.co.soramitsu.account.api.domain.interfaces.AccountAlreadyExistsException
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.Account
import jp.co.soramitsu.account.api.domain.model.AddAccountPayload
import jp.co.soramitsu.account.api.domain.model.AuthType
import jp.co.soramitsu.account.api.domain.model.ImportJsonData
import jp.co.soramitsu.account.api.domain.model.LightMetaAccount
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.MetaAccountOrdering
import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.account.api.domain.model.NomisScoreData
import jp.co.soramitsu.account.api.domain.model.WalletRecoveryStatus
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.account.api.domain.model.cryptoType
import jp.co.soramitsu.account.api.domain.model.hasChainAccount
import jp.co.soramitsu.account.api.domain.model.hasEthereum
import jp.co.soramitsu.account.api.domain.model.hasSubstrate
import jp.co.soramitsu.account.impl.data.mappers.toDomain
import jp.co.soramitsu.account.impl.data.repository.datasource.AccountDataSource
import jp.co.soramitsu.backup.domain.models.BackupAccountType
import jp.co.soramitsu.common.data.Keypair
import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecrets
import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.secrets.v2.SecretStoreV2
import jp.co.soramitsu.common.data.secrets.v1.LegacyV1SecretCorruptionException
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecretStore
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecrets
import jp.co.soramitsu.common.data.secrets.v3.RecoveryAssessment
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecretStore
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.data.secrets.v3.TonSecretStore
import jp.co.soramitsu.common.data.secrets.v3.TonSecrets
import jp.co.soramitsu.common.utils.UniversalWalletPassphrase
import jp.co.soramitsu.common.data.secrets.v3.WalletRootSecretCorruptionException
import jp.co.soramitsu.common.data.secrets.v3.WalletRootSecretValidator
import jp.co.soramitsu.common.data.storage.encrypt.WalletPublicIdentityIntegrityException
import jp.co.soramitsu.common.data.storage.encrypt.WalletRecoveryRequiredException
import jp.co.soramitsu.common.data.storage.encrypt.WalletRecoveryIdentity
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretAccessGuard
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretScope
import jp.co.soramitsu.common.model.WalletEcosystem
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
import jp.co.soramitsu.core.models.IChain
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
import jp.co.soramitsu.fearless_utils.encrypt.MultiChainEncryption
import jp.co.soramitsu.fearless_utils.encrypt.json.JsonSeedDecoder
import jp.co.soramitsu.fearless_utils.encrypt.json.JsonSeedEncoder
import jp.co.soramitsu.fearless_utils.encrypt.junction.BIP32JunctionDecoder
import jp.co.soramitsu.fearless_utils.encrypt.junction.SubstrateJunctionDecoder
import jp.co.soramitsu.fearless_utils.encrypt.keypair.ethereum.EthereumKeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.mnemonic.Mnemonic
import jp.co.soramitsu.fearless_utils.encrypt.mnemonic.MnemonicCreator
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.fearless_utils.scale.EncodableStruct
import jp.co.soramitsu.fearless_utils.scale.toHexString
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.addressByte
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
    private val walletSecretMutationCoordinator: WalletSecretMutationCoordinator,
    private val walletSecretAccessGuard: WalletSecretAccessGuard,
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

    override fun selectedMetaAccountStateFlow(): Flow<MetaAccount?> {
        return accountDataSource.selectedMetaAccountStateFlow()
    }

    override fun metaAccountStateFlow(metaId: Long): Flow<MetaAccount?> {
        return accountDataSource.metaAccountStateFlow(metaId)
    }

    override fun selectedLightMetaAccountFlow(): Flow<LightMetaAccount> {
        return accountDataSource.selectedLightMetaAccount()
    }

    override suspend fun findMetaAccount(accountId: ByteArray): MetaAccount? {
        return accountDataSource.findMetaAccount(accountId)
    }

    override suspend fun allMetaAccounts(): List<MetaAccount> = withContext(Dispatchers.IO){
        return@withContext accountDataSource.allMetaAccounts()
    }

    override fun allMetaAccountsFlow(): StateFlow<List<MetaAccount>> {
        return accountDataSource.observeAllMetaAccounts()
            .flowOn(dispatcher)
            .stateIn(GlobalScope, SharingStarted.Eagerly, emptyList())
    }

    override fun lightMetaAccountsFlow(): Flow<List<LightMetaAccount>> {
        return accountDataSource.lightMetaAccountsFlow()
    }

    override fun lightMetaAccountFlow(metaId: Long): Flow<LightMetaAccount> {
        return accountDataSource.lightMetaAccountFlow(metaId)
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
        return accountRepositoryDelegate.create(payload)
    }

    override suspend fun createAccountFromBackup(
        payload: AddAccountPayload.SubstrateOrEvm,
        ethereumPrivateKeyHex: String
    ): Long {
        return accountRepositoryDelegate.createFromBackup(payload, ethereumPrivateKeyHex)
    }

    override suspend fun deleteAccount(metaId: Long) {
        walletSecretMutationCoordinator.delete(metaId)
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
                isSelected = localMetaAccount.isSelected,
                position = localMetaAccount.position,
                ethereumPublicKey = ethereumKeypair.publicKey,
                ethereumAddress = ethereumKeypair.publicKey.ethereumAddressFromPublicKey(),
                tonPublicKey = localMetaAccount.tonPublicKey,
                isBackedUp = true,
                googleBackupAddress = localMetaAccount.googleBackupAddress,
                initialized = localMetaAccount.initialized
            )
            metaAccount.id = walletId

            val ethereumSecrets = EthereumSecrets(
                ethereumKeypair = ethereumKeypair,
                seed = ethSeedBytes,
                ethereumDerivationPath = BIP32JunctionDecoder.DEFAULT_DERIVATION_PATH
            )
            addEvm(
                existing = localMetaAccount,
                after = metaAccount,
                ethereumSecretPlaintext = ethereumSecrets.toHexString()
            )
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

            val metaAccount = MetaAccountLocal(
                substratePublicKey = keys.publicKey,
                substrateAccountId = keys.publicKey.substrateAccountId(),
                substrateCryptoType = selectedEncryptionType,
                name = username,
                isSelected = true,
                ethereumPublicKey = ethereumKeypair?.publicKey,
                ethereumAddress = ethereumKeypair?.publicKey?.ethereumAddressFromPublicKey(),
                tonPublicKey = null,
                isBackedUp = true,
                googleBackupAddress = googleBackupAddress,
                initialized = false,
                position = 0
            )

            val substrateSecrets = SubstrateSecrets(
                substrateKeyPair = keys,
                substrateDerivationPath = derivationPath,
                seed = substrateSeedBytes
            )
            val ethereumSecrets = ethereumKeypair?.let {
                EthereumSecrets(
                    ethereumKeypair = ethereumKeypair,
                    seed = ethSeedBytes,
                    ethereumDerivationPath = BIP32JunctionDecoder.DEFAULT_DERIVATION_PATH
                )
            }
            createWallet(
                prototype = metaAccount,
                substrateSecretPlaintext = substrateSecrets.toHexString(),
                ethereumSecretPlaintext = ethereumSecrets?.toHexString(),
                tonSecretPlaintext = null
            )
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

            val ethereumKeypair =
                ethKeys?.let { EthereumKeypairFactory.createWithPrivateKey(it.privateKey) }

            val metaAccount = MetaAccountLocal(
                substratePublicKey = substrateKeys.publicKey,
                substrateAccountId = substrateKeys.publicKey.substrateAccountId(),
                substrateCryptoType = mapEncryptionToCryptoType(substrateImportData.multiChainEncryption.encryptionType),
                name = name,
                isSelected = true,
                ethereumAddress = ethereumKeypair?.publicKey?.ethereumAddressFromPublicKey(),
                ethereumPublicKey = ethereumKeypair?.publicKey,
                tonPublicKey = null,
                isBackedUp = true,
                googleBackupAddress = googleBackupAddress,
                initialized = false,
                position = 0
            )

            val substrateSecrets = SubstrateSecrets(
                substrateKeyPair = substrateKeys,
                seed = substrateImportData.seed
            )
            val ethereumSecrets = ethereumKeypair?.let {
                EthereumSecrets(
                    ethereumKeypair = ethereumKeypair
                )
            }
            createWallet(
                prototype = metaAccount,
                substrateSecretPlaintext = substrateSecrets.toHexString(),
                ethereumSecretPlaintext = ethereumSecrets?.toHexString(),
                tonSecretPlaintext = null
            )
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
                tonPublicKey = localMetaAccount.tonPublicKey,
                isBackedUp = true,
                googleBackupAddress = localMetaAccount.googleBackupAddress,
                initialized = localMetaAccount.initialized
            )
            metaAccount.id = walletId

            val ethereumSecrets = EthereumSecrets(
                ethereumKeypair = ethereumKeypair
            )
            addEvm(
                existing = localMetaAccount,
                after = metaAccount,
                ethereumSecretPlaintext = ethereumSecrets.toHexString()
            )
        }
    }

    private suspend fun createWallet(
        prototype: MetaAccountLocal,
        substrateSecretPlaintext: String?,
        ethereumSecretPlaintext: String?,
        tonSecretPlaintext: String?
    ): Long {
        return try {
            walletSecretMutationCoordinator.create(
                prototype = prototype,
                substrateSecretPlaintext = substrateSecretPlaintext,
                ethereumSecretPlaintext = ethereumSecretPlaintext,
                tonSecretPlaintext = tonSecretPlaintext
            )
        } catch (failure: WalletSecretMutationCoordinatorException) {
            if (failure.reason == WalletMutationFailureReason.IDENTITY_CONFLICT) {
                throw AccountAlreadyExistsException()
            }
            throw failure
        }
    }

    private suspend fun addEvm(
        existing: MetaAccountLocal,
        after: MetaAccountLocal,
        ethereumSecretPlaintext: String
    ): Long {
        return try {
            walletSecretMutationCoordinator.addEvm(
                existing = existing,
                after = after,
                ethereumSecretPlaintext = ethereumSecretPlaintext
            )
        } catch (failure: WalletSecretMutationCoordinatorException) {
            if (failure.reason == WalletMutationFailureReason.IDENTITY_CONFLICT) {
                throw AccountAlreadyExistsException()
            }
            throw failure
        }
    }

    override suspend fun isCodeSet(): Boolean {
        return accountDataSource.getPinHealth() != PinHealth.ABSENT
    }

    override suspend fun getPinHealth(): PinHealth = accountDataSource.getPinHealth()

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
        val accountId = try {
            accountAddress.toAccountId()
        } catch (failure: Exception) {
            throw IllegalArgumentException("Invalid signing account address", failure)
        }
        val metaAccount = findMetaAccount(accountId)
            ?: error("No wallet found for the signing account")
        walletSecretAccessGuard.requireAccess(
            metaId = metaAccount.id,
            scope = WalletSecretScope.Substrate,
            substratePublicKey = metaAccount.substratePublicKey
        )

        val expectedPublicKey = metaAccount.substratePublicKey
            ?: throw WalletPublicIdentityIntegrityException(
                "A legacy signing wallet is missing its durable public key"
            )
        val expectedCryptoType = metaAccount.substrateCryptoType
            ?: throw WalletPublicIdentityIntegrityException(
                "A legacy signing wallet is missing its durable crypto type"
            )
        val expectedAccountId = metaAccount.substrateAccountId
            ?: throw WalletPublicIdentityIntegrityException(
                "A legacy signing wallet is missing its durable account id"
            )
        if (!expectedAccountId.contentEquals(accountId)) {
            throw WalletPublicIdentityIntegrityException(
                "A legacy signing request does not match the durable account id"
            )
        }

        val legacyPlaintextSnapshot =
            walletSecretAccessGuard.legacyV1PlaintextSnapshot(accountAddress)
                ?: throw WalletRecoveryRequiredException()
        val source = try {
            accountDataSource.getSecuritySource(accountAddress)
                ?: throw WalletRecoveryRequiredException()
        } catch (failure: LegacyV1SecretCorruptionException) {
            walletSecretAccessGuard.quarantineLegacyV1AndThrow(
                metaId = metaAccount.id,
                accountAddress = accountAddress,
                expectedPublicKey = expectedPublicKey,
                expectedSnapshot = legacyPlaintextSnapshot
            )
        }
        try {
            WalletRootSecretValidator.validateLegacySubstrateSource(
                source = source,
                expectedPublicKey = expectedPublicKey,
                expectedCryptoType = expectedCryptoType,
                expectedAccountId = expectedAccountId
            )
        } catch (failure: WalletRootSecretCorruptionException) {
            walletSecretAccessGuard.quarantineLegacyV1AndThrow(
                metaId = metaAccount.id,
                accountAddress = accountAddress,
                expectedPublicKey = expectedPublicKey,
                expectedSnapshot = legacyPlaintextSnapshot
            )
        }

        return source
    }

    override suspend fun getChainAccountSecrets(
        metaId: Long?,
        chainId: ChainId
    ): EncodableStruct<ChainAccountSecrets>? {
        return (metaId?.let { getMetaAccount(it) } ?: getSelectedMetaAccount()).let { metaAccount ->
            if (metaAccount.hasChainAccount((chainId))) {
                metaAccount.chainAccounts[chainId]?.let { chainAccount ->
                    walletSecretAccessGuard.requireAccess(
                        metaId = metaAccount.id,
                        scope = WalletSecretScope.chainAccount(chainAccount.accountId)
                    )
                    legacyStoreV2.getChainAccountSecretsWithVerifiedRecovery(
                        metaId = metaAccount.id,
                        accountId = chainAccount.accountId,
                        expectedPublicKey = chainAccount.publicKey,
                        expectedCryptoType = chainAccount.cryptoType
                    )
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
                // Only the key this JSON is for must be usable; a broken
                // Substrate key never blocks an Ethereum export, or the reverse.
                val keyScope = if (chain.isEthereumBased) {
                    WalletSecretScope.Ethereum
                } else {
                    WalletSecretScope.Substrate
                }
                requireScopeAccess(metaAccount, keyScope)
                val substrateSecrets = if (isScopeAccessible(metaAccount, WalletSecretScope.Substrate)) {
                    readValidatedSubstrateSecrets(metaAccount)
                } else {
                    null
                }

                val keypairSchema = if (chain.isEthereumBased) {
                    readValidatedEthereumSecrets(metaAccount)
                        ?.get(EthereumSecrets.EthereumKeypair)
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

    override fun polkadotAddressForSelectedAccountFlow(): Flow<String> {
        return selectedMetaAccountFlow().map {
            val chain = chainsRepository.getChain(polkadotChainId)
            it.address(chain) ?: ""
        }
    }

    override suspend fun isGoogleBackupSupported(walletId: Long): Boolean {
        return try {
            val metaAccount = getMetaAccount(walletId)
            requireScopeAccess(metaAccount, WalletSecretScope.Substrate)
            val substrateSecrets = readValidatedSubstrateSecrets(metaAccount)
            substrateSecrets?.get(SubstrateSecrets.Entropy) != null
        } catch (failure: WalletRecoveryRequiredException) {
            false
        }
    }

    override suspend fun getBestBackupType(walletId: Long, type: WalletEcosystem): BackupAccountType? {
        return try {
            val metaAccount = getMetaAccount(walletId)
            when (type) {
                WalletEcosystem.Substrate -> {
                    requireScopeAccess(metaAccount, WalletSecretScope.Substrate)
                    val substrateSecrets =
                        readValidatedSubstrateSecrets(metaAccount)
                    if (substrateSecrets?.get(SubstrateSecrets.Entropy) != null) {
                        return BackupAccountType.PASSPHRASE
                    }
                    if (substrateSecrets?.get(SubstrateSecrets.Seed) != null) {
                        return BackupAccountType.SEED
                    }
                }

                WalletEcosystem.Ethereum -> {
                    requireScopeAccess(metaAccount, WalletSecretScope.Ethereum)
                    val ethereumSecrets =
                        readValidatedEthereumSecrets(metaAccount)
                    if (ethereumSecrets?.get(EthereumSecrets.Entropy) != null) {
                        return BackupAccountType.PASSPHRASE
                    }
                    if (ethereumSecrets?.get(EthereumSecrets.Seed) != null) {
                        return BackupAccountType.SEED
                    }
                }

                WalletEcosystem.Ton -> {
                    requireScopeAccess(metaAccount, WalletSecretScope.Ton)
                    val tonSecrets = readValidatedTonSecrets(metaAccount)
                    if (tonSecrets != null) {
                        return BackupAccountType.PASSPHRASE
                    }
                }

                WalletEcosystem.Bitcoin,
                WalletEcosystem.Solana,
                WalletEcosystem.Iroha -> return null
            }
            null
        } catch (failure: WalletRecoveryRequiredException) {
            null
        }
    }

    override suspend fun getSupportedBackupTypes(walletId: Long): Set<BackupAccountType> {
        return try {
            // Each ecosystem offers what its own key supports; a blocked key
            // contributes nothing and never hides the others.
            val metaAccount = getMetaAccount(walletId)
            val substrateSecrets = readIfAccessible(metaAccount, WalletSecretScope.Substrate) {
                readValidatedSubstrateSecrets(metaAccount)
            }
            val ethereumSecrets = readIfAccessible(metaAccount, WalletSecretScope.Ethereum) {
                readValidatedEthereumSecrets(metaAccount)
            }
            val tonSecrets = readIfAccessible(metaAccount, WalletSecretScope.Ton) {
                readValidatedTonSecrets(metaAccount)
            }

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

            types
        } catch (failure: WalletRecoveryRequiredException) {
            emptySet()
        }
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
        val metaAccount = getMetaAccount(metaId)
        requireScopeAccess(metaAccount, WalletSecretScope.Substrate)
        return readValidatedSubstrateSecrets(metaAccount)
    }

    override suspend fun getEthereumSecrets(metaId: Long): EncodableStruct<EthereumSecrets>? {
        val metaAccount = getMetaAccount(metaId)
        requireScopeAccess(metaAccount, WalletSecretScope.Ethereum)
        return readValidatedEthereumSecrets(metaAccount)
    }

    override suspend fun getTonSecrets(metaId: Long): EncodableStruct<TonSecrets>? {
        val metaAccount = getMetaAccount(metaId)
        requireScopeAccess(metaAccount, WalletSecretScope.Ton)
        return readValidatedTonSecrets(metaAccount)
    }

    // Secrets handed out by this repository feed phrase, seed and JSON export,
    // backup eligibility and Bitcoin/Solana/Iroha derivation, so they carry
    // recovery material only when it re-derives the signing key. A drifted
    // entropy, seed or path is withheld, never exported, and never stops the
    // keypair from signing. Signing itself reads through KeyPairRepository.
    private fun readValidatedSubstrateSecrets(
        metaAccount: MetaAccount
    ): EncodableStruct<SubstrateSecrets>? {
        return substrateSecretStore.getWithVerifiedRecovery(
            metaId = metaAccount.id,
            expectedPublicKey = metaAccount.substratePublicKey,
            expectedCryptoType = metaAccount.substrateCryptoType,
            expectedAccountId = metaAccount.substrateAccountId
        )
    }

    private fun readValidatedEthereumSecrets(
        metaAccount: MetaAccount
    ): EncodableStruct<EthereumSecrets>? {
        return ethereumSecretStore.getWithVerifiedRecovery(
            metaId = metaAccount.id,
            expectedPublicKey = metaAccount.ethereumPublicKey,
            expectedAddress = metaAccount.ethereumAddress
        )
    }

    private suspend fun readValidatedTonSecrets(
        metaAccount: MetaAccount
    ): EncodableStruct<TonSecrets>? = readTonForIdentity(metaAccount) { publicKey, quarantine ->
        getWithVerifiedRecovery(metaAccount.id, publicKey, quarantine = quarantine)
            ?: universalTonPassphrases(metaAccount).takeIf(List<String>::isNotEmpty)?.let { passphrases ->
                getWithVerifiedRecovery(metaAccount.id, publicKey, passphrases, quarantine)
            }
    }

    // [metaAccount] was read before its TON secret; a universal TON swap may have committed since.
    private suspend fun <T> readTonForIdentity(
        metaAccount: MetaAccount,
        read: TonSecretStore.(publicKey: ByteArray?, quarantine: Boolean) -> T
    ): T = tonSecretStore.readForIdentity(
        expectedPublicKey = metaAccount.tonPublicKey,
        currentPublicKey = { accountDataSource.getMetaAccount(metaAccount.id).tonPublicKey },
        read = read
    )

    /**
     * A password wallet derives its universal TON key with its BIP39 passphrase, which only its
     * Substrate or Ethereum derivation path holds. Read only after the phrase alone failed.
     */
    private fun universalTonPassphrases(metaAccount: MetaAccount): List<String> {
        if (metaAccount.tonPublicKey == null) return emptyList()
        if (metaAccount.substratePublicKey == null && metaAccount.ethereumPublicKey == null) return emptyList()
        return try {
            val substrate = if (isScopeAccessible(metaAccount, WalletSecretScope.Substrate)) {
                readValidatedSubstrateSecrets(metaAccount)
            } else {
                null
            }
            val ethereum = if (isScopeAccessible(metaAccount, WalletSecretScope.Ethereum)) {
                readValidatedEthereumSecrets(metaAccount)
            } else {
                null
            }
            val substrateEntropy = substrate?.get(SubstrateSecrets.Entropy)
            val passphrase = when {
                substrateEntropy != null -> UniversalWalletPassphrase.resolve(
                    substrate[SubstrateSecrets.SubstrateDerivationPath],
                    ethereum?.get(EthereumSecrets.EthereumDerivationPath),
                    ethereum?.get(EthereumSecrets.Entropy)?.contentEquals(substrateEntropy) == true
                )
                ethereum?.get(EthereumSecrets.Entropy) != null ->
                    UniversalWalletPassphrase.ethereumPassword(ethereum[EthereumSecrets.EthereumDerivationPath])
                else -> ""
            }
            listOfNotNull(passphrase.takeIf(String::isNotEmpty))
        } catch (failure: kotlinx.coroutines.CancellationException) {
            throw failure
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun isWalletRecoveryRequired(metaId: Long): Boolean {
        return walletRecoveryStatus(metaId).isRecoveryRequired
    }

    override fun walletRecoveryRequiredFlow(metaId: Long): Flow<Boolean> {
        return walletRecoveryStatusFlow(metaId)
            .map { it.isRecoveryRequired }
            .distinctUntilChanged()
    }

    override suspend fun walletRecoveryStatus(metaId: Long): WalletRecoveryStatus {
        return walletRecoveryStatus(getMetaAccount(metaId))
    }

    override fun walletRecoveryStatusFlow(metaId: Long): Flow<WalletRecoveryStatus> {
        return walletSecretAccessGuard.recoveryStateVersion
            .map { walletRecoveryStatus(getMetaAccount(metaId)) }
            .distinctUntilChanged()
            .flowOn(dispatcher)
    }

    override suspend fun isSigningRecoveryRequired(
        metaId: Long,
        scope: WalletSecretScope
    ): Boolean {
        return jp.co.soramitsu.common.data.storage.pin.WalletViewOnlyAccess.isEnabled() || !isScopeAccessible(getMetaAccount(metaId), scope)
    }

    override suspend fun isSigningRecoveryRequired(metaId: Long, chain: IChain): Boolean {
        if (jp.co.soramitsu.common.data.storage.pin.WalletViewOnlyAccess.isEnabled()) return true
        val metaAccount = getMetaAccount(metaId)
        val accountId = metaAccount.accountId(chain)
            ?.takeIf(ByteArray::isNotEmpty)
            ?: return !isScopeAccessible(metaAccount, WalletSecretScope.forEcosystem(chain.ecosystem))
        // The same rule KeyPairRepository signs by: the per-network key named
        // by the account id, and the ecosystem's root key when no per-network
        // key signs.
        if (!isScopeAccessible(metaAccount, WalletSecretScope.chainAccount(accountId))) {
            return true
        }
        if (legacyStoreV2.hasChainSecrets(metaAccount.id, accountId)) return false
        return !isScopeAccessible(metaAccount, WalletSecretScope.forEcosystem(chain.ecosystem))
    }

    override suspend fun recoveryAssessment(
        metaId: Long,
        ecosystem: WalletEcosystem
    ): RecoveryAssessment? = withContext(dispatcher) {
        val metaAccount = getMetaAccount(metaId)
        when (ecosystem) {
            WalletEcosystem.Substrate -> readIfAccessible(metaAccount, WalletSecretScope.Substrate) {
                substrateSecretStore.getWithAssessment(
                    metaId = metaAccount.id,
                    expectedPublicKey = metaAccount.substratePublicKey,
                    expectedCryptoType = metaAccount.substrateCryptoType,
                    expectedAccountId = metaAccount.substrateAccountId
                )?.second
            }

            WalletEcosystem.Ethereum -> readIfAccessible(metaAccount, WalletSecretScope.Ethereum) {
                ethereumSecretStore.getWithAssessment(
                    metaId = metaAccount.id,
                    expectedPublicKey = metaAccount.ethereumPublicKey,
                    expectedAddress = metaAccount.ethereumAddress
                )?.second
            }

            WalletEcosystem.Ton -> readIfAccessible(metaAccount, WalletSecretScope.Ton) {
                readTonForIdentity(metaAccount) { publicKey, quarantine ->
                    val assessment = getWithAssessment(metaAccount.id, publicKey, quarantine = quarantine)?.second
                    if (assessment is RecoveryAssessment.Unverified) {
                        universalTonPassphrases(metaAccount).takeIf(List<String>::isNotEmpty)?.let { passphrases ->
                            getWithAssessment(metaAccount.id, publicKey, passphrases, quarantine)?.second
                        } ?: assessment
                    } else {
                        assessment
                    }
                }
            }

            // Derived from a root phrase; they have no stored material of their own.
            WalletEcosystem.Bitcoin,
            WalletEcosystem.Solana,
            WalletEcosystem.Iroha -> null
        }
    }

    private fun walletRecoveryStatus(metaAccount: MetaAccount): WalletRecoveryStatus {
        return WalletRecoveryStatus(
            metaId = metaAccount.id,
            items = walletSecretAccessGuard.recoveryInventory(
                WalletRecoveryIdentity(
                    metaId = metaAccount.id,
                    substratePublicKey = metaAccount.substratePublicKey,
                    hasEthereumIdentity = metaAccount.ethereumPublicKey != null ||
                        metaAccount.ethereumAddress != null,
                    hasTonIdentity = metaAccount.tonPublicKey != null,
                    chainAccountIds = metaAccount.chainAccounts.values.map { it.accountId }
                )
            )
        )
    }

    private fun isScopeAccessible(metaAccount: MetaAccount, scope: WalletSecretScope): Boolean {
        return !walletSecretAccessGuard.isRecoveryRequired(
            metaId = metaAccount.id,
            scope = scope,
            substratePublicKey = metaAccount.substratePublicKey
        )
    }

    private fun requireScopeAccess(metaAccount: MetaAccount, scope: WalletSecretScope) {
        walletSecretAccessGuard.requireAccess(
            metaId = metaAccount.id,
            scope = scope,
            substratePublicKey = metaAccount.substratePublicKey
        )
    }

    private inline fun <T> readIfAccessible(
        metaAccount: MetaAccount,
        scope: WalletSecretScope,
        read: () -> T?
    ): T? = if (isScopeAccessible(metaAccount, scope)) read() else null
}
