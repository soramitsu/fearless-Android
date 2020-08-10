package jp.co.soramitsu.feature_account_impl.data.repository

import io.reactivex.Completable
import io.reactivex.Single
import jp.co.soramitsu.common.data.network.AppLinksProvider
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.model.CryptoType
import jp.co.soramitsu.feature_account_api.domain.model.Network
import jp.co.soramitsu.feature_account_api.domain.model.NetworkType
import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_account_impl.data.repository.datasource.AccountDatasource
import org.spongycastle.util.encoders.Hex

class AccountRepositoryImpl(
    private val accountDatasource: AccountDatasource,
    private val appLinksProvider: AppLinksProvider
) : AccountRepository {

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
        return Single.just(listOf(
            Network("Kusama", NetworkType.KUSAMA, "wss://kusama-rpc.polkadot.io"),
            Network("Polkadot", NetworkType.POLKADOT, "wss://rpc.polkadot.io"),
            Network("Westend", NetworkType.WESTEND, "wss://westend-rpc.polkadot.io")
        ))
    }

    override fun getSelectedNetwork(): Single<NetworkType> {
        return Single.fromCallable {
            accountDatasource.getNetworkType() ?: NetworkType.KUSAMA
        }
    }

    override fun createAccount(accountName: String, encryptionType: CryptoType, derivationPath: String, networkType: NetworkType): Completable {
        return saveSelectedEncryptionType(encryptionType)
            .andThen(saveSelectedNetwork(networkType))
    }

    private fun saveSelectedEncryptionType(encryptionType: CryptoType): Completable {
        return getSelectedAddress()
            .flatMapCompletable {
                Completable.fromAction {
                    accountDatasource.saveCryptoType(encryptionType, it)
                }
            }
    }

    private fun saveSelectedNetwork(networkType: NetworkType): Completable {
        return Completable.fromAction {
            accountDatasource.saveNetworkType(networkType)
        }
    }

    override fun importFromMnemonic(keyString: String, username: String, derivationPath: String, selectedEncryptionType: CryptoType, node: Node): Completable {
        return Completable.fromAction {
            val entropy = bip39.generateEntropy(keyString)
            val password = junctionDecoder.getPassword(derivationPath)
            val seed = bip39.generateSeed(entropy, password)
            val keys = keypairFactory.generate(mapCryptoTypeToEncryption(selectedEncryptionType), seed, derivationPath)
            val addressType = mapNetworkTypeToAddressType(node.networkType)
            val address = sS58Encoder.encode(keys.publicKey, addressType)

            accountDatasource.saveAccountName(username, address)
            accountDatasource.saveDerivationPath(derivationPath, address)
            accountDatasource.saveSeed(seed, address)
            accountDatasource.setMnemonicIsBackedUp(true)
        }
    }

    override fun importFromSeed(keyString: String, username: String, derivationPath: String, selectedEncryptionType: CryptoType, node: Node): Completable {
        return Completable.fromAction {
            val keys = keypairFactory.generate(mapCryptoTypeToEncryption(selectedEncryptionType), Hex.decode(keyString), derivationPath)
            val addressType = mapNetworkTypeToAddressType(node.networkType)
            val address = sS58Encoder.encode(keys.publicKey, addressType)

            accountDatasource.saveAccountName(username, address)
            accountDatasource.saveDerivationPath(derivationPath, address)
            accountDatasource.saveSeed(Hex.decode(keyString), address)
            accountDatasource.setMnemonicIsBackedUp(true)
        }
    }

    override fun importFromJson(json: String, password: String, node: Node): Completable {
        return Completable.complete()
    }
}