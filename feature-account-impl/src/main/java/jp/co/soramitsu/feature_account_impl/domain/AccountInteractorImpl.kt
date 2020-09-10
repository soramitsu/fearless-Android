package jp.co.soramitsu.feature_account_impl.domain

import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.functions.BiFunction
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.model.CryptoType
import jp.co.soramitsu.feature_account_api.domain.model.Network
import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_account_api.domain.model.SourceType


class AccountInteractorImpl(
    private val accountRepository: AccountRepository
) : AccountInteractor {
    override fun getSelectedNetworkName(): Single<String> {
        return accountRepository.getSelectedNode()
            .observeOn(Schedulers.io())
            .map { it.networkType.readableName }
            .observeOn(AndroidSchedulers.mainThread())
    }

    override fun getMnemonic(): Single<List<String>> {
        return accountRepository.generateMnemonic()
    }

    override fun getSourceTypesWithSelected(): Single<Pair<List<SourceType>, SourceType>> {
        return accountRepository.getSourceTypes()
            .flatMap {
                Single.fromCallable {
                    Pair(it, it.first())
                }
            }
    }

    override fun getCryptoTypes(): Single<List<CryptoType>> {
        return accountRepository.getEncryptionTypes()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
    }

    override fun getPreferredCryptoType(): Single<CryptoType> {
        return accountRepository.getPreferredCryptoType()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
    }

    override fun createAccount(
        accountName: String,
        mnemonic: String,
        encryptionType: CryptoType,
        derivationPath: String,
        node: Node
    ): Completable {
        return accountRepository.createAccount(
            accountName,
            mnemonic,
            encryptionType,
            derivationPath,
            node
        )
    }

    override fun importFromMnemonic(
        keyString: String,
        username: String,
        derivationPath: String,
        selectedEncryptionType: CryptoType,
        node: Node
    ): Completable {
        return accountRepository.importFromMnemonic(
            keyString,
            username,
            derivationPath,
            selectedEncryptionType,
            node
        )
    }

    override fun importFromSeed(
        keyString: String,
        username: String,
        derivationPath: String,
        selectedEncryptionType: CryptoType,
        node: Node
    ): Completable {
        return accountRepository.importFromSeed(
            keyString,
            username,
            derivationPath,
            selectedEncryptionType,
            node
        )
    }

    override fun importFromJson(
        json: String,
        password: String,
        node: Node.NetworkType
    ): Completable {
        return accountRepository.importFromJson(json, password, node)
    }

    override fun getAddressId(): Single<ByteArray> {
        return accountRepository.getAddressId()
    }

    override fun getSelectedLanguage(): Single<String> {
        return Single.just("English")
    }

    override fun isCodeSet(): Single<Boolean> {
        return accountRepository.isCodeSet()
    }

    override fun savePin(code: String): Completable {
        return accountRepository.savePinCode(code)
    }

    override fun isPinCorrect(code: String): Single<Boolean> {
        return Single.fromCallable {
            val pinCode = accountRepository.getPinCode()
            pinCode == code
        }
    }

    override fun isBiometricEnabled(): Single<Boolean> {
        return accountRepository.isBiometricEnabled()
    }

    override fun setBiometricOn(): Completable {
        return accountRepository.setBiometricOn()
    }

    override fun setBiometricOff(): Completable {
        return accountRepository.setBiometricOff()
    }

    override fun getSelectedAccount() = accountRepository.getSelectedAccount()
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())

    override fun getNetworks(): Single<List<Network>> {
        return accountRepository.getNodes()
            .filter { it.isNotEmpty() }
            .firstOrError()
            .map(::formNetworkList)
    }

    override fun getSelectedNode() = accountRepository.getSelectedNode()
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())

    override fun getSelectedNetwork(): Single<Network> {
        return getNetworks()
            .subscribeOn(Schedulers.io())
            .zipWith<Node, Network>(
                getSelectedNode(),
                BiFunction { networks, selectedNode ->
                    networks.first { it.networkType == selectedNode.networkType }
                })
            .observeOn(AndroidSchedulers.mainThread())
    }

    private fun formNetworkList(
        allNodes: List<Node>
    ): List<Network> {
        return allNodes.groupBy(Node::networkType)
            .map { (networkType, nodesPerType) ->
                val defaultNode = nodesPerType.find(Node::isDefault)
                    ?: throw IllegalArgumentException("No default node for ${networkType.readableName} network")

                Network(networkType.readableName, networkType, defaultNode)
            }
    }
}