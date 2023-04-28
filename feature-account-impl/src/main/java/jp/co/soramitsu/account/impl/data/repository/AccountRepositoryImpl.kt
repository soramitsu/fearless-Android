package jp.co.soramitsu.account.impl.data.repository

import android.database.sqlite.SQLiteConstraintException
import jp.co.soramitsu.account.api.domain.interfaces.AccountAlreadyExistsException
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.Account
import jp.co.soramitsu.account.api.domain.model.AuthType
import jp.co.soramitsu.account.api.domain.model.ImportJsonData
import jp.co.soramitsu.account.api.domain.model.LightMetaAccount
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.MetaAccountOrdering
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.account.api.domain.model.cryptoType
import jp.co.soramitsu.account.api.domain.model.hasChainAccount
import jp.co.soramitsu.account.impl.data.repository.datasource.AccountDataSource
import jp.co.soramitsu.common.data.Keypair
import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecrets
import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.secrets.v2.MetaAccountSecrets
import jp.co.soramitsu.common.data.secrets.v2.SecretStoreV2
import jp.co.soramitsu.common.resources.LanguagesHolder
import jp.co.soramitsu.common.utils.DEFAULT_DERIVATION_PATH
import jp.co.soramitsu.common.utils.deriveSeed32
import jp.co.soramitsu.common.utils.ethereumAddressFromPublicKey
import jp.co.soramitsu.common.utils.nullIfEmpty
import jp.co.soramitsu.common.utils.substrateAccountId
import jp.co.soramitsu.core.crypto.mapCryptoTypeToEncryption
import jp.co.soramitsu.core.crypto.mapEncryptionToCryptoType
import jp.co.soramitsu.core.model.Language
import jp.co.soramitsu.core.model.SecuritySource
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.coredb.dao.AccountDao
import jp.co.soramitsu.coredb.dao.MetaAccountDao
import jp.co.soramitsu.coredb.model.AccountLocal
import jp.co.soramitsu.coredb.model.chain.ChainAccountLocal
import jp.co.soramitsu.coredb.model.chain.MetaAccountLocal
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.polkadotChainId
import jp.co.soramitsu.shared_utils.encrypt.MultiChainEncryption
import jp.co.soramitsu.shared_utils.encrypt.json.JsonSeedDecoder
import jp.co.soramitsu.shared_utils.encrypt.json.JsonSeedEncoder
import jp.co.soramitsu.shared_utils.encrypt.junction.BIP32JunctionDecoder
import jp.co.soramitsu.shared_utils.encrypt.junction.SubstrateJunctionDecoder
import jp.co.soramitsu.shared_utils.encrypt.keypair.Keypair
import jp.co.soramitsu.shared_utils.encrypt.keypair.ethereum.EthereumKeypairFactory
import jp.co.soramitsu.shared_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.shared_utils.encrypt.mnemonic.Mnemonic
import jp.co.soramitsu.shared_utils.encrypt.mnemonic.MnemonicCreator
import jp.co.soramitsu.shared_utils.encrypt.seed.ethereum.EthereumSeedFactory
import jp.co.soramitsu.shared_utils.encrypt.seed.substrate.SubstrateSeedFactory
import jp.co.soramitsu.shared_utils.runtime.AccountId
import jp.co.soramitsu.shared_utils.scale.EncodableStruct
import jp.co.soramitsu.shared_utils.ss58.SS58Encoder.addressByte
import jp.co.soramitsu.shared_utils.ss58.SS58Encoder.toAccountId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.bouncycastle.util.encoders.Hex

class AccountRepositoryImpl(
    private val accountDataSource: AccountDataSource,
    private val accountDao: AccountDao,
    private val metaAccountDao: MetaAccountDao,
    private val storeV2: SecretStoreV2,
    private val jsonSeedDecoder: JsonSeedDecoder,
    private val jsonSeedEncoder: JsonSeedEncoder,
    private val languagesHolder: LanguagesHolder,
    private val chainRegistry: ChainRegistry
) : AccountRepository {

    override fun getEncryptionTypes(): List<CryptoType> {
        return CryptoType.values().toList()
    }

    override suspend fun selectAccount(metaAccountId: Long) {
        metaAccountDao.selectMetaAccount(metaAccountId)
    }

    // TODO remove
    override fun selectedAccountFlow(): Flow<Account> {
        return accountDataSource.selectedAccountMapping.map {
            it.getValue(polkadotChainId)!!
        }
    }

    // TODO remove
    override suspend fun getSelectedAccount(): Account {
        return getSelectedAccount(polkadotChainId)
    }

    override suspend fun getSelectedAccount(chainId: String): Account {
        return accountDataSource.selectedAccountMapping.first().getValue(chainId)!!
    }

    override suspend fun getSelectedMetaAccount(): MetaAccount {
        return accountDataSource.getSelectedMetaAccount()
    }

    override suspend fun getMetaAccount(metaId: Long): MetaAccount {
        return accountDataSource.getMetaAccount(metaId)
    }

    override fun selectedMetaAccountFlow(): Flow<MetaAccount> {
        return accountDataSource.selectedMetaAccountFlow()
    }

    override suspend fun findMetaAccount(accountId: ByteArray): MetaAccount? {
        return accountDataSource.findMetaAccount(accountId)
    }

    override suspend fun allMetaAccounts(): List<MetaAccount> {
        return accountDataSource.allMetaAccounts()
    }

    override fun allMetaAccountsFlow(): Flow<List<MetaAccount>> {
        return accountDataSource.observeAllMetaAccounts()
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

    override suspend fun getPreferredCryptoType(): CryptoType {
        return accountDataSource.getPreferredCryptoTypeOrSelected()
    }

    override suspend fun isAccountSelected(): Boolean {
        return accountDataSource.anyAccountSelected()
    }

    override suspend fun createChainAccount(
        metaId: Long,
        chainId: ChainId,
        accountName: String,
        mnemonicWords: String,
        cryptoType: CryptoType,
        substrateDerivationPath: String,
        ethereumDerivationPath: String
    ) {
        saveChainAccountFromMnemonic(metaId, chainId, accountName, mnemonicWords, cryptoType, substrateDerivationPath, ethereumDerivationPath)
    }

    override suspend fun createAccount(
        accountName: String,
        mnemonic: String,
        encryptionType: CryptoType,
        substrateDerivationPath: String,
        ethereumDerivationPath: String
    ) {
        val metaAccountId = saveFromMnemonic(
            accountName,
            mnemonic,
            substrateDerivationPath,
            ethereumDerivationPath,
            encryptionType,
            true
        )

        selectAccount(metaAccountId)
    }

    override suspend fun deleteAccount(metaId: Long) {
        accountDataSource.deleteMetaAccount(metaId)
    }

    override suspend fun getAccounts(): List<Account> {
        return accountDao.getAccounts()
            .map { mapAccountLocalToAccount(it) }
    }

    override suspend fun getAccount(address: String): Account {
        val account = accountDao.getAccount(address) ?: throw NoSuchElementException("No account found for address $address")
        return mapAccountLocalToAccount(account)
    }

    override suspend fun getAccountOrNull(address: String): Account? {
        return accountDao.getAccount(address)?.let { mapAccountLocalToAccount(it) }
    }

    override suspend fun getMyAccounts(query: String, chainId: String): Set<Account> {
//        return withContext(Dispatchers.Default) {
//            accountDao.getAccounts(query, networkType)
//                .map { mapAccountLocalToAccount(it) }
//                .toSet()
//        }

        return emptySet() // TODO wallet
    }

    override suspend fun importFromMnemonic(
        keyString: String,
        username: String,
        substrateDerivationPath: String,
        ethereumDerivationPath: String,
        selectedEncryptionType: CryptoType,
        withEth: Boolean
    ) {
        val metaAccountId = saveFromMnemonic(
            username,
            keyString,
            substrateDerivationPath,
            ethereumDerivationPath,
            selectedEncryptionType,
            withEth
        )

        selectAccount(metaAccountId)
    }

    override suspend fun importChainAccountFromMnemonic(
        metaId: Long,
        chainId: ChainId,
        accountName: String,
        mnemonicWords: String,
        cryptoType: CryptoType,
        substrateDerivationPath: String,
        ethereumDerivationPath: String
    ) {
        saveChainAccountFromMnemonic(metaId, chainId, accountName, mnemonicWords, cryptoType, substrateDerivationPath, ethereumDerivationPath)
    }

    override suspend fun importFromSeed(
        seed: String,
        username: String,
        derivationPath: String,
        selectedEncryptionType: CryptoType,
        ethSeed: String?
    ) {
        return withContext(Dispatchers.Default) {
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

            val ethereumKeypair = ethSeedBytes?.let { EthereumKeypairFactory.createWithPrivateKey(it) }

            val position = metaAccountDao.getNextPosition()

            val secretsV2 = MetaAccountSecrets(
                substrateKeyPair = keys,
                substrateDerivationPath = derivationPath,
                seed = substrateSeedBytes,
                ethereumDerivationPath = BIP32JunctionDecoder.DEFAULT_DERIVATION_PATH,
                ethereumKeypair = ethereumKeypair
            )

            val metaAccount = MetaAccountLocal(
                substratePublicKey = keys.publicKey,
                substrateAccountId = keys.publicKey.substrateAccountId(),
                substrateCryptoType = selectedEncryptionType,
                name = username,
                isSelected = true,
                position = position,
                ethereumPublicKey = ethereumKeypair?.publicKey,
                ethereumAddress = ethereumKeypair?.publicKey?.ethereumAddressFromPublicKey()
            )

            val metaAccountId = insertAccount(metaAccount)
            storeV2.putMetaAccountSecrets(metaAccountId, secretsV2)
            selectAccount(metaAccountId)
        }
    }

    override suspend fun importChainFromSeed(
        metaId: Long,
        chainId: ChainId,
        accountName: String,
        seed: String,
        substrateDerivationPath: String,
        selectedEncryptionType: CryptoType
    ) {
        return withContext(Dispatchers.Default) {
            val seedBytes = Hex.decode(seed.removePrefix("0x"))

            val ethereumBased = chainRegistry.getChain(chainId).isEthereumBased
            val keyPair = when {
                ethereumBased -> EthereumKeypairFactory.createWithPrivateKey(seedBytes)
                else -> {
                    val derivationPathOrNull = substrateDerivationPath.nullIfEmpty()
                    val decodedDerivationPath = derivationPathOrNull?.let {
                        SubstrateJunctionDecoder.decode(it)
                    }

                    SubstrateKeypairFactory.generate(
                        mapCryptoTypeToEncryption(selectedEncryptionType),
                        seedBytes,
                        decodedDerivationPath?.junctions.orEmpty()
                    )
                }
            }

            val derPath = when {
                ethereumBased -> BIP32JunctionDecoder.DEFAULT_DERIVATION_PATH
                else -> substrateDerivationPath
            }

            val secretsV2 = ChainAccountSecrets(
                keyPair = keyPair,
                seed = seedBytes,
                derivationPath = derPath
            )

            val publicKey = keyPair.publicKey

            val accountId = when {
                ethereumBased -> publicKey.ethereumAddressFromPublicKey()
                else -> publicKey.substrateAccountId()
            }

            val crypto = when {
                ethereumBased -> CryptoType.ECDSA
                else -> selectedEncryptionType
            }

            val chainAccount = ChainAccountLocal(
                metaId = metaId,
                chainId = chainId,
                publicKey = publicKey,
                accountId = accountId,
                cryptoType = crypto,
                name = accountName
            )

            insertChainAccount(chainAccount)
            storeV2.putChainAccountSecrets(metaId, accountId, secretsV2)
        }
    }

    override suspend fun importFromJson(
        json: String,
        password: String,
        name: String,
        ethJson: String?
    ) {
        return withContext(Dispatchers.Default) {
            val substrateImportData = jsonSeedDecoder.decode(json, password)
            val ethImportData = ethJson?.let { jsonSeedDecoder.decode(ethJson, password) }

            val substrateKeys = substrateImportData.keypair
            val ethKeys = ethImportData?.keypair

            val position = metaAccountDao.getNextPosition()

            val ethereumKeypair = ethKeys?.let { EthereumKeypairFactory.createWithPrivateKey(it.privateKey) }

            val secretsV2 = MetaAccountSecrets(
                substrateKeyPair = substrateKeys,
                seed = substrateImportData.seed,
                ethereumKeypair = ethereumKeypair
            )

            val metaAccount = MetaAccountLocal(
                substratePublicKey = substrateKeys.publicKey,
                substrateAccountId = substrateKeys.publicKey.substrateAccountId(),
                substrateCryptoType = mapEncryptionToCryptoType(substrateImportData.multiChainEncryption.encryptionType),
                name = name,
                isSelected = true,
                position = position,
                ethereumAddress = ethereumKeypair?.publicKey?.ethereumAddressFromPublicKey(),
                ethereumPublicKey = ethereumKeypair?.publicKey
            )

            val metaAccountId = insertAccount(metaAccount)
            storeV2.putMetaAccountSecrets(metaAccountId, secretsV2)

            selectAccount(metaAccountId)
        }
    }

    override suspend fun importChainFromJson(
        metaId: Long,
        chainId: ChainId,
        accountName: String,
        json: String,
        password: String
    ) {
        return withContext(Dispatchers.Default) {
            val importData = jsonSeedDecoder.decode(json, password)

            val ethereumBased = chainRegistry.getChain(chainId).isEthereumBased
            val keyPair = when {
                ethereumBased -> EthereumKeypairFactory.createWithPrivateKey(importData.keypair.privateKey)
                else -> importData.keypair
            }

            val secretsV2 = ChainAccountSecrets(
                keyPair = keyPair,
                seed = importData.seed
            )

            val publicKey = keyPair.publicKey

            val accountId = when {
                ethereumBased -> publicKey.ethereumAddressFromPublicKey()
                else -> publicKey.substrateAccountId()
            }

            val crypto = when {
                ethereumBased -> CryptoType.ECDSA
                else -> mapEncryptionToCryptoType(importData.multiChainEncryption.encryptionType)
            }

            val chainAccount = ChainAccountLocal(
                metaId = metaId,
                chainId = chainId,
                publicKey = publicKey,
                accountId = accountId,
                cryptoType = crypto,
                name = accountName
            )

            insertChainAccount(chainAccount)
            storeV2.putChainAccountSecrets(metaId, accountId, secretsV2)
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

    override suspend fun generateMnemonic(): List<String> {
        return withContext(Dispatchers.Default) {
            val generationResult = MnemonicCreator.randomMnemonic(Mnemonic.Length.TWELVE)

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
        return withContext(Dispatchers.Default) {
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

    override suspend fun getChainAccountSecrets(metaId: Long?, chainId: ChainId): EncodableStruct<ChainAccountSecrets>? {
        return (metaId?.let { getMetaAccount(it) } ?: getSelectedMetaAccount()).let { metaAccount ->
            if (metaAccount.hasChainAccount((chainId))) {
                metaAccount.chainAccounts[chainId]?.accountId?.let { accountId ->
                    storeV2.getChainAccountSecrets(metaAccount.id, accountId)
                }
            } else {
                null
            }
        }
    }

    override suspend fun getMetaAccountSecrets(metaId: Long?): EncodableStruct<MetaAccountSecrets>? {
        val id = metaId ?: getSelectedMetaAccount().id
        return storeV2.getMetaAccountSecrets(id)
    }

    override suspend fun generateRestoreJson(metaId: Long, chainId: ChainId, password: String) = withContext(Dispatchers.Default) {
        val chain = chainRegistry.getChain(chainId)
        val metaAccount = getMetaAccount(metaId)

        val hasChainAccount = metaAccount.hasChainAccount(chainId)
        val (keypairSchema, seed) = if (hasChainAccount) {
            val secrets = getChainAccountSecrets(metaId, chainId)

            val keypairSchema = secrets?.get(ChainAccountSecrets.Keypair)

            val seed = secrets?.get(ChainAccountSecrets.Seed)
            keypairSchema to seed
        } else {
            val secrets = getMetaAccountSecrets(metaId)

            val keypairSchema = if (chain.isEthereumBased) {
                secrets?.get(MetaAccountSecrets.EthereumKeypair)
            } else {
                secrets?.get(MetaAccountSecrets.SubstrateKeypair)
            }

            val seed = secrets?.get(MetaAccountSecrets.Seed)
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
        val address = metaAccount.address(chain) ?: throw IllegalArgumentException("No address specified for chain ${chain.name}")

        val cryptoType = mapCryptoTypeToEncryption(metaAccount.cryptoType(chain))
        val multiChainEncryption = if (chain.isEthereumBased) MultiChainEncryption.Ethereum else MultiChainEncryption.Substrate(cryptoType)

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

    private suspend fun saveFromMnemonic(
        accountName: String,
        mnemonicWords: String,
        substrateDerivationPath: String,
        ethereumDerivationPath: String,
        cryptoType: CryptoType,
        withEth: Boolean
    ): Long {
        return withContext(Dispatchers.Default) {
            val substrateDerivationPathOrNull = substrateDerivationPath.nullIfEmpty()
            val decodedDerivationPath = substrateDerivationPathOrNull?.let {
                SubstrateJunctionDecoder.decode(it)
            }

            val derivationResult = SubstrateSeedFactory.deriveSeed32(mnemonicWords, decodedDerivationPath?.password)

            val keys = SubstrateKeypairFactory.generate(
                encryptionType = mapCryptoTypeToEncryption(cryptoType),
                seed = derivationResult.seed,
                junctions = decodedDerivationPath?.junctions.orEmpty()
            )

            val mnemonic = MnemonicCreator.fromWords(mnemonicWords)

            val (ethereumKeypair: Keypair?, ethereumDerivationPathOrDefault: String?) = if (withEth) {
                val ethereumDerivationPathOrDefault = ethereumDerivationPath.nullIfEmpty() ?: BIP32JunctionDecoder.DEFAULT_DERIVATION_PATH
                val decodedEthereumDerivationPath = BIP32JunctionDecoder.decode(ethereumDerivationPathOrDefault)
                val ethereumSeed = EthereumSeedFactory.deriveSeed32(mnemonicWords, password = decodedEthereumDerivationPath.password).seed
                val ethereumKeypair = EthereumKeypairFactory.generate(ethereumSeed, junctions = decodedEthereumDerivationPath.junctions)

                ethereumKeypair to ethereumDerivationPathOrDefault
            } else {
                null to null
            }

            val position = metaAccountDao.getNextPosition()

            val secretsV2 = MetaAccountSecrets(
                substrateKeyPair = keys,
                entropy = mnemonic.entropy,
                seed = null,
                substrateDerivationPath = substrateDerivationPath,
                ethereumKeypair = ethereumKeypair,
                ethereumDerivationPath = ethereumDerivationPathOrDefault
            )

            val metaAccount = MetaAccountLocal(
                substratePublicKey = keys.publicKey,
                substrateAccountId = keys.publicKey.substrateAccountId(),
                substrateCryptoType = cryptoType,
                ethereumPublicKey = ethereumKeypair?.publicKey,
                ethereumAddress = ethereumKeypair?.publicKey?.ethereumAddressFromPublicKey(),
                name = accountName,
                isSelected = true,
                position = position
            )

            val metaAccountId = insertAccount(metaAccount)
            storeV2.putMetaAccountSecrets(metaAccountId, secretsV2)

            metaAccountId
        }
    }

    private suspend fun saveChainAccountFromMnemonic(
        metaId: Long,
        chainId: ChainId,
        accountName: String,
        mnemonicWords: String,
        cryptoType: CryptoType,
        substrateDerivationPath: String,
        ethereumDerivationPath: String
    ) {
        val substrateDerivationPathOrNull = substrateDerivationPath.nullIfEmpty()
        val decodedDerivationPath = substrateDerivationPathOrNull?.let {
            SubstrateJunctionDecoder.decode(it)
        }

        val derivationResult = SubstrateSeedFactory.deriveSeed32(mnemonicWords, decodedDerivationPath?.password)

        val keys = SubstrateKeypairFactory.generate(
            encryptionType = mapCryptoTypeToEncryption(cryptoType),
            seed = derivationResult.seed,
            junctions = decodedDerivationPath?.junctions.orEmpty()
        )

        val mnemonic = MnemonicCreator.fromWords(mnemonicWords)

        val ethereumDerivationPathOrDefault = ethereumDerivationPath.nullIfEmpty() ?: BIP32JunctionDecoder.DEFAULT_DERIVATION_PATH
        val decodedEthereumDerivationPath = BIP32JunctionDecoder.decode(ethereumDerivationPathOrDefault)
        val ethereumSeed = EthereumSeedFactory.deriveSeed32(mnemonicWords, password = decodedEthereumDerivationPath.password).seed
        val ethereumKeypair = EthereumKeypairFactory.generate(ethereumSeed, junctions = decodedEthereumDerivationPath.junctions)

        val ethereumBased = chainRegistry.getChain(chainId).isEthereumBased
        val keyPair = when {
            ethereumBased -> ethereumKeypair
            else -> keys
        }

        val seed = when {
            ethereumBased -> ethereumSeed
            else -> derivationResult.seed
        }

        val derPath = when {
            ethereumBased -> ethereumDerivationPathOrDefault
            else -> substrateDerivationPath
        }

        val secretsV2 = ChainAccountSecrets(
            keyPair = keyPair,
            entropy = mnemonic.entropy,
            seed = seed,
            derivationPath = derPath
        )

        val publicKey = keyPair.publicKey

        val accountId = when {
            ethereumBased -> publicKey.ethereumAddressFromPublicKey()
            else -> publicKey.substrateAccountId()
        }

        val crypto = when {
            ethereumBased -> CryptoType.ECDSA
            else -> cryptoType
        }

        val chainAccount = ChainAccountLocal(
            metaId = metaId,
            chainId = chainId,
            publicKey = publicKey,
            accountId = accountId,
            cryptoType = crypto,
            name = accountName
        )

        insertChainAccount(chainAccount)
        storeV2.putChainAccountSecrets(metaId, accountId, secretsV2)
    }

    private fun mapAccountLocalToAccount(accountLocal: AccountLocal): Account {
        return with(accountLocal) {
            Account(
                address = address,
                name = username,
                accountIdHex = publicKey,
                cryptoType = CryptoType.values()[accountLocal.cryptoType],
                position = position
            )
        }
    }

    private suspend fun insertAccount(
        metaAccount: MetaAccountLocal
    ) = try {
        metaAccountDao.insertMetaAccount(metaAccount)
    } catch (e: SQLiteConstraintException) {
        throw AccountAlreadyExistsException()
    }

    private suspend fun insertChainAccount(
        chainAccount: ChainAccountLocal
    ) = try {
        metaAccountDao.insertChainAccount(chainAccount)
    } catch (e: SQLiteConstraintException) {
        throw AccountAlreadyExistsException()
    }

    override fun polkadotAddressForSelectedAccountFlow(): Flow<String> {
        return selectedMetaAccountFlow().map {
            val chain = chainRegistry.getChain(polkadotChainId)
            it.address(chain) ?: ""
        }
    }

    override suspend fun getChain(chainId: ChainId): Chain {
        return chainRegistry.getChain(chainId)
    }
}
