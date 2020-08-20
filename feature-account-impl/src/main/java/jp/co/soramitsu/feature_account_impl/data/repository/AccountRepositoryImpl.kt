package jp.co.soramitsu.feature_account_impl.data.repository

import io.github.novacrypto.bip39.Words
import io.reactivex.Completable
import io.reactivex.Single
import jp.co.soramitsu.common.data.network.AppLinksProvider
import jp.co.soramitsu.core_db.dao.NodeDao
import jp.co.soramitsu.core_db.dao.UserDao
import jp.co.soramitsu.core_db.model.NodeLocal
import jp.co.soramitsu.core_db.model.UserLocal
import jp.co.soramitsu.fearless_utils.bip39.Bip39
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.KeypairFactory
import jp.co.soramitsu.fearless_utils.junction.JunctionDecoder
import jp.co.soramitsu.fearless_utils.ss58.AddressType
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.model.CryptoType
import jp.co.soramitsu.feature_account_api.domain.model.Network
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

    init {
        nodeDao.insert(DEFAULT_NODES_LIST)
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
        return getSelectedAddress()
            .flatMap {
                Single.fromCallable {
                    accountDatasource.getCryptoType(it) ?: CryptoType.SR25519
                }
            }
    }

    private fun getSelectedAddress(): Single<String> {
        return Single.fromCallable {
            val address = accountDatasource.getSelectedAddress()
            if (address == null) {
                // TODO: generate address here
                val newAddress = ""
                accountDatasource.saveSelectedAddress(newAddress)
                newAddress
            } else {
                address
            }
        }
    }

    override fun getNetworks(): Single<List<Network>> {
        return nodeDao.getNodes()
            .flatMap { nodes ->
                getSelectedNetworkLink()
                    .map {
                        Pair(nodes, it)
                    }
            }
            .map { pair ->
                pair.first
                    .map {
                        mapNodeLocalToNetwork(it, pair.second)
                    }
            }
    }

    override fun getSelectedNetwork(): Single<Network> {
        return getSelectedNetworkLink()
            .flatMap { nodeDao.getNode(it) }
            .map { mapNodeLocalToNetwork(it, it.link) }
    }

    override fun saveNetwork(network: Network): Completable {
        return Completable.fromCallable {
            nodeDao.insert(mapNetworkToNodeLocal(network))
        }
    }

    override fun removeNetwork(network: Network): Completable {
        return Completable.fromCallable {
            nodeDao.remove(network.link)
        }
    }

    override fun selectNetwork(network: Network): Completable {
        return Completable.fromAction {
            accountDatasource.saveSelectedNodeLink(network.link)
        }
    }

    private fun getSelectedNetworkLink(): Single<String> {
        return Single.fromCallable {
            accountDatasource.getSelectedNodeLink() ?: DEFAULT_NODES_LIST.first().link
        }
    }

    private fun mapNodeLocalToNetwork(it: NodeLocal, selectedLink: String): Network {
        return Network(it.name, NetworkType.values()[it.networkType], it.link, it.default, it.link == selectedLink)
    }

    private fun mapNetworkToNodeLocal(it: Network): NodeLocal {
        return NodeLocal(0, it.name, it.link, it.networkType.ordinal, it.default)

    }

    override fun selectAccount(address: String): Completable {
        return userDao.getUser(address).ignoreElement()
    }

    override fun removeAccount(address: String): Completable {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun createAccount(accountName: String, mnemonic: String, encryptionType: CryptoType, derivationPath: String, network: NetworkType): Completable {
        return saveSelectedEncryptionType(encryptionType)
//            .andThen(saveSelectedNetwork(network))
            .andThen { saveAccountData(accountName, mnemonic, derivationPath, encryptionType, network) }
    }

    override fun getSourceTypes(): Single<List<SourceType>> {
        return Single.just(listOf(SourceType.MNEMONIC_PASSPHRASE, SourceType.RAW_SEED, SourceType.KEYSTORE))
    }

    private fun saveSelectedEncryptionType(encryptionType: CryptoType): Completable {
        return getSelectedAddress()
            .flatMapCompletable {
                Completable.fromAction {
                    accountDatasource.saveCryptoType(encryptionType, it)
                }
            }
    }

    override fun importFromMnemonic(keyString: String, username: String, derivationPath: String, selectedEncryptionType: CryptoType, networkType: NetworkType): Completable {
        return Completable.fromAction {
            saveAccountData(username, keyString, derivationPath, selectedEncryptionType, networkType)
        }
    }

    override fun importFromSeed(keyString: String, username: String, derivationPath: String, selectedEncryptionType: CryptoType, networkType: NetworkType): Completable {
        return Completable.fromAction {
            val keys = keypairFactory.generate(mapCryptoTypeToEncryption(selectedEncryptionType), Hex.decode(keyString), derivationPath)
            val addressType = mapNetworkTypeToAddressType(networkType)
            val address = sS58Encoder.encode(keys.publicKey, addressType)

            accountDatasource.saveAccountName(username, address)
            accountDatasource.saveDerivationPath(derivationPath, address)
            accountDatasource.saveSeed(Hex.decode(keyString), address)
            accountDatasource.setMnemonicIsBackedUp(true)

            addAccountToList(username, address, Hex.toHexString(keys.publicKey), selectedEncryptionType.ordinal, networkType.ordinal)
        }
    }

    private fun mapCryptoTypeToEncryption(cryptoType: CryptoType): EncryptionType {
        return when (cryptoType) {
            CryptoType.SR25519 -> EncryptionType.SR25519
            CryptoType.ED25519 -> EncryptionType.ED25519
            CryptoType.ECDSA -> EncryptionType.ECDCA
        }
    }

    private fun mapNetworkTypeToAddressType(networkType: NetworkType): AddressType {
        return when (networkType) {
            NetworkType.KUSAMA -> AddressType.KUSAMA
            NetworkType.POLKADOT -> AddressType.POLKADOT
            NetworkType.WESTEND -> AddressType.POLKADOT
        }
    }

    override fun importFromJson(json: String, password: String, networkType: NetworkType): Completable {
        return Completable.complete()
    }

    override fun generateMnemonic(): Single<List<String>> {
        return Single.fromCallable {
            val mnemonic = bip39.generateMnemonic(Words.TWELVE)
            mnemonic.split(" ")
        }
    }

    private fun saveAccountData(accountName: String, mnemonic: String, derivationPath: String, cryptoType: CryptoType, networkType: NetworkType) {
        val entropy = bip39.generateEntropy(mnemonic)
        val password = junctionDecoder.getPassword(derivationPath)
        val seed = bip39.generateSeed(entropy, password)
        val keys = keypairFactory.generate(mapCryptoTypeToEncryption(cryptoType), seed, derivationPath)
        val addressType = mapNetworkTypeToAddressType(networkType)
        val address = sS58Encoder.encode(keys.publicKey, addressType)

        accountDatasource.saveAccountName(accountName, address)
        accountDatasource.saveDerivationPath(derivationPath, address)
        accountDatasource.saveSeed(seed, address)
        accountDatasource.saveEntropy(entropy, address)
        accountDatasource.setMnemonicIsBackedUp(true)

        addAccountToList(accountName, address, Hex.toHexString(keys.publicKey), cryptoType.ordinal, networkType.ordinal)
    }

    private fun addAccountToList(accountName: String, address: String, publicKeyHex: String, cryptoType: Int, networkType: Int) {
        userDao.insert(UserLocal(address, accountName, publicKeyHex, cryptoType, networkType))
    }
}