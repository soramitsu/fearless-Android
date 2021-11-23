package jp.co.soramitsu.feature_account_impl.data.repository

import android.database.sqlite.SQLiteConstraintException
import jp.co.soramitsu.common.data.mappers.mapCryptoTypeToEncryption
import jp.co.soramitsu.common.data.mappers.mapEncryptionToCryptoType
import jp.co.soramitsu.common.data.secrets.v2.MetaAccountSecrets
import jp.co.soramitsu.common.data.secrets.v2.SecretStoreV2
import jp.co.soramitsu.common.resources.LanguagesHolder
import jp.co.soramitsu.common.utils.*
import jp.co.soramitsu.core.model.*
import jp.co.soramitsu.core_db.dao.AccountDao
import jp.co.soramitsu.core_db.dao.MetaAccountDao
import jp.co.soramitsu.core_db.dao.NodeDao
import jp.co.soramitsu.core_db.model.AccountLocal
import jp.co.soramitsu.core_db.model.NodeLocal
import jp.co.soramitsu.core_db.model.chain.MetaAccountLocal
import jp.co.soramitsu.fearless_utils.encrypt.json.JsonSeedDecoder
import jp.co.soramitsu.fearless_utils.encrypt.json.JsonSeedEncoder
import jp.co.soramitsu.fearless_utils.encrypt.junction.BIP32JunctionDecoder
import jp.co.soramitsu.fearless_utils.encrypt.junction.SubstrateJunctionDecoder
import jp.co.soramitsu.fearless_utils.encrypt.keypair.ethereum.EthereumKeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.mnemonic.Mnemonic
import jp.co.soramitsu.fearless_utils.encrypt.mnemonic.MnemonicCreator
import jp.co.soramitsu.fearless_utils.encrypt.qr.QrSharing
import jp.co.soramitsu.fearless_utils.encrypt.seed.ethereum.EthereumSeedFactory
import jp.co.soramitsu.fearless_utils.encrypt.seed.substrate.SubstrateSeedFactory
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountAlreadyExistsException
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.model.*
import jp.co.soramitsu.feature_account_impl.data.mappers.mapNodeLocalToNode
import jp.co.soramitsu.feature_account_impl.data.network.blockchain.AccountSubstrateSource
import jp.co.soramitsu.feature_account_impl.data.repository.datasource.AccountDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext

class AccountRepositoryImpl(
    private val accountDataSource: AccountDataSource,
    private val accountDao: AccountDao,
    private val metaAccountDao: MetaAccountDao,
    private val storeV2: SecretStoreV2,
    private val nodeDao: NodeDao,
    private val jsonSeedDecoder: JsonSeedDecoder,
    private val jsonSeedEncoder: JsonSeedEncoder,
    private val languagesHolder: LanguagesHolder,
    private val accountSubstrateSource: AccountSubstrateSource,
) : AccountRepository {

    override fun getEncryptionTypes(): List<CryptoType> {
        return CryptoType.values().toList()
    }

    override suspend fun getNode(nodeId: Int): Node {
        return withContext(Dispatchers.IO) {
            val node = nodeDao.getNodeById(nodeId)

            mapNodeLocalToNode(node)
        }
    }

    override suspend fun getNetworks(): List<Network> {
        return withContext(Dispatchers.Default) {
            nodeDao.getNodes()
                .map(::mapNodeLocalToNode)
                .map(Node::networkType)
                .distinct()
                .map { getNetworkForType(it) }
        }
    }

    override suspend fun getSelectedNodeOrDefault(): Node {
        return accountDataSource.getSelectedNode() ?: mapNodeLocalToNode(nodeDao.getFirstNode())
    }

    override suspend fun selectNode(node: Node) {
        accountDataSource.saveSelectedNode(node)
    }

    override suspend fun getDefaultNode(networkType: Node.NetworkType): Node {
        return mapNodeLocalToNode(nodeDao.getDefaultNodeFor(networkType.ordinal))
    }

    override suspend fun selectAccount(metaAccountId: Long, newNode: Node?) {
        metaAccountDao.selectMetaAccount(metaAccountId)

//        when {
//            newNode != null -> {
//                require(account.network.type == newNode.networkType) {
//                    "Account network type is not the same as chosen node type"
//                }
//
//                selectNode(newNode)
//            }
//
//            account.network.type != accountDataSource.getSelectedNode()?.networkType -> {
//                val defaultNode = getDefaultNode(account.address.networkType())
//
//                selectNode(defaultNode)
//            }
//        }
    }

    // TODO remove
    override fun selectedAccountFlow(): Flow<Account> {
        return accountDataSource.selectedAccountMapping.map {
            it.getValue(Node.NetworkType.POLKADOT.chainId)!!
        }
    }

    // TODO remove
    override suspend fun getSelectedAccount(): Account {
        return getSelectedAccount(Node.NetworkType.POLKADOT.chainId)
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
        return accountDataSource.getPreferredCryptoType()
    }

    override suspend fun isAccountSelected(): Boolean {
        return accountDataSource.anyAccountSelected()
    }

    override suspend fun createAccount(
        accountName: String,
        mnemonic: String,
        encryptionType: CryptoType,
        derivationPath: String
    ) {
        val metaAccountId = saveFromMnemonic(
            accountName,
            mnemonic,
            derivationPath,
            encryptionType,
            isImport = false
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
        derivationPath: String,
        selectedEncryptionType: CryptoType,
    ) {
        val metaAccountId = saveFromMnemonic(
            username,
            keyString,
            derivationPath,
            selectedEncryptionType,
            isImport = true
        )

        selectAccount(metaAccountId)
    }

    override suspend fun importFromSeed(
        seed: String,
        username: String,
        derivationPath: String,
        selectedEncryptionType: CryptoType,
    ) {
        return withContext(Dispatchers.Default) {
            //todo change to meta-account
//            val seedBytes = Hex.decode(seed.removePrefix("0x"))
//
//            val derivationPathOrNull = derivationPath.nullIfEmpty()
//            val decodedDerivationPath = derivationPathOrNull?.let {
//                SubstrateJunctionDecoder.decode(it)
//            }
//
//            val keys = SubstrateKeypairFactory.generate(
//                mapCryptoTypeToEncryption(selectedEncryptionType),
//                seedBytes,
//                decodedDerivationPath?.junctions.orEmpty()
//            )
//
//            val networkType = Node.NetworkType.KUSAMA//todo hardcoded network type
//            val address = keys.publicKey.toAddress(networkType)
//
//            val securitySource = SecuritySource.Specified.Seed(seedBytes, keys, derivationPath)
//
//            val publicKeyEncoded = Hex.toHexString(keys.publicKey)
//
//            val accountLocal = insertAccount(address, username, publicKeyEncoded, selectedEncryptionType, networkType)
//
//            accountDataSource.saveSecuritySource(address, securitySource)
//
//            val account = mapAccountLocalToAccount(accountLocal)
//
//            selectAccount(account)
        }
    }

    override suspend fun importFromJson(
        json: String,
        password: String,
        name: String,
    ) {
        return withContext(Dispatchers.Default) {
            //todo change to meta-account
//            val importData = jsonSeedDecoder.decode(json, password)
//
//            val newAccount = with(importData) {
//                val publicKeyEncoded = Hex.toHexString(keypair.publicKey)
//
//                val cryptoType = mapEncryptionToCryptoType(encryptionType)
//
//                val securitySource = SecuritySource.Specified.Json(seed, keypair)
//
//                val networkType = Node.NetworkType.KUSAMA//todo hardcoded network type
//                val actualAddress = keypair.publicKey.toAddress(networkType)
//
//                val accountLocal = insertAccount(actualAddress, name, publicKeyEncoded, cryptoType, networkType)
//
//                accountDataSource.saveSecuritySource(actualAddress, securitySource)
//
//                mapAccountLocalToAccount(accountLocal)
//            }
//
//            selectAccount(newAccount)
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
                val cryptoType = mapEncryptionToCryptoType(encryptionType)

                ImportJsonData(name, cryptoType)
            }
        }
    }

    override suspend fun getSecuritySource(accountAddress: String): SecuritySource {
        return accountDataSource.getSecuritySource(accountAddress)!!
    }

    override suspend fun generateRestoreJson(account: Account, password: String): String {
        return withContext(Dispatchers.Default) {
            val securitySource = getSecuritySource(account.address)
            require(securitySource is WithJson)

            val seed = (securitySource.jsonFormer() as? JsonFormer.Seed)?.seed

            val cryptoType = mapCryptoTypeToEncryption(account.cryptoType)
            val runtimeConfiguration = account.network.type.runtimeConfiguration

            jsonSeedEncoder.generate(
                keypair = securitySource.keypair,
                seed = seed,
                password = password,
                name = account.name.orEmpty(),
                encryptionType = cryptoType,
                genesisHash = runtimeConfiguration.genesisHash,
                addressByte = runtimeConfiguration.addressByte
            )
        }
    }

    override suspend fun isAccountExists(accountId: AccountId): Boolean {
        return accountDataSource.accountExists(accountId)
    }

    override fun nodesFlow(): Flow<List<Node>> {
        return nodeDao.nodesFlow()
            .mapList { mapNodeLocalToNode(it) }
            .filter { it.isNotEmpty() }
            .flowOn(Dispatchers.Default)
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

    override suspend fun addNode(nodeName: String, nodeHost: String, networkType: Node.NetworkType) {
        val nodeLocal = NodeLocal(nodeName, nodeHost, networkType.ordinal, false)
        nodeDao.insert(nodeLocal)
    }

    override suspend fun updateNode(nodeId: Int, newName: String, newHost: String, networkType: Node.NetworkType) {
        nodeDao.updateNode(nodeId, newName, newHost, networkType.ordinal)
    }

    override suspend fun checkNodeExists(nodeHost: String): Boolean {
        return nodeDao.checkNodeExists(nodeHost)
    }

    override suspend fun getNetworkName(nodeHost: String): String {
        return accountSubstrateSource.getNodeNetworkType(nodeHost)
    }

    override suspend fun getAccountsByNetworkType(networkType: Node.NetworkType): List<Account> {
        val accounts = accountDao.getAccountsByNetworkType(networkType.ordinal)

        return withContext(Dispatchers.Default) {
            accounts.map { mapAccountLocalToAccount(it) }
        }
    }

    override suspend fun deleteNode(nodeId: Int) {
        return nodeDao.deleteNode(nodeId)
    }

    override fun createQrAccountContent(account: Account): String {
        val payload = QrSharing.Payload(account.address, account.accountIdHex.fromHex(), account.name)

        return QrSharing.encode(payload)
    }

    private suspend fun saveFromMnemonic(
        accountName: String,
        mnemonicWords: String,
        derivationPath: String,
        cryptoType: CryptoType,
        isImport: Boolean,//todo do we still need this argument?
    ): Long {
        return withContext(Dispatchers.Default) {
            val derivationPathOrNull = derivationPath.nullIfEmpty()
            val decodedDerivationPath = derivationPathOrNull?.let {
                SubstrateJunctionDecoder.decode(it)
            }

            val derivationResult = SubstrateSeedFactory.deriveSeed32(mnemonicWords, decodedDerivationPath?.password)

            val keys = SubstrateKeypairFactory.generate(
                encryptionType = mapCryptoTypeToEncryption(cryptoType),
                seed = derivationResult.seed,
                decodedDerivationPath?.junctions.orEmpty()
            )

            val mnemonic = MnemonicCreator.fromWords(mnemonicWords)

            val seed = derivationResult.seed

            val ethereumDerivationPath = BIP32JunctionDecoder.DEFAULT_DERIVATION_PATH
            val decodedEthereumDerivationPath = BIP32JunctionDecoder.decode(ethereumDerivationPath)
            val ethereumSeed = EthereumSeedFactory.deriveSeed32(mnemonicWords, password = decodedEthereumDerivationPath.password).seed
            val ethereumKeypair = EthereumKeypairFactory.generate(ethereumSeed, junctions = decodedEthereumDerivationPath.junctions)
            val position = metaAccountDao.getNextPosition()

            val secretsV2 = MetaAccountSecrets(
                substrateKeyPair = keys,
                entropy = mnemonic.entropy,
                seed = seed,
                substrateDerivationPath = derivationPath,
                ethereumKeypair = ethereumKeypair,
                ethereumDerivationPath = ethereumDerivationPath
            )

            val metaAccount = MetaAccountLocal(
                substratePublicKey = keys.publicKey,
                substrateAccountId = keys.publicKey.substrateAccountId(),
                substrateCryptoType = cryptoType,
                ethereumPublicKey = ethereumKeypair.publicKey,
                ethereumAddress = ethereumKeypair.publicKey.ethereumAddressFromPublicKey(),
                name = accountName,
                isSelected = true,
                position = position
            )

            val metaAccountId = insertAccount(metaAccount)
            storeV2.putMetaAccountSecrets(metaAccountId, secretsV2)

            metaAccountId
        }
    }

    private suspend fun mapAccountLocalToAccount(accountLocal: AccountLocal): Account {
        val network = getNetworkForType(accountLocal.networkType)

        return with(accountLocal) {
            Account(
                address = address,
                name = username,
                accountIdHex = publicKey,
                cryptoType = CryptoType.values()[accountLocal.cryptoType],
                network = network,
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

    private fun getNetworkForType(networkType: Node.NetworkType): Network {
        return Network(networkType)
    }
}
