package jp.co.soramitsu.feature_account_impl.data.repository

import io.reactivex.Completable
import io.reactivex.Single
import jp.co.soramitsu.common.data.network.AppLinksProvider
import jp.co.soramitsu.core_db.dao.NodeDao
import jp.co.soramitsu.core_db.dao.UserDao
import jp.co.soramitsu.core_db.model.NodeLocal
import jp.co.soramitsu.core_db.model.UserLocal
import jp.co.soramitsu.fearless_utils.bip39.Bip39
import jp.co.soramitsu.fearless_utils.bip39.MnemonicLength
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.KeypairFactory
import jp.co.soramitsu.fearless_utils.junction.JunctionDecoder
import jp.co.soramitsu.fearless_utils.ss58.AddressType
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_account_api.domain.model.AuthType
import jp.co.soramitsu.feature_account_api.domain.model.CryptoType
import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_account_api.domain.model.NetworkType
import jp.co.soramitsu.feature_account_api.domain.model.SourceType
import jp.co.soramitsu.feature_account_impl.data.repository.datasource.AccountDatasource
import org.spongycastle.util.encoders.Hex

class AccountRepositoryImpl(
    private val accountDatasource: AccountDatasource,
    private val userDao: UserDao,
    private val nodeDao: NodeDao,
    private val bip39: Bip39,
    private val sS58Encoder: SS58Encoder,
    private val junctionDecoder: JunctionDecoder,
    private val keypairFactory: KeypairFactory,
    private val appLinksProvider: AppLinksProvider
) : AccountRepository {

    companion object {
        val DEFAULT_NODES_LIST = listOf(
            NodeLocal(0, "Kusama", "wss://kusama-rpc.polkadot.io", NetworkType.KUSAMA.ordinal, true),
            NodeLocal(1, "Polkadot", "wss://rpc.polkadot.io", NetworkType.POLKADOT.ordinal, true),
            NodeLocal(2, "Westend", "wss://westend-rpc.polkadot.io", NetworkType.WESTEND.ordinal, true)
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

    override fun getSelectedEncryptionType(): Single<CryptoType> {
        return Single.fromCallable {
            val address = accountDatasource.getSelectedAddress()
            if (address == null) {
                CryptoType.SR25519
            } else {
                accountDatasource.getCryptoType(address) ?: CryptoType.SR25519
            }
        }
    }

    override fun selectEncryptionType(cryptoType: CryptoType): Completable {
        return getSelectedAccount()
            .map {
                accountDatasource.saveCryptoType(cryptoType, it.address)
            }
            .ignoreElement()
    }

    override fun getNodes(): Single<List<Node>> {
        return nodeDao.getNodes()
            .map {
                val nodesLocal = if (it.isEmpty()) {
                    nodeDao.insert(DEFAULT_NODES_LIST)
                    DEFAULT_NODES_LIST
                } else {
                    it
                }

                nodesLocal.map {
                    mapNodeLocalToNode(it)
                }
            }
    }

    override fun getSelectedNode(): Single<Node> {
        return Single.fromCallable {
            accountDatasource.getSelectedNetwork() ?: mapNodeLocalToNode(DEFAULT_NODES_LIST.first())
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
            accountDatasource.saveSelectedNetwork(node)
        }
    }

    private fun mapNodeLocalToNode(it: NodeLocal): Node {
        return Node(it.name, NetworkType.values()[it.networkType], it.link, it.isDefault)
    }

    private fun mapNetworkToNodeLocal(it: Node): NodeLocal {
        return NodeLocal(0, it.name, it.link, it.networkType.ordinal, it.isDefault)
    }

    override fun selectAccount(account: Account): Completable {
        return Completable.fromCallable {
            accountDatasource.saveSelectedAccount(account)
        }
    }

    override fun getSelectedAccount(): Single<Account> {
        return Single.fromCallable {
            accountDatasource.getSelectedAccount()
        }
    }

    override fun removeAccount(account: Account): Completable {
        return Completable.fromCallable {
            userDao.remove(account.address)
        }
    }

    override fun createAccount(accountName: String, mnemonic: String, encryptionType: CryptoType, derivationPath: String, node: Node): Completable {
        return saveAccountData(accountName, mnemonic, derivationPath, encryptionType, node.networkType)
            .flatMapCompletable {
                saveSelectedEncryptionType(it, encryptionType)
                    .andThen(selectNode(node))
            }
    }

    override fun getAccounts(): Single<List<Account>> {
        return userDao.getUsers()
            .map {
                it.map {
                    mapUserLocalToAccount(it)
                }
            }
    }

    private fun mapUserLocalToAccount(it: UserLocal): Account {
        return Account(it.address, it.username, it.publicKey, CryptoType.values()[it.cryptoType], NetworkType.values()[it.networkType])
    }

    override fun getSourceTypes(): Single<List<SourceType>> {
        return Single.just(listOf(SourceType.MNEMONIC_PASSPHRASE, SourceType.RAW_SEED, SourceType.KEYSTORE))
    }

    private fun saveSelectedEncryptionType(address: String, encryptionType: CryptoType): Completable {
        return Completable.fromAction {
            accountDatasource.saveCryptoType(encryptionType, address)
        }
    }

    override fun importFromMnemonic(keyString: String, username: String, derivationPath: String, selectedEncryptionType: CryptoType, node: Node): Completable {
        return saveAccountData(username, keyString, derivationPath, selectedEncryptionType, node.networkType)
            .flatMapCompletable { selectNode(node) }
            .andThen { selectEncryptionType(selectedEncryptionType) }
    }

    override fun importFromSeed(keyString: String, username: String, derivationPath: String, selectedEncryptionType: CryptoType, node: Node): Completable {
        return Completable.fromAction {
            val keys = keypairFactory.generate(mapCryptoTypeToEncryption(selectedEncryptionType), Hex.decode(keyString), derivationPath)
            val addressType = mapNetworkTypeToAddressType(node.networkType)
            val address = sS58Encoder.encode(keys.publicKey, addressType)

            accountDatasource.saveDerivationPath(derivationPath, address)
            accountDatasource.saveSeed(Hex.decode(keyString), address)
            accountDatasource.setMnemonicIsBackedUp(true)

            addAccountToList(username, address, Hex.toHexString(keys.publicKey), selectedEncryptionType.ordinal, node.networkType.ordinal)
        }
            .andThen(selectNode(node))
            .andThen(selectEncryptionType(selectedEncryptionType))
    }

    private fun mapCryptoTypeToEncryption(cryptoType: CryptoType): EncryptionType {
        return when (cryptoType) {
            CryptoType.SR25519 -> EncryptionType.SR25519
            CryptoType.ED25519 -> EncryptionType.ED25519
            CryptoType.ECDSA -> EncryptionType.ECDSA
        }
    }

    private fun mapNetworkTypeToAddressType(networkType: NetworkType): AddressType {
        return when (networkType) {
            NetworkType.KUSAMA -> AddressType.KUSAMA
            NetworkType.POLKADOT -> AddressType.POLKADOT
            NetworkType.WESTEND -> AddressType.WESTEND
        }
    }

    override fun importFromJson(json: String, password: String, networkType: NetworkType): Completable {
        return Completable.complete()
    }

    override fun isCodeSet(): Single<Boolean> {
        return Single.fromCallable {
            accountDatasource.getPinCode() != null
        }
    }

    override fun savePinCode(code: String): Completable {
        return Completable.fromCallable {
            accountDatasource.savePinCode(code)
        }
    }

    override fun isPinCorrect(code: String): Single<Boolean> {
        return Single.fromCallable {
            accountDatasource.getPinCode() == code
        }
    }

    override fun getPinCode(): String? {
        return accountDatasource.getPinCode()
    }

    override fun generateMnemonic(): Single<List<String>> {
        return Single.fromCallable {
            val mnemonic = bip39.generateMnemonic(MnemonicLength.TWELVE)
            mnemonic.split(" ")
        }
    }

    override fun getAddressId(): Single<ByteArray> {
        return Single.fromCallable {
            accountDatasource.getSelectedAddress()?.let { address ->
                accountDatasource.getNetworkType()?.let { networkType ->
                    sS58Encoder.decode(address, mapNetworkTypeToAddressType(networkType))
                }
            }
        }
    }

    override fun getAddress(): Single<String> {
        return Single.fromCallable {
            accountDatasource.getSelectedAddress()
        }
    }

    override fun getUsername(): Single<String> {
        return Single.fromCallable {
            accountDatasource.getSelectedAddress()?.let {
                accountDatasource.getAccountName(it)
            }
        }
    }

    override fun isBiometricEnabled(): Single<Boolean> {
        return Single.fromCallable {
            accountDatasource.getAuthType() == AuthType.BIOMETRY
        }
    }

    override fun setBiometricOn(): Completable {
        return Completable.fromAction {
            accountDatasource.saveAuthType(AuthType.BIOMETRY)
        }
    }

    override fun setBiometricOff(): Completable {
        return Completable.fromAction {
            accountDatasource.saveAuthType(AuthType.PINCODE)
        }
    }

    private fun saveAccountData(accountName: String, mnemonic: String, derivationPath: String, cryptoType: CryptoType, networkType: NetworkType): Single<String> {
        return Single.fromCallable {
            val entropy = bip39.generateEntropy(mnemonic)
            val password = junctionDecoder.getPassword(derivationPath)
            val seed = bip39.generateSeed(entropy, password)
            val keys = keypairFactory.generate(mapCryptoTypeToEncryption(cryptoType), seed, derivationPath)
            val addressType = mapNetworkTypeToAddressType(networkType)
            val address = sS58Encoder.encode(keys.publicKey, addressType)

            accountDatasource.saveDerivationPath(derivationPath, address)
            accountDatasource.saveSeed(seed, address)
            accountDatasource.saveEntropy(entropy, address)
            accountDatasource.setMnemonicIsBackedUp(true)

            addAccountToList(accountName, address, Hex.toHexString(keys.publicKey), cryptoType.ordinal, networkType.ordinal)

            address
        }
    }

    private fun addAccountToList(accountName: String, address: String, publicKeyHex: String, cryptoType: Int, networkType: Int) {
        userDao.insert(UserLocal(address, accountName, publicKeyHex, cryptoType, networkType))
    }
}