package jp.co.soramitsu.feature_account_impl.data.repository

import android.database.sqlite.SQLiteConstraintException
import jp.co.soramitsu.common.resources.LanguagesHolder
import jp.co.soramitsu.common.utils.mapList
import jp.co.soramitsu.common.utils.toAddress
import jp.co.soramitsu.core.model.CryptoType
import jp.co.soramitsu.core.model.Network
import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.core.model.SigningData
import jp.co.soramitsu.core_db.dao.AccountDao
import jp.co.soramitsu.core_db.dao.NodeDao
import jp.co.soramitsu.core_db.model.AccountLocal
import jp.co.soramitsu.core_db.model.NodeLocal
import jp.co.soramitsu.fearless_utils.bip39.Bip39
import jp.co.soramitsu.fearless_utils.bip39.MnemonicLength
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.KeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.json.JsonSeedDecoder
import jp.co.soramitsu.fearless_utils.encrypt.json.JsonSeedEncoder
import jp.co.soramitsu.fearless_utils.encrypt.model.Keypair
import jp.co.soramitsu.fearless_utils.encrypt.model.NetworkTypeIdentifier
import jp.co.soramitsu.fearless_utils.encrypt.qr.QrSharing
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.junction.JunctionDecoder
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.addressByte
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountAlreadyExistsException
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_account_api.domain.model.AuthType
import jp.co.soramitsu.feature_account_api.domain.model.ImportJsonData
import jp.co.soramitsu.feature_account_api.domain.model.JsonFormer
import jp.co.soramitsu.feature_account_api.domain.model.Language
import jp.co.soramitsu.feature_account_api.domain.model.SecuritySource
import jp.co.soramitsu.feature_account_api.domain.model.WithJson
import jp.co.soramitsu.feature_account_impl.data.network.blockchain.AccountSubstrateSource
import jp.co.soramitsu.feature_account_impl.data.repository.datasource.AccountDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.bouncycastle.util.encoders.Hex

class AccountRepositoryImpl(
    private val accountDataSource: AccountDataSource,
    private val accountDao: AccountDao,
    private val nodeDao: NodeDao,
    private val bip39: Bip39,
    private val junctionDecoder: JunctionDecoder,
    private val keypairFactory: KeypairFactory,
    private val jsonSeedDecoder: JsonSeedDecoder,
    private val jsonSeedEncoder: JsonSeedEncoder,
    private val languagesHolder: LanguagesHolder,
    private val accountSubstrateSource: AccountSubstrateSource
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

    override suspend fun getSelectedNode(): Node {
        return accountDataSource.getSelectedNode() ?: mapNodeLocalToNode(nodeDao.getFirstNode())
    }

    override suspend fun selectNode(node: Node) {
        accountDataSource.saveSelectedNode(node)
    }

    override suspend fun getDefaultNode(networkType: Node.NetworkType): Node {
        return getNetworkForType(networkType).defaultNode
    }

    override suspend fun selectAccount(account: Account) {
        accountDataSource.saveSelectedAccount(account)
    }

    override fun selectedAccountFlow(): Flow<Account> {
        return accountDataSource.selectedAccountFlow()
    }

    override suspend fun getSelectedAccount(): Account {
        return accountDataSource.getSelectedAccount()
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
        derivationPath: String,
        networkType: Node.NetworkType
    ) {
        val account = saveFromMnemonic(
            accountName,
            mnemonic,
            derivationPath,
            encryptionType,
            networkType,
            isImport = false
        )

        switchToAccount(account)
    }

    override fun accountsFlow(): Flow<List<Account>> {
        return accountDao.accountsFlow()
            .mapList(::mapAccountLocalToAccount)
            .flowOn(Dispatchers.Default)
    }

    override suspend fun getAccounts(): List<Account> {
        return accountDao.getAccounts()
            .map { mapAccountLocalToAccount(it) }
    }

    override suspend fun getAccount(address: String): Account {
        val account = accountDao.getAccount(address)
        return mapAccountLocalToAccount(account)
    }

    override suspend fun getMyAccounts(query: String, networkType: Node.NetworkType): Set<Account> {
        return withContext(Dispatchers.Default) {
            accountDao.getAccounts(query, networkType)
                .map { mapAccountLocalToAccount(it) }
                .toSet()
        }
    }

    override suspend fun importFromMnemonic(
        keyString: String,
        username: String,
        derivationPath: String,
        selectedEncryptionType: CryptoType,
        networkType: Node.NetworkType
    ) {
        val account = saveFromMnemonic(
            username,
            keyString,
            derivationPath,
            selectedEncryptionType,
            networkType,
            isImport = true
        )

        switchToAccount(account)
    }

    override suspend fun importFromSeed(
        seed: String,
        username: String,
        derivationPath: String,
        selectedEncryptionType: CryptoType,
        networkType: Node.NetworkType
    ) {
        return withContext(Dispatchers.Default) {
            val seedBytes = Hex.decode(seed.removePrefix("0x"))

            val keys = keypairFactory.generate(
                mapCryptoTypeToEncryption(selectedEncryptionType),
                seedBytes,
                derivationPath
            )

            val signingData = mapKeyPairToSigningData(keys)

            val address = keys.publicKey.toAddress(networkType)

            val securitySource = SecuritySource.Specified.Seed(seedBytes, signingData, derivationPath)

            val publicKeyEncoded = Hex.toHexString(keys.publicKey)

            val accountLocal = insertAccount(address, username, publicKeyEncoded, selectedEncryptionType, networkType)

            accountDataSource.saveSecuritySource(address, securitySource)

            val account = mapAccountLocalToAccount(accountLocal)

            switchToAccount(account)
        }
    }

    override suspend fun importFromJson(
        json: String,
        password: String,
        networkType: Node.NetworkType,
        name: String
    ) {
        return withContext(Dispatchers.Default) {
            val importData = jsonSeedDecoder.decode(json, password)

            val newAccount = with(importData) {
                val publicKeyEncoded = Hex.toHexString(keypair.publicKey)

                val cryptoType = mapEncryptionToCryptoType(encryptionType)

                val signingData = mapKeyPairToSigningData(keypair)

                val securitySource = SecuritySource.Specified.Json(seed, signingData)

                val actualAddress = keypair.publicKey.toAddress(networkType)

                val accountLocal = insertAccount(actualAddress, name, publicKeyEncoded, cryptoType, networkType)

                accountDataSource.saveSecuritySource(actualAddress, securitySource)

                mapAccountLocalToAccount(accountLocal)
            }

            switchToAccount(newAccount)
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
            val mnemonic = bip39.generateMnemonic(MnemonicLength.TWELVE)

            mnemonic.split(" ")
        }
    }

    override suspend fun isInCurrentNetwork(address: String): Boolean {
        val currentAccount = getSelectedAccount()

        return try {
            val otherAddressByte = address.addressByte()
            val currentAddressByte = currentAccount.address.addressByte()

            address.toAccountId() // decoded without exception

            otherAddressByte == currentAddressByte
        } catch (_: Exception) {
            false
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

    override suspend fun updateAccount(newAccount: Account) {
        return accountDao.updateAccount(mapAccountToAccountLocal(newAccount))
    }

    override suspend fun updateAccounts(accounts: List<Account>) {
        val accountsLocal = accounts.map(::mapAccountToAccountLocal)

        return accountDao.updateAccounts(accountsLocal)
    }

    override suspend fun deleteAccount(address: String) {
        return accountDao.remove(address)
    }

    override suspend fun processAccountJson(json: String): ImportJsonData {
        return withContext(Dispatchers.Default) {
            val importAccountMeta = jsonSeedDecoder.extractImportMetaData(json)

            with(importAccountMeta) {
                val networkType = constructNetworkType(networkTypeIdentifier)
                val cryptoType = mapEncryptionToCryptoType(encryptionType)

                ImportJsonData(name, networkType, cryptoType)
            }
        }
    }

    override suspend fun getCurrentSecuritySource(): SecuritySource {
        val account = getSelectedAccount()

        return accountDataSource.getSecuritySource(account.address)!!
    }

    override suspend fun getSecuritySource(accountAddress: String): SecuritySource {
        return accountDataSource.getSecuritySource(accountAddress)!!
    }

    override suspend fun generateRestoreJson(account: Account, password: String): String {
        return withContext(Dispatchers.Default) {
            val securitySource = getSecuritySource(account.address)
            require(securitySource is WithJson)

            val seed = (securitySource.jsonFormer() as? JsonFormer.Seed)?.seed
            val keypair = mapSigningDataToKeypair(securitySource.signingData)

            val cryptoType = mapCryptoTypeToEncryption(account.cryptoType)
            val runtimeConfiguration = account.network.type.runtimeConfiguration

            jsonSeedEncoder.generate(
                keypair = keypair,
                seed = seed,
                password = password,
                name = account.name.orEmpty(),
                encryptionType = cryptoType,
                genesisHash = runtimeConfiguration.genesisHash,
                addressByte = runtimeConfiguration.addressByte
            )
        }
    }

    override fun nodesFlow(): Flow<List<Node>> {
        return nodeDao.nodesFlow()
            .mapList { mapNodeLocalToNode(it) }
            .filter { it.isNotEmpty() }
            .flowOn(Dispatchers.Default)
    }

    override fun selectedNodeFlow(): Flow<Node> {
        return accountDataSource.selectedNodeFlow()
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
        val payload = QrSharing.Payload(account.address, account.publicKey.fromHex(), account.name)

        return QrSharing.encode(payload)
    }

    private suspend fun saveFromMnemonic(
        accountName: String,
        mnemonic: String,
        derivationPath: String,
        cryptoType: CryptoType,
        networkType: Node.NetworkType,
        isImport: Boolean
    ): Account {
        return withContext(Dispatchers.Default) {
            val entropy = bip39.generateEntropy(mnemonic)
            val password = junctionDecoder.getPassword(derivationPath)
            val seed = bip39.generateSeed(entropy, password)
            val keys = keypairFactory.generate(mapCryptoTypeToEncryption(cryptoType), seed, derivationPath)
            val address = keys.publicKey.toAddress(networkType)
            val signingData = mapKeyPairToSigningData(keys)

            val securitySource: SecuritySource.Specified = if (isImport) {
                SecuritySource.Specified.Mnemonic(seed, signingData, mnemonic, derivationPath)
            } else {
                SecuritySource.Specified.Create(seed, signingData, mnemonic, derivationPath)
            }

            val publicKeyEncoded = Hex.toHexString(keys.publicKey)

            val accountLocal = insertAccount(address, accountName, publicKeyEncoded, cryptoType, networkType)
            accountDataSource.saveSecuritySource(address, securitySource)

            mapAccountLocalToAccount(accountLocal)
        }
    }

    private fun mapCryptoTypeToEncryption(cryptoType: CryptoType): EncryptionType {
        return when (cryptoType) {
            CryptoType.SR25519 -> EncryptionType.SR25519
            CryptoType.ED25519 -> EncryptionType.ED25519
            CryptoType.ECDSA -> EncryptionType.ECDSA
        }
    }

    private fun mapEncryptionToCryptoType(cryptoType: EncryptionType): CryptoType {
        return when (cryptoType) {
            EncryptionType.SR25519 -> CryptoType.SR25519
            EncryptionType.ED25519 -> CryptoType.ED25519
            EncryptionType.ECDSA -> CryptoType.ECDSA
        }
    }

    private fun constructNetworkType(identifier: NetworkTypeIdentifier): Node.NetworkType? {
        return when (identifier) {
            is NetworkTypeIdentifier.Genesis -> Node.NetworkType.findByGenesis(identifier.genesis)
            is NetworkTypeIdentifier.AddressByte -> Node.NetworkType.findByAddressByte(identifier.addressByte)
            is NetworkTypeIdentifier.Undefined -> null
        }
    }

    private fun mapKeyPairToSigningData(keyPair: Keypair): SigningData {
        return with(keyPair) {
            SigningData(
                publicKey = publicKey,
                privateKey = privateKey,
                nonce = nonce
            )
        }
    }

    private fun mapSigningDataToKeypair(singingData: SigningData): Keypair {
        return with(singingData) {
            Keypair(
                publicKey = publicKey,
                privateKey = privateKey,
                nonce = nonce
            )
        }
    }

    private suspend fun mapAccountLocalToAccount(accountLocal: AccountLocal): Account {
        val network = getNetworkForType(accountLocal.networkType)

        return with(accountLocal) {
            Account(
                address = address,
                name = username,
                publicKey = publicKey,
                cryptoType = CryptoType.values()[accountLocal.cryptoType],
                network = network,
                position = position
            )
        }
    }

    private fun mapAccountToAccountLocal(account: Account): AccountLocal {
        val nameLocal = account.name ?: ""

        return with(account) {
            AccountLocal(
                address = address,
                username = nameLocal,
                cryptoType = cryptoType.ordinal,
                networkType = network.type,
                publicKey = publicKey,
                position = position
            )
        }
    }

    private suspend fun switchToAccount(account: Account) {
        selectAccount(account)

        selectNode(account.network.defaultNode)
    }

    private suspend fun insertAccount(
        address: String,
        accountName: String,
        publicKeyEncoded: String,
        cryptoType: CryptoType,
        networkType: Node.NetworkType
    ) = try {
        val cryptoTypeLocal = cryptoType.ordinal

        val positionInGroup = accountDao.getNextPosition()

        val account = AccountLocal(
            address = address,
            username = accountName,
            publicKey = publicKeyEncoded,
            cryptoType = cryptoTypeLocal,
            networkType = networkType,
            position = positionInGroup
        )

        accountDao.insert(account)

        account
    } catch (e: SQLiteConstraintException) {
        throw AccountAlreadyExistsException()
    }

    private suspend fun getNetworkForType(networkType: Node.NetworkType): Network {
        val defaultNode = nodeDao.getDefaultNodeFor(networkType.ordinal)

        return Network(networkType, mapNodeLocalToNode(defaultNode))
    }

    private fun mapNodeLocalToNode(it: NodeLocal): Node {
        val networkType = Node.NetworkType.values()[it.networkType]

        return Node(it.id, it.name, networkType, it.link, it.isDefault)
    }
}