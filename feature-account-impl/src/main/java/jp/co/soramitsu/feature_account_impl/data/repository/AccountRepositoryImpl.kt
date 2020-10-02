package jp.co.soramitsu.feature_account_impl.data.repository

import android.database.sqlite.SQLiteConstraintException
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import jp.co.soramitsu.common.data.network.AppLinksProvider
import jp.co.soramitsu.common.resources.LanguagesHolder
import jp.co.soramitsu.core_db.dao.AccountDao
import jp.co.soramitsu.core_db.dao.NodeDao
import jp.co.soramitsu.core_db.model.AccountLocal
import jp.co.soramitsu.core_db.model.NodeLocal
import jp.co.soramitsu.fearless_utils.bip39.Bip39
import jp.co.soramitsu.fearless_utils.bip39.MnemonicLength
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.JsonSeedDecoder
import jp.co.soramitsu.fearless_utils.encrypt.KeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.model.Keypair
import jp.co.soramitsu.fearless_utils.junction.JunctionDecoder
import jp.co.soramitsu.fearless_utils.ss58.AddressType
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountAlreadyExistsException
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_account_api.domain.model.AuthType
import jp.co.soramitsu.feature_account_api.domain.model.CryptoType
import jp.co.soramitsu.feature_account_api.domain.model.ImportJsonData
import jp.co.soramitsu.feature_account_api.domain.model.Language
import jp.co.soramitsu.feature_account_api.domain.model.Network
import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_account_api.domain.model.SigningData
import jp.co.soramitsu.feature_account_impl.data.network.blockchain.AccountSubstrateSource
import jp.co.soramitsu.feature_account_impl.data.repository.datasource.AccountDataSource
import org.spongycastle.util.encoders.Hex

class AccountRepositoryImpl(
    private val accountDataSource: AccountDataSource,
    private val accountDao: AccountDao,
    private val nodeDao: NodeDao,
    private val bip39: Bip39,
    private val sS58Encoder: SS58Encoder,
    private val junctionDecoder: JunctionDecoder,
    private val keypairFactory: KeypairFactory,
    private val appLinksProvider: AppLinksProvider,
    private val jsonSeedDecoder: JsonSeedDecoder,
    private val languagesHolder: LanguagesHolder,
    private val accountSubstrateSource: AccountSubstrateSource
) : AccountRepository {

    companion object {
        val DEFAULT_NODES_LIST = listOf(
            NodeLocal(
                "Kusama Parity Node",
                "wss://kusama-rpc.polkadot.io",
                Node.NetworkType.KUSAMA.ordinal,
                true
            ),
            NodeLocal(
                "Kusama, Web3 Foundation node",
                "wss://cc3-5.kusama.network",
                Node.NetworkType.KUSAMA.ordinal,
                true
            ),
            NodeLocal(
                "Polkadot Parity Node", "wss://rpc.polkadot.io",
                Node.NetworkType.POLKADOT.ordinal,
                true
            ),
            NodeLocal(
                "Polkadot, Web3 Foundation node",
                "wss://cc1-1.polkadot.network",
                Node.NetworkType.KUSAMA.ordinal,
                true
            ),
            NodeLocal(
                "Westend Parity Node",
                "wss://westend-rpc.polkadot.io",
                Node.NetworkType.WESTEND.ordinal,
                true
            )
        )
    }

    override fun getTermsAddress(): Single<String> {
        return Single.just(appLinksProvider.termsUrl)
    }

    override fun getPrivacyAddress(): Single<String> {
        return Single.just(appLinksProvider.privacyUrl)
    }

    override fun getEncryptionTypes(): Single<List<CryptoType>> {
        return Single.just(listOf(CryptoType.SR25519, CryptoType.ED25519, CryptoType.ECDSA))
    }

    override fun getNodes(): Observable<List<Node>> {
        return nodeDao.getNodes()
            .doOnNext {
                if (it.isEmpty()) nodeDao.insert(DEFAULT_NODES_LIST)
            }.map { it.map(::mapNodeLocalToNode) }
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
            accountDataSource.getSelectedNode() ?: mapNodeLocalToNode(DEFAULT_NODES_LIST.first())
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
        node: Node
    ): Completable {
        return saveAccountData(
            accountName,
            mnemonic,
            derivationPath,
            encryptionType,
            node
        ).flatMapCompletable { maybeSelectInitial(it, node) }
    }

    override fun observeAccounts(): Observable<List<Account>> {
        return accountDao.observeAccounts()
            .map { it.map(::mapAccountLocalToAccount) }
    }

    override fun getAccount(address: String): Single<Account> {
        return accountDao.getAccount(address)
            .map(::mapAccountLocalToAccount)
    }

    override fun importFromMnemonic(
        keyString: String,
        username: String,
        derivationPath: String,
        selectedEncryptionType: CryptoType,
        node: Node
    ): Completable {
        return saveAccountData(
            username,
            keyString,
            derivationPath,
            selectedEncryptionType,
            node
        ).flatMapCompletable { maybeSelectInitial(it, node) }
    }

    override fun importFromSeed(
        keyString: String,
        username: String,
        derivationPath: String,
        selectedEncryptionType: CryptoType,
        node: Node
    ): Completable {
        return Single.fromCallable {
            val keys = keypairFactory.generate(
                mapCryptoTypeToEncryption(selectedEncryptionType),
                Hex.decode(keyString),
                derivationPath
            )

            val addressType = mapNetworkTypeToAddressType(node.networkType)
            val address = sS58Encoder.encode(keys.publicKey, addressType)

            accountDataSource.saveDerivationPath(derivationPath, address)
            accountDataSource.saveSeed(Hex.decode(keyString), address)
            accountDataSource.saveSigningData(address, mapKeyPairToSigningData(keys))
            accountDataSource.setMnemonicIsBackedUp(true)

            val publicKeyEncoded = Hex.toHexString(keys.publicKey)

            val accountLocal = insertAccount(address, username, publicKeyEncoded, selectedEncryptionType, node.networkType)

            val network = getNetworkForType(node.networkType)

            Account(address, username, publicKeyEncoded, selectedEncryptionType, accountLocal.position, network)
        }.flatMapCompletable { maybeSelectInitial(it, node) }
    }

    override fun importFromJson(
        json: String,
        password: String,
        name: String
    ): Completable {
        return Completable.fromAction {
            val importData = jsonSeedDecoder.decode(json, password)

            val publicKeyEncoded = Hex.toHexString(importData.keypair.publicKey)

            val cryptoType = mapEncryptionToCryptoType(importData.encryptionType)
            val networkType = mapAddressTypeToNetworkType(importData.networType)

            accountDataSource.saveSigningData(importData.address, mapKeyPairToSigningData(importData.keypair))

            val accountLocal = insertAccount(importData.address, name, publicKeyEncoded, cryptoType, networkType)

            val network = getNetworkForType(networkType)

            val account = Account(accountLocal.address, name, publicKeyEncoded, cryptoType, accountLocal.position, network)

            val node = account.network.defaultNode

            maybeSelectInitial(account, node).blockingAwait()
        }
    }

    override fun isCodeSet(): Single<Boolean> {
        return Single.fromCallable {
            accountDataSource.getPinCode() != null
        }
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

    override fun getAddressId(account: Account): Single<ByteArray> {
        return Single.fromCallable {
            val addressType = mapNetworkTypeToAddressType(account.network.type)

            sS58Encoder.decode(account.address, addressType)
        }
    }

    override fun isBiometricEnabled(): Single<Boolean> {
        return Single.fromCallable {
            accountDataSource.getAuthType() == AuthType.BIOMETRY
        }
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
                val network = getNetworkForType(mapAddressTypeToNetworkType(networkType))
                val cryptoType = mapEncryptionToCryptoType(encryptionType)

                ImportJsonData(name, network, cryptoType)
            }
        }
    }

    override fun getSigningData(): Single<SigningData> {
        return observeSelectedAccount().firstOrError()
            .map { accountDataSource.getSigningData(it.address)!! }
    }

    private fun saveAccountData(
        accountName: String,
        mnemonic: String,
        derivationPath: String,
        cryptoType: CryptoType,
        node: Node
    ): Single<Account> {
        return Single.fromCallable {
            val entropy = bip39.generateEntropy(mnemonic)
            val password = junctionDecoder.getPassword(derivationPath)
            val seed = bip39.generateSeed(entropy, password)
            val keys =
                keypairFactory.generate(mapCryptoTypeToEncryption(cryptoType), seed, derivationPath)
            val addressType = mapNetworkTypeToAddressType(node.networkType)
            val address = sS58Encoder.encode(keys.publicKey, addressType)

            accountDataSource.saveDerivationPath(derivationPath, address)
            accountDataSource.saveSeed(seed, address)
            accountDataSource.saveEntropy(entropy, address)
            accountDataSource.saveSigningData(address, mapKeyPairToSigningData(keys))
            accountDataSource.setMnemonicIsBackedUp(true)

            val publicKeyEncoded = Hex.toHexString(keys.publicKey)

            val accountLocal = insertAccount(address, accountName, publicKeyEncoded, cryptoType, node.networkType)

            val network = getNetworkForType(node.networkType)

            Account(address, accountName, publicKeyEncoded, cryptoType, accountLocal.position, network)
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

    private fun mapNetworkTypeToAddressType(networkType: Node.NetworkType): AddressType {
        return when (networkType) {
            Node.NetworkType.KUSAMA -> AddressType.KUSAMA
            Node.NetworkType.POLKADOT -> AddressType.POLKADOT
            Node.NetworkType.WESTEND -> AddressType.WESTEND
        }
    }

    private fun mapAddressTypeToNetworkType(networkType: AddressType): Node.NetworkType {
        return when (networkType) {
            AddressType.KUSAMA -> Node.NetworkType.KUSAMA
            AddressType.POLKADOT -> Node.NetworkType.POLKADOT
            AddressType.WESTEND -> Node.NetworkType.WESTEND
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

    private fun mapAccountLocalToAccount(accountLocal: AccountLocal): Account {
        val networkType = Node.NetworkType.values()[accountLocal.networkType]
        val network = getNetworkForType(networkType)

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
                networkType = network.type.ordinal,
                publicKey = publicKey,
                position = position
            )
        }
    }

    private fun maybeSelectInitial(account: Account, node: Node): Completable {
        return isAccountSelected().flatMapCompletable { isSelected ->
            if (isSelected) {
                Completable.complete()
            } else {
                selectAccount(account)
                    .andThen(selectNode(node))
            }
        }
    }

    private fun insertAccount(
        address: String,
        accountName: String,
        publicKeyEncoded: String,
        cryptoType: CryptoType,
        networkType: Node.NetworkType
    ) = try {
        val networkTypeLocal = networkType.ordinal
        val cryptoTypeLocal = cryptoType.ordinal

        val positionInGroup = accountDao.getNextPosition()

        val account = AccountLocal(
            address = address,
            username = accountName,
            publicKey = publicKeyEncoded,
            cryptoType = cryptoTypeLocal,
            networkType = networkTypeLocal,
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

    override fun checkNodeExists(nodeHost: String): Single<Boolean> {
        return nodeDao.getNodesCountByHost(nodeHost)
            .map { it > 0 }
    }

    override fun getNetworkName(nodeHost: String): Single<String> {
        return accountSubstrateSource.getNodeNetworkType(nodeHost)
    }
}