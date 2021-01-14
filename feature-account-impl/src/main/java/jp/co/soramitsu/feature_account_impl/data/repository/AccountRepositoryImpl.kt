package jp.co.soramitsu.feature_account_impl.data.repository

import android.database.sqlite.SQLiteConstraintException
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import jp.co.soramitsu.common.resources.LanguagesHolder
import jp.co.soramitsu.common.utils.encode
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
import jp.co.soramitsu.fearless_utils.junction.JunctionDecoder
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountAlreadyExistsException
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_account_api.domain.model.AuthType
import jp.co.soramitsu.feature_account_api.domain.model.CryptoType
import jp.co.soramitsu.feature_account_api.domain.model.ImportJsonData
import jp.co.soramitsu.feature_account_api.domain.model.JsonFormer
import jp.co.soramitsu.feature_account_api.domain.model.Language
import jp.co.soramitsu.feature_account_api.domain.model.Network
import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_account_api.domain.model.SecuritySource
import jp.co.soramitsu.feature_account_api.domain.model.SigningData
import jp.co.soramitsu.feature_account_api.domain.model.WithJson
import jp.co.soramitsu.feature_account_impl.data.network.blockchain.AccountSubstrateSource
import jp.co.soramitsu.feature_account_impl.data.repository.datasource.AccountDataSource
import org.bouncycastle.util.encoders.Hex

class AccountRepositoryImpl(
    private val accountDataSource: AccountDataSource,
    private val accountDao: AccountDao,
    private val nodeDao: NodeDao,
    private val bip39: Bip39,
    private val sS58Encoder: SS58Encoder,
    private val junctionDecoder: JunctionDecoder,
    private val keypairFactory: KeypairFactory,
    private val jsonSeedDecoder: JsonSeedDecoder,
    private val jsonSeedEncoder: JsonSeedEncoder,
    private val languagesHolder: LanguagesHolder,
    private val accountSubstrateSource: AccountSubstrateSource
) : AccountRepository {

    override fun getEncryptionTypes(): Single<List<CryptoType>> {
        return Single.just(listOf(CryptoType.SR25519, CryptoType.ED25519, CryptoType.ECDSA))
    }

    override fun getNodes(): Observable<List<Node>> {
        return nodeDao.getNodes()
            .map { it.map(::mapNodeLocalToNode) }
    }

    override fun getNode(nodeId: Int): Single<Node> {
        return nodeDao.getNodeById(nodeId)
            .map(::mapNodeLocalToNode)
    }

    override fun getNetworks(): Single<List<Network>> {
        return getNodes()
            .filter { it.isNotEmpty() }
            .map { it.map(Node::networkType) }
            .map { it.map(::getNetworkForType).distinct() }
            .firstOrError()
    }

    override fun getSelectedNode(): Single<Node> {
        return Single.fromCallable {
            accountDataSource.getSelectedNode() ?: mapNodeLocalToNode(nodeDao.getFirstNode())
        }
    }

    override fun saveNode(node: Node): Completable {
        return Completable.fromCallable {
            nodeDao.insert(mapNetworkToNodeLocal(node))
        }
    }

    override fun removeNode(node: Node): Completable {
        return Completable.fromCallable {
            nodeDao.remove(node.link)
        }
    }

    override fun selectNode(node: Node): Completable {
        return Completable.fromAction {
            accountDataSource.saveSelectedNode(node)
        }
    }

    override fun getDefaultNode(networkType: Node.NetworkType): Single<Node> {
        return Single.fromCallable {
            getNetworkForType(networkType).defaultNode
        }
    }

    override fun selectAccount(account: Account): Completable {
        return Completable.fromCallable {
            accountDataSource.saveSelectedAccount(account)
        }
    }

    override fun observeSelectedAccount(): Observable<Account> {
        return accountDataSource.observeSelectedAccount()
    }

    override fun getSelectedAccount(): Single<Account> {
        return observeSelectedAccount().firstOrError()
    }

    override fun getPreferredCryptoType(): Single<CryptoType> {
        return accountDataSource.getPreferredCryptoType()
    }

    override fun isAccountSelected(): Single<Boolean> {
        return Single.fromCallable(accountDataSource::anyAccountSelected)
    }

    override fun removeAccount(account: Account): Completable {
        return Completable.fromCallable {
            accountDao.remove(account.address)
        }
    }

    override fun createAccount(
        accountName: String,
        mnemonic: String,
        encryptionType: CryptoType,
        derivationPath: String,
        networkType: Node.NetworkType
    ): Completable {
        return saveFromMnemonic(
            accountName,
            mnemonic,
            derivationPath,
            encryptionType,
            networkType,
            isImport = false
        ).flatMapCompletable(this::switchToAccount)
    }

    override fun observeAccounts(): Observable<List<Account>> {
        return accountDao.observeAccounts()
            .map { it.map(::mapAccountLocalToAccount) }
    }

    override fun getAccount(address: String): Single<Account> {
        return accountDao.getAccount(address)
            .map(::mapAccountLocalToAccount)
    }

    override fun getMyAccounts(query: String, networkType: Node.NetworkType): Single<Set<String>> {
        return accountDao.getAddresses(query, networkType).map { it.toSet() }
    }

    override fun importFromMnemonic(
        keyString: String,
        username: String,
        derivationPath: String,
        selectedEncryptionType: CryptoType,
        networkType: Node.NetworkType
    ): Completable {
        return saveFromMnemonic(
            username,
            keyString,
            derivationPath,
            selectedEncryptionType,
            networkType,
            isImport = true
        ).flatMapCompletable { switchToAccount(it) }
    }

    override fun importFromSeed(
        seed: String,
        username: String,
        derivationPath: String,
        selectedEncryptionType: CryptoType,
        networkType: Node.NetworkType
    ): Completable {
        return Single.fromCallable {
            val seedBytes = Hex.decode(seed.removePrefix("0x"))

            val keys = keypairFactory.generate(
                mapCryptoTypeToEncryption(selectedEncryptionType),
                seedBytes,
                derivationPath
            )

            val signingData = mapKeyPairToSigningData(keys)

            val address = sS58Encoder.encode(keys.publicKey, networkType)

            val securitySource = SecuritySource.Specified.Seed(seedBytes, signingData, derivationPath)

            val publicKeyEncoded = Hex.toHexString(keys.publicKey)

            val accountLocal = insertAccount(address, username, publicKeyEncoded, selectedEncryptionType, networkType)

            accountDataSource.saveSecuritySource(address, securitySource)

            mapAccountLocalToAccount(accountLocal)
        }.flatMapCompletable(this::switchToAccount)
    }

    override fun importFromJson(
        json: String,
        password: String,
        networkType: Node.NetworkType,
        name: String
    ): Completable {
        return Single.fromCallable {
            val importData = jsonSeedDecoder.decode(json, password)

            with(importData) {
                val publicKeyEncoded = Hex.toHexString(keypair.publicKey)

                val cryptoType = mapEncryptionToCryptoType(encryptionType)

                val signingData = mapKeyPairToSigningData(keypair)

                val securitySource = SecuritySource.Specified.Json(seed, signingData)

                val actualAddress = sS58Encoder.encode(keypair.publicKey, networkType)

                val accountLocal = insertAccount(actualAddress, name, publicKeyEncoded, cryptoType, networkType)

                accountDataSource.saveSecuritySource(actualAddress, securitySource)

                mapAccountLocalToAccount(accountLocal)
            }
        }.flatMapCompletable(this::switchToAccount)
    }

    override fun isCodeSet(): Boolean {
        return accountDataSource.getPinCode() != null
    }

    override fun savePinCode(code: String): Completable {
        return Completable.fromCallable {
            accountDataSource.savePinCode(code)
        }
    }

    override fun isPinCorrect(code: String): Single<Boolean> {
        return Single.fromCallable {
            accountDataSource.getPinCode() == code
        }
    }

    override fun getPinCode(): String? {
        return accountDataSource.getPinCode()
    }

    override fun generateMnemonic(): Single<List<String>> {
        return Single.fromCallable {
            val mnemonic = bip39.generateMnemonic(MnemonicLength.TWELVE)
            mnemonic.split(" ")
        }
    }

    override fun getAddressId(address: String): Single<ByteArray> {
        return Single.fromCallable {
            sS58Encoder.decode(address)
        }
    }

    override fun isInCurrentNetwork(address: String): Single<Boolean> {
        return getSelectedAccount().map {
            val otherAddressByte = sS58Encoder.extractAddressByte(address)
            val currentAddressByte = sS58Encoder.extractAddressByte(it.address)

            otherAddressByte == currentAddressByte
        }
    }

    override fun isBiometricEnabled(): Boolean {
        return accountDataSource.getAuthType() == AuthType.BIOMETRY
    }

    override fun setBiometricOn(): Completable {
        return Completable.fromAction {
            accountDataSource.saveAuthType(AuthType.BIOMETRY)
        }
    }

    override fun setBiometricOff(): Completable {
        return Completable.fromAction {
            accountDataSource.saveAuthType(AuthType.PINCODE)
        }
    }

    override fun updateAccount(newAccount: Account): Completable {
        return accountDao.updateAccount(mapAccountToAccountLocal(newAccount))
    }

    override fun updateAccounts(accounts: List<Account>): Completable {
        val accountsLocal = accounts.map(::mapAccountToAccountLocal)

        return accountDao.updateAccounts(accountsLocal)
    }

    override fun deleteAccount(address: String): Completable {
        return accountDao.remove(address)
    }

    override fun processAccountJson(json: String): Single<ImportJsonData> {
        return Single.fromCallable {
            val importAccountMeta = jsonSeedDecoder.extractImportMetaData(json)

            with(importAccountMeta) {
                val networkType = constructNetworkType(networkTypeIdentifier)
                val cryptoType = mapEncryptionToCryptoType(encryptionType)

                ImportJsonData(name, networkType, cryptoType)
            }
        }
    }

    override fun getCurrentSecuritySource(): Single<SecuritySource> {
        return observeSelectedAccount().firstOrError()
            .map { accountDataSource.getSecuritySource(it.address) }
    }

    override fun getSecuritySource(accountAddress: String): Single<SecuritySource> {
        return Single.fromCallable {
            accountDataSource.getSecuritySource(accountAddress)
        }
    }

    override fun generateRestoreJson(account: Account, password: String): Single<String> {
        return getSecuritySource(account.address).map {
            require(it is WithJson)

            val seed = (it.jsonFormer() as? JsonFormer.Seed)?.seed
            val keypair = mapSigningDataToKeypair(it.signingData)

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

    private fun saveFromMnemonic(
        accountName: String,
        mnemonic: String,
        derivationPath: String,
        cryptoType: CryptoType,
        networkType: Node.NetworkType,
        isImport: Boolean
    ): Single<Account> {
        return Single.fromCallable {
            val entropy = bip39.generateEntropy(mnemonic)
            val password = junctionDecoder.getPassword(derivationPath)
            val seed = bip39.generateSeed(entropy, password)
            val keys = keypairFactory.generate(mapCryptoTypeToEncryption(cryptoType), seed, derivationPath)
            val address = sS58Encoder.encode(keys.publicKey, networkType)
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

    private fun mapAccountLocalToAccount(accountLocal: AccountLocal): Account {
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

    private fun switchToAccount(account: Account): Completable {
        return selectAccount(account)
            .andThen(selectNode(account.network.defaultNode))
    }

    private fun insertAccount(
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

    private fun getNetworkForType(networkType: Node.NetworkType): Network {
        val defaultNode = nodeDao.getDefaultNodeFor(networkType.ordinal)

        return Network(networkType, mapNodeLocalToNode(defaultNode))
    }

    private fun mapNodeLocalToNode(it: NodeLocal): Node {
        val networkType = Node.NetworkType.values()[it.networkType]

        return Node(it.id, it.name, networkType, it.link, it.isDefault)
    }

    private fun mapNetworkToNodeLocal(it: Node): NodeLocal {
        return NodeLocal(it.name, it.link, it.networkType.ordinal, it.isDefault)
    }

    override fun observeNodes(): Observable<List<Node>> {
        return getNodes()
            .filter { it.isNotEmpty() }
    }

    override fun observeSelectedNode(): Observable<Node> {
        return accountDataSource.observeSelectedNode()
    }

    override fun observeLanguages(): Observable<List<Language>> {
        return Observable.just(languagesHolder.getLanguages())
    }

    override fun getSelectedLanguage(): Single<Language> {
        return Single.just(accountDataSource.getSelectedLanguage())
    }

    override fun changeLanguage(language: Language): Completable {
        return Completable.fromAction {
            accountDataSource.changeSelectedLanguage(language)
        }
    }

    override fun addNode(nodeName: String, nodeHost: String, networkType: Node.NetworkType): Completable {
        return Completable.fromAction {
            val nodeLocal = NodeLocal(nodeName, nodeHost, networkType.ordinal, false)
            nodeDao.insert(nodeLocal)
        }
    }

    override fun updateNode(nodeId: Int, newName: String, newHost: String, networkType: Node.NetworkType): Completable {
        return nodeDao.updateNode(nodeId, newName, newHost, networkType.ordinal)
    }

    override fun checkNodeExists(nodeHost: String): Single<Boolean> {
        return nodeDao.checkNodeExists(nodeHost)
    }

    override fun getNetworkName(nodeHost: String): Single<String> {
        return accountSubstrateSource.getNodeNetworkType(nodeHost)
    }

    override fun getAccountsByNetworkType(networkType: Node.NetworkType): Single<List<Account>> {
        return accountDao.getAccountsByNetworkType(networkType.ordinal)
            .map { it.map(::mapAccountLocalToAccount) }
    }

    override fun deleteNode(nodeId: Int): Completable {
        return nodeDao.deleteNode(nodeId)
    }
}