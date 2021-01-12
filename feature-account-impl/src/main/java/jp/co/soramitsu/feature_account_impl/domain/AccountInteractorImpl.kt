package jp.co.soramitsu.feature_account_impl.domain

import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_account_api.domain.model.CryptoType
import jp.co.soramitsu.feature_account_api.domain.model.ImportJsonData
import jp.co.soramitsu.feature_account_api.domain.model.Language
import jp.co.soramitsu.feature_account_api.domain.model.Network
import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_account_api.domain.model.SecuritySource
import jp.co.soramitsu.feature_account_impl.domain.errors.NodeAlreadyExistsException
import jp.co.soramitsu.feature_account_impl.domain.errors.UnsupportedNetworkException

class AccountInteractorImpl(
    private val accountRepository: AccountRepository
) : AccountInteractor {
    override fun getSecuritySource(accountAddress: String): Single<SecuritySource> {
        return accountRepository.getSecuritySource(accountAddress)
    }

    override fun generateMnemonic(): Single<List<String>> {
        return accountRepository.generateMnemonic()
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
        networkType: Node.NetworkType
    ): Completable {
        return accountRepository.createAccount(
            accountName,
            mnemonic,
            encryptionType,
            derivationPath,
            networkType
        )
    }

    override fun importFromMnemonic(
        keyString: String,
        username: String,
        derivationPath: String,
        selectedEncryptionType: CryptoType,
        networkType: Node.NetworkType
    ): Completable {
        return accountRepository.importFromMnemonic(
            keyString,
            username,
            derivationPath,
            selectedEncryptionType,
            networkType
        )
    }

    override fun importFromSeed(
        keyString: String,
        username: String,
        derivationPath: String,
        selectedEncryptionType: CryptoType,
        networkType: Node.NetworkType
    ): Completable {
        return accountRepository.importFromSeed(
            keyString,
            username,
            derivationPath,
            selectedEncryptionType,
            networkType
        )
    }

    override fun importFromJson(
        json: String,
        password: String,
        networkType: Node.NetworkType,
        name: String
    ): Completable {
        return accountRepository.importFromJson(json, password, networkType, name)
    }

    override fun getAddressId(address: String): Single<ByteArray> {
        return accountRepository.getAddressId(address)
    }

    override fun isCodeSet(): Boolean {
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

    override fun isBiometricEnabled(): Boolean {
        return accountRepository.isBiometricEnabled()
    }

    override fun setBiometricOn(): Completable {
        return accountRepository.setBiometricOn()
    }

    override fun setBiometricOff(): Completable {
        return accountRepository.setBiometricOff()
    }

    override fun getAccount(address: String): Single<Account> {
        return accountRepository.getAccount(address)
    }

    override fun observeSelectedAccount() = accountRepository.observeSelectedAccount()

    override fun getNetworks(): Single<List<Network>> {
        return accountRepository.getNetworks()
    }

    override fun getSelectedNode() = accountRepository.getSelectedNode()
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())

    override fun getSelectedNetworkType(): Single<Node.NetworkType> {
        return getSelectedNode().map(Node::networkType)
    }

    override fun shouldOpenOnboarding(): Single<Boolean> {
        return accountRepository.isAccountSelected()
            .map(Boolean::not)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
    }

    override fun observeGroupedAccounts(): Observable<List<Any>> {
        return accountRepository.observeAccounts()
            .map(::mergeAccountsWithNetworks)
    }

    override fun selectAccount(address: String): Completable {
        return accountRepository.getAccount(address)
            .subscribeOn(Schedulers.io())
            .flatMapCompletable(::selectAccount)
            .observeOn(AndroidSchedulers.mainThread())
    }

    override fun updateAccountName(account: Account, newName: String): Completable {
        val newAccount = account.copy(name = newName)

        return accountRepository.updateAccount(newAccount)
            .andThen(maybeUpdateSelectedAccount(newAccount))
    }

    override fun deleteAccount(address: String): Completable {
        return accountRepository.deleteAccount(address)
    }

    override fun updateAccountPositionsInNetwork(newOrdering: List<Account>): Completable {
        return Single.fromCallable {
            newOrdering.mapIndexed { index: Int, account: Account ->
                account.copy(position = index)
            }
        }.flatMapCompletable(accountRepository::updateAccounts)
    }

    private fun maybeUpdateSelectedAccount(newAccount: Account): Completable {
        return accountRepository.observeSelectedAccount()
            .firstOrError()
            .flatMapCompletable {
                if (it.address == newAccount.address) {
                    accountRepository.selectAccount(newAccount)
                } else {
                    Completable.complete()
                }
            }
    }

    private fun selectAccount(account: Account): Completable {
        return accountRepository.getDefaultNode(account.network.type)
            .flatMapCompletable(accountRepository::selectNode)
            .andThen(accountRepository.selectAccount(account))
    }

    private fun mergeAccountsWithNetworks(accounts: List<Account>): List<Any> {
        return accounts.groupBy { it.network.type }
            .map { (network, accounts) -> listOf(network, *accounts.toTypedArray()) }
            .flatten()
    }

    override fun observeNodes(): Observable<List<Node>> {
        return accountRepository.observeNodes()
    }

    override fun observeSelectedNode(): Observable<Node> {
        return accountRepository.observeSelectedNode()
    }

    override fun getNode(nodeId: Int): Single<Node> {
        return accountRepository.getNode(nodeId)
    }

    override fun processAccountJson(json: String): Single<ImportJsonData> {
        return accountRepository.processAccountJson(json)
    }

    override fun observeLanguages(): Observable<List<Language>> {
        return accountRepository.observeLanguages()
    }

    override fun getSelectedLanguage(): Single<Language> {
        return accountRepository.getSelectedLanguage()
    }

    override fun changeSelectedLanguage(language: Language): Completable {
        return accountRepository.changeLanguage(language)
    }

    override fun addNode(nodeName: String, nodeHost: String): Completable {
        return accountRepository.checkNodeExists(nodeHost)
            .flatMap { nodeExists ->
                if (nodeExists) {
                    throw NodeAlreadyExistsException()
                } else {
                    getNetworkTypeByNodeHost(nodeHost)
                }
            }
            .flatMapCompletable { networkType -> accountRepository.addNode(nodeName, nodeHost, networkType) }
    }

    override fun updateNode(nodeId: Int, newName: String, newHost: String): Completable {
        return getNetworkTypeByNodeHost(newHost)
            .flatMapCompletable { networkType -> accountRepository.updateNode(nodeId, newName, newHost, networkType) }
    }

    private fun getNetworkTypeByNodeHost(nodeHost: String): Single<Node.NetworkType> {
        return accountRepository.getNetworkName(nodeHost)
            .map { networkName ->
                val supportedNetworks = Node.NetworkType.values()
                val networkType = supportedNetworks.firstOrNull { networkName == it.readableName }
                networkType ?: throw UnsupportedNetworkException()
            }
    }

    override fun getAccountsByNetworkTypeWithSelectedNode(networkType: Node.NetworkType): Single<Pair<List<Account>, Node>> {
        return accountRepository.getAccountsByNetworkType(networkType)
            .flatMap { accounts ->
                accountRepository.observeSelectedNode()
                    .firstOrError()
                    .map { Pair(accounts, it) }
            }
    }

    override fun selectNodeAndAccount(nodeId: Int, accountAddress: String): Completable {
        return accountRepository.getAccount(accountAddress)
            .flatMapCompletable { account ->
                accountRepository.getNode(nodeId)
                    .flatMapCompletable { node ->
                        accountRepository.selectNode(node)
                            .andThen(accountRepository.selectAccount(account))
                    }
            }
    }

    override fun selectNode(nodeId: Int): Completable {
        return accountRepository.getNode(nodeId)
            .flatMapCompletable(accountRepository::selectNode)
    }

    override fun deleteNode(nodeId: Int): Completable {
        return accountRepository.deleteNode(nodeId)
    }

    override fun generateRestoreJson(accountAddress: String, password: String): Single<String> {
        return accountRepository.getAccount(accountAddress)
            .flatMap { accountRepository.generateRestoreJson(it, password) }
    }
}